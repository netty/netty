/*
 * Copyright 2020 The Netty Project
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
package io.netty5.handler.codec.http2;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.local.LocalAddress;
import io.netty5.channel.local.LocalChannel;
import io.netty5.channel.local.LocalHandler;
import io.netty5.channel.local.LocalServerChannel;
import io.netty5.util.concurrent.EventExecutor;
import io.netty5.util.concurrent.ImmediateEventExecutor;
import io.netty5.util.concurrent.Promise;
import io.netty5.util.internal.logging.InternalLogger;
import io.netty5.util.internal.logging.InternalLoggerFactory;
import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

import static io.netty5.handler.codec.http2.Http2FrameCodecBuilder.forClient;
import static io.netty5.handler.codec.http2.Http2FrameCodecBuilder.forServer;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Http2StreamChannelBootstrapTest {

    private static final InternalLogger logger =
            InternalLoggerFactory.getInstance(Http2StreamChannelBootstrapTest.class);

    private volatile Channel serverConnectedChannel;

    @Test
    public void testStreamIsNotCreatedIfParentConnectionIsClosedConcurrently() throws Exception {
        EventLoopGroup group = null;
        Channel serverChannel = null;
        Channel clientChannel = null;
        try {
            final CountDownLatch serverChannelLatch = new CountDownLatch(1);
            group = new MultithreadEventLoopGroup(LocalHandler.newFactory());
            LocalAddress serverAddress = new LocalAddress(getClass().getName());
            ServerBootstrap sb = new ServerBootstrap()
                    .channel(LocalServerChannel.class)
                    .group(group)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            serverConnectedChannel = ch;
                            ch.pipeline().addLast(forServer().build(), newMultiplexedHandler());
                            serverChannelLatch.countDown();
                        }
                    });
            serverChannel = sb.bind(serverAddress).get();

            Bootstrap cb = new Bootstrap()
                    .channel(LocalChannel.class)
                    .group(group)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast(forClient().build(), newMultiplexedHandler());
                        }
                    });
            clientChannel = cb.connect(serverAddress).get();
            assertTrue(serverChannelLatch.await(3, SECONDS));

            Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap(clientChannel);
            final Promise<Http2StreamChannel> promise = clientChannel.executor().newPromise();
            clientChannel.close().sync();

            bootstrap.open(promise);

            ExecutionException exception = assertThrows(ExecutionException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    promise.asFuture().get(3, SECONDS);
                }
            });
            assertThat(exception.getCause(), IsInstanceOf.instanceOf(ClosedChannelException.class));
        } finally {
            safeClose(clientChannel);
            safeClose(serverConnectedChannel);
            safeClose(serverChannel);
            if (group != null) {
                group.shutdownGracefully(0, 3, SECONDS);
            }
        }
    }

    private static Http2MultiplexHandler newMultiplexedHandler() {
        return new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
            @Override
            protected void initChannel(Http2StreamChannel ch) {
                // noop
            }
        });
    }

    private static void safeClose(Channel channel) {
        if (channel != null) {
            try {
                channel.close().syncUninterruptibly();
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }

    @Test
    public void open0FailsPromiseOnHttp2MultiplexHandlerError() {
        Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap(mock(Channel.class));

        Http2MultiplexHandler handler = new Http2MultiplexHandler(mock(ChannelHandler.class));
        EventExecutor executor = mock(EventExecutor.class);
        when(executor.inEventLoop()).thenReturn(true);
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.executor()).thenReturn(executor);
        when(ctx.handler()).thenReturn(handler);

        Promise<Http2StreamChannel> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        bootstrap.open0(ctx, promise);
        assertThat(promise.isDone(), is(true));
        assertThat(promise.cause(), is(instanceOf(IllegalStateException.class)));
    }
}
