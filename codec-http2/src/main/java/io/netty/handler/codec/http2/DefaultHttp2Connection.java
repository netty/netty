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
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Stream.State.CLOSED;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.HALF_CLOSED_REMOTE;
import static io.netty.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty.handler.codec.http2.Http2Stream.State.OPEN;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_LOCAL;
import static io.netty.handler.codec.http2.Http2Stream.State.RESERVED_REMOTE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.handler.codec.http2.Http2StreamRemovalPolicy.Action;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

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
    private final DefaultEndpoint localEndpoint;
    private final DefaultEndpoint remoteEndpoint;
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
    public Endpoint local() {
        return localEndpoint;
    }

    @Override
    public Endpoint remote() {
        return remoteEndpoint;
    }

    @Override
    public boolean isGoAway() {
        return goAwaySent() || goAwayReceived();
    }

    @Override
    public Http2Stream createLocalStream(int streamId, boolean halfClosed) throws Http2Exception {
        return local().createStream(streamId, halfClosed);
    }

    @Override
    public Http2Stream createRemoteStream(int streamId, boolean halfClosed) throws Http2Exception {
        return remote().createStream(streamId, halfClosed);
    }

    @Override
    public boolean goAwayReceived() {
        return localEndpoint.lastKnownStream >= 0;
    }

    @Override
    public void goAwayReceived(int lastKnownStream) {
        localEndpoint.lastKnownStream(lastKnownStream);
    }

    @Override
    public boolean goAwaySent() {
        return remoteEndpoint.lastKnownStream >= 0;
    }

    @Override
    public void goAwaySent(int lastKnownStream) {
        remoteEndpoint.lastKnownStream(lastKnownStream);
    }

    private void removeStream(DefaultStream stream) {
        // Notify the listeners of the event first.
        for (Listener listener : listeners) {
            listener.streamRemoved(stream);
        }

        // Remove it from the map and priority tree.
        streamMap.remove(stream.id());
        stream.parent().removeChild(stream);
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
        private boolean resetSent;
        private boolean resetReceived;
        private boolean endOfStreamSent;
        private boolean endOfStreamReceived;
        private Http2FlowState inboundFlow;
        private Http2FlowState outboundFlow;
        private Http2FlowControlWindowManager garbageCollector;
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
        public boolean isEndOfStreamReceived() {
            return endOfStreamReceived;
        }

        @Override
        public Http2Stream endOfStreamReceived() {
            endOfStreamReceived = true;
            return this;
        }

        @Override
        public boolean isEndOfStreamSent() {
            return endOfStreamSent;
        }

        @Override
        public Http2Stream endOfStreamSent() {
            endOfStreamSent = true;
            return this;
        }

        @Override
        public boolean isResetReceived() {
            return resetReceived;
        }

        @Override
        public Http2Stream resetReceived() {
            resetReceived = true;
            return this;
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
        public boolean isReset() {
            return resetSent || resetReceived;
        }

        @Override
        public Object setProperty(Object key, Object value) {
            return data.put(key, value);
        }

        @Override
        public <V> V getProperty(Object key) {
            return data.get(key);
        }

        @Override
        public <V> V removeProperty(Object key) {
            return data.remove(key);
        }

        @Override
        public Http2FlowState inboundFlow() {
            return inboundFlow;
        }

        @Override
        public void inboundFlow(Http2FlowState state) {
            inboundFlow = state;
        }

        @Override
        public Http2FlowState outboundFlow() {
            return outboundFlow;
        }

        @Override
        public void outboundFlow(Http2FlowState state) {
            outboundFlow = state;
        }

        @Override
        public Http2FlowControlWindowManager garbageCollector() {
            return garbageCollector;
        }

        @Override
        public void garbageCollector(Http2FlowControlWindowManager collector) {
            garbageCollector = collector;
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

            // Get the parent stream.
            DefaultStream newParent = (DefaultStream) requireStream(parentStreamId);
            if (this == newParent) {
                throw new IllegalArgumentException("A stream cannot depend on itself");
            }

            // Already have a priority. Re-prioritize the stream.
            weight(weight);

            if (newParent != parent() || exclusive) {
                List<ParentChangedEvent> events;
                if (newParent.isDescendantOf(this)) {
                    events = new ArrayList<ParentChangedEvent>(2 + (exclusive ? newParent.numChildren(): 0));
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
        public Http2Stream openForPush() throws Http2Exception {
            switch (state) {
            case RESERVED_LOCAL:
                state = HALF_CLOSED_REMOTE;
                break;
            case RESERVED_REMOTE:
                state = HALF_CLOSED_LOCAL;
                break;
            default:
                throw connectionError(PROTOCOL_ERROR, "Attempting to open non-reserved stream for push");
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
            return localEndpoint.createdStreamId(id) ? localEndpoint : remoteEndpoint;
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
            IntObjectMap<DefaultStream> prevChildren = children;
            children = newChildMap();
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

            if (exclusive) {
                // If it was requested that this child be the exclusive dependency of this node,
                // move any previous children to the child node, becoming grand children
                // of this node.
                if (!children.isEmpty()) {
                    for (DefaultStream grandchild : removeAllChildren().values()) {
                        child.takeChild(grandchild, false, events);
                    }
                }
            }

            if (children.put(child.id(), child) == null) {
                totalChildWeights += child.weight();
            }

            if (oldParent != null && oldParent.children.remove(child.id()) != null) {
                oldParent.totalChildWeights -= child.weight();
            }
        }

        /**
         * Removes the child priority and moves any of its dependencies to being direct dependencies on this node.
         */
        final void removeChild(DefaultStream child) {
            if (children.remove(child.id()) != null) {
                List<ParentChangedEvent> events = new ArrayList<ParentChangedEvent>(1 + child.children.size());
                events.add(new ParentChangedEvent(child, child.parent()));
                notifyParentChanging(child, null);
                child.parent = null;
                totalChildWeights -= child.weight();

                // Move up any grand children to be directly dependent on this node.
                for (DefaultStream grandchild : child.children.values()) {
                    takeChild(grandchild, false, events);
                }

                notifyParentChanged(events);
            }
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

    private static IntObjectMap<DefaultStream> newChildMap() {
        return new IntObjectHashMap<DefaultStream>(4);
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
            l.priorityTreeParentChanged(stream, oldParent);
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
            l.priorityTreeParentChanging(stream, newParent);
        }
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
            return nextStreamId > 1 ? nextStreamId : nextStreamId + 2;
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
                listener.streamAdded(stream);
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
            return lastKnownStream >= 0 ? lastKnownStream : lastStreamCreated;
        }

        private void lastKnownStream(int lastKnownStream) {
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
                throw connectionError(PROTOCOL_ERROR, "Cannot create a stream since the connection is going away");
            }
            verifyStreamId(streamId);
            if (!acceptingNewStreams()) {
                throw connectionError(PROTOCOL_ERROR, "Maximum streams exceeded for this endpoint.");
            }
        }

        private void verifyStreamId(int streamId) throws Http2Exception {
            if (streamId < 0) {
                throw new Http2NoMoreStreamIdsException();
            }
            if (streamId < nextStreamId) {
                throw connectionError(PROTOCOL_ERROR, "Request stream %d is behind the next expected stream %d",
                        streamId, nextStreamId);
            }
            if (!createdStreamId(streamId)) {
                throw connectionError(PROTOCOL_ERROR, "Request stream %d is not correct for %s connection",
                        streamId, server ? "server" : "client");
            }
        }

        private boolean isLocal() {
            return this == localEndpoint;
        }
    }
}
