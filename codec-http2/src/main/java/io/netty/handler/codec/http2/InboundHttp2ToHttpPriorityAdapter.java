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

import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;

import java.util.Map.Entry;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AsciiString;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;
import io.netty.util.internal.PlatformDependent;

/**
 * Translate header/data/priority HTTP/2 frame events into HTTP events.  Just as {@link InboundHttp2ToHttpAdapter}
 * may generate multiple {@link FullHttpMessage} objects per stream, this class is more likely to
 * generate multiple messages per stream because the chances of an HTTP/2 event happening outside
 * the header/data message flow is more likely.
 */
public final class InboundHttp2ToHttpPriorityAdapter extends InboundHttp2ToHttpAdapter {
    private static final AsciiString OUT_OF_MESSAGE_SEQUENCE_METHOD = new AsciiString(
            HttpUtil.OUT_OF_MESSAGE_SEQUENCE_METHOD.toString());
    private static final AsciiString OUT_OF_MESSAGE_SEQUENCE_PATH = new AsciiString(
            HttpUtil.OUT_OF_MESSAGE_SEQUENCE_PATH);
    private static final AsciiString OUT_OF_MESSAGE_SEQUENCE_RETURN_CODE = new AsciiString(
            HttpUtil.OUT_OF_MESSAGE_SEQUENCE_RETURN_CODE.toString());
    private final IntObjectMap<HttpHeaders> outOfMessageFlowHeaders;

    public static final class Builder extends InboundHttp2ToHttpAdapter.Builder {

        /**
         * Creates a new {@link InboundHttp2ToHttpPriorityAdapter} builder for the specified {@link Http2Connection}.
         *
         * @param connection The object which will provide connection notification events for the current connection
         */
        public Builder(Http2Connection connection) {
            super(connection);
        }

        @Override
        public InboundHttp2ToHttpPriorityAdapter build() {
            final InboundHttp2ToHttpPriorityAdapter instance = new InboundHttp2ToHttpPriorityAdapter(this);
            instance.connection.addListener(instance);
            return instance;
        }
    }

    InboundHttp2ToHttpPriorityAdapter(Builder builder) {
        super(builder);
        outOfMessageFlowHeaders = new IntObjectHashMap<HttpHeaders>();
    }

    @Override
    protected void removeMessage(int streamId) {
        super.removeMessage(streamId);
        outOfMessageFlowHeaders.remove(streamId);
    }

    /**
     * Get either the header or the trailing headers depending on which is valid to add to
     * @param msg The message containing the headers and trailing headers
     * @return The headers object which can be appended to or modified
     */
    private static HttpHeaders getActiveHeaders(FullHttpMessage msg) {
        return msg.content().isReadable() ? msg.trailingHeaders() : msg.headers();
    }

    /**
     * This method will add the {@code headers} to the out of order headers map
     * @param streamId The stream id associated with {@code headers}
     * @param headers Newly encountered out of order headers which must be stored for future use
     */
    private void importOutOfMessageFlowHeaders(int streamId, HttpHeaders headers) {
        final HttpHeaders outOfMessageFlowHeader = outOfMessageFlowHeaders.get(streamId);
        if (outOfMessageFlowHeader == null) {
            outOfMessageFlowHeaders.put(streamId, headers);
        } else {
            outOfMessageFlowHeader.setAll(headers);
        }
    }

    /**
     * Take any saved out of order headers and export them to {@code headers}
     * @param streamId The stream id to search for out of order headers for
     * @param headers If any out of order headers exist for {@code streamId} they will be added to this object
     */
    private void exportOutOfMessageFlowHeaders(int streamId, final HttpHeaders headers) {
        final HttpHeaders outOfMessageFlowHeader = outOfMessageFlowHeaders.get(streamId);
        if (outOfMessageFlowHeader != null) {
            headers.setAll(outOfMessageFlowHeader);
        }
    }

    /**
     * This will remove all headers which are related to priority tree events
     * @param headers The headers to remove the priority tree elements from
     */
    private static void removePriorityRelatedHeaders(HttpHeaders headers) {
        headers.remove(HttpUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text());
        headers.remove(HttpUtil.ExtensionHeaderNames.STREAM_WEIGHT.text());
    }

    /**
     * Initializes the pseudo header fields for out of message flow HTTP/2 headers
     * @param headers The headers to be initialized with pseudo header values
     */
    private void initializePseudoHeaders(Http2Headers headers) {
        if (connection.isServer()) {
            headers.method(OUT_OF_MESSAGE_SEQUENCE_METHOD).path(OUT_OF_MESSAGE_SEQUENCE_PATH);
        } else {
            headers.status(OUT_OF_MESSAGE_SEQUENCE_RETURN_CODE);
        }
    }

    /**
     * Add all the HTTP headers into the HTTP/2 headers object
     * @param httpHeaders The HTTP headers to translate to HTTP/2
     * @param http2Headers The target HTTP/2 headers
     */
    private static void addHttpHeadersToHttp2Headers(HttpHeaders httpHeaders, final Http2Headers http2Headers) {
        try {
            for (Entry<CharSequence, CharSequence> entry : httpHeaders) {
                http2Headers.add(AsciiString.of(entry.getKey()), AsciiString.of(entry.getValue()));
            }
        } catch (Exception ex) {
            PlatformDependent.throwException(ex);
        }
    }

    @Override
    protected void fireChannelRead(ChannelHandlerContext ctx, FullHttpMessage msg, int streamId) {
        exportOutOfMessageFlowHeaders(streamId, getActiveHeaders(msg));
        super.fireChannelRead(ctx, msg, streamId);
    }

    @Override
    protected FullHttpMessage processHeadersBegin(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            boolean endOfStream, boolean allowAppend, boolean appendToTrailer) throws Http2Exception {
        FullHttpMessage msg = super.processHeadersBegin(ctx, streamId, headers,
                endOfStream, allowAppend, appendToTrailer);
        if (msg != null) {
            exportOutOfMessageFlowHeaders(streamId, getActiveHeaders(msg));
        }
        return msg;
    }

    @Override
    public void onPriorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
        Http2Stream parent = stream.parent();
        FullHttpMessage msg = messageMap.get(stream.id());
        if (msg == null) {
            // msg may be null if a HTTP/2 frame event in received outside the HTTP message flow
            // For example a PRIORITY frame can be received in any state
            // and the HTTP message flow exists in OPEN.
            if (parent != null && !parent.equals(connection.connectionStream())) {
                HttpHeaders headers = new DefaultHttpHeaders();
                headers.setInt(HttpUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), parent.id());
                importOutOfMessageFlowHeaders(stream.id(), headers);
            }
        } else {
            if (parent == null) {
                removePriorityRelatedHeaders(msg.headers());
                removePriorityRelatedHeaders(msg.trailingHeaders());
            } else if (!parent.equals(connection.connectionStream())) {
                HttpHeaders headers = getActiveHeaders(msg);
                headers.setInt(HttpUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), parent.id());
            }
        }
    }

    @Override
    public void onWeightChanged(Http2Stream stream, short oldWeight) {
        FullHttpMessage msg = messageMap.get(stream.id());
        final HttpHeaders headers;
        if (msg == null) {
            // msg may be null if a HTTP/2 frame event in received outside the HTTP message flow
            // For example a PRIORITY frame can be received in any state
            // and the HTTP message flow exists in OPEN.
            headers = new DefaultHttpHeaders();
            importOutOfMessageFlowHeaders(stream.id(), headers);
        } else {
            headers = getActiveHeaders(msg);
        }
        headers.setShort(HttpUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), stream.weight());
    }

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                    boolean exclusive) throws Http2Exception {
        FullHttpMessage msg = messageMap.get(streamId);
        if (msg == null) {
            HttpHeaders httpHeaders = outOfMessageFlowHeaders.remove(streamId);
            if (httpHeaders == null) {
                throw connectionError(PROTOCOL_ERROR, "Priority Frame recieved for unknown stream id %d", streamId);
            }

            Http2Headers http2Headers = new DefaultHttp2Headers();
            initializePseudoHeaders(http2Headers);
            addHttpHeadersToHttp2Headers(httpHeaders, http2Headers);
            msg = newMessage(streamId, http2Headers, validateHttpHeaders);
            fireChannelRead(ctx, msg, streamId);
        }
    }
}
