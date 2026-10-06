/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandlerContext;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT_ENCODING;
import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * An HTTP/2 frame listener that captures the {@code accept-encoding} header of a request into a
 * {@link Http2RequestAcceptEncodingPropertyKey} so it can be read again when the response for the same stream is
 * written. This listener does not modify the headers in any way; the {@code accept-encoding} header remains
 * visible to the application and to the delegate {@link Http2FrameListener}.
 */
public class Http2RequestAcceptEncodingFrameListener extends Http2FrameListenerDecorator {

    private final Http2Connection connection;
    private final Http2RequestAcceptEncodingPropertyKey key;

    /**
     * Create a new instance.
     *
     * @param connection the connection whose streams carry the captured {@code accept-encoding} value
     * @param listener the delegate listener used by {@link Http2FrameListenerDecorator}
     * @param key the shared property key used to store the captured {@code accept-encoding} value
     */
    public Http2RequestAcceptEncodingFrameListener(Http2Connection connection, Http2FrameListener listener,
                                                    Http2RequestAcceptEncodingPropertyKey key) {
        super(listener);
        this.connection = checkNotNull(connection, "connection");
        this.key = checkNotNull(key, "key");
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                               boolean endStream) throws Http2Exception {
        captureAcceptEncoding(streamId, headers);
        listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                               short weight, boolean exclusive, int padding, boolean endStream)
            throws Http2Exception {
        captureAcceptEncoding(streamId, headers);
        listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive, padding, endStream);
    }

    private void captureAcceptEncoding(int streamId, Http2Headers headers) {
        Http2Stream stream = connection.stream(streamId);
        if (stream == null) {
            return;
        }
        CharSequence acceptEncoding = headers.get(ACCEPT_ENCODING);
        if (acceptEncoding != null) {
            key.setAcceptEncoding(stream, acceptEncoding);
        }
    }
}
