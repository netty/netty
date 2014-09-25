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

import static io.netty.handler.codec.base64.Base64Dialect.URL_SAFE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http2.Http2CodecUtil.FRAME_HEADER_LENGTH;
import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME;
import static io.netty.handler.codec.http2.Http2CodecUtil.HTTP_UPGRADE_SETTINGS_HEADER;
import static io.netty.handler.codec.http2.Http2CodecUtil.checkNotNull;
import static io.netty.handler.codec.http2.Http2CodecUtil.writeFrameHeader;
import static io.netty.handler.codec.http2.Http2FrameTypes.SETTINGS;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.util.CharsetUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Server-side codec for performing a cleartext upgrade from HTTP/1.x to HTTP/2.
 */
public class Http2ServerUpgradeCodec implements HttpServerUpgradeHandler.UpgradeCodec {

    private static final List<String> REQUIRED_UPGRADE_HEADERS =
            Collections.singletonList(HTTP_UPGRADE_SETTINGS_HEADER);

    private final String handlerName;
    private final Http2ConnectionHandler connectionHandler;
    private final Http2FrameReader frameReader;
    private Http2Settings settings;

    /**
     * Creates the codec using a default name for the connection handler when adding to the
     * pipeline.
     *
     * @param connectionHandler the HTTP/2 connection handler.
     */
    public Http2ServerUpgradeCodec(Http2ConnectionHandler connectionHandler) {
        this("http2ConnectionHandler", connectionHandler);
    }

    /**
     * Creates the codec providing an upgrade to the given handler for HTTP/2.
     *
     * @param handlerName the name of the HTTP/2 connection handler to be used in the pipeline.
     * @param connectionHandler the HTTP/2 connection handler.
     */
    public Http2ServerUpgradeCodec(String handlerName, Http2ConnectionHandler connectionHandler) {
        this.handlerName = checkNotNull(handlerName, "handlerName");
        this.connectionHandler = checkNotNull(connectionHandler, "connectionHandler");
        frameReader = new DefaultHttp2FrameReader();
    }

    @Override
    public String protocol() {
        return HTTP_UPGRADE_PROTOCOL_NAME;
    }

    @Override
    public Collection<String> requiredUpgradeHeaders() {
        return REQUIRED_UPGRADE_HEADERS;
    }

    @Override
    public void prepareUpgradeResponse(ChannelHandlerContext ctx, FullHttpRequest upgradeRequest,
            FullHttpResponse upgradeResponse) {
        try {
            // Decode the HTTP2-Settings header and set the settings on the handler to make
            // sure everything is fine with the request.
            String settingsHeader = upgradeRequest.headers().get(HTTP_UPGRADE_SETTINGS_HEADER);
            settings = decodeSettingsHeader(ctx, settingsHeader);
            connectionHandler.onHttpServerUpgrade(settings);
            // Everything looks good, no need to modify the response.
        } catch (Throwable e) {
            // Send a failed response back to the client.
            upgradeResponse.setStatus(BAD_REQUEST);
            upgradeResponse.headers().clear();
        }
    }

    @Override
    public void upgradeTo(final ChannelHandlerContext ctx, FullHttpRequest upgradeRequest,
            FullHttpResponse upgradeResponse) {
        // Add the HTTP/2 connection handler to the pipeline immediately following the current
        // handler.
        ctx.pipeline().addAfter(ctx.name(), handlerName, connectionHandler);
    }

    /**
     * Decodes the settings header and returns a {@link Http2Settings} object.
     */
    private Http2Settings decodeSettingsHeader(ChannelHandlerContext ctx, String settingsHeader)
            throws Http2Exception {
        ByteBuf header = Unpooled.wrappedBuffer(settingsHeader.getBytes(CharsetUtil.UTF_8));
        try {
            // Decode the SETTINGS payload.
            ByteBuf payload = Base64.decode(header, URL_SAFE);

            // Create an HTTP/2 frame for the settings.
            ByteBuf frame = createSettingsFrame(ctx, payload);

            // Decode the SETTINGS frame and return the settings object.
            return decodeSettings(ctx, frame);
        } finally {
            header.release();
        }
    }

    /**
     * Decodes the settings frame and returns the settings.
     */
    private Http2Settings decodeSettings(ChannelHandlerContext ctx, ByteBuf frame) throws Http2Exception {
        try {
            final Http2Settings decodedSettings = new Http2Settings();
            frameReader.readFrame(ctx, frame, new Http2FrameAdapter() {
                @Override
                public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                        throws Http2Exception {
                    decodedSettings.copyFrom(settings);
                }
            });
            return decodedSettings;
        } finally {
            frame.release();
        }
    }

    /**
     * Creates an HTTP2-Settings header with the given payload. The payload buffer is released.
     */
    private static ByteBuf createSettingsFrame(ChannelHandlerContext ctx, ByteBuf payload) {
        ByteBuf frame = ctx.alloc().buffer(FRAME_HEADER_LENGTH + payload.readableBytes());
        writeFrameHeader(frame, payload.readableBytes(), SETTINGS, new Http2Flags(), 0);
        frame.writeBytes(payload);
        payload.release();
        return frame;
    }
}
