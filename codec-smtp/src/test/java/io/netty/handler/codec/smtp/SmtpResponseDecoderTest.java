/*
 * Copyright 2016 The Netty Project
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
package io.netty.handler.codec.smtp;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SmtpResponseDecoderTest {

    @Test
    public void testDecodeOneLineResponse() {
        EmbeddedChannel channel = newChannel();
        assertTrue(channel.writeInbound(newBuffer("200 Ok\r\n")));
        assertTrue(channel.finish());

        SmtpResponse response = channel.readInbound();
        assertEquals(200, response.code());
        List<CharSequence> sequences = response.details();
        assertEquals(1, sequences.size());

        assertEquals("Ok", sequences.get(0).toString());
        assertNull(channel.readInbound());
    }

    @Test
    public void testDecodeOneLineResponseNoDetails() {
        EmbeddedChannel channel = newChannel();
        assertTrue(channel.writeInbound(newBuffer("250 \r\n")));
        assertTrue(channel.finish());

        SmtpResponse response = channel.readInbound();
        assertEquals(250, response.code());
        List<CharSequence> sequences = response.details();
        assertEquals(0, sequences.size());
    }

    @Test
    public void testDecodeOneLineResponseChunked() {
        EmbeddedChannel channel = newChannel();
        assertFalse(channel.writeInbound(newBuffer("200 Ok")));
        assertTrue(channel.writeInbound(newBuffer("\r\n")));
        assertTrue(channel.finish());

        SmtpResponse response = channel.readInbound();
        assertEquals(200, response.code());
        List<CharSequence> sequences = response.details();
        assertEquals(1, sequences.size());

        assertEquals("Ok", sequences.get(0).toString());
        assertNull(channel.readInbound());
    }

    @Test
    public void testDecodeTwoLineResponse() {
        EmbeddedChannel channel = newChannel();
        assertTrue(channel.writeInbound(newBuffer("200-Hello\r\n200 Ok\r\n")));
        assertTrue(channel.finish());

        SmtpResponse response = channel.readInbound();
        assertEquals(200, response.code());
        List<CharSequence> sequences = response.details();
        assertEquals(2, sequences.size());

        assertEquals("Hello", sequences.get(0).toString());
        assertEquals("Ok", sequences.get(1).toString());
        assertNull(channel.readInbound());
    }

    @Test
    public void testDecodeTwoLineResponseChunked() {
        EmbeddedChannel channel = newChannel();
        assertFalse(channel.writeInbound(newBuffer("200-")));
        assertFalse(channel.writeInbound(newBuffer("Hello\r\n2")));
        assertFalse(channel.writeInbound(newBuffer("00 Ok")));
        assertTrue(channel.writeInbound(newBuffer("\r\n")));
        assertTrue(channel.finish());

        SmtpResponse response = channel.readInbound();
        assertEquals(200, response.code());
        List<CharSequence> sequences = response.details();
        assertEquals(2, sequences.size());

        assertEquals("Hello", sequences.get(0).toString());
        assertEquals("Ok", sequences.get(1).toString());
        assertNull(channel.readInbound());
    }

    @Test(expected = DecoderException.class)
    public void testDecodeInvalidSeparator() {
        EmbeddedChannel channel = newChannel();
        assertTrue(channel.writeInbound(newBuffer("200:Ok\r\n")));
    }

    @Test(expected = DecoderException.class)
    public void testDecodeInvalidCode() {
        EmbeddedChannel channel = newChannel();
        assertTrue(channel.writeInbound(newBuffer("xyz Ok\r\n")));
    }

    @Test(expected = DecoderException.class)
    public void testDecodeInvalidLine() {
        EmbeddedChannel channel = newChannel();
        assertTrue(channel.writeInbound(newBuffer("Ok\r\n")));
    }

    private static EmbeddedChannel newChannel() {
        return new EmbeddedChannel(new SmtpResponseDecoder(Integer.MAX_VALUE));
    }

    private static ByteBuf newBuffer(CharSequence seq) {
        return Unpooled.copiedBuffer(seq, CharsetUtil.US_ASCII);
    }
}
