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
import io.netty.util.DomainNameMapping;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.File;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class SniHandlerTest {

    private static SslContext makeSslContext() throws Exception {
        File keyFile = new File(SniHandlerTest.class.getResource("test_encrypted.pem").getFile());
        File crtFile = new File(SniHandlerTest.class.getResource("test.crt").getFile());

        return new JdkSslServerContext(crtFile, keyFile, "12345");
    }

    @Test
    public void testServerNameParsing() throws Exception {
        SslContext nettyContext = makeSslContext();
        SslContext leanContext = makeSslContext();
        SslContext leanContext2 = makeSslContext();

        DomainNameMapping<SslContext> mapping = new DomainNameMapping<SslContext>(nettyContext);
        mapping.add("*.netty.io", nettyContext);

        // input with custom cases
        mapping.add("*.LEANCLOUD.CN", leanContext);

        // a hostname conflict with previous one, since we are using order-sensitive config, the engine won't
        // be used with the handler.
        mapping.add("chat4.leancloud.cn", leanContext2);

        SniHandler handler = new SniHandler(mapping);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // hex dump of a client hello packet, which contains hostname "CHAT4。LEANCLOUD。CN"
        String tlsHandshakeMessageHex1 = "16030100";
        // part 2
        String tlsHandshakeMessageHex = "bd010000b90303a74225676d1814ba57faff3b366" +
                "3656ed05ee9dbb2a4dbb1bb1c32d2ea5fc39e0000000100008c0000001700150000164348" +
                "415434E380824C45414E434C4F5544E38082434E000b000403000102000a00340032000e0" +
                "00d0019000b000c00180009000a0016001700080006000700140015000400050012001300" +
                "0100020003000f0010001100230000000d0020001e0601060206030501050205030401040" +
                "20403030103020303020102020203000f00010133740000";

        try {
            // Push the handshake message.
            // Decode should fail because SNI error
            ch.writeInbound(Unpooled.wrappedBuffer(DatatypeConverter.parseHexBinary(tlsHandshakeMessageHex1)));
            ch.writeInbound(Unpooled.wrappedBuffer(DatatypeConverter.parseHexBinary(tlsHandshakeMessageHex)));
            fail();
        } catch (DecoderException e) {
            // expected
        }

        assertThat(ch.finish(), is(false));
        assertThat(handler.hostname(), is("chat4.leancloud.cn"));
        assertThat(handler.sslContext(), is(leanContext));
    }

    @Test
    public void testFallbackToDefaultContext() throws Exception {
        SslContext nettyContext = makeSslContext();
        SslContext leanContext = makeSslContext();
        SslContext leanContext2 = makeSslContext();

        DomainNameMapping<SslContext> mapping = new DomainNameMapping<SslContext>(nettyContext);
        mapping.add("*.netty.io", nettyContext);

        // input with custom cases
        mapping.add("*.LEANCLOUD.CN", leanContext);

        // a hostname conflict with previous one, since we are using order-sensitive config, the engine won't
        // be used with the handler.
        mapping.add("chat4.leancloud.cn", leanContext2);

        SniHandler handler = new SniHandler(mapping);
        EmbeddedChannel ch = new EmbeddedChannel(handler);

        // invalid
        byte[] message = { 22, 3, 1, 0, 0 };

        try {
            // Push the handshake message.
            ch.writeInbound(Unpooled.wrappedBuffer(message));
        } catch (Exception e) {
            // expected
        }

        assertThat(ch.finish(), is(false));
        assertThat(handler.hostname(), nullValue());
        assertThat(handler.sslContext(), is(nettyContext));
    }
}
