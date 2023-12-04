/*
 * Copyright 2015 The Netty Project
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
package io.netty5.handler.codec.rtsp;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.DefaultFullHttpRequest;
import io.netty5.handler.codec.http.DefaultFullHttpResponse;
import io.netty5.handler.codec.http.DefaultHttpRequest;
import io.netty5.handler.codec.http.DefaultHttpResponse;
import io.netty5.handler.codec.http.HttpDecoderConfig;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObjectDecoder;
import io.netty5.handler.codec.http.HttpResponseStatus;

import java.util.regex.Pattern;

/**
 * Decodes {@link io.netty5.buffer.Buffer}s into RTSP messages represented in
 * {@link HttpMessage}s.
 * <p>
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "SETUP / RTSP/1.0"} or {@code "RTSP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link io.netty5.handler.codec.TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers. If the sum of the length of each
 *     header exceeds this value, a {@link io.netty5.handler.codec.TooLongFrameException} will be
 *     raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxContentLength}</td>
 * <td>The maximum length of the content.  If the content length exceeds this
 *     value, a {@link io.netty5.handler.codec.TooLongFrameException} will be raised.</td>
 * </tr>
 * </table>
 */
public class RtspDecoder extends HttpObjectDecoder {
    /**
     * Status code for unknown responses.
     */
    private static final HttpResponseStatus UNKNOWN_STATUS =
            new HttpResponseStatus(999, "Unknown");
    /**
     * True if the message to decode is a request.
     * False if the message to decode is a response.
     */
    private boolean isDecodingRequest;

    /**
     * Regex used on first line in message to detect if it is a response.
     */
    private static final Pattern versionPattern = Pattern.compile("RTSP/\\d\\.\\d");

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength} ({@value DEFAULT_MAX_INITIAL_LINE_LENGTH}),
     * {@code maxHeaderSize} ({@value DEFAULT_MAX_HEADER_SIZE}),
     * and {@code chunkedSupported} ({@value DEFAULT_CHUNKED_SUPPORTED}).
     */
    public RtspDecoder() {
        this(new HttpDecoderConfig());
    }

    /**
     * Creates a new instance with the specified parameters.
     * @param maxInitialLineLength The max allowed length of initial line
     * @param maxHeaderSize The max allowed size of header
     */
    public RtspDecoder(final int maxInitialLineLength,
                       final int maxHeaderSize) {
        this(new HttpDecoderConfig().setMaxInitialLineLength(maxInitialLineLength).setMaxHeaderSize(maxHeaderSize));
    }

    /**
     * Creates a new instance with the specified configuration.
     * @param config The decoder configuration.
     */
    public RtspDecoder(HttpDecoderConfig config) {
        super(config);
    }

    @Override
    protected HttpMessage createMessage(final String[] initialLine)
            throws Exception {
        // If the first element of the initial line is a version string then
        // this is a response
        if (versionPattern.matcher(initialLine[0]).matches()) {
            isDecodingRequest = false;
            return new DefaultHttpResponse(RtspVersions.valueOf(initialLine[0]),
                new HttpResponseStatus(Integer.parseInt(initialLine[1]),
                                       initialLine[2]),
                headersFactory);
        } else {
            isDecodingRequest = true;
            return new DefaultHttpRequest(RtspVersions.valueOf(initialLine[2]),
                    RtspMethods.valueOf(initialLine[0]),
                    initialLine[1],
                    headersFactory);
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(final HttpMessage msg) {
        // Unlike HTTP, RTSP always assumes zero-length body if Content-Length
        // header is absent.
        return super.isContentAlwaysEmpty(msg) || !msg.headers().contains(RtspHeaderNames.CONTENT_LENGTH);
    }

    @Override
    protected HttpMessage createInvalidMessage(ChannelHandlerContext ctx) {
        if (isDecodingRequest) {
            return new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.OPTIONS, "/bad-request",
                    ctx.bufferAllocator().allocate(0), headersFactory, trailersFactory);
        } else {
            return new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, UNKNOWN_STATUS,
                    ctx.bufferAllocator().allocate(0), headersFactory, trailersFactory);
        }
    }

    @Override
    protected boolean isDecodingRequest() {
        return isDecodingRequest;
    }
}
