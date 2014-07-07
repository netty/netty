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

import io.netty.handler.codec.http2.Http2StreamRemovalPolicy.Action;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.PrimitiveCollections;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.handler.codec.http2.Http2Exception.*;
import static io.netty.handler.codec.http2.Http2Stream.State.*;

/**
 * Simple implementation of {@link Http2Connection}.
 */
public class DefaultHttp2Connection implements Http2Connection {

    private final Set<Listener> listeners = new HashSet<Listener>(4);
    private final IntObjectMap<Http2Stream> streamMap = new IntObjectHashMap<Http2Stream>();
    private final ConnectionStream connectionStream = new ConnectionStream();
    private final Set<Http2Stream> activeStreams = new LinkedHashSet<Http2Stream>();
    private final DefaultEndpoint localEndpoint;
    private final DefaultEndpoint remoteEndpoint;
    private final Http2StreamRemovalPolicy removalPolicy;

    /**
     * Creates a connection with an immediate stream removal policy.
     *
     * @param server whether or not this end-point is the server-side of the HTTP/2 connection.
     */
    public DefaultHttp2Connection(boolean server) {
        this(server, immediateRemovalPolicy());
    }

    /**
     * Creates a new connection with the given settings.
     *
     * @param server whether or not this end-point is the server-side of the HTTP/2 connection.
     * @param removalPolicy the policy to be used for removal of closed stream.
     */
    public DefaultHttp2Connection(boolean server,
            Http2StreamRemovalPolicy removalPolicy) {
        if (removalPolicy == null) {
            throw new NullPointerException("removalPolicy");
        }
        this.removalPolicy = removalPolicy;
        localEndpoint = new DefaultEndpoint(server);
        remoteEndpoint = new DefaultEndpoint(!server);

        // Tell the removal policy how to remove a stream from this connection.
        removalPolicy.setAction(new Action() {
            @Override
            public void removeStream(Http2Stream stream) {
                DefaultHttp2Connection.this.removeStream((DefaultStream) stream);
            }
        });

        // Add the connection stream to the map.
        streamMap.put(connectionStream.id(), connectionStream);
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
    public boolean isServer() {
        return localEndpoint.isServer();
    }

    @Override
    public Http2Stream connectionStream() {
        return connectionStream;
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
    public boolean isGoAway() {
        return localEndpoint.isGoAwayReceived() || remoteEndpoint.isGoAwayReceived();
    }

    private void removeStream(DefaultStream stream) {
        // Notify the observers of the event first.
        for (Listener listener : listeners) {
            listener.streamRemoved(stream);
        }

        // Remove it from the map and priority tree.
        streamMap.remove(stream.id());
        ((DefaultStream) stream.parent()).removeChild(stream);
    }

    private void activate(DefaultStream stream) {
        activeStreams.add(stream);

        // Update the number of active streams initiated by the endpoint.
        stream.createdBy().numActiveStreams++;

        // Notify the listeners.
        for (Listener listener : listeners) {
            listener.streamActive(stream);
        }
    }

    /**
     * Simple stream implementation. Streams can be compared to each other by priority.
     */
    private class DefaultStream implements Http2Stream {
        private final int id;
        private State state = IDLE;
        private short weight = DEFAULT_PRIORITY_WEIGHT;
        private DefaultStream parent;
        private IntObjectMap<DefaultStream> children = newChildMap();
        private int totalChildWeights;
        private boolean terminateSent;
        private boolean terminateReceived;
        private FlowState inboundFlow;
        private FlowState outboundFlow;
        private Object data;

        DefaultStream(int id) {
            this.id = id;
        }

        @Override
        public final int id() {
            return id;
        }

        @Override
        public final State state() {
            return state;
        }

        @Override
        public boolean isTerminateReceived() {
            return terminateReceived;
        }

        @Override
        public void terminateReceived() {
            terminateReceived = true;
        }

        @Override
        public boolean isTerminateSent() {
            return terminateSent;
        }

        @Override
        public void terminateSent() {
            terminateSent = true;
        }

        @Override
        public boolean isTerminated() {
            return terminateSent || terminateReceived;
        }

        @Override
        public void data(Object data) {
            this.data = data;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T data() {
            return (T) data;
        }

        @Override
        public FlowState inboundFlow() {
            return inboundFlow;
        }

        @Override
        public void inboundFlow(FlowState state) {
            inboundFlow = state;
        }

        @Override
        public FlowState outboundFlow() {
            return outboundFlow;
        }

        @Override
        public void outboundFlow(FlowState state) {
            outboundFlow = state;
        }

        @Override
        public final boolean isRoot() {
            return parent == null;
        }

        @Override
        public final short weight() {
            return weight;
        }

        @Override
        public final int totalChildWeights() {
            return totalChildWeights;
        }

        @Override
        public final Http2Stream parent() {
            return parent;
        }

        @Override
        public final boolean isDescendantOf(Http2Stream stream) {
            Http2Stream next = parent();
            while (next != null) {
                if (next == stream) {
                    return true;
                }
                next = next.parent();
            }
            return false;
        }

        @Override
        public final boolean isLeaf() {
            return numChildren() == 0;
        }

        @Override
        public final int numChildren() {
            return children.size();
        }

        @Override
        public final Collection<? extends Http2Stream> children() {
            DefaultStream[] childrenArray = children.values(DefaultStream.class);
            return Arrays.asList(childrenArray);
        }

        @Override
        public final boolean hasChild(int streamId) {
            return child(streamId) != null;
        }

        @Override
        public final Http2Stream child(int streamId) {
            return children.get(streamId);
        }

        @Override
        public Http2Stream setPriority(int parentStreamId, short weight, boolean exclusive)
                throws Http2Exception {
            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException(String.format(
                        "Invalid weight: %d.  Must be between %d and %d (inclusive).", weight,
                        MIN_WEIGHT, MAX_WEIGHT));
            }

            // Get the parent stream.
            DefaultStream newParent = (DefaultStream) requireStream(parentStreamId);
            if (this == newParent) {
                throw new IllegalArgumentException("A stream cannot depend on itself");
            }

            // Already have a priority. Re-prioritize the stream.
            weight(weight);

            boolean needToRestructure = newParent.isDescendantOf(this);
            DefaultStream oldParent = (DefaultStream) parent();
            try {
                if (newParent == oldParent && !exclusive) {
                    // No changes were made to the tree structure.
                    return this;
                }

                // Break off the priority branch from it's current parent.
                oldParent.removeChildBranch(this);

                if (needToRestructure) {
                    // Adding a circular dependency (priority<->newParent). Break off the new
                    // parent's branch and add it above this priority.
                    ((DefaultStream) newParent.parent()).removeChildBranch(newParent);
                    oldParent.addChild(newParent, false);
                }

                // Add the priority under the new parent.
                newParent.addChild(this, exclusive);
                return this;
            } finally {
                // Notify observers.
                if (needToRestructure) {
                    notifyPrioritySubtreeChanged(this, newParent);
                } else {
                    notifyPriorityChanged(this, oldParent);
                }
            }
        }

        private void notifyPriorityChanged(Http2Stream stream, Http2Stream previousParent) {
            for (Listener listener : listeners) {
                listener.streamPriorityChanged(stream, previousParent);
            }
        }

        private void notifyPrioritySubtreeChanged(Http2Stream stream, Http2Stream subtreeRoot) {
            for (Listener listener : listeners) {
                listener.streamPrioritySubtreeChanged(stream, subtreeRoot);
            }
        }

        @Override
        public Http2Stream verifyState(Http2Error error, State... allowedStates)
                throws Http2Exception {
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
            activate(this);
            return this;
        }

        @Override
        public Http2Stream close() {
            if (state == CLOSED) {
                return this;
            }

            state = CLOSED;
            deactivate(this);

            // Mark this stream for removal.
            removalPolicy.markForRemoval(this);
            return this;
        }

        private void deactivate(DefaultStream stream) {
            activeStreams.remove(stream);

            // Update the number of active streams initiated by the endpoint.
            stream.createdBy().numActiveStreams--;

            // Notify the listeners.
            for (Listener listener : listeners) {
                listener.streamInactive(stream);
            }
        }

        @Override
        public Http2Stream closeLocalSide() {
            switch (state) {
                case OPEN:
                    state = HALF_CLOSED_LOCAL;
                    notifyHalfClosed(this);
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
                    notifyHalfClosed(this);
                    break;
                case HALF_CLOSED_REMOTE:
                    break;
                default:
                    close();
                    break;
            }
            return this;
        }

        private void notifyHalfClosed(Http2Stream stream) {
            for (Listener listener : listeners) {
                listener.streamHalfClosed(stream);
            }
        }

        @Override
        public final boolean remoteSideOpen() {
            return state == HALF_CLOSED_LOCAL || state == OPEN || state == RESERVED_REMOTE;
        }

        @Override
        public final boolean localSideOpen() {
            return state == HALF_CLOSED_REMOTE || state == OPEN || state == RESERVED_LOCAL;
        }

        final DefaultEndpoint createdBy() {
            return localEndpoint.createdStreamId(id)? localEndpoint : remoteEndpoint;
        }

        final void weight(short weight) {
            if (parent != null && weight != this.weight) {
                int delta = weight - this.weight;
                parent.totalChildWeights += delta;
            }
            this.weight = weight;
        }

        final IntObjectMap<DefaultStream> removeAllChildren() {
            if (children.isEmpty()) {
                return PrimitiveCollections.emptyIntObjectMap();
            }

            totalChildWeights = 0;
            IntObjectMap<DefaultStream> prevChildren = children;
            children = newChildMap();
            return prevChildren;
        }

        /**
         * Adds a child to this priority. If exclusive is set, any children of this node are moved
         * to being dependent on the child.
         */
        final void addChild(DefaultStream child, boolean exclusive) {
            if (exclusive) {
                // If it was requested that this child be the exclusive dependency of this node,
                // move any previous children to the child node, becoming grand children
                // of this node.
                for (DefaultStream grandchild : removeAllChildren().values(DefaultStream.class)) {
                    child.addChild(grandchild, false);
                }
            }

            child.parent = this;
            if (children.put(child.id(), child) == null) {
                totalChildWeights += child.weight();
            }
        }

        /**
         * Removes the child priority and moves any of its dependencies to being direct dependencies
         * on this node.
         */
        final void removeChild(DefaultStream child) {
            if (children.remove(child.id()) != null) {
                child.parent = null;
                totalChildWeights -= child.weight();

                // Move up any grand children to be directly dependent on this node.
                for (DefaultStream grandchild : child.children.values(DefaultStream.class)) {
                    addChild(grandchild, false);
                }
            }
        }

        /**
         * Removes the child priority but unlike {@link #removeChild}, leaves its branch unaffected.
         */
        final void removeChildBranch(DefaultStream child) {
            if (children.remove(child.id()) != null) {
                child.parent = null;
                totalChildWeights -= child.weight();
            }
        }
    }

    private static IntObjectMap<DefaultStream> newChildMap() {
        return new IntObjectHashMap<DefaultStream>(4);
    }

    /**
     * Stream class representing the connection, itself.
     */
    private final class ConnectionStream extends DefaultStream {
        private ConnectionStream() {
            super(CONNECTION_STREAM_ID);
        }

        @Override
        public Http2Stream setPriority(int parentStreamId, short weight, boolean exclusive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream verifyState(Http2Error error, State... allowedStates) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream openForPush() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream close() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream closeLocalSide() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream closeRemoteSide() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Simple endpoint implementation.
     */
    private final class DefaultEndpoint implements Endpoint {
        private final boolean server;
        private int nextStreamId;
        private int lastStreamCreated;
        private int lastKnownStream = -1;
        private boolean pushToAllowed = true;

        /**
         * The maximum number of active streams allowed to be created by this endpoint.
         */
        private int maxStreams;

        /**
         * The current number of active streams created by this endpoint.
         */
        private int numActiveStreams;

        DefaultEndpoint(boolean server) {
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
        public int nextStreamId() {
            // For manually created client-side streams, 1 is reserved for HTTP upgrade, so
            // start at 3.
            return nextStreamId > 1? nextStreamId : nextStreamId + 2;
        }

        @Override
        public boolean createdStreamId(int streamId) {
            boolean even = (streamId & 1) == 0;
            return server == even;
        }

        @Override
        public boolean acceptingNewStreams() {
            return nextStreamId() > 0 && numActiveStreams + 1 <= maxStreams;
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
            addStream(stream);
            activate(stream);
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
            addStream(stream);
            return stream;
        }

        private void addStream(DefaultStream stream) {
            // Add the stream to the map and priority tree.
            streamMap.put(stream.id(), stream);
            connectionStream.addChild(stream, false);

            // Notify the observers of the event.
            for (Listener listener : listeners) {
                listener.streamAdded(stream);
            }
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
        public int numActiveStreams() {
            return numActiveStreams;
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
        public int lastStreamCreated() {
            return lastStreamCreated;
        }

        @Override
        public int lastKnownStream() {
            return isGoAwayReceived()? lastKnownStream : lastStreamCreated;
        }

        @Override
        public boolean isGoAwayReceived() {
            return lastKnownStream >= 0;
        }

        @Override
        public void goAwayReceived(int lastKnownStream) {
            boolean alreadyNotified = isGoAway();
            this.lastKnownStream = lastKnownStream;
            if (!alreadyNotified) {
                notifyGoingAway();
            }
        }

        private void notifyGoingAway() {
            for (Listener listener : listeners) {
                listener.goingAway();
            }
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
            if (!acceptingNewStreams()) {
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
            if (!createdStreamId(streamId)) {
                throw protocolError("Request stream %d is not correct for %s connection", streamId,
                        server ? "server" : "client");
            }
        }

        private boolean isLocal() {
            return this == localEndpoint;
        }
    }
}
