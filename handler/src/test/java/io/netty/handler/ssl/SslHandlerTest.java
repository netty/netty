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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLProtocolException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class SslHandlerTest {

    @Test
    public void testTruncatedPacket() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);

        EmbeddedChannel ch = new EmbeddedChannel(new SslHandler(engine));

        // Push the first part of a 5-byte handshake message.
        ch.writeInbound(Unpooled.wrappedBuffer(new byte[] { 22, 3, 1, 0, 5 }));

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
}
