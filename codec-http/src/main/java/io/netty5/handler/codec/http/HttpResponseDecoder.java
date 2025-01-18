/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;

/**
 * Decodes {@link Buffer}s into {@link HttpResponse}s and {@link HttpContent}s.
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line (e.g. {@code "HTTP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongHttpLineException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongHttpHeaderException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     exceeds this value, the transfer encoding of the decoded response will be
 *     converted to 'chunked' and the content will be split into multiple
 *     {@link HttpContent}s.  If the transfer encoding of the HTTP response is
 *     'chunked' already, each chunk will be split into smaller chunks if the
 *     length of the chunk exceeds this value.  If you prefer not to handle
 *     {@link HttpContent}s in your handler, insert {@link HttpObjectAggregator}
 *     after this decoder in the {@link ChannelPipeline}.</td>
 * </tr>
 * </table>
 *
 * <h3>Decoding a response for a <tt>HEAD</tt> request</h3>
 * <p>
 * Unlike other HTTP requests, the successful response of a <tt>HEAD</tt>
 * request does not have any content even if there is <tt>Content-Length</tt>
 * header.  Because {@link HttpResponseDecoder} is not able to determine if the
 * response currently being decoded is associated with a <tt>HEAD</tt> request,
 * you must override {@link #isContentAlwaysEmpty(HttpMessage)} to return
 * <tt>true</tt> for the response of the <tt>HEAD</tt> request.
 * </p><p>
 * If you are writing an HTTP client that issues a <tt>HEAD</tt> request,
 * please use {@link HttpClientCodec} instead of this decoder.  It will perform
 * additional state management to handle the responses for <tt>HEAD</tt>
 * requests correctly.
 * </p>
 *
 * <h3>Decoding a response for a <tt>CONNECT</tt> request</h3>
 * <p>
 * You also need to do additional state management to handle the response of a
 * <tt>CONNECT</tt> request properly, like you did for <tt>HEAD</tt>.  One
 * difference is that the decoder should stop decoding completely after decoding
 * the successful 200 response since the connection is not an HTTP connection
 * anymore.
 * </p><p>
 * {@link HttpClientCodec} also handles this edge case correctly, so you have to
 * use {@link HttpClientCodec} if you are writing an HTTP client that issues a
 * <tt>CONNECT</tt> request.
 * </p>
 *
 * <h3>Header Validation</h3>
 *
 * It is recommended to always enable header validation.
 * <p>
 * Without header validation, your system can become vulnerable to
 * <a href="https://cwe.mitre.org/data/definitions/113.html">
 *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
 * </a>.
 * <p>
 * This recommendation stands even when both peers in the HTTP exchange are trusted,
 * as it helps with defence-in-depth.
 */
public class HttpResponseDecoder extends HttpObjectDecoder {

    private static final HttpResponseStatus UNKNOWN_STATUS = new HttpResponseStatus(999, "Unknown");

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength} ({@value DEFAULT_MAX_INITIAL_LINE_LENGTH}),
     * {@code maxHeaderSize} ({@value DEFAULT_MAX_HEADER_SIZE}),
     * and {@code chunkedSupported} ({@value DEFAULT_CHUNKED_SUPPORTED}).
     */
    public HttpResponseDecoder() {
    }

    /**
     * Creates a new instance with the specified parameters.
     *
     * @param maxInitialLineLength the initial size of the temporary buffer used when parsing the lines of the
     * HTTP headers.
     * @param maxHeaderSize the maximum permitted combined size of all headers in any one response.
     * @see HttpDecoderConfig HttpDecoderConfig API documentation for detailed descriptions of
     * the configuration parameters.
     */
    public HttpResponseDecoder(
            int maxInitialLineLength, int maxHeaderSize) {
        super(new HttpDecoderConfig()
                .setMaxInitialLineLength(maxInitialLineLength)
                .setMaxHeaderSize(maxHeaderSize));
    }

    /**
     * Creates a new instance with the specified configuration.
     * @param config The configuration for the response decoder.
     * @see HttpDecoderConfig HttpDecoderConfig API documentation for detailed descriptions of
     * the configuration parameters.
     */
    public HttpResponseDecoder(HttpDecoderConfig config) {
        super(config);
    }

    @Override
    protected HttpMessage createMessage(String[] initialLine) {
        return new DefaultHttpResponse(
                // Do strict version checking
                HttpVersion.valueOf(initialLine[0], true),
                HttpResponseStatus.valueOf(Integer.parseInt(initialLine[1]), initialLine[2]), headersFactory);
    }

    @Override
    protected HttpMessage createInvalidMessage(ChannelHandlerContext ctx) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, UNKNOWN_STATUS, ctx.bufferAllocator().allocate(0),
                headersFactory, trailersFactory);
    }

    @Override
    protected boolean isDecodingRequest() {
        return false;
    }
}
