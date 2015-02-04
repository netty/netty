/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.http2;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_MAX_FRAME_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_FRAME_SIZE_UPPER_BOUND;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.AsciiString;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty.microbench.util.AbstractSharedExecutorMicrobenchmark;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.AfterClass;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class Http2FrameWriterBenchmark extends AbstractSharedExecutorMicrobenchmark {
    private static final EnvironmentParameters NIO_UNPOOLED_PARAMS =
            new NioEnvironmentParametersBase(UnpooledByteBufAllocator.DEFAULT);
    private static final EnvironmentParameters NIO_POOLED_PARAMS =
            new NioEnvironmentParametersBase(PooledByteBufAllocator.DEFAULT);
    private static final EnvironmentParameters EPOLL_UNPOOLED_PARAMS =
            new EpollEnvironmentParametersBase(UnpooledByteBufAllocator.DEFAULT);
    private static final EnvironmentParameters EPOLL_POOLED_PARAMS =
            new EpollEnvironmentParametersBase(PooledByteBufAllocator.DEFAULT);
    private static final EnvironmentParameters OIO_UNPOOLED_PARAMS =
            new OioEnvironmentParametersBase(UnpooledByteBufAllocator.DEFAULT);
    private static final EnvironmentParameters OIO_POOLED_PARAMS =
            new OioEnvironmentParametersBase(PooledByteBufAllocator.DEFAULT);

    public static enum EnvironmentType {
        EMBEDDED_POOLED, EMBEDDED_UNPOOLED,
        NIO_POOLED, NIO_UNPOOLED,
        EPOLL_POOLED, EPOLL_UNPOOLED,
        OIO_POOLED, OIO_UNPOOLED;
    }

    public static enum DataPayloadType {
        SMALL, MEDIUM, LARGE, JUMBO;
    }

    private static final Map<EnvironmentType, Environment> ENVIRONMENTS = new HashMap<EnvironmentType, Environment>();
    private static final Map<DataPayloadType, BenchmarkTestPayload> PAYLOADS =
            new HashMap<DataPayloadType, BenchmarkTestPayload>();

    static {
        ENVIRONMENTS.put(EnvironmentType.OIO_POOLED, boostrapEnvWithTransport(OIO_POOLED_PARAMS));
        ENVIRONMENTS.put(EnvironmentType.OIO_UNPOOLED, boostrapEnvWithTransport(OIO_UNPOOLED_PARAMS));
        ENVIRONMENTS.put(EnvironmentType.NIO_POOLED, boostrapEnvWithTransport(NIO_POOLED_PARAMS));
        ENVIRONMENTS.put(EnvironmentType.NIO_UNPOOLED, boostrapEnvWithTransport(NIO_UNPOOLED_PARAMS));
        if (Epoll.isAvailable()) {
            ENVIRONMENTS.put(EnvironmentType.EPOLL_POOLED, boostrapEnvWithTransport(EPOLL_POOLED_PARAMS));
            ENVIRONMENTS.put(EnvironmentType.EPOLL_UNPOOLED, boostrapEnvWithTransport(EPOLL_UNPOOLED_PARAMS));
        }
        ENVIRONMENTS.put(EnvironmentType.EMBEDDED_POOLED, boostrapEmbeddedEnv(PooledByteBufAllocator.DEFAULT));
        ENVIRONMENTS.put(EnvironmentType.EMBEDDED_UNPOOLED, boostrapEmbeddedEnv(UnpooledByteBufAllocator.DEFAULT));
        PAYLOADS.put(DataPayloadType.SMALL, createPayload(DataPayloadType.SMALL));
        PAYLOADS.put(DataPayloadType.MEDIUM, createPayload(DataPayloadType.MEDIUM));
        PAYLOADS.put(DataPayloadType.LARGE, createPayload(DataPayloadType.LARGE));
        PAYLOADS.put(DataPayloadType.JUMBO, createPayload(DataPayloadType.JUMBO));
    }

    @Param
    public EnvironmentType environmentType;

    @Param
    public DataPayloadType dataType;

    @Param({ "0", "255" })
    public int padding;

    private Environment environment;

    private BenchmarkTestPayload payload;

    @AfterClass
    public static void teardown() {
        for (Environment env : ENVIRONMENTS.values()) {
            try {
                env.teardown();
            } catch (Exception e) {
                handleUnexpectedException(e);
            }
        }
        for (BenchmarkTestPayload payload : PAYLOADS.values()) {
            payload.release();
        }
    }

    @Setup(Level.Trial)
    public void setup() {
        environment = ENVIRONMENTS.get(environmentType);
        if (environment == null) {
            throw new IllegalStateException("Environment type [" + environmentType + "] is not supported.");
        }
        AbstractSharedExecutorMicrobenchmark.executor(environment.eventLoop());
        payload = PAYLOADS.get(dataType);
    }

    @Benchmark
    public void writeData() {
        ChannelHandlerContext context = environment.context();
        environment.writer().writeData(context, 3, payload.data().retain(), padding, true, context.voidPromise());
        context.flush();
    }

    @Benchmark
    public void writeHeaders() {
        ChannelHandlerContext context = environment.context();
        environment.writer().writeHeaders(context, 3, payload.headers(), padding, true, context.voidPromise());
        context.flush();
    }

    private static Http2Headers createHeaders(int numValues, int nameLength, int valueLength) {
        Http2Headers headers = new DefaultHttp2Headers();
        Random r = new Random();
        for (int i = 0; i < numValues; ++i) {
            byte[] tmp = new byte[nameLength];
            r.nextBytes(tmp);
            AsciiString name = new AsciiString(tmp);
            tmp = new byte[valueLength];
            r.nextBytes(tmp);
            headers.add(name, new AsciiString(tmp));
        }
        return headers;
    }

    private static ByteBuf createData(int length) {
        byte[] result = new byte[length];
        new Random().nextBytes(result);
        return Unpooled.wrappedBuffer(result);
    }

    private static BenchmarkTestPayload createPayload(DataPayloadType type) {
        switch (type) {
        case SMALL:
            return new BenchmarkTestPayload(createData(256), createHeaders(5, 20, 20));
        case MEDIUM:
            return new BenchmarkTestPayload(createData(DEFAULT_MAX_FRAME_SIZE), createHeaders(20, 40, 40));
        case LARGE:
            return new BenchmarkTestPayload(createData(MAX_FRAME_SIZE_UPPER_BOUND), createHeaders(100, 100, 100));
        case JUMBO:
            return new BenchmarkTestPayload(createData(10 * MAX_FRAME_SIZE_UPPER_BOUND), createHeaders(300, 300, 300));
        default:
            throw new Error();
        }
    }

    private static final class BenchmarkTestPayload {
        private final ByteBuf data;
        private final Http2Headers headers;

        public BenchmarkTestPayload(ByteBuf data, Http2Headers headers) {
            this.data = data;
            this.headers = headers;
        }

        public ByteBuf data() {
            return data;
        }

        public Http2Headers headers() {
            return headers;
        }

        public void release() {
            data.release();
        }
    }

    private static Environment boostrapEnvWithTransport(final EnvironmentParameters params) {
        ServerBootstrap sb = new ServerBootstrap();
        Bootstrap cb = new Bootstrap();
        final TrasportEnvironment environment = new TrasportEnvironment(cb, sb);

        EventLoopGroup serverEventLoopGroup = params.newEventLoopGroup();
        sb.group(serverEventLoopGroup, serverEventLoopGroup);
        sb.channel(params.serverChannelClass());
        sb.option(ChannelOption.ALLOCATOR, params.serverAllocator());
        sb.childOption(ChannelOption.ALLOCATOR, params.serverAllocator());
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
            }
        });

        cb.group(params.newEventLoopGroup());
        cb.channel(params.clientChannelClass());
        cb.option(ChannelOption.ALLOCATOR, params.clientAllocator());
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2Connection connection = new DefaultHttp2Connection(false);
                Http2RemoteFlowController remoteFlowController = params.remoteFlowController();
                if (remoteFlowController != null) {
                    connection.remote().flowController(params.remoteFlowController());
                }
                Http2LocalFlowController localFlowController = params.localFlowController();
                if (localFlowController != null) {
                    connection.local().flowController(localFlowController);
                }
                environment.writer(new DefaultHttp2FrameWriter());
                Http2ConnectionHandler connectionHandler = new Http2ConnectionHandler(
                        new DefaultHttp2ConnectionDecoder.Builder().connection(connection)
                                .frameReader(new DefaultHttp2FrameReader()).listener(new Http2FrameAdapter()),
                        new DefaultHttp2ConnectionEncoder.Builder().connection(connection).frameWriter(
                                environment.writer()));
                p.addLast(connectionHandler);
                environment.context(p.lastContext());
            }
        });

        environment.serverChannel(sb.bind(params.address()));
        params.address(environment.serverChannel().localAddress());
        environment.clientChannel(cb.connect(params.address()));
        return environment;
    }

    private static Environment boostrapEmbeddedEnv(final ByteBufAllocator alloc) {
        final EmbeddedEnvironment env = new EmbeddedEnvironment(new DefaultHttp2FrameWriter());
        final Http2Connection connection = new DefaultHttp2Connection(false);
        final Http2ConnectionHandler connectionHandler = new Http2ConnectionHandler(
                new DefaultHttp2ConnectionDecoder.Builder().connection(connection)
                        .frameReader(new DefaultHttp2FrameReader()).listener(new Http2FrameAdapter()),
                new DefaultHttp2ConnectionEncoder.Builder().connection(connection).frameWriter(env.writer()));
        env.context(new EmbeddedChannelWriteReleaseHandlerContext(alloc, connectionHandler) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        });

        return env;
    }

    private interface Environment {
        /**
         * Get the event loop that should be shared with JMH to execute the benchmark.
         */
        EventLoop eventLoop();

        /**
         * The context to use during the benchmark.
         */
        ChannelHandlerContext context();

        /**
         * The writer which will be subject to benchmarking.
         */
        Http2FrameWriter writer();

        /**
         * Do any cleanup after environment is no longer needed.
         */
        void teardown() throws Exception;
    }

    private interface EnvironmentParameters {
        EventLoopGroup newEventLoopGroup();

        Class<? extends ServerChannel> serverChannelClass();

        Class<? extends Channel> clientChannelClass();

        ByteBufAllocator clientAllocator();

        ByteBufAllocator serverAllocator();

        SocketAddress address();

        void address(SocketAddress address);

        Http2RemoteFlowController remoteFlowController();

        Http2LocalFlowController localFlowController();
    }

    private abstract static class EnvironmentParametersBase implements EnvironmentParameters {
        private final ByteBufAllocator clientAlloc;
        private final ByteBufAllocator serverAlloc;
        private final Class<? extends Channel> clientChannelClass;
        private final Class<? extends ServerChannel> serverChannelClass;
        private final Http2RemoteFlowController remoteFlowController;
        private final Http2LocalFlowController localFlowController;
        private SocketAddress address;

        EnvironmentParametersBase(ByteBufAllocator serverAlloc, ByteBufAllocator clientAlloc,
                Class<? extends ServerChannel> serverChannelClass, Class<? extends Channel> clientChannelClass) {
            this(serverAlloc, clientAlloc, serverChannelClass, clientChannelClass,
                    NoopHttp2RemoteFlowController.INSTANCE, NoopHttp2LocalFlowController.INSTANCE);
        }

        EnvironmentParametersBase(ByteBufAllocator serverAlloc, ByteBufAllocator clientAlloc,
                Class<? extends ServerChannel> serverChannelClass, Class<? extends Channel> clientChannelClass,
                Http2RemoteFlowController remoteFlowController, Http2LocalFlowController localFlowController) {
            this.serverAlloc = checkNotNull(serverAlloc, "serverAlloc");
            this.clientAlloc = checkNotNull(clientAlloc, "clientAlloc");
            this.clientChannelClass = checkNotNull(clientChannelClass, "clientChannelClass");
            this.serverChannelClass = checkNotNull(serverChannelClass, "serverChannelClass");
            this.remoteFlowController = remoteFlowController; // OK to be null
            this.localFlowController = localFlowController; // OK to be null
        }

        @Override
        public SocketAddress address() {
            if (address == null) {
                return new InetSocketAddress(0);
            }
            return address;
        }

        @Override
        public void address(SocketAddress address) {
            this.address = address;
        }

        @Override
        public Class<? extends ServerChannel> serverChannelClass() {
            return serverChannelClass;
        }

        @Override
        public Class<? extends Channel> clientChannelClass() {
            return clientChannelClass;
        }

        @Override
        public ByteBufAllocator clientAllocator() {
            return clientAlloc;
        }

        @Override
        public ByteBufAllocator serverAllocator() {
            return serverAlloc;
        }

        @Override
        public Http2RemoteFlowController remoteFlowController() {
            return remoteFlowController;
        }

        @Override
        public Http2LocalFlowController localFlowController() {
            return localFlowController;
        }
    };

    private static class NioEnvironmentParametersBase extends EnvironmentParametersBase {
        NioEnvironmentParametersBase(ByteBufAllocator clientAlloc) {
            super(UnpooledByteBufAllocator.DEFAULT, clientAlloc, NioServerSocketChannel.class, NioSocketChannel.class);
        }

        @Override
        public EventLoopGroup newEventLoopGroup() {
            return new NioEventLoopGroup(1);
        }
    }

    private static class EpollEnvironmentParametersBase extends EnvironmentParametersBase {
        EpollEnvironmentParametersBase(ByteBufAllocator clientAlloc) {
            super(UnpooledByteBufAllocator.DEFAULT, clientAlloc,
                    EpollServerSocketChannel.class, EpollSocketChannel.class);
        }

        @Override
        public EventLoopGroup newEventLoopGroup() {
            return new EpollEventLoopGroup(1);
        }
    }

    private static class OioEnvironmentParametersBase extends EnvironmentParametersBase {
        OioEnvironmentParametersBase(ByteBufAllocator clientAlloc) {
            super(UnpooledByteBufAllocator.DEFAULT, clientAlloc, OioServerSocketChannel.class, OioSocketChannel.class);
        }

        @Override
        public EventLoopGroup newEventLoopGroup() {
            return new OioEventLoopGroup(1);
        }
    }

    private static final class TrasportEnvironment implements Environment {
        private final ServerBootstrap sb;
        private final Bootstrap cb;
        private Channel serverChannel;
        private Channel clientChannel;
        private ChannelHandlerContext clientContext;
        private Http2FrameWriter clientWriter;

        public TrasportEnvironment(Bootstrap cb, ServerBootstrap sb) {
            this.sb = checkNotNull(sb, "sb");
            this.cb = checkNotNull(cb, "cb");
        }

        @Override
        public EventLoop eventLoop() {
            // It is assumed the channel is registered to the event loop by the time this is called
            return clientChannel.eventLoop();
        }

        public Channel serverChannel() {
            return serverChannel;
        }

        public void serverChannel(ChannelFuture bindFuture) {
            // No need to sync or wait by default...local channel immediate executor
            serverChannel = checkNotNull(bindFuture, "bindFuture").channel();
        }

        public void clientChannel(ChannelFuture connectFuture) {
            // No need to sync or wait by default...local channel immediate executor
            clientChannel = checkNotNull(connectFuture, "connectFuture").channel();
        }

        public void context(ChannelHandlerContext context) {
            clientContext = checkNotNull(context, "context");
        }

        @Override
        public ChannelHandlerContext context() {
            return clientContext;
        }

        @Override
        public void teardown() throws InterruptedException {
            if (clientChannel != null) {
                clientChannel.close();
            }
            if (serverChannel != null) {
                serverChannel.close();
            }
            Future<?> serverGroup = null;
            Future<?> serverChildGroup = null;
            Future<?> clientGroup = null;
            if (sb != null) {
                serverGroup = sb.group().shutdownGracefully(0, 0, MILLISECONDS);
                serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, MILLISECONDS);
            }
            if (cb != null) {
                clientGroup = cb.group().shutdownGracefully(0, 0, MILLISECONDS);
            }
            if (sb != null) {
                serverGroup.sync();
                serverChildGroup.sync();
            }
            if (cb != null) {
                clientGroup.sync();
            }
        }

        public void writer(Http2FrameWriter writer) {
            clientWriter = checkNotNull(writer, "writer");
        }

        @Override
        public Http2FrameWriter writer() {
            return clientWriter;
        }
    }

    private static final class EmbeddedEnvironment implements Environment {
        private final Http2FrameWriter writer;
        private ChannelHandlerContext context;
        private EventLoop eventLoop;

        public EmbeddedEnvironment(Http2FrameWriter writer) {
            this.writer = checkNotNull(writer, "writer");
        }

        @Override
        public EventLoop eventLoop() {
            return eventLoop;
        }

        public void context(EmbeddedChannelWriteReleaseHandlerContext context) {
            this.context = checkNotNull(context, "context");
            Channel channel = checkNotNull(context.channel(), "context.channel()");
            this.eventLoop = checkNotNull(channel.eventLoop(), "channel.eventLoop()");
        }

        @Override
        public ChannelHandlerContext context() {
            return context;
        }

        @Override
        public Http2FrameWriter writer() {
            return writer;
        }

        @Override
        public void teardown() throws Exception {
        }
    }
}
