/*
 * Copyright 2022 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

public class Http2MultiplexHandlerEmbeddedTest {
    @Test
    public void h2cServerInEmbeddedChannel() {
        final AtomicReference<String> receivedRequestPath = new AtomicReference<String>();

        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        HttpServerCodec httpServerCodec = new HttpServerCodec();
        final Http2FrameCodec frameCodec = Http2FrameCodecBuilder.forServer().build();
        final HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(
                httpServerCodec,
                new HttpServerUpgradeHandler.UpgradeCodecFactory() {
                    @Override
                    public HttpServerUpgradeHandler.UpgradeCodec newUpgradeCodec(CharSequence protocol) {
                        Assertions.assertEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol);
                        return new Http2ServerUpgradeCodec(frameCodec, Http2MultiplexHandler.forServer(
                                new ChannelInitializer<Http2StreamChannel>() {
                                    @Override
                                    protected void initChannel(Http2StreamChannel ch) throws Exception {
                                        ch.pipeline()
                                                .addLast(new Http2StreamFrameToHttpObjectCodec(true))
                                                .addLast(new SimpleChannelInboundHandler<HttpRequest>() {
                                                    @Override
                                                    protected void channelRead0(ChannelHandlerContext ctx,
                                                                                HttpRequest msg) {
                                                        receivedRequestPath.set(msg.uri());
                                                        ctx.writeAndFlush(new DefaultFullHttpResponse(
                                                                HttpVersion.HTTP_1_1, HttpResponseStatus.OK));
                                                    }
                                                });
                                    }
                                }));
                    }
                },
                1024
        );
        embeddedChannel.pipeline()
                .addLast(httpServerCodec)
                .addLast(upgradeHandler);

        embeddedChannel.writeInbound(Unpooled.wrappedBuffer(
                ("GET /xyz HTTP/1.1\r\n" +
                        "Host: foo\r\n" +
                        "Upgrade: h2c\r\n" +
                        "Connection: Upgrade, HTTP2-Settings\r\n" +
                        "HTTP2-Settings: \r\n" + // empty (default) settings is fine
                        "\r\n").getBytes(CharsetUtil.UTF_8)));
        embeddedChannel.runPendingTasks();
        embeddedChannel.checkException();

        Assertions.assertEquals("/xyz", receivedRequestPath.get());
    }
}
