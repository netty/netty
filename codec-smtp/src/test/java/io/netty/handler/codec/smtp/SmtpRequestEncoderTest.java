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
import io.netty.handler.codec.EncoderException;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SmtpRequestEncoderTest {

    @Test
    public void testEncodeEhlo() {
        testEncode(SmtpRequests.ehlo("localhost"), "EHLO localhost\r\n");
    }

    @Test
    public void testEncodeHelo() {
        testEncode(SmtpRequests.helo("localhost"), "HELO localhost\r\n");
    }

    @Test
    public void testEncodeAuth() {
        testEncode(SmtpRequests.auth("LOGIN"), "AUTH LOGIN\r\n");
    }

    @Test
    public void testEncodeAuthWithParameter() {
        testEncode(SmtpRequests.auth("PLAIN", "dGVzdAB0ZXN0ADEyMzQ="), "AUTH PLAIN dGVzdAB0ZXN0ADEyMzQ=\r\n");
    }

    @Test
    public void testEncodeEmpty() {
        testEncode(SmtpRequests.empty("dGVzdAB0ZXN0ADEyMzQ="),  "dGVzdAB0ZXN0ADEyMzQ=\r\n");
    }

    @Test
    public void testEncodeMail() {
        testEncode(SmtpRequests.mail("me@netty.io"), "MAIL FROM:<me@netty.io>\r\n");
    }

    @Test
    public void testEncodeMailNullSender() {
        testEncode(SmtpRequests.mail(null), "MAIL FROM:<>\r\n");
    }

    @Test
    public void testEncodeRcpt() {
        testEncode(SmtpRequests.rcpt("me@netty.io"), "RCPT TO:<me@netty.io>\r\n");
    }

    @Test
    public void testEncodeNoop() {
        testEncode(SmtpRequests.noop(), "NOOP\r\n");
    }

    @Test
    public void testEncodeRset() {
        testEncode(SmtpRequests.rset(), "RSET\r\n");
    }

    @Test
    public void testEncodeHelp() {
        testEncode(SmtpRequests.help(null), "HELP\r\n");
    }

    @Test
    public void testEncodeHelpWithArg() {
        testEncode(SmtpRequests.help("MAIL"), "HELP MAIL\r\n");
    }

    @Test
    public void testEncodeData() {
        testEncode(SmtpRequests.data(), "DATA\r\n");
    }

    @Test
    public void testEncodeDataAndContent() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());
        assertTrue(channel.writeOutbound(SmtpRequests.data()));
        assertTrue(channel.writeOutbound(
                new DefaultSmtpContent(Unpooled.copiedBuffer("Subject: Test\r\n\r\n", CharsetUtil.US_ASCII))));
        assertTrue(channel.writeOutbound(
                new DefaultLastSmtpContent(Unpooled.copiedBuffer("Test\r\n", CharsetUtil.US_ASCII))));
        assertTrue(channel.finish());

        assertEquals("DATA\r\nSubject: Test\r\n\r\nTest\r\n.\r\n", getWrittenString(channel));
    }

    @Test(expected = EncoderException.class)
    public void testThrowsIfContentExpected() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());
        try {
            assertTrue(channel.writeOutbound(SmtpRequests.data()));
            channel.writeOutbound(SmtpRequests.noop());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testRsetClearsContentExpectedFlag() {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());

        assertTrue(channel.writeOutbound(SmtpRequests.data()));
        assertTrue(channel.writeOutbound(SmtpRequests.rset()));
        assertTrue(channel.writeOutbound(SmtpRequests.noop()));
        assertTrue(channel.finish());

        assertEquals("DATA\r\nRSET\r\nNOOP\r\n", getWrittenString(channel));
    }

    private static String getWrittenString(EmbeddedChannel channel) {
        ByteBuf written = Unpooled.buffer();

        for (;;) {
            ByteBuf buffer = channel.readOutbound();
            if (buffer == null) {
                break;
            }
            written.writeBytes(buffer);
            buffer.release();
        }

        String writtenString = written.toString(CharsetUtil.US_ASCII);
        written.release();

        return writtenString;
    }

    private static void testEncode(SmtpRequest request, String expected) {
        EmbeddedChannel channel = new EmbeddedChannel(new SmtpRequestEncoder());
        assertTrue(channel.writeOutbound(request));
        assertTrue(channel.finish());
        ByteBuf buffer = channel.readOutbound();
        assertEquals(expected, buffer.toString(CharsetUtil.US_ASCII));
        buffer.release();
        assertNull(channel.readOutbound());
    }
}
