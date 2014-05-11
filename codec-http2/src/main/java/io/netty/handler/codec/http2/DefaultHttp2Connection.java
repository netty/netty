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

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2Exception.format;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simple implementation of {@link Http2Connection}.
 */
public class DefaultHttp2Connection implements Http2Connection {

    private final Map<Integer, Http2Stream> streamMap = new HashMap<Integer, Http2Stream>();
    private final Set<Http2Stream> activeStreams = new LinkedHashSet<Http2Stream>();
    private final DefaultEndpoint localEndpoint;
    private final DefaultEndpoint remoteEndpoint;
    private boolean goAwaySent;
    private boolean goAwayReceived;

    public DefaultHttp2Connection(boolean server, boolean allowCompressedData) {
        localEndpoint = new DefaultEndpoint(server, allowCompressedData);
        remoteEndpoint = new DefaultEndpoint(!server, false);
    }

    @Override
    public boolean isServer() {
        return localEndpoint.isServer();
    }

    @Override
    public Http2Stream requireStream(int streamId) throws Http2Exception {
        Http2Stream stream = stream(streamId);
        if (stream == null) {
            throw protocolError("Stream does not exist %d", streamId);
        }
        return stream;
    }

    @Override
    public Http2Stream stream(int streamId) {
        return streamMap.get(streamId);
    }

    @Override
    public int numActiveStreams() {
        return activeStreams.size();
    }

    @Override
    public Set<Http2Stream> activeStreams() {
        return Collections.unmodifiableSet(activeStreams);
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
    public void goAwaySent() {
        goAwaySent = true;
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

    /**
     * Simple stream implementation. Streams can be compared to each other by priority.
     */
    private final class DefaultStream implements Http2Stream {
        private final int id;
        private State state = State.IDLE;

        DefaultStream(int id) {
            this.id = id;
        }

        @Override
        public int id() {
            return id;
        }

        @Override
        public State state() {
            return state;
        }

        @Override
        public Http2Stream verifyState(Http2Error error, State... allowedStates) throws Http2Exception {
            for (State allowedState : allowedStates) {
                if (state == allowedState) {
                    return this;
                }
            }
            throw format(error, "Stream %d in unexpected state: %s", id, state);
        }

        @Override
        public Http2Stream openForPush() throws Http2Exception {
            switch (state) {
                case RESERVED_LOCAL:
                    state = HALF_CLOSED_REMOTE;
                    break;
                case RESERVED_REMOTE:
                    state = HALF_CLOSED_LOCAL;
                    break;
                default:
                    throw protocolError("Attempting to open non-reserved stream for push");
            }
            return this;
        }

        @Override
        public Http2Stream close() {
            if (state == State.CLOSED) {
                return this;
            }

            state = State.CLOSED;
            activeStreams.remove(this);
            streamMap.remove(id);
            return this;
        }

        @Override
        public Http2Stream closeLocalSide() {
            switch (state) {
                case OPEN:
                    state = HALF_CLOSED_LOCAL;
                    break;
                case HALF_CLOSED_LOCAL:
                    break;
                default:
                    close();
                    break;
            }
            return this;
        }

        @Override
        public Http2Stream closeRemoteSide() {
            switch (state) {
                case OPEN:
                    state = HALF_CLOSED_REMOTE;
                    break;
                case HALF_CLOSED_REMOTE:
                    break;
                default:
                    close();
                    break;
            }
            return this;
        }

        @Override
        public boolean remoteSideOpen() {
            return state == HALF_CLOSED_LOCAL || state == OPEN || state == RESERVED_REMOTE;
        }

        @Override
        public boolean localSideOpen() {
            return state == HALF_CLOSED_REMOTE || state == OPEN || state == RESERVED_LOCAL;
        }
    }

    /**
     * Simple endpoint implementation.
     */
    private final class DefaultEndpoint implements Endpoint {
        private final boolean server;
        private int nextStreamId;
        private int lastStreamCreated;
        private int maxStreams;
        private boolean pushToAllowed;
        private boolean allowCompressedData;

        DefaultEndpoint(boolean server, boolean allowCompressedData) {
            this.allowCompressedData = allowCompressedData;
            this.server = server;

            // Determine the starting stream ID for this endpoint. Client-initiated streams
            // are odd and server-initiated streams are even. Zero is reserved for the
            // connection. Stream 1 is reserved client-initiated stream for responding to an
            // upgrade from HTTP 1.1.
            nextStreamId = server ? 2 : 1;

            // Push is disallowed by default for servers and allowed for clients.
            pushToAllowed = !server;
            maxStreams = Integer.MAX_VALUE;
        }

        @Override
        public DefaultStream createStream(int streamId, boolean halfClosed) throws Http2Exception {
            checkNewStreamAllowed(streamId);

            // Create and initialize the stream.
            DefaultStream stream = new DefaultStream(streamId);
            if (halfClosed) {
                stream.state = isLocal() ? HALF_CLOSED_LOCAL : HALF_CLOSED_REMOTE;
            } else {
                stream.state = OPEN;
            }

            // Update the next and last stream IDs.
            nextStreamId = streamId + 2;
            lastStreamCreated = streamId;

            // Register the stream and mark it as active.
            streamMap.put(streamId, stream);
            activeStreams.add(stream);
            return stream;
        }

        @Override
        public boolean isServer() {
            return server;
        }

        @Override
        public DefaultStream reservePushStream(int streamId, Http2Stream parent)
                throws Http2Exception {
            if (parent == null) {
                throw protocolError("Parent stream missing");
            }
            if (isLocal() ? !parent.localSideOpen() : !parent.remoteSideOpen()) {
                throw protocolError("Stream %d is not open for sending push promise", parent.id());
            }
            if (!opposite().allowPushTo()) {
                throw protocolError("Server push not allowed to opposite endpoint.");
            }

            // Create and initialize the stream.
            DefaultStream stream = new DefaultStream(streamId);
            stream.state = isLocal() ? RESERVED_LOCAL : RESERVED_REMOTE;

            // Update the next and last stream IDs.
            nextStreamId = streamId + 2;
            lastStreamCreated = streamId;

            // Register the stream.
            streamMap.put(streamId, stream);
            return stream;
        }

        @Override
        public void allowPushTo(boolean allow) {
            if (allow && server) {
                throw new IllegalArgumentException("Servers do not allow push");
            }
            pushToAllowed = allow;
        }

        @Override
        public boolean allowPushTo() {
            return pushToAllowed;
        }

        @Override
        public int maxStreams() {
            return maxStreams;
        }

        @Override
        public void maxStreams(int maxStreams) {
            this.maxStreams = maxStreams;
        }

        @Override
        public boolean allowCompressedData() {
            return allowCompressedData;
        }

        @Override
        public void allowCompressedData(boolean allow) {
            allowCompressedData = allow;
        }

        @Override
        public int lastStreamCreated() {
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
            verifyStreamId(streamId);
            if (streamMap.size() + 1 > maxStreams) {
                throw protocolError("Maximum streams exceeded for this endpoint.");
            }
        }

        private void verifyStreamId(int streamId) throws Http2Exception {
            if (nextStreamId < 0) {
                throw protocolError("No more streams can be created on this connection");
            }
            if (streamId < nextStreamId) {
                throw protocolError("Request stream %d is behind the next expected stream %d",
                        streamId, nextStreamId);
            }
            boolean even = (streamId & 1) == 0;
            if (server != even) {
                throw protocolError("Request stream %d is not correct for %s connection", streamId,
                        server ? "server" : "client");
            }
        }

        private boolean isLocal() {
            return this == localEndpoint;
        }
    }
}
