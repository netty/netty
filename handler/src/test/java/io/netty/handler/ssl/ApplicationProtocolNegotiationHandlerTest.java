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
package io.netty.handler.ssl;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class ApplicationProtocolNegotiationHandlerTest {

    @Test
    public void testHandshakeFailure() {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        SSLHandshakeException exception = new SSLHandshakeException("error");
        SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(exception);
        channel.pipeline().fireUserEventTriggered(completionEvent);
        channel.pipeline().fireExceptionCaught(new DecoderException(exception));
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testHandshakeSuccess() throws NoSuchAlgorithmException {
        final AtomicBoolean configureCalled = new AtomicBoolean(false);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                configureCalled.set(true);
                assertEquals(ApplicationProtocolNames.HTTP_1_1, protocol);
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), alpnHandler);
        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        assertNull(channel.pipeline().context(alpnHandler));
        // Should produce the close_notify messages
        assertTrue(channel.finishAndReleaseAll());
        assertTrue(configureCalled.get());
    }

    @Test(expected = IllegalStateException.class)
    public void testHandshakeSuccessButNoSslHandler() {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        try {
            channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        } finally {
            assertNull(channel.pipeline().context(alpnHandler));
            assertFalse(channel.finishAndReleaseAll());
        }
    }
}
