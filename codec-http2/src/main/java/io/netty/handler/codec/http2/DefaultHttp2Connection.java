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

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.immediateRemovalPolicy;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.REFUSED_STREAM;
import static io.netty.handler.codec.http2.Http2Exception.closedStreamError;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2StreamRemovalPolicy.Action;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.PrimitiveCollections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple implementation of {@link Http2Connection}.
 */
public class DefaultHttp2Connection implements Http2Connection {

    private final Set<Listener> listeners = new HashSet<Listener>(4);
    private final IntObjectMap<Http2Stream> streamMap = new IntObjectHashMap<Http2Stream>();
    private final ConnectionStream connectionStream = new ConnectionStream();
    private final Set<Http2Stream> activeStreams = new LinkedHashSet<Http2Stream>();
    private final DefaultEndpoint<Http2LocalFlowController> localEndpoint;
    private final DefaultEndpoint<Http2RemoteFlowController> remoteEndpoint;
    private final Http2StreamRemovalPolicy removalPolicy;

    /**
     * Creates a connection with an immediate stream removal policy.
     *
     * @param server
     *            whether or not this end-point is the server-side of the HTTP/2 connection.
     */
    public DefaultHttp2Connection(boolean server) {
        this(server, immediateRemovalPolicy());
    }

    /**
     * Creates a new connection with the given settings.
     *
     * @param server
     *            whether or not this end-point is the server-side of the HTTP/2 connection.
     * @param removalPolicy
     *            the policy to be used for removal of closed stream.
     */
    public DefaultHttp2Connection(boolean server, Http2StreamRemovalPolicy removalPolicy) {
        this.removalPolicy = checkNotNull(removalPolicy, "removalPolicy");
        localEndpoint = new DefaultEndpoint<Http2LocalFlowController>(server);
        remoteEndpoint = new DefaultEndpoint<Http2RemoteFlowController>(!server);

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
            throw connectionError(PROTOCOL_ERROR, "Stream does not exist %d", streamId);
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
    public Endpoint<Http2LocalFlowController> local() {
        return localEndpoint;
    }

    @Override
    public Endpoint<Http2RemoteFlowController> remote() {
        return remoteEndpoint;
    }

    @Override
    public boolean goAwayReceived() {
        return localEndpoint.lastKnownStream >= 0;
    }

    @Override
    public void goAwayReceived(int lastKnownStream, long errorCode, ByteBuf debugData) {
        boolean alreadyNotified = goAwayReceived();
        localEndpoint.lastKnownStream(lastKnownStream);
        if (!alreadyNotified) {
            for (Listener listener : listeners) {
                listener.onGoAwayReceived(lastKnownStream, errorCode, debugData);
            }
        }
    }

    @Override
    public boolean goAwaySent() {
        return remoteEndpoint.lastKnownStream >= 0;
    }

    @Override
    public void goAwaySent(int lastKnownStream, long errorCode, ByteBuf debugData) {
        boolean alreadyNotified = goAwaySent();
        remoteEndpoint.lastKnownStream(lastKnownStream);
        if (!alreadyNotified) {
            for (Listener listener : listeners) {
                listener.onGoAwaySent(lastKnownStream, errorCode, debugData);
            }
        }
    }

    /**
     * Closed streams may stay in the priority tree if they have dependents that are in prioritizable states.
     * When a stream is requested to be removed we can only actually remove that stream when there are no more
     * prioritizable children.
     * (see [1] {@link Http2Stream#prioritizableForTree()} and [2] {@link DefaultStream#removeChild(DefaultStream)}).
     * When a priority tree edge changes we also have to re-evaluate viable nodes
     * (see [3] {@link DefaultStream#takeChild(DefaultStream, boolean, List)}).
     * @param stream The stream to remove.
     */
    void removeStream(DefaultStream stream) {
        // [1] Check if this stream can be removed because it has no prioritizable descendants.
        if (stream.parent().removeChild(stream)) {
            // Remove it from the map and priority tree.
            streamMap.remove(stream.id());

            for (Listener listener : listeners) {
                listener.onStreamRemoved(stream);
            }
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
        private IntObjectMap<DefaultStream> children = PrimitiveCollections.emptyIntObjectMap();
        private int totalChildWeights;
        private int prioritizableForTree = 1;
        private boolean resetSent;
        private PropertyMap data;

        DefaultStream(int id) {
            this.id = id;
            data = new LazyPropertyMap(this);
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
        public boolean isResetSent() {
            return resetSent;
        }

        @Override
        public Http2Stream resetSent() {
            resetSent = true;
            return this;
        }

        @Override
        public final Object setProperty(Object key, Object value) {
            return data.put(key, value);
        }

        @Override
        public final <V> V getProperty(Object key) {
            return data.get(key);
        }

        @Override
        public final <V> V removeProperty(Object key) {
            return data.remove(key);
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
        public final DefaultStream parent() {
            return parent;
        }

        @Override
        public final int prioritizableForTree() {
            return prioritizableForTree;
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
            return children.values();
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
        public Http2Stream setPriority(int parentStreamId, short weight, boolean exclusive) throws Http2Exception {
            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException(String.format(
                        "Invalid weight: %d.  Must be between %d and %d (inclusive).", weight, MIN_WEIGHT, MAX_WEIGHT));
            }

            DefaultStream newParent = (DefaultStream) stream(parentStreamId);
            if (newParent == null) {
                // Streams can depend on other streams in the IDLE state. We must ensure
                // the stream has been "created" in order to use it in the priority tree.
                newParent = createdBy().createStream(parentStreamId);
            } else if (this == newParent) {
                throw new IllegalArgumentException("A stream cannot depend on itself");
            }

            // Already have a priority. Re-prioritize the stream.
            weight(weight);

            if (newParent != parent() || (exclusive && newParent.numChildren() != 1)) {
                final List<ParentChangedEvent> events;
                if (newParent.isDescendantOf(this)) {
                    events = new ArrayList<ParentChangedEvent>(2 + (exclusive ? newParent.numChildren() : 0));
                    parent.takeChild(newParent, false, events);
                } else {
                    events = new ArrayList<ParentChangedEvent>(1 + (exclusive ? newParent.numChildren() : 0));
                }
                newParent.takeChild(this, exclusive, events);
                notifyParentChanged(events);
            }

            return this;
        }

        @Override
        public Http2Stream open(boolean halfClosed) throws Http2Exception {
            switch (state) {
            case IDLE:
                state = halfClosed ? isLocal() ? HALF_CLOSED_LOCAL : HALF_CLOSED_REMOTE : OPEN;
                break;
            case RESERVED_LOCAL:
                state = HALF_CLOSED_REMOTE;
                break;
            case RESERVED_REMOTE:
                state = HALF_CLOSED_LOCAL;
                break;
            default:
                throw streamError(id, PROTOCOL_ERROR, "Attempting to open a stream in an invalid state: " + state);
            }

            if (activeStreams.add(this)) {
                // Update the number of active streams initiated by the endpoint.
                createdBy().numActiveStreams++;

                // Notify the listeners.
                for (Listener listener : listeners) {
                    listener.onStreamActive(this);
                }
            }
            return this;
        }

        @Override
        public Http2Stream close() {
            if (state == CLOSED) {
                return this;
            }

            state = CLOSED;
            decrementPrioritizableForTree(1);
            if (activeStreams.remove(this)) {
                try {
                    // Update the number of active streams initiated by the endpoint.
                    createdBy().numActiveStreams--;

                    // Notify the listeners.
                    for (Listener listener : listeners) {
                        listener.onStreamClosed(this);
                    }
                } finally {
                    // Mark this stream for removal.
                    removalPolicy.markForRemoval(this);
                }
            }
            return this;
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

        /**
         * Recursively increment the {@link #prioritizableForTree} for this object up the parent links until
         * either we go past the root or {@code oldParent} is encountered.
         * @param amt The amount to increment by. This must be positive.
         * @param oldParent The previous parent for this stream.
         */
        private void incrementPrioritizableForTree(int amt, Http2Stream oldParent) {
            if (amt != 0) {
                incrementPrioritizableForTree0(amt, oldParent);
            }
        }

        /**
         * Direct calls to this method are discouraged.
         * Instead use {@link #incrementPrioritizableForTree(int, Http2Stream)}.
         */
        private void incrementPrioritizableForTree0(int amt, Http2Stream oldParent) {
            assert amt > 0;
            prioritizableForTree += amt;
            if (parent != null && parent != oldParent) {
                parent.incrementPrioritizableForTree(amt, oldParent);
            }
        }

        /**
         * Recursively increment the {@link #prioritizableForTree} for this object up the parent links until
         * either we go past the root.
         * @param amt The amount to decrement by. This must be positive.
         */
        private void decrementPrioritizableForTree(int amt) {
            if (amt != 0) {
                decrementPrioritizableForTree0(amt);
            }
        }

        /**
         * Direct calls to this method are discouraged. Instead use {@link #decrementPrioritizableForTree(int)}.
         */
        private void decrementPrioritizableForTree0(int amt) {
            assert amt > 0;
            prioritizableForTree -= amt;
            if (parent != null) {
                parent.decrementPrioritizableForTree(amt);
            }
        }

        /**
         * Determine if this node by itself is considered to be valid in the priority tree.
         */
        private boolean isPrioritizable() {
            return state != CLOSED;
        }

        private void notifyHalfClosed(Http2Stream stream) {
            for (Listener listener : listeners) {
                listener.onStreamHalfClosed(stream);
            }
        }

        private void initChildrenIfEmpty() {
            if (children == PrimitiveCollections.<DefaultStream>emptyIntObjectMap()) {
                children = new IntObjectHashMap<DefaultStream>(4);
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

        final DefaultEndpoint<? extends Http2FlowController> createdBy() {
            return localEndpoint.createdStreamId(id) ? localEndpoint : remoteEndpoint;
        }

        final boolean isLocal() {
            return localEndpoint.createdStreamId(id);
        }

        final void weight(short weight) {
            if (weight != this.weight) {
                if (parent != null) {
                    int delta = weight - this.weight;
                    parent.totalChildWeights += delta;
                }
                final short oldWeight = this.weight;
                this.weight = weight;
                for (Listener l : listeners) {
                    l.onWeightChanged(this, oldWeight);
                }
            }
        }

        final IntObjectMap<DefaultStream> removeAllChildren() {
            totalChildWeights = 0;
            prioritizableForTree = isPrioritizable() ? 1 : 0;
            IntObjectMap<DefaultStream> prevChildren = children;
            children = PrimitiveCollections.emptyIntObjectMap();
            return prevChildren;
        }

        /**
         * Adds a child to this priority. If exclusive is set, any children of this node are moved to being dependent on
         * the child.
         */
        final void takeChild(DefaultStream child, boolean exclusive, List<ParentChangedEvent> events) {
            DefaultStream oldParent = child.parent();
            events.add(new ParentChangedEvent(child, oldParent));
            notifyParentChanging(child, this);
            child.parent = this;

            if (exclusive && !children.isEmpty()) {
                // If it was requested that this child be the exclusive dependency of this node,
                // move any previous children to the child node, becoming grand children of this node.
                for (DefaultStream grandchild : removeAllChildren().values()) {
                    child.takeChild(grandchild, false, events);
                }
            }

            // Lazily initialize the children to save object allocations.
            initChildrenIfEmpty();

            if (children.put(child.id(), child) == null) {
                totalChildWeights += child.weight();
                incrementPrioritizableForTree(child.prioritizableForTree(), oldParent);
            }

            if (oldParent != null && oldParent.children.remove(child.id()) != null) {
                oldParent.totalChildWeights -= child.weight();
                if (!child.isDescendantOf(oldParent)) {
                    oldParent.decrementPrioritizableForTree(child.prioritizableForTree());
                    if (oldParent.prioritizableForTree() == 0) {
                        removeStream(oldParent);
                    }
                }
            }
        }

        /**
         * Removes the child priority and moves any of its dependencies to being direct dependencies on this node.
         */
        final boolean removeChild(DefaultStream child) {
            if (child.prioritizableForTree() == 0 && children.remove(child.id()) != null) {
                List<ParentChangedEvent> events = new ArrayList<ParentChangedEvent>(1 + child.numChildren());
                events.add(new ParentChangedEvent(child, child.parent()));
                notifyParentChanging(child, null);
                child.parent = null;
                totalChildWeights -= child.weight();
                decrementPrioritizableForTree(child.prioritizableForTree());

                // Move up any grand children to be directly dependent on this node.
                for (DefaultStream grandchild : child.children.values()) {
                    takeChild(grandchild, false, events);
                }

                if (prioritizableForTree() == 0) {
                    removeStream(this);
                }
                notifyParentChanged(events);
                return true;
            }
            return false;
        }
    }

    /**
     * Allows the data map to be lazily initialized for {@link DefaultStream}.
     */
    private interface PropertyMap {
        Object put(Object key, Object value);

        <V> V get(Object key);

        <V> V remove(Object key);
    }

    /**
     * Provides actual {@link HashMap} functionality for {@link DefaultStream}'s application data.
     */
    private static final class DefaultProperyMap implements PropertyMap {
        private final Map<Object, Object> data;

        DefaultProperyMap(int initialSize) {
            data = new HashMap<Object, Object>(initialSize);
        }

        @Override
        public Object put(Object key, Object value) {
            return data.put(key, value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <V> V get(Object key) {
            return (V) data.get(key);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <V> V remove(Object key) {
            return (V) data.remove(key);
        }
    }

    /**
     * Provides the lazy initialization for the {@link DefaultStream} data map.
     */
    private static final class LazyPropertyMap implements PropertyMap {
        private static final int DEFAULT_INITIAL_SIZE = 4;
        private final DefaultStream stream;

        LazyPropertyMap(DefaultStream stream) {
            this.stream = stream;
        }

        @Override
        public Object put(Object key, Object value) {
            stream.data = new DefaultProperyMap(DEFAULT_INITIAL_SIZE);
            return stream.data.put(key, value);
        }

        @Override
        public <V> V get(Object key) {
            stream.data = new DefaultProperyMap(DEFAULT_INITIAL_SIZE);
            return stream.data.get(key);
        }

        @Override
        public <V> V remove(Object key) {
            stream.data = new DefaultProperyMap(DEFAULT_INITIAL_SIZE);
            return stream.data.remove(key);
        }
    }

    /**
     * Allows a correlation to be made between a stream and its old parent before a parent change occurs
     */
    private static final class ParentChangedEvent {
        private final Http2Stream stream;
        private final Http2Stream oldParent;

        /**
         * Create a new instance
         * @param stream The stream who has had a parent change
         * @param oldParent The previous parent
         */
        ParentChangedEvent(Http2Stream stream, Http2Stream oldParent) {
            this.stream = stream;
            this.oldParent = oldParent;
        }

        /**
         * Notify all listeners of the tree change event
         * @param l The listener to notify
         */
        public void notifyListener(Listener l) {
            l.onPriorityTreeParentChanged(stream, oldParent);
        }
    }

    /**
     * Notify all listeners of the priority tree change events (in ascending order)
     * @param events The events (top down order) which have changed
     */
    private void notifyParentChanged(List<ParentChangedEvent> events) {
        for (int i = 0; i < events.size(); ++i) {
            ParentChangedEvent event = events.get(i);
            for (Listener l : listeners) {
                event.notifyListener(l);
            }
        }
    }

    private void notifyParentChanging(Http2Stream stream, Http2Stream newParent) {
        for (Listener l : listeners) {
            l.onPriorityTreeParentChanging(stream, newParent);
        }
    }

    /**
     * Stream class representing the connection, itself.
     */
    private final class ConnectionStream extends DefaultStream {
        ConnectionStream() {
            super(CONNECTION_STREAM_ID);
        }

        @Override
        public boolean isResetSent() {
            return false;
        }

        @Override
        public Http2Stream resetSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream setPriority(int parentStreamId, short weight, boolean exclusive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream open(boolean halfClosed) {
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
    private final class DefaultEndpoint<F extends Http2FlowController> implements Endpoint<F> {
        private final boolean server;
        private int nextStreamId;
        private int lastStreamCreated;
        private int lastKnownStream = -1;
        private boolean pushToAllowed = true;
        private F flowController;
        private int numActiveStreams;
        private int maxActiveStreams;

        DefaultEndpoint(boolean server) {
            this.server = server;

            // Determine the starting stream ID for this endpoint. Client-initiated streams
            // are odd and server-initiated streams are even. Zero is reserved for the
            // connection. Stream 1 is reserved client-initiated stream for responding to an
            // upgrade from HTTP 1.1.
            nextStreamId = server ? 2 : 1;

            // Push is disallowed by default for servers and allowed for clients.
            pushToAllowed = !server;
            maxActiveStreams = Integer.MAX_VALUE;
        }

        @Override
        public int nextStreamId() {
            // For manually created client-side streams, 1 is reserved for HTTP upgrade, so start at 3.
            return nextStreamId > 1 ? nextStreamId : nextStreamId + 2;
        }

        @Override
        public boolean createdStreamId(int streamId) {
            boolean even = (streamId & 1) == 0;
            return server == even;
        }

        @Override
        public boolean canCreateStream() {
            return nextStreamId() > 0 && numActiveStreams + 1 <= maxActiveStreams;
        }

        @Override
        public DefaultStream createStream(int streamId) throws Http2Exception {
            checkNewStreamAllowed(streamId);

            // Create and initialize the stream.
            DefaultStream stream = new DefaultStream(streamId);

            // Update the next and last stream IDs.
            nextStreamId = streamId + 2;
            lastStreamCreated = streamId;

            addStream(stream);
            return stream;
        }

        @Override
        public boolean isServer() {
            return server;
        }

        @Override
        public DefaultStream reservePushStream(int streamId, Http2Stream parent) throws Http2Exception {
            if (parent == null) {
                throw connectionError(PROTOCOL_ERROR, "Parent stream missing");
            }
            if (isLocal() ? !parent.localSideOpen() : !parent.remoteSideOpen()) {
                throw connectionError(PROTOCOL_ERROR, "Stream %d is not open for sending push promise", parent.id());
            }
            if (!opposite().allowPushTo()) {
                throw connectionError(PROTOCOL_ERROR, "Server push not allowed to opposite endpoint.");
            }
            checkNewStreamAllowed(streamId);

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
            List<ParentChangedEvent> events = new ArrayList<ParentChangedEvent>(1);
            connectionStream.takeChild(stream, false, events);

            // Notify the listeners of the event.
            for (Listener listener : listeners) {
                listener.onStreamAdded(stream);
            }

            notifyParentChanged(events);
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
        public int maxActiveStreams() {
            return maxActiveStreams;
        }

        @Override
        public void maxActiveStreams(int maxActiveStreams) {
            this.maxActiveStreams = maxActiveStreams;
        }

        @Override
        public int lastStreamCreated() {
            return lastStreamCreated;
        }

        @Override
        public int lastKnownStream() {
            return lastKnownStream >= 0 ? lastKnownStream : lastStreamCreated;
        }

        private void lastKnownStream(int lastKnownStream) {
            this.lastKnownStream = lastKnownStream;
        }

        @Override
        public F flowController() {
            return flowController;
        }

        @Override
        public void flowController(F flowController) {
            this.flowController = checkNotNull(flowController, "flowController");
        }

        @Override
        public Endpoint<? extends Http2FlowController> opposite() {
            return isLocal() ? remoteEndpoint : localEndpoint;
        }

        private void checkNewStreamAllowed(int streamId) throws Http2Exception {
            if (goAwaySent() || goAwayReceived()) {
                throw connectionError(PROTOCOL_ERROR, "Cannot create a stream since the connection is going away");
            }
            if (streamId < 0) {
                throw new Http2NoMoreStreamIdsException();
            }
            if (!createdStreamId(streamId)) {
                throw connectionError(PROTOCOL_ERROR, "Request stream %d is not correct for %s connection",
                        streamId, server ? "server" : "client");
            }
            // This check must be after all id validated checks, but before the max streams check because it may be
            // recoverable to some degree for handling frames which can be sent on closed streams.
            if (streamId < nextStreamId) {
                throw closedStreamError(PROTOCOL_ERROR, "Request stream %d is behind the next expected stream %d",
                        streamId, nextStreamId);
            }
            if (!canCreateStream()) {
                throw connectionError(REFUSED_STREAM, "Maximum streams exceeded for this endpoint.");
            }
        }

        private boolean isLocal() {
            return this == localEndpoint;
        }
    }
}
