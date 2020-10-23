/*
 * Copyright 2017 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class OptionalSslHandlerTest {

    private static final String SSL_HANDLER_NAME = "sslhandler";
    private static final String HANDLER_NAME = "handler";

    @Mock
    private ChannelHandlerContext context;

    @Mock
    private SslContext sslContext;

    @Mock
    private ChannelPipeline pipeline;

    @Before
    public void setUp() throws Exception {
        when(context.pipeline()).thenReturn(pipeline);
    }

    @Test
    public void handlerRemoved() throws Exception {
        OptionalSslHandler handler = new OptionalSslHandler(sslContext);
        final ByteBuf payload = Unpooled.copiedBuffer("plaintext".getBytes());
        try {
            handler.decode(context, payload, null);
            verify(pipeline).remove(handler);
        } finally {
            payload.release();
        }
    }

    @Test
    public void handlerReplaced() throws Exception {
        final ChannelHandler nonSslHandler = Mockito.mock(ChannelHandler.class);
        OptionalSslHandler handler = new OptionalSslHandler(sslContext) {
            @Override
            protected ChannelHandler newNonSslHandler(ChannelHandlerContext context) {
                return nonSslHandler;
            }

            @Override
            protected String newNonSslHandlerName() {
                return HANDLER_NAME;
            }
        };
        final ByteBuf payload = Unpooled.copiedBuffer("plaintext".getBytes());
        try {
            handler.decode(context, payload, null);
            verify(pipeline).replace(handler, HANDLER_NAME, nonSslHandler);
        } finally {
            payload.release();
        }
    }

    @Test
    public void sslHandlerReplaced() throws Exception {
        final SslHandler sslHandler = Mockito.mock(SslHandler.class);
        OptionalSslHandler handler = new OptionalSslHandler(sslContext) {
            @Override
            protected SslHandler newSslHandler(ChannelHandlerContext context, SslContext sslContext) {
                return sslHandler;
            }

            @Override
            protected String newSslHandlerName() {
                return SSL_HANDLER_NAME;
            }
        };
        final ByteBuf payload = Unpooled.wrappedBuffer(new byte[] { 22, 3, 1, 0, 5 });
        try {
            handler.decode(context, payload, null);
            verify(pipeline).replace(handler, SSL_HANDLER_NAME, sslHandler);
        } finally {
            payload.release();
        }
    }

    @Test
    public void decodeBuffered() throws Exception {
        OptionalSslHandler handler = new OptionalSslHandler(sslContext);
        final ByteBuf payload = Unpooled.wrappedBuffer(new byte[] { 22, 3 });
        try {
            handler.decode(context, payload, null);
            verifyZeroInteractions(pipeline);
        } finally {
            payload.release();
        }
    }
}
