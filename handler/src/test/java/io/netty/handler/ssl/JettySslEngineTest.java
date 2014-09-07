/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class JettySslEngineTest {
    private static final String APPLICATION_LEVEL_PROTOCOL = "my-protocol";
    private static final int MESSAGE_AWAIT_SECONDS = 5;
    private static EventLoopGroup[] groups;

    @Mock
    private MessageReciever serverReceiver;
    @Mock
    private MessageReciever clientReceiver;

    private SslContext serverSslCtx;
    private SslContext clientSslCtx;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel serverConnectedChannel;
    private Channel clientChannel;
    private CountDownLatch serverLatch;
    private CountDownLatch clientLatch;

    private interface MessageReciever {
        void messageReceived(ByteBuf msg);
    }

    private final class MessageDelegatorChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final MessageReciever receiver;
        private final CountDownLatch latch;

        public MessageDelegatorChannelHandler(MessageReciever receiver, CountDownLatch latch) {
            super(false);
            this.receiver = receiver;
            this.latch = latch;
        }

        @Override
        protected void messageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            receiver.messageReceived(msg);
            latch.countDown();
        }
    }

    @BeforeClass
    public static void newGroups() {
        groups = new EventLoopGroup[3];
        for (int i = 0; i < groups.length; ++i) {
            groups[i] = new NioEventLoopGroup();
        }
    }

    @AfterClass
    public static void teardownGroups() throws Exception {
        for (int i = 0; i < groups.length; ++i) {
            groups[i].shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
    }

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        serverLatch = new CountDownLatch(1);
        clientLatch = new CountDownLatch(1);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.close().sync();
            // EventLoopGroups are shutdown in @AfterClass
        }
        clientChannel = null;
        serverChannel = null;
        serverConnectedChannel = null;
    }

    @Test
    public void testNpn() throws Exception {
        SslEngineWrapperFactory wrapper = null;
        try {
            wrapper = JettyNpnSslEngineWrapper.instance();
        } catch (SSLException e) {
            // NPN availability is dependent on the java version.  If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
        mySetup(wrapper);
        runTest();
    }

    @Test
    public void testAlpn() throws Exception {
        SslEngineWrapperFactory wrapper = null;
        try {
            wrapper = JettyAlpnSslEngineWrapper.instance();
        } catch (SSLException e) {
            // ALPN availability is dependent on the java version.  If NPN is not available because of
            // java version incompatibility don't fail the test, but instead just skip the test
            assumeNoException(e);
        }
        mySetup(wrapper);
        runTest();
    }

    private void mySetup(SslEngineWrapperFactory wrapper) throws InterruptedException, SSLException,
            CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContext.newServerContext(SslProvider.JDK, ssc.certificate(), ssc.privateKey(), null, null,
                Arrays.asList(APPLICATION_LEVEL_PROTOCOL), wrapper, 0, 0);
        clientSslCtx = SslContext.newClientContext(SslProvider.JDK, null, InsecureTrustManagerFactory.INSTANCE, null,
                Arrays.asList(APPLICATION_LEVEL_PROTOCOL), wrapper, 0, 0);

        serverConnectedChannel = null;
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(groups[0], groups[1]);
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(serverSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(serverReceiver, serverLatch));
                serverConnectedChannel = ch;
            }
        });

        cb.group(groups[2]);
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(clientSslCtx.newHandler(ch.alloc()));
                p.addLast(new MessageDelegatorChannelHandler(clientReceiver, clientLatch));
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    private void runTest() throws Exception {
        ByteBuf clientMessage = Unpooled.copiedBuffer("I am a client".getBytes());
        ByteBuf serverMessage = Unpooled.copiedBuffer("I am a server".getBytes());
        try {
            writeAndVerifyReceived(clientMessage.retain(), clientChannel, serverLatch, serverReceiver);
            writeAndVerifyReceived(serverMessage.retain(), serverConnectedChannel, clientLatch, clientReceiver);
            verifyApplicationLevelProtocol(clientChannel);
            verifyApplicationLevelProtocol(serverConnectedChannel);
        } finally {
            clientMessage.release();
            serverMessage.release();
        }
    }

    private void verifyApplicationLevelProtocol(Channel channel) {
        SslHandler handler = channel.pipeline().get(SslHandler.class);
        assertNotNull(handler);
        String[] protocol = handler.engine().getSession().getProtocol().split(":");
        assertNotNull(protocol);
        assertTrue("protocol.length must be greater than 1 but is " + protocol.length, protocol.length > 1);
        assertEquals(APPLICATION_LEVEL_PROTOCOL, protocol[1]);
    }

    private static void writeAndVerifyReceived(ByteBuf message, Channel sendChannel, CountDownLatch receiverLatch,
            MessageReciever receiver) throws Exception {
        sendChannel.writeAndFlush(message);
        receiverLatch.await(MESSAGE_AWAIT_SECONDS, TimeUnit.SECONDS);
        message.resetReaderIndex();
        verify(receiver).messageReceived(eq(message));
    }
}
