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

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.xml.bind.DatatypeConverter;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class SniSslHandlerTest {

    private static SSLEngine makeSslEngine() throws Exception {
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
    }

    @Test
    public void testServerNameParsing() throws Exception {
        SniSslHandler.Builder builder = new SniSslHandler.Builder();

        SSLEngine nettyEngine = makeSslEngine();
        builder.addEngine("*.netty.io", nettyEngine);

        SSLEngine leanEngine = makeSslEngine();
        // input with custom cases
        builder.addEngine("*.LEANCLOUD.CN", leanEngine);

        // a hostname conflict with previous one, since we are using order-sensitive config, the engine won't
        // be used with the handler.
        SSLEngine leanEngin2 = makeSslEngine();
        builder.addEngine("chat4.leancloud.cn", leanEngin2);

        builder.defaultEngine(nettyEngine);

        SniSslHandler handler = builder.build();
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // hex dump of a client hello packet, which contains hostname "CHAT4。LEANCLOUD。CN"
        String tlsHandshakeMessageHex = "16030100bd010000b90303a74225676d1814ba57faff3b366" +
                "3656ed05ee9dbb2a4dbb1bb1c32d2ea5fc39e0000000100008c0000001700150000164348" +
                "415434E380824C45414E434C4F5544E38082434E000b000403000102000a00340032000e0" +
                "00d0019000b000c00180009000a0016001700080006000700140015000400050012001300" +
                "0100020003000f0010001100230000000d0020001e0601060206030501050205030401040" +
                "20403030103020303020102020203000f00010133740000";
        byte[] handshakeBytes = DatatypeConverter.parseHexBinary(tlsHandshakeMessageHex);

        try {
            // Push the handshake message.
            // Decode should fail because no cipher suites in common, but SNI detection should be finished already
            ch.writeInbound(Unpooled.wrappedBuffer(handshakeBytes));
            fail();
        } catch (DecoderException e) {
            // expected
        }

        assertThat(ch.finish(), is(false));
        assertThat(handler.hostname(), is("chat4.leancloud.cn"));
        assertThat(handler.engine(), is(leanEngine));
    }

}
