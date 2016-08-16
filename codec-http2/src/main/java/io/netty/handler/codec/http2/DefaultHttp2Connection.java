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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2Stream.State;
import io.netty.util.collection.IntCollections;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.IntObjectMap.PrimitiveEntry;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.UnaryPromiseNotifier;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_WEIGHT;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_WEIGHT;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
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
import static java.lang.Math.max;

/**
 * Simple implementation of {@link Http2Connection}.
 */
@UnstableApi
public class DefaultHttp2Connection implements Http2Connection {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultHttp2Connection.class);
    // Fields accessed by inner classes
    final IntObjectMap<Http2Stream> streamMap = new IntObjectHashMap<Http2Stream>();
    final PropertyKeyRegistry propertyKeyRegistry = new PropertyKeyRegistry();
    final ConnectionStream connectionStream = new ConnectionStream();
    final DefaultEndpoint<Http2LocalFlowController> localEndpoint;
    final DefaultEndpoint<Http2RemoteFlowController> remoteEndpoint;

    /**
     * The initial size of the children map is chosen to be conservative on initial memory allocations under
     * the assumption that most streams will have a small number of children. This choice may be
     * sub-optimal if when children are present there are many children (i.e. a web page which has many
     * dependencies to load).
     */
    private static final int INITIAL_CHILDREN_MAP_SIZE =
            max(1, SystemPropertyUtil.getInt("io.netty.http2.childrenMapSize", 4));

    /**
     * We chose a {@link List} over a {@link Set} to avoid allocating an {@link Iterator} objects when iterating over
     * the listeners.
     */
    final List<Listener> listeners = new ArrayList<Listener>(4);
    final ActiveStreams activeStreams;
    Promise<Void> closePromise;

    /**
     * Creates a new connection with the given settings.
     *
     * @param server
     *            whether or not this end-point is the server-side of the HTTP/2 connection.
     */
    public DefaultHttp2Connection(boolean server) {
        activeStreams = new ActiveStreams(listeners);
        localEndpoint = new DefaultEndpoint<Http2LocalFlowController>(server);
        remoteEndpoint = new DefaultEndpoint<Http2RemoteFlowController>(!server);

        // Add the connection stream to the map.
        streamMap.put(connectionStream.id(), connectionStream);
    }

    /**
     * Determine if {@link #close(Promise)} has been called and no more streams are allowed to be created.
     */
    final boolean isClosed() {
        return closePromise != null;
    }

    @Override
    public Future<Void> close(final Promise<Void> promise) {
        checkNotNull(promise, "promise");
        // Since we allow this method to be called multiple times, we must make sure that all the promises are notified
        // when all streams are removed and the close operation completes.
        if (closePromise != null) {
            if (closePromise == promise) {
                // Do nothing
            } else if ((promise instanceof ChannelPromise) && ((ChannelPromise) closePromise).isVoid()) {
                closePromise = promise;
            } else {
                closePromise.addListener(new UnaryPromiseNotifier<Void>(promise));
            }
        } else {
            closePromise = promise;
        }
        if (isStreamMapEmpty()) {
            promise.trySuccess(null);
            return promise;
        }

        Iterator<PrimitiveEntry<Http2Stream>> itr = streamMap.entries().iterator();
        // We must take care while iterating the streamMap as to not modify while iterating in case there are other code
        // paths iterating over the active streams.
        if (activeStreams.allowModifications()) {
            activeStreams.incrementPendingIterations();
            try {
                while (itr.hasNext()) {
                    DefaultStream stream = (DefaultStream) itr.next().value();
                    if (stream.id() != CONNECTION_STREAM_ID) {
                        // If modifications of the activeStream map is allowed, then a stream close operation will also
                        // modify the streamMap. Pass the iterator in so that remove will be called to prevent
                        // concurrent modification exceptions.
                        stream.close(itr);
                    }
                }
            } finally {
                activeStreams.decrementPendingIterations();
            }
        } else {
            while (itr.hasNext()) {
                Http2Stream stream = itr.next().value();
                if (stream.id() != CONNECTION_STREAM_ID) {
                    // We are not allowed to make modifications, so the close calls will be executed after this
                    // iteration completes.
                    stream.close();
                }
            }
        }
        return closePromise;
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
    public Http2Stream stream(int streamId) {
        return streamMap.get(streamId);
    }

    @Override
    public boolean streamMayHaveExisted(int streamId) {
        return remoteEndpoint.mayHaveCreatedStream(streamId) || localEndpoint.mayHaveCreatedStream(streamId);
    }

    @Override
    public int numActiveStreams() {
        return activeStreams.size();
    }

    @Override
    public Http2Stream forEachActiveStream(Http2StreamVisitor visitor) throws Http2Exception {
        return activeStreams.forEachActiveStream(visitor);
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
        return localEndpoint.lastStreamKnownByPeer >= 0;
    }

    @Override
    public void goAwayReceived(final int lastKnownStream, long errorCode, ByteBuf debugData) {
        localEndpoint.lastStreamKnownByPeer(lastKnownStream);
        for (int i = 0; i < listeners.size(); ++i) {
            try {
                listeners.get(i).onGoAwayReceived(lastKnownStream, errorCode, debugData);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onGoAwayReceived.", cause);
            }
        }

        try {
            forEachActiveStream(new Http2StreamVisitor() {
                @Override
                public boolean visit(Http2Stream stream) {
                    if (stream.id() > lastKnownStream && localEndpoint.isValidStreamId(stream.id())) {
                        stream.close();
                    }
                    return true;
                }
            });
        } catch (Http2Exception e) {
            PlatformDependent.throwException(e);
        }
    }

    @Override
    public boolean goAwaySent() {
        return remoteEndpoint.lastStreamKnownByPeer >= 0;
    }

    @Override
    public void goAwaySent(final int lastKnownStream, long errorCode, ByteBuf debugData) {
        remoteEndpoint.lastStreamKnownByPeer(lastKnownStream);
        for (int i = 0; i < listeners.size(); ++i) {
            try {
                listeners.get(i).onGoAwaySent(lastKnownStream, errorCode, debugData);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onGoAwaySent.", cause);
            }
        }

        try {
            forEachActiveStream(new Http2StreamVisitor() {
                @Override
                public boolean visit(Http2Stream stream) {
                    if (stream.id() > lastKnownStream && remoteEndpoint.isValidStreamId(stream.id())) {
                        stream.close();
                    }
                    return true;
                }
            });
        } catch (Http2Exception e) {
            PlatformDependent.throwException(e);
        }
    }

    /**
     * Determine if {@link #streamMap} only contains the connection stream.
     */
    private boolean isStreamMapEmpty() {
        return streamMap.size() == 1;
    }

    /**
     * Remove a stream from the {@link #streamMap}.
     * @param stream the stream to remove.
     * @param itr an iterator that may be pointing to the stream during iteration and {@link Iterator#remove()} will be
     * used if non-{@code null}.
     */
    void removeStream(DefaultStream stream, Iterator<?> itr) {
        if (stream.parent().removeChild(stream)) {
            if (itr == null) {
                streamMap.remove(stream.id());
            } else {
                itr.remove();
            }

            for (int i = 0; i < listeners.size(); i++) {
                try {
                    listeners.get(i).onStreamRemoved(stream);
                } catch (Throwable cause) {
                    logger.error("Caught Throwable from listener onStreamRemoved.", cause);
                }
            }

            if (closePromise != null && isStreamMapEmpty()) {
                closePromise.trySuccess(null);
            }
        }
    }

    static State activeState(int streamId, State initialState, boolean isLocal, boolean halfClosed)
            throws Http2Exception {
        switch (initialState) {
        case IDLE:
            return halfClosed ? isLocal ? HALF_CLOSED_LOCAL : HALF_CLOSED_REMOTE : OPEN;
        case RESERVED_LOCAL:
            return HALF_CLOSED_REMOTE;
        case RESERVED_REMOTE:
            return HALF_CLOSED_LOCAL;
        default:
            throw streamError(streamId, PROTOCOL_ERROR, "Attempting to open a stream in an invalid state: "
                    + initialState);
        }
    }

    void notifyHalfClosed(Http2Stream stream) {
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onStreamHalfClosed(stream);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onStreamHalfClosed.", cause);
            }
        }
    }

    void notifyClosed(Http2Stream stream) {
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onStreamClosed(stream);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onStreamClosed.", cause);
            }
        }
    }

    @Override
    public PropertyKey newKey() {
        return propertyKeyRegistry.newKey();
    }

    /**
     * Verifies that the key is valid and returns it as the internal {@link DefaultPropertyKey} type.
     *
     * @throws NullPointerException if the key is {@code null}.
     * @throws ClassCastException if the key is not of type {@link DefaultPropertyKey}.
     * @throws IllegalArgumentException if the key was not created by this connection.
     */
    final DefaultPropertyKey verifyKey(PropertyKey key) {
        return checkNotNull((DefaultPropertyKey) key, "key").verifyConnection(this);
    }

    /**
     * Simple stream implementation. Streams can be compared to each other by priority.
     */
    private class DefaultStream implements Http2Stream {
        private final int id;
        private final PropertyMap properties = new PropertyMap();
        private State state;
        private short weight = DEFAULT_PRIORITY_WEIGHT;
        private DefaultStream parent;
        private IntObjectMap<DefaultStream> children = IntCollections.emptyMap();
        private boolean resetSent;

        DefaultStream(int id, State state) {
            this.id = id;
            this.state = state;
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
        public final <V> V setProperty(PropertyKey key, V value) {
            return properties.add(verifyKey(key), value);
        }

        @Override
        public final <V> V getProperty(PropertyKey key) {
            return properties.get(verifyKey(key));
        }

        @Override
        public final <V> V removeProperty(PropertyKey key) {
            return properties.remove(verifyKey(key));
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
        public Http2Stream forEachChild(Http2StreamVisitor visitor) throws Http2Exception {
            for (DefaultStream stream : children.values()) {
                if (!visitor.visit(stream)) {
                    return stream;
                }
            }
            return null;
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
                newParent = createdBy().createIdleStream(parentStreamId);
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
            state = activeState(id, state, isLocal(), halfClosed);
            if (!createdBy().canOpenStream()) {
                throw connectionError(PROTOCOL_ERROR, "Maximum active streams violated for this endpoint.");
            }
            activate();
            return this;
        }

        void activate() {
            activeStreams.activate(this);
        }

        Http2Stream close(Iterator<?> itr) {
            if (state == CLOSED) {
                return this;
            }

            state = CLOSED;

            --createdBy().numStreams;
            activeStreams.deactivate(this, itr);
            return this;
        }

        @Override
        public Http2Stream close() {
            return close(null);
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

        private void initChildrenIfEmpty() {
            if (children == IntCollections.<DefaultStream>emptyMap()) {
                initChildren();
            }
        }

        private void initChildren() {
            children = new IntObjectHashMap<DefaultStream>(INITIAL_CHILDREN_MAP_SIZE);
        }

        DefaultEndpoint<? extends Http2FlowController> createdBy() {
            return localEndpoint.isValidStreamId(id) ? localEndpoint : remoteEndpoint;
        }

        final boolean isLocal() {
            return localEndpoint.isValidStreamId(id);
        }

        final void weight(short weight) {
            if (weight != this.weight) {
                final short oldWeight = this.weight;
                this.weight = weight;
                for (int i = 0; i < listeners.size(); i++) {
                    try {
                        listeners.get(i).onWeightChanged(this, oldWeight);
                    } catch (Throwable cause) {
                        logger.error("Caught Throwable from listener onWeightChanged.", cause);
                    }
                }
            }
        }

        /**
         * Remove all children with the exception of {@code streamToRetain}.
         * This method is intended to be used to support an exclusive priority dependency operation.
         * @return The map of children prior to this operation, excluding {@code streamToRetain} if present.
         */
        private IntObjectMap<DefaultStream> retain(DefaultStream streamToRetain) {
            streamToRetain = children.remove(streamToRetain.id());
            IntObjectMap<DefaultStream> prevChildren = children;
            // This map should be re-initialized in anticipation for the 1 exclusive child which will be added.
            // It will either be added directly in this method, or after this method is called...but it will be added.
            initChildren();
            if (streamToRetain != null) {
                children.put(streamToRetain.id(), streamToRetain);
            }
            return prevChildren;
        }

        /**
         * Adds a child to this priority. If exclusive is set, any children of this node are moved to being dependent on
         * the child.
         */
        final void takeChild(DefaultStream child, boolean exclusive, List<ParentChangedEvent> events) {
            DefaultStream oldParent = child.parent();

            if (oldParent != this) {
                events.add(new ParentChangedEvent(child, oldParent));
                notifyParentChanging(child, this);
                child.parent = this;
                // Note that the removal operation may not be successful and may return null. This is because when an
                // exclusive dependency is processed the children are removed in a previous recursive call but the
                // child's parent link is updated here.
                if (oldParent != null) {
                    oldParent.children.remove(child.id());
                }

                // Lazily initialize the children to save object allocations.
                initChildrenIfEmpty();

                final Http2Stream oldChild = children.put(child.id(), child);
                assert oldChild == null : "A stream with the same stream ID was already in the child map.";
            }

            if (exclusive && !children.isEmpty()) {
                // If it was requested that this child be the exclusive dependency of this node,
                // move any previous children to the child node, becoming grand children of this node.
                for (DefaultStream grandchild : retain(child).values()) {
                    child.takeChild(grandchild, false, events);
                }
            }
        }

        /**
         * Removes the child priority and moves any of its dependencies to being direct dependencies on this node.
         */
        final boolean removeChild(DefaultStream child) {
            if (children.remove(child.id()) != null) {
                List<ParentChangedEvent> events = new ArrayList<ParentChangedEvent>(1 + child.numChildren());
                events.add(new ParentChangedEvent(child, child.parent()));
                notifyParentChanging(child, null);
                child.parent = null;

                // Move up any grand children to be directly dependent on this node.
                for (DefaultStream grandchild : child.children.values()) {
                    takeChild(grandchild, false, events);
                }

                notifyParentChanged(events);
                return true;
            }
            return false;
        }

        /**
         * Provides the lazy initialization for the {@link DefaultStream} data map.
         */
        private class PropertyMap {
            Object[] values = EmptyArrays.EMPTY_OBJECTS;

            <V> V add(DefaultPropertyKey key, V value) {
                resizeIfNecessary(key.index);
                @SuppressWarnings("unchecked")
                V prevValue = (V) values[key.index];
                values[key.index] = value;
                return prevValue;
            }

            @SuppressWarnings("unchecked")
            <V> V get(DefaultPropertyKey key) {
                if (key.index >= values.length) {
                    return null;
                }
                return (V) values[key.index];
            }

            @SuppressWarnings("unchecked")
            <V> V remove(DefaultPropertyKey key) {
                V prevValue = null;
                if (key.index < values.length) {
                    prevValue = (V) values[key.index];
                    values[key.index] = null;
                }
                return prevValue;
            }

            void resizeIfNecessary(int index) {
                if (index >= values.length) {
                    values = Arrays.copyOf(values, propertyKeyRegistry.size());
                }
            }
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
            try {
                l.onPriorityTreeParentChanged(stream, oldParent);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onPriorityTreeParentChanged.", cause);
            }
        }
    }

    /**
     * Notify all listeners of the priority tree change events (in ascending order)
     * @param events The events (top down order) which have changed
     */
    private void notifyParentChanged(List<ParentChangedEvent> events) {
        for (int i = 0; i < events.size(); ++i) {
            ParentChangedEvent event = events.get(i);
            for (int j = 0; j < listeners.size(); j++) {
                event.notifyListener(listeners.get(j));
            }
        }
    }

    private void notifyParentChanging(Http2Stream stream, Http2Stream newParent) {
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onPriorityTreeParentChanging(stream, newParent);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onPriorityTreeParentChanging.", cause);
            }
        }
    }

    /**
     * Stream class representing the connection, itself.
     */
    private final class ConnectionStream extends DefaultStream {
        ConnectionStream() {
            super(CONNECTION_STREAM_ID, IDLE);
        }

        @Override
        public boolean isResetSent() {
            return false;
        }

        @Override
        DefaultEndpoint<? extends Http2FlowController> createdBy() {
            return null;
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
        /**
         * The minimum stream ID allowed when creating the next stream. This only applies at the time the stream is
         * created. If the ID of the stream being created is less than this value, stream creation will fail. Upon
         * successful creation of a stream, this value is incremented to the next valid stream ID.
         */
        private int nextStreamIdToCreate;
        /**
         * Used for reservation of stream IDs. Stream IDs can be reserved in advance by applications before the streams
         * are actually created.  For example, applications may choose to buffer stream creation attempts as a way of
         * working around {@code SETTINGS_MAX_CONCURRENT_STREAMS}, in which case they will reserve stream IDs for each
         * buffered stream.
         */
        private int nextReservationStreamId;
        private int lastStreamKnownByPeer = -1;
        private boolean pushToAllowed = true;
        private F flowController;
        private int maxActiveStreams;
        private int maxStreams;
        // Fields accessed by inner classes
        int numActiveStreams;
        int numStreams;

        DefaultEndpoint(boolean server) {
            this.server = server;

            // Determine the starting stream ID for this endpoint. Client-initiated streams
            // are odd and server-initiated streams are even. Zero is reserved for the
            // connection. Stream 1 is reserved client-initiated stream for responding to an
            // upgrade from HTTP 1.1.
            if (server) {
                nextStreamIdToCreate = 2;
                nextReservationStreamId = 0;
            } else {
                nextStreamIdToCreate = 1;
                // For manually created client-side streams, 1 is reserved for HTTP upgrade, so start at 3.
                nextReservationStreamId = 1;
            }

            // Push is disallowed by default for servers and allowed for clients.
            pushToAllowed = !server;
            maxStreams = maxActiveStreams = Integer.MAX_VALUE;
        }

        @Override
        public int incrementAndGetNextStreamId() {
            return nextReservationStreamId >= 0 ? nextReservationStreamId += 2 : nextReservationStreamId;
        }

        private void incrementExpectedStreamId(int streamId) {
            if (streamId > nextReservationStreamId && nextReservationStreamId >= 0) {
                nextReservationStreamId = streamId;
            }
            nextStreamIdToCreate = streamId + 2;
            ++numStreams;
        }

        @Override
        public boolean isValidStreamId(int streamId) {
            return streamId > 0 && server == ((streamId & 1) == 0);
        }

        @Override
        public boolean mayHaveCreatedStream(int streamId) {
            return isValidStreamId(streamId) && streamId <= lastStreamCreated();
        }

        @Override
        public boolean canOpenStream() {
            return numActiveStreams < maxActiveStreams;
        }

        private DefaultStream createStream(int streamId, State state) throws Http2Exception {
            checkNewStreamAllowed(streamId, state);

            // Create and initialize the stream.
            DefaultStream stream = new DefaultStream(streamId, state);

            incrementExpectedStreamId(streamId);

            addStream(stream);
            return stream;
        }

        @Override
        public DefaultStream createIdleStream(int streamId) throws Http2Exception {
            return createStream(streamId, IDLE);
        }

        @Override
        public DefaultStream createStream(int streamId, boolean halfClosed) throws Http2Exception {
            DefaultStream stream = createStream(streamId, activeState(streamId, IDLE, isLocal(), halfClosed));
            stream.activate();
            return stream;
        }

        @Override
        public boolean created(Http2Stream stream) {
            return stream instanceof DefaultStream && ((DefaultStream) stream).createdBy() == this;
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
            if (isLocal() ? !parent.state().localSideOpen() : !parent.state().remoteSideOpen()) {
                throw connectionError(PROTOCOL_ERROR, "Stream %d is not open for sending push promise", parent.id());
            }
            if (!opposite().allowPushTo()) {
                throw connectionError(PROTOCOL_ERROR, "Server push not allowed to opposite endpoint.");
            }
            State state = isLocal() ? RESERVED_LOCAL : RESERVED_REMOTE;
            checkNewStreamAllowed(streamId, state);

            // Create and initialize the stream.
            DefaultStream stream = new DefaultStream(streamId, state);

            incrementExpectedStreamId(streamId);

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
            for (int i = 0; i < listeners.size(); i++) {
                try {
                    listeners.get(i).onStreamAdded(stream);
                } catch (Throwable cause) {
                    logger.error("Caught Throwable from listener onStreamAdded.", cause);
                }
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
        public int maxStreams() {
            return maxStreams;
        }

        @Override
        public void maxStreams(int maxActiveStreams, int maxStreams) throws Http2Exception {
            if (maxStreams < maxActiveStreams) {
                throw connectionError(PROTOCOL_ERROR, "maxStream[%d] streams must be >= maxActiveStreams[%d]",
                        maxStreams,  maxActiveStreams);
            }
            this.maxStreams = maxStreams;
            this.maxActiveStreams = maxActiveStreams;
        }

        @Override
        public int lastStreamCreated() {
            return nextStreamIdToCreate > 1 ? nextStreamIdToCreate - 2 : 0;
        }

        @Override
        public int lastStreamKnownByPeer() {
            return lastStreamKnownByPeer;
        }

        private void lastStreamKnownByPeer(int lastKnownStream) {
            this.lastStreamKnownByPeer = lastKnownStream;
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

        private void checkNewStreamAllowed(int streamId, State state) throws Http2Exception {
            if (goAwayReceived() && streamId > localEndpoint.lastStreamKnownByPeer()) {
                throw connectionError(PROTOCOL_ERROR, "Cannot create stream %d since this endpoint has received a " +
                                                      "GOAWAY frame with last stream id %d.", streamId,
                                                      localEndpoint.lastStreamKnownByPeer());
            }
            if (streamId < 0) {
                throw new Http2NoMoreStreamIdsException();
            }
            if (!isValidStreamId(streamId)) {
                throw connectionError(PROTOCOL_ERROR, "Request stream %d is not correct for %s connection", streamId,
                        server ? "server" : "client");
            }
            // This check must be after all id validated checks, but before the max streams check because it may be
            // recoverable to some degree for handling frames which can be sent on closed streams.
            if (streamId < nextStreamIdToCreate) {
                throw closedStreamError(PROTOCOL_ERROR, "Request stream %d is behind the next expected stream %d",
                        streamId, nextStreamIdToCreate);
            }
            if (nextStreamIdToCreate <= 0) {
                throw connectionError(REFUSED_STREAM, "Stream IDs are exhausted for this endpoint.");
            }
            if (state.localSideOpen() || state.remoteSideOpen()) {
                if (!canOpenStream()) {
                    throw streamError(streamId, REFUSED_STREAM, "Maximum active streams violated for this endpoint.");
                }
            } else if (numStreams == maxStreams) {
                throw streamError(streamId, REFUSED_STREAM, "Maximum streams violated for this endpoint.");
            }
            if (isClosed()) {
                throw connectionError(INTERNAL_ERROR, "Attempted to create stream id %d after connection was closed",
                                      streamId);
            }
        }

        private boolean isLocal() {
            return this == localEndpoint;
        }
    }

    /**
     * Allows events which would modify the collection of active streams to be queued while iterating via {@link
     * #forEachActiveStream(Http2StreamVisitor)}.
     */
    interface Event {
        /**
         * Trigger the original intention of this event. Expect to modify the active streams list.
         * <p/>
         * If a {@link RuntimeException} object is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         */
        void process();
    }

    /**
     * Manages the list of currently active streams.  Queues any {@link Event}s that would modify the list of
     * active streams in order to prevent modification while iterating.
     */
    private final class ActiveStreams {
        private final List<Listener> listeners;
        private final Queue<Event> pendingEvents = new ArrayDeque<Event>(4);
        private final Set<Http2Stream> streams = new LinkedHashSet<Http2Stream>();
        private int pendingIterations;

        public ActiveStreams(List<Listener> listeners) {
            this.listeners = listeners;
        }

        public int size() {
            return streams.size();
        }

        public void activate(final DefaultStream stream) {
            if (allowModifications()) {
                addToActiveStreams(stream);
            } else {
                pendingEvents.add(new Event() {
                    @Override
                    public void process() {
                        addToActiveStreams(stream);
                    }
                });
            }
        }

        public void deactivate(final DefaultStream stream, final Iterator<?> itr) {
            if (allowModifications() || itr != null) {
                removeFromActiveStreams(stream, itr);
            } else {
                pendingEvents.add(new Event() {
                    @Override
                    public void process() {
                        // When deactivate is called the stream state has already been set to CLOSE however
                        // it is possible that since this job has been queued other circumstances have caused
                        // it to be removed from the priority tree and thus have a null parent (i.e. reprioritization).
                        // If the parent is null this means it has already been removed from active streams and we
                        // should not process the removal any further as this will lead to a NPE.
                        if (stream.parent() == null) {
                            return;
                        }
                        removeFromActiveStreams(stream, itr);
                    }
                });
            }
        }

        public Http2Stream forEachActiveStream(Http2StreamVisitor visitor) throws Http2Exception {
            incrementPendingIterations();
            try {
                for (Http2Stream stream : streams) {
                    if (!visitor.visit(stream)) {
                        return stream;
                    }
                }
                return null;
            } finally {
                decrementPendingIterations();
            }
        }

        void addToActiveStreams(DefaultStream stream) {
            if (streams.add(stream)) {
                // Update the number of active streams initiated by the endpoint.
                stream.createdBy().numActiveStreams++;

                for (int i = 0; i < listeners.size(); i++) {
                    try {
                        listeners.get(i).onStreamActive(stream);
                    } catch (Throwable cause) {
                        logger.error("Caught Throwable from listener onStreamActive.", cause);
                    }
                }
            }
        }

        void removeFromActiveStreams(DefaultStream stream, Iterator<?> itr) {
            if (streams.remove(stream)) {
                // Update the number of active streams initiated by the endpoint.
                stream.createdBy().numActiveStreams--;
                notifyClosed(stream);
            }
            removeStream(stream, itr);
        }

        boolean allowModifications() {
            return pendingIterations == 0;
        }

        void incrementPendingIterations() {
            ++pendingIterations;
        }

        void decrementPendingIterations() {
            --pendingIterations;
            if (allowModifications()) {
                for (;;) {
                    Event event = pendingEvents.poll();
                    if (event == null) {
                        break;
                    }
                    try {
                        event.process();
                    } catch (Throwable cause) {
                        logger.error("Caught Throwable while processing pending ActiveStreams$Event.", cause);
                    }
                }
            }
        }
    }

    /**
     * Implementation of {@link PropertyKey} that specifies the index position of the property.
     */
    final class DefaultPropertyKey implements PropertyKey {
        final int index;

        DefaultPropertyKey(int index) {
            this.index = index;
        }

        DefaultPropertyKey verifyConnection(Http2Connection connection) {
            if (connection != DefaultHttp2Connection.this) {
                throw new IllegalArgumentException("Using a key that was not created by this connection");
            }
            return this;
        }
    }

    /**
     * A registry of all stream property keys known by this connection.
     */
    private final class PropertyKeyRegistry {
        final List<DefaultPropertyKey> keys = new ArrayList<DefaultPropertyKey>(4);

        /**
         * Registers a new property key.
         */
        DefaultPropertyKey newKey() {
            DefaultPropertyKey key = new DefaultPropertyKey(keys.size());
            keys.add(key);
            return key;
        }

        int size() {
            return keys.size();
        }
    }
}
