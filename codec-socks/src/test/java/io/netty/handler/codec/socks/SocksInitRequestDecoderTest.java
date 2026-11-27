/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.codec.socks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SocksInitRequestDecoderTest {

    /**
     * NMETHODS values in [0x80, 0xFF] sign-extend to a negative Java {@code byte},
     * so a decoder that reads them with {@link ByteBuf#readByte()} instead of
     * {@link ByteBuf#readUnsignedByte()} would wrongly treat the greeting as if
     * it offered zero authentication methods, without consuming the method bytes
     * that follow on the wire. Those unconsumed bytes would then leak to whatever
     * handler follows once the decoder removes itself from the pipeline.
     */
    @Test
    public void testHighNMethodsIsReadAsUnsigned() {
        int authSchemeNum = 0x80; // 128, negative if interpreted as a signed byte
        EmbeddedChannel e = new EmbeddedChannel(new SocksInitRequestDecoder());

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(SocksProtocolVersion.SOCKS5.byteValue());
        buf.writeByte(authSchemeNum);
        for (int i = 0; i < authSchemeNum; i++) {
            buf.writeByte(SocksAuthScheme.NO_AUTH.byteValue());
        }

        assertTrue(e.writeInbound(buf));

        Object o = e.readInbound();
        SocksInitRequest req = assertInstanceOf(SocksInitRequest.class, o);
        List<SocksAuthScheme> schemes = req.authSchemes();
        assertEquals(authSchemeNum, schemes.size());
        for (SocksAuthScheme scheme : schemes) {
            assertEquals(SocksAuthScheme.NO_AUTH, scheme);
        }

        // No leftover bytes must have been forwarded to the next handler after self-removal.
        assertNull(e.readInbound());
        assertFalse(e.finish());
    }

    @Test
    public void testMaxNMethodsIsReadAsUnsigned() {
        int authSchemeNum = 0xFF; // 255
        EmbeddedChannel e = new EmbeddedChannel(new SocksInitRequestDecoder());

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(SocksProtocolVersion.SOCKS5.byteValue());
        buf.writeByte(authSchemeNum);
        for (int i = 0; i < authSchemeNum; i++) {
            buf.writeByte(SocksAuthScheme.NO_AUTH.byteValue());
        }

        assertTrue(e.writeInbound(buf));

        Object o = e.readInbound();
        SocksInitRequest req = assertInstanceOf(SocksInitRequest.class, o);
        assertEquals(authSchemeNum, req.authSchemes().size());

        assertNull(e.readInbound());
        assertFalse(e.finish());
    }

    /**
     * RFC 1928 section 3 requires NMETHODS to be at least 1; a wire value of 0 must be rejected
     * rather than accepted as a zero-auth-scheme success.
     */
    @Test
    public void testZeroNMethodsIsRejected() {
        EmbeddedChannel e = new EmbeddedChannel(new SocksInitRequestDecoder());

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(SocksProtocolVersion.SOCKS5.byteValue());
        buf.writeByte(0x00);

        assertTrue(e.writeInbound(buf));

        Object o = e.readInbound();
        assertInstanceOf(UnknownSocksRequest.class, o);

        assertNull(e.readInbound());
        assertFalse(e.finish());
    }

    @Test
    public void testNormalNMethodsStillWorks() {
        EmbeddedChannel e = new EmbeddedChannel(new SocksInitRequestDecoder());

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(SocksProtocolVersion.SOCKS5.byteValue());
        buf.writeByte(0x01);
        buf.writeByte(SocksAuthScheme.NO_AUTH.byteValue());

        assertTrue(e.writeInbound(buf));

        Object o = e.readInbound();
        SocksInitRequest req = assertInstanceOf(SocksInitRequest.class, o);
        assertEquals(1, req.authSchemes().size());
        assertEquals(SocksAuthScheme.NO_AUTH, req.authSchemes().get(0));

        assertNull(e.readInbound());
        assertFalse(e.finish());
    }
}
