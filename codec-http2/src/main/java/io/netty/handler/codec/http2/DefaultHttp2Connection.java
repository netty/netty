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
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.collection.IntObjectMap.PrimitiveEntry;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.UnaryPromiseNotifier;
import io.netty.util.internal.EmptyArrays;
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
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_RESERVED_STREAMS;
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
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.lang.Integer.MAX_VALUE;

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
     * We chose a {@link List} over a {@link Set} to avoid allocating an {@link Iterator} objects when iterating over
     * the listeners.
     * <p>
     * Initial size of 4 because the default configuration currently has 3 listeners
     * (local/remote flow controller and {@link StreamByteDistributor}) and we leave room for 1 extra.
     * We could be more aggressive but the ArrayList resize will double the size if we are too small.
     */
    final List<Listener> listeners = new ArrayList<Listener>(4);
    final ActiveStreams activeStreams;
    Promise<Void> closePromise;

    /**
     * Creates a new connection with the given settings.
     * @param server whether or not this end-point is the server-side of the HTTP/2 connection.
     */
    public DefaultHttp2Connection(boolean server) {
        this(server, DEFAULT_MAX_RESERVED_STREAMS);
    }

    /**
     * Creates a new connection with the given settings.
     * @param server whether or not this end-point is the server-side of the HTTP/2 connection.
     * @param maxReservedStreams The maximum amount of streams which can exist in the reserved state for each endpoint.
     */
    public DefaultHttp2Connection(boolean server, int maxReservedStreams) {
        activeStreams = new ActiveStreams(listeners);
        // Reserved streams are excluded from the SETTINGS_MAX_CONCURRENT_STREAMS limit according to [1] and the RFC
        // doesn't define a way to communicate the limit on reserved streams. We rely upon the peer to send RST_STREAM
        // in response to any locally enforced limits being exceeded [2].
        // [1] https://tools.ietf.org/html/rfc7540#section-5.1.2
        // [2] https://tools.ietf.org/html/rfc7540#section-8.2.2
        localEndpoint = new DefaultEndpoint<Http2LocalFlowController>(server, server ? MAX_VALUE : maxReservedStreams);
        remoteEndpoint = new DefaultEndpoint<Http2RemoteFlowController>(!server, maxReservedStreams);

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
    public void goAwayReceived(final int lastKnownStream, long errorCode, ByteBuf debugData) throws Http2Exception {
        if (localEndpoint.lastStreamKnownByPeer() >= 0 && localEndpoint.lastStreamKnownByPeer() < lastKnownStream) {
            throw connectionError(PROTOCOL_ERROR, "lastStreamId MUST NOT increase. Current value: %d new value: %d",
                    localEndpoint.lastStreamKnownByPeer(), lastKnownStream);
        }

        localEndpoint.lastStreamKnownByPeer(lastKnownStream);
        for (int i = 0; i < listeners.size(); ++i) {
            try {
                listeners.get(i).onGoAwayReceived(lastKnownStream, errorCode, debugData);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onGoAwayReceived.", cause);
            }
        }

        closeStreamsGreaterThanLastKnownStreamId(lastKnownStream, localEndpoint);
    }

    @Override
    public boolean goAwaySent() {
        return remoteEndpoint.lastStreamKnownByPeer >= 0;
    }

    @Override
    public boolean goAwaySent(final int lastKnownStream, long errorCode, ByteBuf debugData) throws Http2Exception {
        if (remoteEndpoint.lastStreamKnownByPeer() >= 0) {
            // Protect against re-entrancy. Could happen if writing the frame fails, and error handling
            // treating this is a connection handler and doing a graceful shutdown...
            if (lastKnownStream == remoteEndpoint.lastStreamKnownByPeer()) {
                return false;
            }
            if (lastKnownStream > remoteEndpoint.lastStreamKnownByPeer()) {
                throw connectionError(PROTOCOL_ERROR, "Last stream identifier must not increase between " +
                                "sending multiple GOAWAY frames (was '%d', is '%d').",
                        remoteEndpoint.lastStreamKnownByPeer(), lastKnownStream);
            }
        }

        remoteEndpoint.lastStreamKnownByPeer(lastKnownStream);
        for (int i = 0; i < listeners.size(); ++i) {
            try {
                listeners.get(i).onGoAwaySent(lastKnownStream, errorCode, debugData);
            } catch (Throwable cause) {
                logger.error("Caught Throwable from listener onGoAwaySent.", cause);
            }
        }

        closeStreamsGreaterThanLastKnownStreamId(lastKnownStream, remoteEndpoint);
        return true;
    }

    private void closeStreamsGreaterThanLastKnownStreamId(final int lastKnownStream,
                                                          final DefaultEndpoint<?> endpoint) throws Http2Exception {
        forEachActiveStream(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) {
                if (stream.id() > lastKnownStream && endpoint.isValidStreamId(stream.id())) {
                    stream.close();
                }
                return true;
            }
        });
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
        final boolean removed;
        if (itr == null) {
            removed = streamMap.remove(stream.id()) != null;
        } else {
            itr.remove();
            removed = true;
        }

        if (removed) {
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
        private static final byte META_STATE_SENT_RST = 1;
        private static final byte META_STATE_SENT_HEADERS = 1 << 1;
        private static final byte META_STATE_SENT_TRAILERS = 1 << 2;
        private static final byte META_STATE_SENT_PUSHPROMISE = 1 << 3;
        private static final byte META_STATE_RECV_HEADERS = 1 << 4;
        private static final byte META_STATE_RECV_TRAILERS = 1 << 5;
        private final int id;
        private final PropertyMap properties = new PropertyMap();
        private State state;
        private byte metaState;

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
            return (metaState & META_STATE_SENT_RST) != 0;
        }

        @Override
        public Http2Stream resetSent() {
            metaState |= META_STATE_SENT_RST;
            return this;
        }

        @Override
        public Http2Stream headersSent(boolean isInformational) {
            if (!isInformational) {
                metaState |= isHeadersSent() ? META_STATE_SENT_TRAILERS : META_STATE_SENT_HEADERS;
            }
            return this;
        }

        @Override
        public boolean isHeadersSent() {
            return (metaState & META_STATE_SENT_HEADERS) != 0;
        }

        @Override
        public boolean isTrailersSent() {
            return (metaState & META_STATE_SENT_TRAILERS) != 0;
        }

        @Override
        public Http2Stream headersReceived(boolean isInformational) {
            if (!isInformational) {
                metaState |= isHeadersReceived() ? META_STATE_RECV_TRAILERS : META_STATE_RECV_HEADERS;
            }
            return this;
        }

        @Override
        public boolean isHeadersReceived() {
            return (metaState & META_STATE_RECV_HEADERS) != 0;
        }

        @Override
        public boolean isTrailersReceived() {
            return (metaState & META_STATE_RECV_TRAILERS) != 0;
        }

        @Override
        public Http2Stream pushPromiseSent() {
            metaState |= META_STATE_SENT_PUSHPROMISE;
            return this;
        }

        @Override
        public boolean isPushPromiseSent() {
            return (metaState & META_STATE_SENT_PUSHPROMISE) != 0;
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
        public Http2Stream open(boolean halfClosed) throws Http2Exception {
            state = activeState(id, state, isLocal(), halfClosed);
            if (!createdBy().canOpenStream()) {
                throw connectionError(PROTOCOL_ERROR, "Maximum active streams violated for this endpoint.");
            }

            activate();
            return this;
        }

        void activate() {
            // If the stream is opened in a half-closed state, the headers must have either
            // been sent if this is a local stream, or received if it is a remote stream.
            if (state == HALF_CLOSED_LOCAL) {
                headersSent(/*isInformational*/ false);
            } else if (state == HALF_CLOSED_REMOTE) {
                headersReceived(/*isInformational*/ false);
            }
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

        DefaultEndpoint<? extends Http2FlowController> createdBy() {
            return localEndpoint.isValidStreamId(id) ? localEndpoint : remoteEndpoint;
        }

        final boolean isLocal() {
            return localEndpoint.isValidStreamId(id);
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

        @Override
        public Http2Stream headersSent(boolean isInformational) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isHeadersSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Http2Stream pushPromiseSent() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPushPromiseSent() {
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
        private int maxStreams;
        private int maxActiveStreams;
        private final int maxReservedStreams;
        // Fields accessed by inner classes
        int numActiveStreams;
        int numStreams;

        DefaultEndpoint(boolean server, int maxReservedStreams) {
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
            maxActiveStreams = MAX_VALUE;
            this.maxReservedStreams = checkPositiveOrZero(maxReservedStreams, "maxReservedStreams");
            updateMaxStreams();
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

        @Override
        public DefaultStream createStream(int streamId, boolean halfClosed) throws Http2Exception {
            State state = activeState(streamId, IDLE, isLocal(), halfClosed);

            checkNewStreamAllowed(streamId, state);

            // Create and initialize the stream.
            DefaultStream stream = new DefaultStream(streamId, state);

            incrementExpectedStreamId(streamId);

            addStream(stream);

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
                throw connectionError(PROTOCOL_ERROR, "Server push not allowed to opposite endpoint");
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

            // Notify the listeners of the event.
            for (int i = 0; i < listeners.size(); i++) {
                try {
                    listeners.get(i).onStreamAdded(stream);
                } catch (Throwable cause) {
                    logger.error("Caught Throwable from listener onStreamAdded.", cause);
                }
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
        public int maxActiveStreams() {
            return maxActiveStreams;
        }

        @Override
        public void maxActiveStreams(int maxActiveStreams) {
            this.maxActiveStreams = maxActiveStreams;
            updateMaxStreams();
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

        private void updateMaxStreams() {
            maxStreams = (int) Math.min(MAX_VALUE, (long) maxActiveStreams + maxReservedStreams);
        }

        private void checkNewStreamAllowed(int streamId, State state) throws Http2Exception {
            assert state != IDLE;
            if (lastStreamKnownByPeer >= 0 && streamId > lastStreamKnownByPeer) {
                throw streamError(streamId, REFUSED_STREAM,
                        "Cannot create stream %d greater than Last-Stream-ID %d from GOAWAY.",
                        streamId, lastStreamKnownByPeer);
            }
            if (!isValidStreamId(streamId)) {
                if (streamId < 0) {
                    throw new Http2NoMoreStreamIdsException();
                }
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
            boolean isReserved = state == RESERVED_LOCAL || state == RESERVED_REMOTE;
            if (!isReserved && !canOpenStream() || isReserved && numStreams >= maxStreams) {
                throw streamError(streamId, REFUSED_STREAM, "Maximum active streams violated for this endpoint.");
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
        /**
         * Initial size of 4 because the default configuration currently has 3 listeners
         * (local/remote flow controller and {@link StreamByteDistributor}) and we leave room for 1 extra.
         * We could be more aggressive but the ArrayList resize will double the size if we are too small.
         */
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
