/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.spdy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AsciiString;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Encodes {@link HttpRequest}s, {@link HttpResponse}s, and {@link HttpContent}s
 * into {@link SpdySynStreamFrame}s and {@link SpdySynReplyFrame}s.
 *
 * <h3>Request Annotations</h3>
 *
 * SPDY specific headers must be added to {@link HttpRequest}s:
 * <table border=1>
 * <tr>
 * <th>Header Name</th><th>Header Value</th>
 * </tr>
 * <tr>
 * <td>{@code "X-SPDY-Stream-ID"}</td>
 * <td>The Stream-ID for this request.
 * Stream-IDs must be odd, positive integers, and must increase monotonically.</td>
 * </tr>
 * <tr>
 * <td>{@code "X-SPDY-Priority"}</td>
 * <td>The priority value for this request.
 * The priority should be between 0 and 7 inclusive.
 * 0 represents the highest priority and 7 represents the lowest.
 * This header is optional and defaults to 0.</td>
 * </tr>
 * </table>
 *
 * <h3>Response Annotations</h3>
 *
 * SPDY specific headers must be added to {@link HttpResponse}s:
 * <table border=1>
 * <tr>
 * <th>Header Name</th><th>Header Value</th>
 * </tr>
 * <tr>
 * <td>{@code "X-SPDY-Stream-ID"}</td>
 * <td>The Stream-ID of the request corresponding to this response.</td>
 * </tr>
 * </table>
 *
 * <h3>Pushed Resource Annotations</h3>
 *
 * SPDY specific headers must be added to pushed {@link HttpRequest}s:
 * <table border=1>
 * <tr>
 * <th>Header Name</th><th>Header Value</th>
 * </tr>
 * <tr>
 * <td>{@code "X-SPDY-Stream-ID"}</td>
 * <td>The Stream-ID for this resource.
 * Stream-IDs must be even, positive integers, and must increase monotonically.</td>
 * </tr>
 * <tr>
 * <td>{@code "X-SPDY-Associated-To-Stream-ID"}</td>
 * <td>The Stream-ID of the request that initiated this pushed resource.</td>
 * </tr>
 * <tr>
 * <td>{@code "X-SPDY-Priority"}</td>
 * <td>The priority value for this resource.
 * The priority should be between 0 and 7 inclusive.
 * 0 represents the highest priority and 7 represents the lowest.
 * This header is optional and defaults to 0.</td>
 * </tr>
 * </table>
 *
 * <h3>Required Annotations</h3>
 *
 * SPDY requires that all Requests and Pushed Resources contain
 * an HTTP "Host" header.
 *
 * <h3>Optional Annotations</h3>
 *
 * Requests and Pushed Resources must contain a SPDY scheme header.
 * This can be set via the {@code "X-SPDY-Scheme"} header but otherwise
 * defaults to "https" as that is the most common SPDY deployment.
 *
 * <h3>Chunked Content</h3>
 *
 * This encoder associates all {@link HttpContent}s that it receives
 * with the most recently received 'chunked' {@link HttpRequest}
 * or {@link HttpResponse}.
 *
 * <h3>Pushed Resources</h3>
 *
 * All pushed resources should be sent before sending the response
 * that corresponds to the initial request.
 */
public class SpdyHttpEncoder extends MessageToMessageEncoder<HttpObject> {

    private int currentStreamId;

    private final boolean validateHeaders;
    private final boolean headersToLowerCase;

    /**
     * Creates a new instance.
     *
     * @param version the protocol version
     */
    public SpdyHttpEncoder(SpdyVersion version) {
        this(version, true, true);
    }

    /**
     * Creates a new instance.
     *
     * @param version            the protocol version
     * @param headersToLowerCase convert header names to lowercase. In a controlled environment,
     *                           one can disable the conversion.
     * @param validateHeaders    validate the header names and values when adding them to the {@link SpdyHeaders}
     */
    public SpdyHttpEncoder(SpdyVersion version, boolean headersToLowerCase, boolean validateHeaders) {
        if (version == null) {
            throw new NullPointerException("version");
        }
        this.headersToLowerCase = headersToLowerCase;
        this.validateHeaders = validateHeaders;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {

        boolean valid = false;
        boolean last = false;

        if (msg instanceof HttpRequest) {

            HttpRequest httpRequest = (HttpRequest) msg;
            SpdySynStreamFrame spdySynStreamFrame = createSynStreamFrame(httpRequest);
            out.add(spdySynStreamFrame);

            last = spdySynStreamFrame.isLast() || spdySynStreamFrame.isUnidirectional();
            valid = true;
        }
        if (msg instanceof HttpResponse) {

            HttpResponse httpResponse = (HttpResponse) msg;
            SpdyHeadersFrame spdyHeadersFrame = createHeadersFrame(httpResponse);
            out.add(spdyHeadersFrame);

            last = spdyHeadersFrame.isLast();
            valid = true;
        }
        if (msg instanceof HttpContent && !last) {

            HttpContent chunk = (HttpContent) msg;

            chunk.content().retain();
            SpdyDataFrame spdyDataFrame = new DefaultSpdyDataFrame(currentStreamId, chunk.content());
            if (chunk instanceof LastHttpContent) {
                LastHttpContent trailer = (LastHttpContent) chunk;
                HttpHeaders trailers = trailer.trailingHeaders();
                if (trailers.isEmpty()) {
                    spdyDataFrame.setLast(true);
                    out.add(spdyDataFrame);
                } else {
                    // Create SPDY HEADERS frame out of trailers
                    SpdyHeadersFrame spdyHeadersFrame = new DefaultSpdyHeadersFrame(currentStreamId, validateHeaders);
                    spdyHeadersFrame.setLast(true);
                    Iterator<Entry<CharSequence, CharSequence>> itr = trailers.iteratorCharSequence();
                    while (itr.hasNext()) {
                        Map.Entry<CharSequence, CharSequence> entry = itr.next();
                        final CharSequence headerName =
                                headersToLowerCase ? AsciiString.of(entry.getKey()).toLowerCase() : entry.getKey();
                        spdyHeadersFrame.headers().add(headerName, entry.getValue());
                    }

                    // Write DATA frame and append HEADERS frame
                    out.add(spdyDataFrame);
                    out.add(spdyHeadersFrame);
                }
            } else {
                out.add(spdyDataFrame);
            }

            valid = true;
        }

        if (!valid) {
            throw new UnsupportedMessageTypeException(msg);
        }
    }

    @SuppressWarnings("deprecation")
    private SpdySynStreamFrame createSynStreamFrame(HttpRequest httpRequest) throws Exception {
        // Get the Stream-ID, Associated-To-Stream-ID, Priority, and scheme from the headers
        final HttpHeaders httpHeaders = httpRequest.headers();
        int streamId = httpHeaders.getInt(SpdyHttpHeaders.Names.STREAM_ID);
        int associatedToStreamId = httpHeaders.getInt(SpdyHttpHeaders.Names.ASSOCIATED_TO_STREAM_ID, 0);
        byte priority = (byte) httpHeaders.getInt(SpdyHttpHeaders.Names.PRIORITY, 0);
        CharSequence scheme = httpHeaders.get(SpdyHttpHeaders.Names.SCHEME);
        httpHeaders.remove(SpdyHttpHeaders.Names.STREAM_ID);
        httpHeaders.remove(SpdyHttpHeaders.Names.ASSOCIATED_TO_STREAM_ID);
        httpHeaders.remove(SpdyHttpHeaders.Names.PRIORITY);
        httpHeaders.remove(SpdyHttpHeaders.Names.SCHEME);

        // The Connection, Keep-Alive, Proxy-Connection, and Transfer-Encoding
        // headers are not valid and MUST not be sent.
        httpHeaders.remove(HttpHeaderNames.CONNECTION);
        httpHeaders.remove("Keep-Alive");
        httpHeaders.remove("Proxy-Connection");
        httpHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);

        SpdySynStreamFrame spdySynStreamFrame =
                new DefaultSpdySynStreamFrame(streamId, associatedToStreamId, priority, validateHeaders);

        // Unfold the first line of the message into name/value pairs
        SpdyHeaders frameHeaders = spdySynStreamFrame.headers();
        frameHeaders.set(SpdyHeaders.HttpNames.METHOD, httpRequest.method().name());
        frameHeaders.set(SpdyHeaders.HttpNames.PATH, httpRequest.uri());
        frameHeaders.set(SpdyHeaders.HttpNames.VERSION, httpRequest.protocolVersion().text());

        // Replace the HTTP host header with the SPDY host header
        CharSequence host = httpHeaders.get(HttpHeaderNames.HOST);
        httpHeaders.remove(HttpHeaderNames.HOST);
        frameHeaders.set(SpdyHeaders.HttpNames.HOST, host);

        // Set the SPDY scheme header
        if (scheme == null) {
            scheme = "https";
        }
        frameHeaders.set(SpdyHeaders.HttpNames.SCHEME, scheme);

        // Transfer the remaining HTTP headers
        Iterator<Entry<CharSequence, CharSequence>> itr = httpHeaders.iteratorCharSequence();
        while (itr.hasNext()) {
            Map.Entry<CharSequence, CharSequence> entry = itr.next();
            final CharSequence headerName =
                    headersToLowerCase ? AsciiString.of(entry.getKey()).toLowerCase() : entry.getKey();
            frameHeaders.add(headerName, entry.getValue());
        }
        currentStreamId = spdySynStreamFrame.streamId();
        if (associatedToStreamId == 0) {
            spdySynStreamFrame.setLast(isLast(httpRequest));
        } else {
            spdySynStreamFrame.setUnidirectional(true);
        }

        return spdySynStreamFrame;
    }

    @SuppressWarnings("deprecation")
    private SpdyHeadersFrame createHeadersFrame(HttpResponse httpResponse) throws Exception {
        // Get the Stream-ID from the headers
        final HttpHeaders httpHeaders = httpResponse.headers();
        int streamId = httpHeaders.getInt(SpdyHttpHeaders.Names.STREAM_ID);
        httpHeaders.remove(SpdyHttpHeaders.Names.STREAM_ID);

        // The Connection, Keep-Alive, Proxy-Connection, and Transfer-Encoding
        // headers are not valid and MUST not be sent.
        httpHeaders.remove(HttpHeaderNames.CONNECTION);
        httpHeaders.remove("Keep-Alive");
        httpHeaders.remove("Proxy-Connection");
        httpHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);

        SpdyHeadersFrame spdyHeadersFrame;
        if (SpdyCodecUtil.isServerId(streamId)) {
            spdyHeadersFrame = new DefaultSpdyHeadersFrame(streamId, validateHeaders);
        } else {
            spdyHeadersFrame = new DefaultSpdySynReplyFrame(streamId, validateHeaders);
        }
        SpdyHeaders frameHeaders = spdyHeadersFrame.headers();
        // Unfold the first line of the response into name/value pairs
        frameHeaders.set(SpdyHeaders.HttpNames.STATUS, httpResponse.status().codeAsText());
        frameHeaders.set(SpdyHeaders.HttpNames.VERSION, httpResponse.protocolVersion().text());

        // Transfer the remaining HTTP headers
        Iterator<Entry<CharSequence, CharSequence>> itr = httpHeaders.iteratorCharSequence();
        while (itr.hasNext()) {
            Map.Entry<CharSequence, CharSequence> entry = itr.next();
            final CharSequence headerName =
                    headersToLowerCase ? AsciiString.of(entry.getKey()).toLowerCase() : entry.getKey();
            spdyHeadersFrame.headers().add(headerName, entry.getValue());
        }

        currentStreamId = streamId;
        spdyHeadersFrame.setLast(isLast(httpResponse));

        return spdyHeadersFrame;
    }

    /**
     * Checks if the given HTTP message should be considered as a last SPDY frame.
     *
     * @param httpMessage check this HTTP message
     * @return whether the given HTTP message should generate a <em>last</em> SPDY frame.
     */
    private static boolean isLast(HttpMessage httpMessage) {
        if (httpMessage instanceof FullHttpMessage) {
            FullHttpMessage fullMessage = (FullHttpMessage) httpMessage;
            if (fullMessage.trailingHeaders().isEmpty() && !fullMessage.content().isReadable()) {
                return true;
            }
        }

        return false;
    }
}
