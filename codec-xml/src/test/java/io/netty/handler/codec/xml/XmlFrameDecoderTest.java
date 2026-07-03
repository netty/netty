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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    public void testDifferentialFuzzAgainstLegacyDecoder() {
        for (List<String> xmlFrames : differentialFuzzInputs()) {
            DecodeResult legacyResult = decodeWith(new LegacyXmlFrameDecoder(1048576), xmlFrames);
            DecodeResult currentResult = decodeWith(new XmlFrameDecoder(1048576), xmlFrames);
            if (!legacyResult.equals(currentResult)) {
                String xml = join(xmlFrames);
                if (!isExpectedDifference(xml, legacyResult, currentResult)) {
                    assertEquals(legacyResult, currentResult, "Unexpected differential result for " + xmlFrames);
                }
            }
        }
    }

    @Test
    public void testDecodeWithSampleXml() {
        for (final String xmlSample : xmlSamples) {
            testDecodeWithXml(xmlSample, xmlSample);
        }
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

    private static List<List<String>> differentialFuzzInputs() {
        List<String> inputs = Arrays.asList(
                "<root/>",
                "<root></root>",
                "<root><child/></root>",
                "<root><child>text</child></root>",
                "<root><child><grandchild/></child></root>",
                "<root><?xml-stylesheet href=\"x\" ?></root>",
                "<root><!-- comment --></root>",
                "<root><![CDATA[<child></child>]]></root>",
                "<root><!-- close </a --></root>",
                "<root><!-- close </a then open <b --></root>",
                "<root><?pi close </a ?></root>",
                "<root><?pi close </a then open <b ?></root>",
                "<root><![CDATA[close </a then open <b]]></root>",
                "<root><a></a><b/></root>",
                "<root><a/></root><root><b/></root>",
                "<root><a></</a></root>",
                "<root></</root>",
                "</</",
                "<a></</a>"
        );
        List<List<String>> fuzzInputs = new ArrayList<List<String>>();
        for (String input : inputs) {
            fuzzInputs.add(Collections.singletonList(input));
            for (int i = 1; i < input.length(); i++) {
                fuzzInputs.add(Arrays.asList(input.substring(0, i), input.substring(i)));
            }
        }
        return fuzzInputs;
    }

    private static boolean isExpectedDifference(String xml, DecodeResult legacyResult, DecodeResult currentResult) {
        if (containsTagLikeContentInComment(xml) || containsTagLikeContentInProcessingInstruction(xml)) {
            return currentResult.failure == null;
        }
        return xml.contains("</<") && CorruptedFrameException.class.getName().equals(currentResult.failure)
                && !currentResult.failure.equals(legacyResult.failure);
    }

    private static boolean containsTagLikeContentInComment(String xml) {
        int start = xml.indexOf("<!--");
        while (start >= 0) {
            int end = xml.indexOf("-->", start + 4);
            if (end < 0) {
                return false;
            }
            if (xml.indexOf('<', start + 4) >= 0 && xml.indexOf('<', start + 4) < end) {
                return true;
            }
            start = xml.indexOf("<!--", end + 3);
        }
        return false;
    }

    private static boolean containsTagLikeContentInProcessingInstruction(String xml) {
        int start = xml.indexOf("<?");
        while (start >= 0) {
            int end = xml.indexOf("?>", start + 2);
            if (end < 0) {
                return false;
            }
            if (xml.indexOf('<', start + 2) >= 0 && xml.indexOf('<', start + 2) < end) {
                return true;
            }
            start = xml.indexOf("<?", end + 2);
        }
        return false;
    }

    private static DecodeResult decodeWith(ByteToMessageDecoder decoder, List<String> xmlFrames) {
        EmbeddedChannel ch = new EmbeddedChannel(decoder);
        List<String> frames = new ArrayList<String>();
        String failure = null;
        try {
            for (String xmlFrame : xmlFrames) {
                ch.writeInbound(Unpooled.copiedBuffer(xmlFrame, CharsetUtil.UTF_8));
            }
        } catch (Exception e) {
            failure = e.getClass().getName();
        }
        try {
            for (;;) {
                ByteBuf buf = ch.readInbound();
                if (buf == null) {
                    break;
                }
                frames.add(buf.toString(CharsetUtil.UTF_8));
                buf.release();
            }
            return new DecodeResult(frames, failure);
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    private static String join(List<String> xmlFrames) {
        StringBuilder builder = new StringBuilder();
        for (String xmlFrame : xmlFrames) {
            builder.append(xmlFrame);
        }
        return builder.toString();
    }

    private static final class DecodeResult {
        final List<String> frames;
        final String failure;

        DecodeResult(List<String> frames, String failure) {
            this.frames = frames;
            this.failure = failure;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DecodeResult)) {
                return false;
            }
            DecodeResult that = (DecodeResult) o;
            return frames.equals(that.frames)
                    && (failure == null ? that.failure == null : failure.equals(that.failure));
        }

        @Override
        public int hashCode() {
            return 31 * frames.hashCode() + (failure == null ? 0 : failure.hashCode());
        }

        @Override
        public String toString() {
            return "DecodeResult{frames=" + frames + ", failure=" + failure + '}';
        }
    }

    private static final class LegacyXmlFrameDecoder extends ByteToMessageDecoder {

        private final int maxFrameLength;

        LegacyXmlFrameDecoder(int maxFrameLength) {
            if (maxFrameLength <= 0) {
                throw new IllegalArgumentException("maxFrameLength: " + maxFrameLength + " (expected: > 0)");
            }
            this.maxFrameLength = maxFrameLength;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            boolean openingBracketFound = false;
            boolean atLeastOneXmlElementFound = false;
            boolean inCDATASection = false;
            long openBracketsCount = 0;
            int length = 0;
            int leadingWhiteSpaceCount = 0;
            final int bufferLength = in.writerIndex();

            if (bufferLength > maxFrameLength) {
                in.skipBytes(in.readableBytes());
                fail(bufferLength);
                return;
            }

            for (int i = in.readerIndex(); i < bufferLength; i++) {
                final byte readByte = in.getByte(i);
                if (!openingBracketFound && Character.isWhitespace(readByte)) {
                    leadingWhiteSpaceCount++;
                } else if (!openingBracketFound && readByte != '<') {
                    fail(ctx);
                    in.skipBytes(in.readableBytes());
                    return;
                } else if (!inCDATASection && readByte == '<') {
                    openingBracketFound = true;

                    if (i < bufferLength - 1) {
                        final byte peekAheadByte = in.getByte(i + 1);
                        if (peekAheadByte == '/') {
                            int peekFurtherAheadIndex = i + 2;
                            while (peekFurtherAheadIndex <= bufferLength - 1) {
                                if (in.getByte(peekFurtherAheadIndex) == '>') {
                                    openBracketsCount--;
                                    break;
                                }
                                peekFurtherAheadIndex++;
                            }
                        } else if (isValidStartCharForXmlElement(peekAheadByte)) {
                            atLeastOneXmlElementFound = true;
                            openBracketsCount++;
                        } else if (peekAheadByte == '!') {
                            if (isCommentBlockStart(in, i)) {
                                openBracketsCount++;
                            } else if (isCDATABlockStart(in, i)) {
                                openBracketsCount++;
                                inCDATASection = true;
                            }
                        } else if (peekAheadByte == '?') {
                            openBracketsCount++;
                        }
                    }
                } else if (!inCDATASection && readByte == '/') {
                    if (i < bufferLength - 1 && in.getByte(i + 1) == '>') {
                        openBracketsCount--;
                    }
                } else if (readByte == '>') {
                    length = i + 1;

                    if (i - 1 > -1) {
                        final byte peekBehindByte = in.getByte(i - 1);

                        if (!inCDATASection) {
                            if (peekBehindByte == '?') {
                                openBracketsCount--;
                            } else if (peekBehindByte == '-' && i - 2 > -1 && in.getByte(i - 2) == '-') {
                                openBracketsCount--;
                            }
                        } else if (peekBehindByte == ']' && i - 2 > -1 && in.getByte(i - 2) == ']') {
                            openBracketsCount--;
                            inCDATASection = false;
                        }
                    }

                    if (atLeastOneXmlElementFound && openBracketsCount == 0) {
                        break;
                    }
                }
            }

            final int readerIndex = in.readerIndex();
            int xmlElementLength = length - readerIndex;

            if (openBracketsCount == 0 && xmlElementLength > 0) {
                if (readerIndex + xmlElementLength >= bufferLength) {
                    xmlElementLength = in.readableBytes();
                }
                final ByteBuf frame = extractFrame(in, readerIndex + leadingWhiteSpaceCount,
                        xmlElementLength - leadingWhiteSpaceCount);
                in.skipBytes(xmlElementLength);
                out.add(frame);
            }
        }

        private void fail(long frameLength) {
            if (frameLength > 0) {
                throw new TooLongFrameException(
                        "frame length exceeds " + maxFrameLength + ": " + frameLength + " - discarded");
            } else {
                throw new TooLongFrameException(
                        "frame length exceeds " + maxFrameLength + " - discarding");
            }
        }

        private static void fail(ChannelHandlerContext ctx) {
            ctx.fireExceptionCaught(new CorruptedFrameException("frame contains content before the xml starts"));
        }

        private static ByteBuf extractFrame(ByteBuf buffer, int index, int length) {
            return buffer.copy(index, length);
        }

        private static boolean isValidStartCharForXmlElement(final byte b) {
            return b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b == ':' || b == '_';
        }

        private static boolean isCommentBlockStart(final ByteBuf in, final int i) {
            return i < in.writerIndex() - 3
                    && in.getByte(i + 2) == '-'
                    && in.getByte(i + 3) == '-';
        }

        private static boolean isCDATABlockStart(final ByteBuf in, final int i) {
            return i < in.writerIndex() - 8
                    && in.getByte(i + 2) == '['
                    && in.getByte(i + 3) == 'C'
                    && in.getByte(i + 4) == 'D'
                    && in.getByte(i + 5) == 'A'
                    && in.getByte(i + 6) == 'T'
                    && in.getByte(i + 7) == 'A'
                    && in.getByte(i + 8) == '[';
        }
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
