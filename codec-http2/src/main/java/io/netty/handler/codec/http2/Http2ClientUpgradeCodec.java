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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.base64.Base64;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.collection.IntObjectMap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.netty.handler.codec.base64.Base64Dialect.*;
import static io.netty.handler.codec.http2.Http2CodecUtil.*;
import static io.netty.util.CharsetUtil.*;
import static io.netty.util.ReferenceCountUtil.*;

/**
 * Client-side cleartext upgrade codec from HTTP to HTTP/2.
 */
public class Http2ClientUpgradeCodec implements HttpClientUpgradeHandler.UpgradeCodec {

    private static final List<String> UPGRADE_HEADERS = Collections.singletonList(HTTP_UPGRADE_SETTINGS_HEADER);

    private final String handlerName;
    private final Http2InboundConnectionHandler connectionHandler;

    /**
     * Creates the codec using a default name for the connection handler when adding to the
     * pipeline.
     *
     * @param connectionHandler the HTTP/2 connection handler.
     */
    public Http2ClientUpgradeCodec(Http2InboundConnectionHandler connectionHandler) {
        this("http2ConnectionHandler", connectionHandler);
    }

    /**
     * Creates the codec providing an upgrade to the given handler for HTTP/2.
     *
     * @param handlerName the name of the HTTP/2 connection handler to be used in the pipeline.
     * @param connectionHandler the HTTP/2 connection handler.
     */
    public Http2ClientUpgradeCodec(String handlerName,
            Http2InboundConnectionHandler connectionHandler) {
        if (handlerName == null) {
            throw new NullPointerException("handlerName");
        }
        if (connectionHandler == null) {
            throw new NullPointerException("connectionHandler");
        }
        this.handlerName = handlerName;
        this.connectionHandler = connectionHandler;
    }

    @Override
    public String protocol() {
        return HTTP_UPGRADE_PROTOCOL_NAME;
    }

    @Override
    public Collection<String> setUpgradeHeaders(ChannelHandlerContext ctx,
            HttpRequest upgradeRequest) {
        String settingsValue = getSettingsHeaderValue(ctx);
        upgradeRequest.headers().set(HTTP_UPGRADE_SETTINGS_HEADER, settingsValue);
        return UPGRADE_HEADERS;
    }

    @Override
    public void upgradeTo(ChannelHandlerContext ctx, FullHttpResponse upgradeResponse)
            throws Exception {
        // Reserve local stream 1 for the response.
        connectionHandler.onHttpClientUpgrade();

        // Add the handler to the pipeline.
        ctx.pipeline().addAfter(ctx.name(), handlerName, connectionHandler);
    }

    /**
     * Converts the current settings for the handler to the Base64-encoded representation used in
     * the HTTP2-Settings upgrade header.
     */
    private String getSettingsHeaderValue(ChannelHandlerContext ctx) {
        ByteBuf buf = null;
        ByteBuf encodedBuf = null;
        try {
            // Get the local settings for the handler.
            Http2Settings settings = connectionHandler.settings();

            // Serialize the payload of the SETTINGS frame.
            int payloadLength = SETTING_ENTRY_LENGTH * settings.size();
            buf = ctx.alloc().buffer(payloadLength);
            for (IntObjectMap.Entry<Long> entry : settings.entries()) {
                writeUnsignedShort(entry.key(), buf);
                writeUnsignedInt(entry.value(), buf);
            }

            // Base64 encode the payload and then convert to a string for the header.
            encodedBuf = Base64.encode(buf, URL_SAFE);
            return encodedBuf.toString(UTF_8);
        } finally {
            release(buf);
            release(encodedBuf);
        }
    }
}
