/*
 * Copyright 2015 The Netty Project
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
package io.netty5.microbench.http2;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.adapt.AdaptivePoolingAllocator;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalIoHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.ServerSocketChannel;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.http2.AbstractHttp2ConnectionHandlerBuilder;
import io.netty5.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty5.handler.codec.http2.Http2ConnectionDecoder;
import io.netty5.handler.codec.http2.Http2ConnectionEncoder;
import io.netty5.handler.codec.http2.Http2ConnectionHandler;
import io.netty5.handler.codec.http2.Http2DataFrame;
import io.netty5.handler.codec.http2.Http2Flags;
import io.netty5.handler.codec.http2.Http2FrameCodec;
import io.netty5.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty5.handler.codec.http2.Http2FrameListener;
import io.netty5.handler.codec.http2.Http2HeadersFrame;
import io.netty5.handler.codec.http2.Http2MultiplexHandler;
import io.netty5.handler.codec.http2.Http2SecurityUtil;
import io.netty5.handler.codec.http2.Http2Settings;
import io.netty5.handler.codec.http2.Http2StreamChannel;
import io.netty5.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty5.handler.codec.http2.Http2StreamFrame;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.handler.ssl.SslContext;
import io.netty5.handler.ssl.SslContextBuilder;
import io.netty5.handler.ssl.SupportedCipherSuiteFilter;
import io.netty5.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty5.handler.ssl.util.SelfSignedCertificate;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.netty5.handler.codec.http.HttpResponseStatus.OK;
import static io.netty5.handler.ssl.SslProvider.OPENSSL;
import static java.nio.charset.StandardCharsets.US_ASCII;

@Warmup(iterations = 20, time = 1)
@Measurement(iterations = 20, time = 1)
@Threads(1)
@State(Scope.Benchmark)
public class Http2ThroughputBenchmark extends AbstractMicrobenchmark {
    private static final Logger logger = LoggerFactory.getLogger(Http2ThroughputBenchmark.class);
    private static final BufferAllocator pooled = BufferAllocator.offHeapPooled();
    private static final BufferAllocator adaptive = new AdaptivePoolingAllocator(true);

    @Param({"true", "false"})
    public boolean ssl;

    @Param("local")
    public Transport transport;

    @Param({"adaptive", "pooled"})
    public String allocatorConfig;

    private Channel serverChannel;
    private EventLoopGroup serverGroup;
    private BufferAllocator allocator;
    private SocketAddress serverAddress;
    private EventLoopGroup clientGroup;
    private Channel clientChannel;
    private Http2StreamChannel clientStreamChannel;
    private Semaphore clientResponse;
    private Http2ClientStreamFrameResponseHandler streamFrameResponseHandler;
    private IoHandlerFactory handlerFactory;
    private Bootstrap cb;

    public enum Transport {
        local, nio, epoll, kqueue, io_uring
    }

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        allocator = configureAllocator();

        // Configure the server.
        handlerFactory = getHandlerFactory();
        serverGroup = new MultithreadEventLoopGroup(threadFactory("server"), handlerFactory);
        ServerBootstrap sb = new ServerBootstrap();
        sb.option(ChannelOption.SO_BACKLOG, 1024);
        sb.option(ChannelOption.BUFFER_ALLOCATOR, allocator);
        sb.childOption(ChannelOption.BUFFER_ALLOCATOR, allocator);
        sb.group(serverGroup)
                .channel(serverChannelClass())
                .childHandler(new Http2ServerInitializer(configureServerSsl()));

        SocketAddress anyAddress = transport == Transport.local?
                new LocalAddress(Http2ThroughputBenchmark.class) : new InetSocketAddress(NetUtil.LOCALHOST, 0);
        serverChannel = sb.bind(anyAddress).asStage().get();
        serverAddress = serverChannel.localAddress();
        logger.debug("Server channel: {}", serverChannel);

        // Configure the client.
        clientGroup = new MultithreadEventLoopGroup(threadFactory("client"), handlerFactory);
        cb = new Bootstrap();
        cb.group(clientGroup);
        cb.channel(clientChannelClass());
        cb.option(ChannelOption.SO_KEEPALIVE, true);
        cb.option(ChannelOption.BUFFER_ALLOCATOR, allocator);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                SslContext sslContext = configureClientSsl();
                if (sslContext != null) {
                    pipeline.addLast(sslContext.newHandler(ch.bufferAllocator()));
                }
                final Http2FrameCodec http2FrameCodec = Http2FrameCodecBuilder.forClient().build();
                ch.pipeline().addLast(http2FrameCodec);
                ch.pipeline().addLast(new Http2MultiplexHandler(new SimpleChannelInboundHandler<>() {
                    @Override
                    protected void messageReceived(ChannelHandlerContext ctx, Object msg) {
                        // NOOP (this is the handler for 'inbound' streams, which is not relevant in this example)
                    }
                }));
            }
        });

        // Start the client.
        clientChannel = cb.connect(serverAddress).asStage().get();
        logger.debug("Client channel: {}",  clientChannel);
        clientResponse = new Semaphore(0);
        streamFrameResponseHandler = new Http2ClientStreamFrameResponseHandler(clientResponse);
    }

    @Benchmark
    public void request() throws Exception {
        final Http2StreamChannelBootstrap streamChannelBootstrap = new Http2StreamChannelBootstrap(clientChannel);
        clientStreamChannel = streamChannelBootstrap.open().asStage().get();
        clientStreamChannel.pipeline().addLast(streamFrameResponseHandler);

        // Send request (a HTTP/2 HEADERS frame - with ':method = GET' in this case)
        final Http2Headers headers = Http2Headers.newHeaders();
        headers.method("GET");
        headers.path("/");
        headers.scheme(ssl? "https" : "http");
        final Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(headers, true);
        clientStreamChannel.writeAndFlush(headersFrame);

        // Wait for the responses (or for the latch to expire), then clean up the connections
        if (!streamFrameResponseHandler.responseSuccessfullyCompleted()) {
            logger.warn("Did not get HTTP/2 response in expected time.");
        }
        clientStreamChannel.close().asStage().get();
    }

    @TearDown(Level.Trial)
    public void tearDownTrial() throws InterruptedException {
        Future<Void> clientClose = clientChannel.close();
        Future<Void> serverClose = serverChannel.close();
        try {
            clientClose.asStage().sync();
            serverClose.asStage().sync();
        } finally {
            clientGroup.shutdownGracefully();
            serverGroup.shutdownGracefully();
        }
    }

    private BufferAllocator configureAllocator() {
        switch (allocatorConfig) {
            case "pooled": return pooled;
            case "adaptive": return adaptive;
            default: throw new UnsupportedOperationException("Unrecognized allocator: " + allocatorConfig);
        }
    }

    private static ThreadFactory threadFactory(String name) {
        return runnable -> new Thread(runnable, name);
    }

    private IoHandlerFactory getHandlerFactory() throws Exception {
        Class<?> factoryClass;
        switch (transport) {
            case local: return LocalIoHandler.newFactory();
            case nio: return NioIoHandler.newFactory();
            case epoll: factoryClass = Class.forName("io.netty5.channel.epoll.EpollIoHandler"); break;
            case kqueue: factoryClass = Class.forName("io.netty5.channel.kqueue.KQueueIoHandler"); break;
            case io_uring: factoryClass = Class.forName("io.netty5.channel.uring.IoUring"); break;
            default: throw new UnsupportedOperationException("Unrecognized transport: " + transport);
        }
        Method newFactory = factoryClass.getDeclaredMethod("newFactory");
        return (IoHandlerFactory) newFactory.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends ServerChannel> serverChannelClass() throws ClassNotFoundException {
        switch (transport) {
            case local: return LocalServerChannel.class;
            case nio: return NioServerSocketChannel.class;
            case epoll: return (Class<? extends ServerSocketChannel>) Class.forName(
                    "io.netty5.channel.epoll.EpollServerSocketChannel");
            case kqueue: return (Class<? extends ServerSocketChannel>) Class.forName(
                    "io.netty5.channel.kqueue.KQueueServerSocketChannel");
            case io_uring: return (Class<? extends ServerSocketChannel>) Class.forName(
                    "io.netty5.channel.uring.IoUringServerSocketChannel");
            default: throw new UnsupportedOperationException("Unrecognized transport: " + transport);
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Channel> clientChannelClass() throws ClassNotFoundException {
        switch (transport) {
            case local: return LocalChannel.class;
            case nio: return NioSocketChannel.class;
            case epoll: return (Class<? extends SocketChannel>) Class.forName(
                    "io.netty5.channel.epoll.EpollSocketChannel");
            case kqueue: return (Class<? extends SocketChannel>) Class.forName(
                    "io.netty5.channel.kqueue.KQueueSocketChannel");
            case io_uring: return (Class<? extends SocketChannel>) Class.forName(
                    "io.netty5.channel.uring.IoUringSocketChannel");
            default: throw new UnsupportedOperationException("Unrecognized transport: " + transport);
        }
    }

    private SslContext configureServerSsl() throws CertificateException, SSLException {
        if (ssl) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                    .sslProvider(OPENSSL)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .build();
        }
        return null;
    }

    private SslContext configureClientSsl() throws CertificateException, SSLException {
        if (ssl) {
            return SslContextBuilder.forClient()
                    .sslProvider(OPENSSL)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE) // Accept the self-signed server cert.
                    .build();
        }
        return null;
    }

    static class Http2ServerInitializer extends ChannelInitializer<Channel> {
        private final SslContext sslCtx;

        Http2ServerInitializer(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        public void initChannel(Channel ch) {
            if (sslCtx != null) {
                configureSsl(ch);
            }
            configureClearText(ch);
        }

        private void configureSsl(Channel ch) {
            ch.pipeline().addLast(sslCtx.newHandler(ch.bufferAllocator()));
        }

        private static void configureClearText(Channel ch) {
            final ChannelPipeline p = ch.pipeline();
            p.addLast(new HelloWorldHttp2HandlerBuilder().build());
        }
    }

    static class HelloWorldHttp2HandlerBuilder extends
            AbstractHttp2ConnectionHandlerBuilder<HelloWorldHttp2Handler, HelloWorldHttp2HandlerBuilder> {
        @Override
        public HelloWorldHttp2Handler build() {
            return super.build();
        }

        @Override
        protected HelloWorldHttp2Handler build(
                Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings)
                throws Exception {
            HelloWorldHttp2Handler handler = new HelloWorldHttp2Handler(decoder, encoder, initialSettings);
            frameListener(handler);
            return handler;
        }
    }

    static class HelloWorldHttp2Handler extends Http2ConnectionHandler implements Http2FrameListener {
        static final byte[] RESPONSE_BYTES = "Hello World".getBytes(StandardCharsets.UTF_8);

        HelloWorldHttp2Handler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                               Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }

        @Override
        public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.channelExceptionCaught(ctx, cause);
            logger.warn("Server-side exception", cause);
            ctx.close();
        }

        /**
         * Sends a "Hello World" DATA frame to the client.
         */
        private void sendResponse(ChannelHandlerContext ctx, int streamId, Buffer payload) {
            // Send a frame for the response status
            Http2Headers headers = Http2Headers.newHeaders().status(OK.codeAsText());
            encoder().writeHeaders(ctx, streamId, headers, 0, false);
            encoder().writeData(ctx, streamId, payload, 0, true);

            // no need to call flush as channelReadComplete(...) will take care of it.
        }

        @Override
        public int onDataRead(ChannelHandlerContext ctx, int streamId, Buffer data, int padding, boolean endOfStream) {
            int processed = data.readableBytes() + padding;
            if (endOfStream) {
                sendResponse(ctx, streamId, data);
            }
            return processed;
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                                  Http2Headers headers, int padding, boolean endOfStream) {
            if (endOfStream) {
                final byte[] viaBytes = " - via HTTP/2".getBytes(US_ASCII);
                Buffer content = ctx.bufferAllocator().allocate(RESPONSE_BYTES.length + viaBytes.length)
                        .writeBytes(RESPONSE_BYTES).writeBytes(viaBytes);
                sendResponse(ctx, streamId, content);
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                                  short weight, boolean exclusive, int padding, boolean endOfStream) {
            onHeadersRead(ctx, streamId, headers, padding, endOfStream);
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                                   short weight, boolean exclusive) {
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) {
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, long data) {
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, long data) {
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                      Http2Headers headers, int padding) {
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, Buffer debugData) {
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                                   Http2Flags flags, Buffer payload) {
        }
    }

    static class Http2ClientStreamFrameResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private final Semaphore latch;

        Http2ClientStreamFrameResponseHandler(Semaphore clientResponse) {
            latch = clientResponse;
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, Http2StreamFrame msg) {
            logger.debug("Received HTTP/2 'stream' frame: {}", msg);

            // isEndStream() is not from a common interface, so we currently must check both
            if (msg instanceof Http2DataFrame && ((Http2DataFrame) msg).isEndStream()) {
                latch.release();
            } else if (msg instanceof Http2HeadersFrame && ((Http2HeadersFrame) msg).isEndStream()) {
                latch.release();
            }
        }

        /**
         * Waits for the latch to be decremented (i.e. for an end of stream message to be received), or for
         * the latch to expire after 5 seconds.
         * @return true if a successful HTTP/2 end of stream message was received.
         */
        public boolean responseSuccessfullyCompleted() {
            try {
                return latch.tryAcquire(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                logger.warn("Latch exception", ie);
                return false;
            }
        }
    }
}
