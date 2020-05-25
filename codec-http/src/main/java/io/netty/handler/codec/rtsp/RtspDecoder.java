/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.rtsp;

import java.util.regex.Pattern;

import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * Decodes {@link io.netty.buffer.ByteBuf}s into RTSP messages represented in
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
 *     {@link io.netty.handler.codec.TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers. If the sum of the length of each
 *     header exceeds this value, a {@link io.netty.handler.codec.TooLongFrameException} will be
 *     raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxContentLength}</td>
 * <td>The maximum length of the content.  If the content length exceeds this
 *     value, a {@link io.netty.handler.codec.TooLongFrameException} will be raised.</td>
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
     * Constant for default max content length.
     */
    public static final int DEFAULT_MAX_CONTENT_LENGTH = 8192;

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096)}, {@code maxHeaderSize (8192)}, and
     * {@code maxContentLength (8192)}.
     */
    public RtspDecoder() {
        this(DEFAULT_MAX_INITIAL_LINE_LENGTH,
             DEFAULT_MAX_HEADER_SIZE,
             DEFAULT_MAX_CONTENT_LENGTH);
    }

    /**
     * Creates a new instance with the specified parameters.
     * @param maxInitialLineLength The max allowed length of initial line
     * @param maxHeaderSize The max allowed size of header
     * @param maxContentLength The max allowed content length
     */
    public RtspDecoder(final int maxInitialLineLength,
                       final int maxHeaderSize,
                       final int maxContentLength) {
        super(maxInitialLineLength, maxHeaderSize, maxContentLength * 2, false);
    }

    /**
     * Creates a new instance with the specified parameters.
     * @param maxInitialLineLength The max allowed length of initial line
     * @param maxHeaderSize The max allowed size of header
     * @param maxContentLength The max allowed content length
     * @param validateHeaders Set to true if headers should be validated
     */
    public RtspDecoder(final int maxInitialLineLength,
                       final int maxHeaderSize,
                       final int maxContentLength,
                       final boolean validateHeaders) {
        super(maxInitialLineLength,
              maxHeaderSize,
              maxContentLength * 2,
              false,
              validateHeaders);
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
                validateHeaders);
        } else {
            isDecodingRequest = true;
            return new DefaultHttpRequest(RtspVersions.valueOf(initialLine[2]),
                    RtspMethods.valueOf(initialLine[0]),
                    initialLine[1],
                    validateHeaders);
        }
    }

    @Override
    protected boolean isContentAlwaysEmpty(final HttpMessage msg) {
        // Unlike HTTP, RTSP always assumes zero-length body if Content-Length
        // header is absent.
        return super.isContentAlwaysEmpty(msg) || !msg.headers().contains(RtspHeaderNames.CONTENT_LENGTH);
    }

    @Override
    protected HttpMessage createInvalidMessage() {
        if (isDecodingRequest) {
            return new DefaultFullHttpRequest(RtspVersions.RTSP_1_0,
                       RtspMethods.OPTIONS, "/bad-request", validateHeaders);
        } else {
            return new DefaultFullHttpResponse(RtspVersions.RTSP_1_0,
                                               UNKNOWN_STATUS,
                                               validateHeaders);
        }
    }

    @Override
    protected boolean isDecodingRequest() {
        return isDecodingRequest;
    }
}
