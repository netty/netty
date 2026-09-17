/*
 * Copyright 2013 The Netty Project
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

package io.netty.handler.codec.xml;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class XmlFrameDecoderTest {

    private final List<String> xmlSamples;

    public XmlFrameDecoderTest() throws IOException, URISyntaxException {
        xmlSamples = Arrays.asList(
                sample("01"), sample("02"), sample("03"),
                sample("04"), sample("05"), sample("06")
        );
    }

    @Test
    public void testConstructorWithIllegalArgs01() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new XmlFrameDecoder(0);
            }
        });
    }

    @Test
    public void testConstructorWithIllegalArgs02() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() {
                new XmlFrameDecoder(-23);
            }
        });
    }

    @Test
    public void testDecodeWithFrameExceedingMaxLength() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(3);
        final EmbeddedChannel ch = new EmbeddedChannel(decoder);
        assertThrows(TooLongFrameException.class, new Executable() {
            @Override
            public void execute() {
                ch.writeInbound(Unpooled.copiedBuffer("<v/>", CharsetUtil.UTF_8));
            }
        });
    }

    @Test
    public void testDecodeWithInvalidInput() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(1048576);
        final EmbeddedChannel ch = new EmbeddedChannel(decoder);
        assertThrows(CorruptedFrameException.class, new Executable() {
            @Override
            public void execute() {
                ch.writeInbound(Unpooled.copiedBuffer("invalid XML", CharsetUtil.UTF_8));
            }
        });
    }

    @Test
    public void testDecodeWithInvalidContentBeforeXml() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(1048576);
        final EmbeddedChannel ch = new EmbeddedChannel(decoder);
        assertThrows(CorruptedFrameException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                ch.writeInbound(Unpooled.copiedBuffer("invalid XML<foo/>", CharsetUtil.UTF_8));
            }
        });
    }

    @Test
    public void testDecodeShortValidXml() {
        testDecodeWithXml("<xxx/>", "<xxx/>");
    }

    @Test
    public void testDecodeShortValidXmlWithLeadingWhitespace01() {
        testDecodeWithXml("   <xxx/>", "<xxx/>");
    }

    @Test
    public void testDecodeShortValidXmlWithLeadingWhitespace02() {
        testDecodeWithXml("  \n\r \t<xxx/>\t", "<xxx/>");
    }

    @Test
    public void testDecodeShortValidXmlWithLeadingWhitespace02AndTrailingGarbage() {
        testDecodeWithXml("  \n\r \t<xxx/>\ttrash", "<xxx/>", CorruptedFrameException.class);
    }

    @Test
    public void testDecodeInvalidXml() {
        testDecodeWithXml("<a></", new Object[0]);
        testDecodeWithXml("<a></a", new Object[0]);
    }

    @Test
    public void testDecodeInvalidNestedClosingTag() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(1048576);
        final EmbeddedChannel ch = new EmbeddedChannel(decoder);
        assertThrows(CorruptedFrameException.class, new Executable() {
            @Override
            public void execute() {
                ch.writeInbound(Unpooled.copiedBuffer("<a></</a>", CharsetUtil.UTF_8));
            }
        });
        ch.finishAndReleaseAll();
    }

    @Test
    public void testDecodeInvalidRepeatedClosingTags() {
        XmlFrameDecoder decoder = new XmlFrameDecoder(1048576);
        final EmbeddedChannel ch = new EmbeddedChannel(decoder);
        ch.writeInbound(Unpooled.copiedBuffer("</", CharsetUtil.UTF_8));
        assertThrows(CorruptedFrameException.class, new Executable() {
            @Override
            public void execute() {
                ch.writeInbound(Unpooled.copiedBuffer("</", CharsetUtil.UTF_8));
            }
        });
        ch.finishAndReleaseAll();
    }

    @Test
    public void testDecodeWithCDATABlock() {
        final String xml = "<book>" +
                "<![CDATA[K&R, a.k.a. Kernighan & Ritchie]]>" +
                "</book>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithCDATABlockContainingNestedUnbalancedXml() {
        // <br> isn't closed, also <a> should have been </a>
        final String xml = "<info>" +
                "<![CDATA[Copyright 2012-2013,<br><a href=\"http://www.acme.com\">ACME Inc.<a>]]>" +
                "</info>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithCDATABlockContainingClosingTagThenOpeningBracket() {
        final String xml = "<root>" +
                "<![CDATA[close </a then open <b]]>" +
                "</root>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithCommentContainingClosingTagThenOpeningBracket() {
        final String xml = "<root>" +
                "<!-- close </a then open <b -->" +
                "</root>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithCommentContainingClosingTag() {
        final String xml = "<root>" +
                "<!-- close </a -->" +
                "</root>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithProcessingInstructionContainingClosingTagThenOpeningBracket() {
        final String xml = "<root>" +
                "<?pi close </a then open <b ?>" +
                "</root>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithProcessingInstructionContainingClosingTag() {
        final String xml = "<root>" +
                "<?pi close </a ?>" +
                "</root>";
        testDecodeWithXml(xml, xml);
    }

    @Test
    public void testDecodeWithMultipleMessages() {
        final String input = "<root xmlns=\"http://www.acme.com/acme\" status=\"loginok\" " +
                "timestamp=\"1362410583776\"/>\n\n" +
                "<root xmlns=\"http://www.acme.com/acme\" status=\"start\" time=\"0\" " +
                "timestamp=\"1362410584794\">\n<child active=\"1\" status=\"started\" id=\"935449\" " +
                "msgnr=\"2\"/>\n</root>" +
                "<root xmlns=\"http://www.acme.com/acme\" status=\"logout\" timestamp=\"1362410584795\"/>";
        final String frame1 = "<root xmlns=\"http://www.acme.com/acme\" status=\"loginok\" " +
                "timestamp=\"1362410583776\"/>";
        final String frame2 = "<root xmlns=\"http://www.acme.com/acme\" status=\"start\" time=\"0\" " +
                "timestamp=\"1362410584794\">\n<child active=\"1\" status=\"started\" id=\"935449\" " +
                "msgnr=\"2\"/>\n</root>";
        final String frame3 = "<root xmlns=\"http://www.acme.com/acme\" status=\"logout\" " +
                "timestamp=\"1362410584795\"/>";
        testDecodeWithXml(input, frame1, frame2, frame3);
    }

    @Test
    public void testFraming() {
        testDecodeWithXml(Arrays.asList("<abc", ">123</a", "bc>"), "<abc>123</abc>");
    }

    @Test
    public void testFramingWithSplitClosingTag() {
        testDecodeWithXml(Arrays.asList("<abc>", "123</", "abc>"), "<abc>123</abc>");
    }

    @Test
    public void testFramingWithCommentContainingClosingTagThenOpeningBracket() {
        final String frame = "<root><!-- close </a then open <b --></root>";
        testDecodeWithXml(Arrays.asList("<root><!-- close </", "a then open <b --></root>"), frame);
    }

    @Test
    public void testFramingWithProcessingInstructionContainingClosingTagThenOpeningBracket() {
        final String frame = "<root><?pi close </a then open <b ?></root>";
        testDecodeWithXml(Arrays.asList("<root><?pi close </", "a then open <b ?></root>"), frame);
    }

    @Test
    public void testDecodeWithSampleXml() {
        for (final String xmlSample : xmlSamples) {
            testDecodeWithXml(xmlSample, xmlSample);
        }
    }

    @Test
    public void testFramingWithCommentStartSplitAcrossChunks() {
        final String frame = "<root><!-- comment --></root>";
        // split right in the middle of the "<!--" start marker, so the decoder cannot
        // tell yet (with only "<!" available) whether this is a comment or CDATA block.
        testDecodeWithXml(Arrays.asList("<root><!", "-- comment --></root>"), frame);
    }

    @Test
    public void testFramingWithCDATAStartSplitAcrossChunks() {
        final String frame = "<root><![CDATA[hello]]></root>";
        // split in the middle of the "<![CDATA[" start marker.
        testDecodeWithXml(Arrays.asList("<root><![CDA", "TA[hello]]></root>"), frame);
    }

    @Test
    public void testFramingWithOpeningBracketAsLastByteOfChunk() {
        // '<' arrives with nothing after it yet, so the decoder cannot peek ahead to
        // classify it until the next chunk arrives.
        testDecodeWithXml(Arrays.asList("<", "abc/>"), "<abc/>");
    }

    @Test
    public void testFramingWithSelfClosingSlashAsLastByteOfChunk() {
        // '/' arrives with nothing after it yet, so the decoder cannot peek ahead to see
        // whether it is immediately followed by '>' until the next chunk arrives.
        testDecodeWithXml(Arrays.asList("<abc", "/", ">"), "<abc/>");
    }

    @Test
    public void testDecodeDoesNotHangOnTrickledUnbalancedElement() {
        // Regression test: a peer that opens an element ("<a>") and then trickles
        // non-markup content one byte at a time, without ever closing the element, used
        // to force XmlFrameDecoder to rescan the whole accumulated buffer from the start
        // on every single decode() call, making the total work quadratic in the number
        // of bytes received. If the fix regresses, this test will time out instead of
        // failing an assertion.
        final int contentBytes = 200000;
        final XmlFrameDecoder decoder = new XmlFrameDecoder(contentBytes + 1024);
        final EmbeddedChannel ch = new EmbeddedChannel(decoder);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), new Executable() {
                @Override
                public void execute() {
                    ch.writeInbound(Unpooled.copiedBuffer("<a>", CharsetUtil.UTF_8));
                    for (int i = 0; i < contentBytes; i++) {
                        ch.writeInbound(Unpooled.copiedBuffer("x", CharsetUtil.UTF_8));
                    }
                }
            });
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    @Test
    public void lookAheadBufferForUnknownMarkupDeclarationsMustNotIgnoreFollowingTags() {
        testDecodeWithXml("<a><!x></a>", "<a><!x></a>");
        testDecodeWithXml("<aa><!x></aa>", "<aa><!x></aa>");
    }

    @Test
    public void mustDecodeRemainingDataAfterCumulationCompaction() {
        // Regression test for the retained *absolute* 'length' field. The chunk sizes are
        // chosen so that ByteToMessageDecoder's cumulation buffer is re-based in between:
        //  - chunk 1 (59 bytes) becomes the cumulation buffer itself, capacity == 59;
        //  - chunk 2 (5 bytes) is appended, growing the capacity to exactly 64. The
        //    60-byte element is emitted and the stray "</b>" is left unread, which drives
        //    openBracketsCount to -1 while 'length' keeps the absolute value 64;
        //  - channelReadComplete() then calls discardSomeReadBytes(): readerIndex (60) is
        //    >= capacity/2 (32), so the 4 unread bytes move down to offset 0 and
        //    readerIndex becomes 0 -- 'length' is now stale by 60 bytes;
        //  - chunk 3 ("<c") brings openBracketsCount back to 0 via the '<' + start-char
        //    increment, without any '>' being scanned, so the stale 'length' decides the
        //    frame boundary: "</b><c" is emitted instead of "</b>", swallowing the start
        //    of the next element, and chunk 4 ("/>") then looks like leading garbage.
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 53; i++) {
            content.append('x');
        }
        final String frame = "<a>" + content + "</a>";
        assertEquals(60, frame.length());

        testDecodeWithXml(Arrays.asList(
                frame.substring(0, 59),       // the element minus its final '>'
                frame.substring(59) + "</b>", // completes it, plus a stray closing tag
                "<c",
                "/>"),
            frame, "</b>", "<c/>");
    }

    private static void testDecodeWithXml(List<String> xmlFrames, Object... expected) {
        EmbeddedChannel ch = new EmbeddedChannel(new XmlFrameDecoder(1048576));
        Exception cause = null;
        try {
            for (String xmlFrame : xmlFrames) {
                ch.writeInbound(Unpooled.copiedBuffer(xmlFrame, CharsetUtil.UTF_8));
            }
        } catch (Exception e) {
            cause = e;
        }
        List<Object> actual = new ArrayList<Object>();
        for (;;) {
            ByteBuf buf = ch.readInbound();
            if (buf == null) {
                break;
            }
            actual.add(buf.toString(CharsetUtil.UTF_8));
            buf.release();
        }

        if (cause != null) {
            actual.add(cause.getClass());
        }

        try {
            List<Object> expectedList = new ArrayList<Object>();
            Collections.addAll(expectedList, expected);
            assertEquals(expectedList, actual);
        } finally {
            ch.finish();
        }
    }

    private static void testDecodeWithXml(String xml, Object... expected) {
        testDecodeWithXml(Collections.singletonList(xml), expected);
    }

    private String sample(String number) throws IOException, URISyntaxException {
        String path = "io/netty/handler/codec/xml/sample-" + number + ".xml";
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("file not found: " + path);
        }
        byte[] buf = Files.readAllBytes(Paths.get(url.toURI()));
        return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(buf)).toString();
    }
}
