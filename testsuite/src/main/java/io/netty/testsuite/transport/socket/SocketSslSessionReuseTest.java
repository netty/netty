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
package io.netty.testsuite.transport.socket;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.JdkSslClientContext;
import io.netty.handler.ssl.JdkSslServerContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSessionContext;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketSslSessionReuseTest extends AbstractSocketTest {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SocketSslSessionReuseTest.class);

    private static final File CERT_FILE;
    private static final File KEY_FILE;

    static {
        SelfSignedCertificate ssc;
        try {
            ssc = new SelfSignedCertificate();
        } catch (CertificateException e) {
            throw new Error(e);
        }
        CERT_FILE = ssc.certificate();
        KEY_FILE = ssc.privateKey();
    }

    public static Collection<Object[]> data() throws Exception {
        return Collections.singletonList(new Object[] {
            new JdkSslServerContext(CERT_FILE, KEY_FILE),
            new JdkSslClientContext(CERT_FILE)
        });
    }

    @ParameterizedTest(name = "{index}: serverEngine = {0}, clientEngine = {1}")
    @MethodSource("data")
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSslSessionReuse(final SslContext serverCtx, final SslContext clientCtx, TestInfo testInfo)
            throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSslSessionReuse(sb, cb, serverCtx, clientCtx);
            }
        });
    }

    public void testSslSessionReuse(ServerBootstrap sb, Bootstrap cb,
                                    final SslContext serverCtx, final SslContext clientCtx) throws Throwable {
        final ReadAndDiscardHandler sh = new ReadAndDiscardHandler(true, true);
        final ReadAndDiscardHandler ch = new ReadAndDiscardHandler(false, true);
        final String[] protocols = { "TLSv1", "TLSv1.1", "TLSv1.2" };

        sb.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                SSLEngine engine = serverCtx.newEngine(sch.alloc());
                engine.setUseClientMode(false);
                engine.setEnabledProtocols(protocols);

                sch.pipeline().addLast(new SslHandler(engine));
                sch.pipeline().addLast(sh);
            }
        });
        final Channel sc = sb.bind().sync().channel();

        cb.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel sch) throws Exception {
                InetSocketAddress serverAddr = (InetSocketAddress) sc.localAddress();
                SSLEngine engine = clientCtx.newEngine(sch.alloc(), serverAddr.getHostString(), serverAddr.getPort());
                engine.setUseClientMode(true);
                engine.setEnabledProtocols(protocols);

                sch.pipeline().addLast(new SslHandler(engine));
                sch.pipeline().addLast(ch);
            }
        });

        try {
            SSLSessionContext clientSessionCtx = clientCtx.sessionContext();
            ByteBuf msg = Unpooled.wrappedBuffer(new byte[] { 0xa, 0xb, 0xc, 0xd }, 0, 4);
            Channel cc = cb.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(msg).sync();
            cc.closeFuture().sync();
            rethrowHandlerExceptions(sh, ch);
            Set<String> sessions = sessionIdSet(clientSessionCtx.getIds());

            msg = Unpooled.wrappedBuffer(new byte[] { 0xa, 0xb, 0xc, 0xd }, 0, 4);
            cc = cb.connect(sc.localAddress()).sync().channel();
            cc.writeAndFlush(msg).sync();
            cc.closeFuture().sync();
            assertEquals(sessions, sessionIdSet(clientSessionCtx.getIds()), "Expected no new sessions");
            rethrowHandlerExceptions(sh, ch);
        } finally {
            sc.close().awaitUninterruptibly();
        }
    }

    private static void rethrowHandlerExceptions(ReadAndDiscardHandler sh, ReadAndDiscardHandler ch) throws Throwable {
        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    private static Set<String> sessionIdSet(Enumeration<byte[]> sessionIds) {
        Set<String> idSet = new HashSet<String>();
        byte[] id;
        while (sessionIds.hasMoreElements()) {
            id = sessionIds.nextElement();
            idSet.add(ByteBufUtil.hexDump(Unpooled.wrappedBuffer(id)));
        }
        return idSet;
    }

    @Sharable
    private static class ReadAndDiscardHandler extends SimpleChannelInboundHandler<ByteBuf> {
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        private final boolean server;
        private final boolean autoRead;

        ReadAndDiscardHandler(boolean server, boolean autoRead) {
            this.server = server;
            this.autoRead = autoRead;
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);
            ctx.close();
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            try {
                ctx.flush();
            } finally {
                if (!autoRead) {
                    ctx.read();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                Throwable cause) throws Exception {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Unexpected exception from the " +
                        (server? "server" : "client") + " side", cause);
            }

            exception.compareAndSet(null, cause);
            ctx.close();
        }
    }
}
