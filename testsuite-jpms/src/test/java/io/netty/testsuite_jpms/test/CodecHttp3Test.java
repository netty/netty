/*
 * Copyright 2025 The Netty Project
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
package io.netty.testsuite_jpms.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CodecHttp3Test {

    private static final byte[] CONTENT = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);
    static final int PORT = 9999;

    @Test
    public void smokeTest() throws Exception {

        int port = PORT;
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        try {
            Channel serverChannel = startServer(group, port);
            runClient(group, port);
            serverChannel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private void runClient(EventLoopGroup group, int port) throws InterruptedException, ExecutionException {
        QuicSslContext context = QuicSslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .applicationProtocols(Http3.supportedApplicationProtocols()).build();
        ChannelHandler codec = Http3.newQuicClientCodecBuilder()
                .sslContext(context)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();

        Bootstrap bs = new Bootstrap();
        Channel channel = bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(0).sync().channel();

        QuicChannel quicChannel = QuicChannel.newBootstrap(channel)
                .handler(new Http3ClientConnectionHandler())
                .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST4, port))
                .connect()
                .get();

        CompletableFuture<String> response = new CompletableFuture<>();

        QuicStreamChannel streamChannel = Http3.newRequestStream(quicChannel,
                new Http3RequestStreamInboundHandler() {

                    @Override
                    protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
                        ReferenceCountUtil.release(frame);
                    }

                    @Override
                    protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
                        response.complete(frame.content().toString(CharsetUtil.US_ASCII));
                        ReferenceCountUtil.release(frame);
                    }

                    @Override
                    protected void channelInputClosed(ChannelHandlerContext ctx) {
                        ctx.close();
                    }
                }).sync().getNow();

        // Write the Header frame and send the FIN to mark the end of the request.
        // After this its not possible anymore to write any more data.
        Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
        frame.headers().method("GET").path("/")
                .authority(NetUtil.LOCALHOST4.getHostAddress() + ":" + port)
                .scheme("https");
        streamChannel.writeAndFlush(frame)
                .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

        // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
        // After this is done we will close the underlying datagram channel.
        streamChannel.closeFuture().sync();

        assertEquals("Hello World!\r\n", response.get());

        // After we received the response lets also close the underlying QUIC channel and datagram channel.
        quicChannel.close().sync();
        channel.close().sync();
    }

    private Channel startServer(EventLoopGroup group, int port) throws InterruptedException, CertificateException {
        SelfSignedCertificate cert = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(cert.key(), null, cert.cert())
                .applicationProtocols(Http3.supportedApplicationProtocols()).build();
        ChannelHandler codec = Http3.newQuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        // Called for each connection
                        ch.pipeline().addLast(new Http3ServerConnectionHandler(
                                new ChannelInitializer<QuicStreamChannel>() {
                                    // Called for each request-stream,
                                    @Override
                                    protected void initChannel(QuicStreamChannel ch) {
                                        ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {

                                            @Override
                                            protected void channelRead(
                                                    ChannelHandlerContext ctx, Http3HeadersFrame frame) {
                                                ReferenceCountUtil.release(frame);
                                            }

                                            @Override
                                            protected void channelRead(
                                                    ChannelHandlerContext ctx, Http3DataFrame frame) {
                                                ReferenceCountUtil.release(frame);
                                            }

                                            @Override
                                            protected void channelInputClosed(ChannelHandlerContext ctx) {
                                                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                                                headersFrame.headers().status("404");
                                                headersFrame.headers().add("server", "netty");
                                                headersFrame.headers().addInt("content-length", CONTENT.length);
                                                ctx.write(headersFrame);
                                                ctx.writeAndFlush(new DefaultHttp3DataFrame(
                                                                Unpooled.wrappedBuffer(CONTENT)))
                                                        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                            }
                                        });
                                    }
                                }));
                    }
                }).build();

        Bootstrap bs = new Bootstrap();
        return bs.group(group)
                .channel(NioDatagramChannel.class)
                .handler(codec)
                .bind(new InetSocketAddress(port)).sync().channel();
    }
}
