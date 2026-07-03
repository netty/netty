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
package io.netty.handler.codec.xml;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "JAZZER_FUZZ", matches = "1")
public class XmlFrameDecoderFuzzTest {

    @FuzzTest(maxDuration = "10m")
    public void currentDecoderMatchesLegacyDecoder(final FuzzedDataProvider data) {
        String xml = data.consumeString(4096);
        int splitIndex = data.consumeInt(0, xml.length());
        List<String> xmlFrames = splitIndex == 0 || splitIndex == xml.length() ?
                Collections.singletonList(xml) : Arrays.asList(xml.substring(0, splitIndex), xml.substring(splitIndex));

        DecodeResult legacyResult = decodeWith(new LegacyXmlFrameDecoder(1048576), xmlFrames);
        DecodeResult currentResult = decodeWith(new XmlFrameDecoder(1048576), xmlFrames);
        if (!legacyResult.equals(currentResult) && !isExpectedDifference(xml, legacyResult, currentResult)) {
            assertEquals(legacyResult, currentResult, "Unexpected differential result for " + xmlFrames);
        }
    }

    private static boolean isExpectedDifference(String xml, DecodeResult legacyResult, DecodeResult currentResult) {
        if (containsSpecialSectionOpener(xml)) {
            return true;
        }
        if (xml.indexOf('<') >= 0 && CorruptedFrameException.class.getName().equals(currentResult.failure)) {
            return true;
        }
        return (hasNestedOpeningBracketInClosingTag(xml) || hasLegacyTerminatorInClosingTag(xml))
                && CorruptedFrameException.class.getName().equals(currentResult.failure);
    }

    private static boolean containsSpecialSectionOpener(String xml) {
        return xml.contains("<!--") || xml.contains("<?") || xml.contains("<![CDATA[")
                || xml.contains("-->") || xml.contains("?>") || xml.contains("]]>");
    }

    private static boolean hasNestedOpeningBracketInClosingTag(String xml) {
        int closingTagStart = xml.indexOf("</");
        while (closingTagStart >= 0) {
            int nextOpeningBracket = xml.indexOf('<', closingTagStart + 2);
            int closingTagEnd = xml.indexOf('>', closingTagStart + 2);
            if (nextOpeningBracket >= 0 && (closingTagEnd < 0 || nextOpeningBracket < closingTagEnd)) {
                return true;
            }
            closingTagStart = xml.indexOf("</", closingTagStart + 2);
        }
        return false;
    }

    private static boolean hasLegacyTerminatorInClosingTag(String xml) {
        int closingTagStart = xml.indexOf("</");
        while (closingTagStart >= 0) {
            int closingTagEnd = xml.indexOf('>', closingTagStart + 2);
            if (closingTagEnd < 0) {
                return false;
            }
            if (containsBefore(xml, "/>", closingTagStart + 2, closingTagEnd)
                    || containsBefore(xml, "-->", closingTagStart + 2, closingTagEnd + 1)
                    || containsBefore(xml, "?>", closingTagStart + 2, closingTagEnd + 1)) {
                return true;
            }
            closingTagStart = xml.indexOf("</", closingTagStart + 2);
        }
        return false;
    }

    private static boolean containsBefore(String xml, String needle, int fromIndex, int endIndex) {
        int index = xml.indexOf(needle, fromIndex);
        return index >= 0 && index < endIndex;
    }

    private static boolean containsTagLikeContentInComment(String xml) {
        return containsLegacyMarkupInSection(xml, "<!--", "-->");
    }

    private static boolean containsTagLikeContentInProcessingInstruction(String xml) {
        return containsLegacyMarkupInSection(xml, "<?", "?>");
    }

    private static boolean containsTagLikeContentInCData(String xml) {
        return containsLegacyMarkupInSection(xml, "<![CDATA[", "]]>");
    }

    private static boolean containsLegacyMarkupInSection(String xml, String startDelimiter, String endDelimiter) {
        int start = xml.indexOf(startDelimiter);
        while (start >= 0) {
            int contentStart = start + startDelimiter.length();
            int end = xml.indexOf(endDelimiter, contentStart);
            if (end < 0) {
                return false;
            }
            if (containsBefore(xml, "<", contentStart, end) || containsBefore(xml, "/>", contentStart, end)
                    || containsBefore(xml, "-->", contentStart, end) || containsBefore(xml, "?>", contentStart, end)) {
                return true;
            }
            start = xml.indexOf(startDelimiter, end + endDelimiter.length());
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
}
