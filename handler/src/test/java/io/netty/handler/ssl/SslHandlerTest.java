/*
 * Copyright 2013 The Netty Project
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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLProtocolException;

import org.junit.Test;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;

public class SslHandlerTest {

    @Test
    public void testTruncatedPacket() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        // Push the first part of a 5-byte handshake message.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{22, 3, 1, 0, 5}));

        // Should decode nothing yet.
        assertThat(ch.readInbound(), is(nullValue()));

        try {
            // Push the second part of the 5-byte handshake message.
            ch.writeInbound(Unpooled.wrappedBuffer(new byte[]{2, 0, 0, 1, 0}));
            fail();
        } catch (DecoderException e) {
            // The pushed message is invalid, so it should raise an exception if it decoded the message correctly.
            assertThat(e.getCause(), is(instanceOf(SSLProtocolException.class)));
        }
    }

    @Test(expected = UnsupportedMessageTypeException.class)
    public void testNonByteBufNotPassThrough() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        ch.writeOutbound(new Object());
    }

    @Test
    public void testReleaseSslEngine() throws Exception {
        assumeTrue(OpenSsl.isAvailable());

        SelfSignedCertificate cert = new SelfSignedCertificate();
        try {
            SslContext sslContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(SslProvider.OPENSSL)
                .build();
            try {
                SSLEngine sslEngine = sslContext.newEngine(ByteBufAllocator.DEFAULT);
                EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(sslEngine));

                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(1, ((ReferenceCounted) sslEngine).refCnt());

                ch.close().syncUninterruptibly();

                assertEquals(1, ((ReferenceCounted) sslContext).refCnt());
                assertEquals(0, ((ReferenceCounted) sslEngine).refCnt());
            } finally {
                ReferenceCountUtil.release(sslContext);
            }
        } finally {
            cert.delete();
        }
    }

    private static final class TlsReadTest extends ChannelOutboundHandlerAdapter {
        private volatile boolean readIssued;

        @Override
        public void read(ChannelHandlerContext ctx) throws Exception {
            readIssued = true;
            super.read(ctx);
        }

        public void test(final boolean dropChannelActive) throws Exception {
          SSLEngine engine = SSLContext.getDefault().createSSLEngine();
          engine.setUseClientMode(true);

          EmbeddedChannel ch = new EmbeddedChannel(
              this,
              new SslHandler(engine),
              new ChannelInboundHandlerAdapter() {
                @Override
                public void channelActive(ChannelHandlerContext ctx) throws Exception {
                  if (!dropChannelActive) {
                    ctx.fireChannelActive();
                  }
                }
              }
          );
          ch.config().setAutoRead(false);
          assertFalse(ch.config().isAutoRead());

          assertTrue(ch.writeOutbound(Unpooled.EMPTY_BUFFER));
          assertTrue(readIssued);
          assertTrue(ch.finishAndReleaseAll());
       }
    }

    @Test
    public void testIssueReadAfterActiveWriteFlush() throws Exception {
        // the handshake is initiated by channelActive
        new TlsReadTest().test(false);
    }

    @Test
    public void testIssueReadAfterWriteFlushActive() throws Exception {
        // the handshake is initiated by flush
        new TlsReadTest().test(true);
    }
}
