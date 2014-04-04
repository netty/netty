/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2.draft10.connection;

import static io.netty.handler.codec.http2.draft10.Http2Error.NO_ERROR;
import static io.netty.handler.codec.http2.draft10.Http2Exception.format;
import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.toByteBuf;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.DEFAULT_STREAM_PRIORITY;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.draft10.Http2Error;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.connection.Http2Stream.State;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2GoAwayFrame;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.TreeMultiset;

public class DefaultHttp2Connection implements Http2Connection {

  private final List<Listener> listeners = Lists.newArrayList();
  private final Map<Integer, Http2Stream> streamMap = Maps.newHashMap();
  private final Multiset<Http2Stream> activeStreams = TreeMultiset.create();
  private final DefaultEndpoint localEndpoint;
  private final DefaultEndpoint remoteEndpoint;
  private boolean goAwaySent;
  private boolean goAwayReceived;
  private ChannelFutureListener closeListener;

  public DefaultHttp2Connection(boolean server) {
    this.localEndpoint = new DefaultEndpoint(server);
    this.remoteEndpoint = new DefaultEndpoint(!server);
  }

  @Override
  public void addListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public Http2Stream getStreamOrFail(int streamId) throws Http2Exception {
    Http2Stream stream = getStream(streamId);
    if (stream == null) {
      throw protocolError("Stream does not exist %d", streamId);
    }
    return stream;
  }

  @Override
  public Http2Stream getStream(int streamId) {
    return streamMap.get(streamId);
  }

  @Override
  public List<Http2Stream> getActiveStreams() {
    // Copy the list in case any operation on the returned streams causes the activeStreams set
    // to change.
    return ImmutableList.copyOf(activeStreams);
  }

  @Override
  public Endpoint local() {
    return localEndpoint;
  }

  @Override
  public Endpoint remote() {
    return remoteEndpoint;
  }

  @Override
  public void sendGoAway(ChannelHandlerContext ctx, ChannelPromise promise, Http2Exception cause) {
    closeListener = getOrCreateCloseListener(ctx, promise);
    ChannelFuture future;
    if (!goAwaySent) {
      goAwaySent = true;

      int errorCode = cause != null ? cause.getError().getCode() : NO_ERROR.getCode();
      ByteBuf debugData = toByteBuf(ctx, cause);

      Http2GoAwayFrame frame = new DefaultHttp2GoAwayFrame.Builder().setErrorCode(errorCode)
          .setLastStreamId(remote().getLastStreamCreated()).setDebugData(debugData).build();
      future = ctx.writeAndFlush(frame);
    } else {
      future = ctx.newSucceededFuture();
    }

    // If there are no active streams, close immediately after the send is complete.
    // Otherwise wait until all streams are inactive.
    if (cause != null || activeStreams.isEmpty()) {
      future.addListener(closeListener);
    }
  }

  @Override
  public void goAwayReceived() {
    goAwayReceived = true;
  }

  @Override
  public boolean isGoAwaySent() {
    return goAwaySent;
  }

  @Override
  public boolean isGoAwayReceived() {
    return goAwayReceived;
  }

  @Override
  public boolean isGoAway() {
    return isGoAwaySent() || isGoAwayReceived();
  }

  private ChannelFutureListener getOrCreateCloseListener(final ChannelHandlerContext ctx,
      final ChannelPromise promise) {
    if (closeListener == null) {
      closeListener = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          ctx.close(promise);
        }
      };
    }
    return closeListener;
  }

  private void notifyStreamClosed(int id) {
    for (Listener listener : listeners) {
      listener.streamClosed(id);
    }
  }

  private void notifyStreamCreated(int id) {
    for (Listener listener : listeners) {
      listener.streamCreated(id);
    }
  }

  /**
   * Simple stream implementation. Streams can be compared to each other by priority.
   */
  private class DefaultStream implements Http2Stream {
    private final int id;
    private State state = State.IDLE;
    private int priority;

    public DefaultStream(int id) {
      this.id = id;
      this.priority = DEFAULT_STREAM_PRIORITY;
    }

    @Override
    public int getId() {
      return id;
    }

    @Override
    public State getState() {
      return state;
    }

    @Override
    public int compareTo(Http2Stream other) {
      // Sort streams with the same priority by their ID.
      if (priority == other.getPriority()) {
        return id - other.getId();
      }
      return priority - other.getPriority();
    }

    @Override
    public void verifyState(Http2Error error, State... allowedStates) throws Http2Exception {
      Predicate<State> predicate = Predicates.in(Arrays.asList(allowedStates));
      if (!predicate.apply(state)) {
        throw format(error, "Stream %d in unexpected state: %s", id, state);
      }
    }

    @Override
    public void setPriority(int priority) throws Http2Exception {
      if (priority < 0) {
        throw protocolError("Invalid priority: %d", priority);
      }

      // If it was active, we must remove it from the set before changing the priority.
      // Otherwise it won't be able to locate the stream in the set.
      boolean wasActive = activeStreams.remove(this);
      this.priority = priority;

      // If this stream was in the active set, re-add it so that it's properly sorted.
      if (wasActive) {
        activeStreams.add(this);
      }
    }

    @Override
    public int getPriority() {
      return priority;
    }

    @Override
    public void openForPush() throws Http2Exception {
      switch (state) {
        case RESERVED_LOCAL:
          state = State.HALF_CLOSED_REMOTE;
          break;
        case RESERVED_REMOTE:
          state = State.HALF_CLOSED_LOCAL;
          break;
        default:
          throw protocolError("Attempting to open non-reserved stream for push");
      }
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelFuture future) {
      if (state == State.CLOSED) {
        return;
      }

      state = State.CLOSED;
      activeStreams.remove(this);
      streamMap.remove(id);
      notifyStreamClosed(id);

      // If this connection is closing and there are no longer any
      // active streams, close after the current operation completes.
      if (closeListener != null && activeStreams.isEmpty()) {
        future.addListener(closeListener);
      }
    }

    @Override
    public void closeLocalSide(ChannelHandlerContext ctx, ChannelFuture future) {
      switch (state) {
        case OPEN:
        case HALF_CLOSED_LOCAL:
          state = State.HALF_CLOSED_LOCAL;
          break;
        case HALF_CLOSED_REMOTE:
        case RESERVED_LOCAL:
        case RESERVED_REMOTE:
        case IDLE:
        case CLOSED:
        default:
          close(ctx, future);
          break;
      }
    }

    @Override
    public void closeRemoteSide(ChannelHandlerContext ctx, ChannelFuture future) {
      switch (state) {
        case OPEN:
        case HALF_CLOSED_REMOTE:
          state = State.HALF_CLOSED_REMOTE;
          break;
        case RESERVED_LOCAL:
        case RESERVED_REMOTE:
        case IDLE:
        case HALF_CLOSED_LOCAL:
        case CLOSED:
        default:
          close(ctx, future);
          break;
      }
    }

    @Override
    public boolean isRemoteSideOpen() {
      switch (state) {
        case HALF_CLOSED_LOCAL:
        case OPEN:
        case RESERVED_REMOTE:
          return true;
        case IDLE:
        case RESERVED_LOCAL:
        case HALF_CLOSED_REMOTE:
        case CLOSED:
        default:
          return false;
      }
    }

    @Override
    public boolean isLocalSideOpen() {
      switch (state) {
        case HALF_CLOSED_REMOTE:
        case OPEN:
        case RESERVED_LOCAL:
          return true;
        case IDLE:
        case RESERVED_REMOTE:
        case HALF_CLOSED_LOCAL:
        case CLOSED:
        default:
          return false;
      }
    }
  }

  /**
   * Simple endpoint implementation.
   */
  private class DefaultEndpoint implements Endpoint {
    private int nextStreamId;
    private int lastStreamCreated;
    private int maxStreams = Integer.MAX_VALUE;
    private boolean pushToAllowed = true;

    public DefaultEndpoint(boolean serverEndpoint) {
      // Determine the starting stream ID for this endpoint. Zero is reserved for the
      // connection and 1 is reserved for responding to an upgrade from HTTP 1.1.
      // Client-initiated streams use odd identifiers and server-initiated streams use
      // even.
      nextStreamId = serverEndpoint ? 2 : 3;
    }

    @Override
    public DefaultStream createStream(int streamId, int priority, boolean halfClosed)
        throws Http2Exception {
      checkNewStreamAllowed(streamId);

      // Create and initialize the stream.
      DefaultStream stream = new DefaultStream(streamId);
      stream.setPriority(priority);
      if (halfClosed) {
        stream.state = isLocal() ? State.HALF_CLOSED_LOCAL : State.HALF_CLOSED_REMOTE;
      } else {
        stream.state = State.OPEN;
      }

      // Update the next and last stream IDs.
      nextStreamId += 2;
      lastStreamCreated = streamId;

      // Register the stream and mark it as active.
      streamMap.put(streamId, stream);
      activeStreams.add(stream);

      notifyStreamCreated(streamId);
      return stream;
    }

    @Override
    public DefaultStream reservePushStream(int streamId, Http2Stream parent) throws Http2Exception {
      if (parent == null) {
        throw protocolError("Parent stream missing");
      }
      if (isLocal() ? !parent.isLocalSideOpen() : !parent.isRemoteSideOpen()) {
        throw protocolError("Stream %d is not open for sending push promise", parent.getId());
      }
      if (!opposite().isPushToAllowed()) {
        throw protocolError("Server push not allowed to opposite endpoint.");
      }

      // Create and initialize the stream.
      DefaultStream stream = new DefaultStream(streamId);
      stream.setPriority(parent.getPriority() + 1);
      stream.state = isLocal() ? State.RESERVED_LOCAL : State.RESERVED_REMOTE;

      // Update the next and last stream IDs.
      nextStreamId += 2;
      lastStreamCreated = streamId;

      // Register the stream.
      streamMap.put(streamId, stream);

      notifyStreamCreated(streamId);
      return stream;
    }

    @Override
    public void setPushToAllowed(boolean allow) {
      this.pushToAllowed = allow;
    }

    @Override
    public boolean isPushToAllowed() {
      return pushToAllowed;
    }

    @Override
    public int getMaxStreams() {
      return maxStreams;
    }

    @Override
    public void setMaxStreams(int maxStreams) {
      this.maxStreams = maxStreams;
    }

    @Override
    public int getLastStreamCreated() {
      return lastStreamCreated;
    }

    @Override
    public Endpoint opposite() {
      return isLocal() ? remoteEndpoint : localEndpoint;
    }

    private void checkNewStreamAllowed(int streamId) throws Http2Exception {
      if (isGoAway()) {
        throw protocolError("Cannot create a stream since the connection is going away");
      }
      if (nextStreamId < 0) {
        throw protocolError("No more streams can be created on this connection");
      }
      if (streamId != nextStreamId) {
        throw protocolError("Incorrect next stream ID requested: %d", streamId);
      }
      if (streamMap.size() + 1 > maxStreams) {
        // TODO(nathanmittler): is this right?
        throw protocolError("Maximum streams exceeded for this endpoint.");
      }
    }

    private boolean isLocal() {
      return this == localEndpoint;
    }
  }
}
