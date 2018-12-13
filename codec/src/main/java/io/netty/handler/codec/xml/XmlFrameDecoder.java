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
package io.netty.handler.codec.xml;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

/**
 * A frame decoder for single separate XML based message streams.
 * <p/>
 * A couple examples will better help illustrate
 * what this decoder actually does.
 * <p/>
 * Given an input array of bytes split over 3 frames like this:
 * <pre>
 * +-----+-----+-----------+
 * | &lt;an | Xml | Element/&gt; |
 * +-----+-----+-----------+
 * </pre>
 * <p/>
 * this decoder would output a single frame:
 * <p/>
 * <pre>
 * +-----------------+
 * | &lt;anXmlElement/&gt; |
 * +-----------------+
 * </pre>
 *
 * Given an input array of bytes split over 5 frames like this:
 * <pre>
 * +-----+-----+-----------+-----+----------------------------------+
 * | &lt;an | Xml | Element/&gt; | &lt;ro | ot&gt;&lt;child&gt;content&lt;/child&gt;&lt;/root&gt; |
 * +-----+-----+-----------+-----+----------------------------------+
 * </pre>
 * <p/>
 * this decoder would output two frames:
 * <p/>
 * <pre>
 * +-----------------+-------------------------------------+
 * | &lt;anXmlElement/&gt; | &lt;root&gt;&lt;child&gt;content&lt;/child&gt;&lt;/root&gt; |
 * +-----------------+-------------------------------------+
 * </pre>
 *
 * <p/>
 * The byte stream is expected to be in UTF-8 character encoding or ASCII. The current implementation
 * uses direct {@code byte} to {@code char} cast and then compares that {@code char} to a few low range
 * ASCII characters like {@code '<'}, {@code '>'} or {@code '/'}. UTF-8 is not using low range [0..0x7F]
 * byte values for multibyte codepoint representations therefore fully supported by this implementation.
 * <p/>
 * Please note that this decoder is not suitable for
 * xml streaming protocols such as
 * <a href="http://xmpp.org/rfcs/rfc6120.html">XMPP</a>,
 * where an initial xml element opens the stream and only
 * gets closed at the end of the session, although this class
 * could probably allow for such type of message flow with
 * minor modifications.
 */
public class XmlFrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;

    public XmlFrameDecoder(int maxFrameLength) {
        if (maxFrameLength < 1) {
            throw new IllegalArgumentException("maxFrameLength must be a positive int");
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
            // bufferLength exceeded maxFrameLength; dropping frame
            in.skipBytes(in.readableBytes());
            fail(bufferLength);
            return;
        }

        for (int i = in.readerIndex(); i < bufferLength; i++) {
            final byte readByte = in.getByte(i);
            if (!openingBracketFound && Character.isWhitespace(readByte)) {
                // xml has not started and whitespace char found
                leadingWhiteSpaceCount++;
            } else if (!openingBracketFound && readByte != '<') {
                // garbage found before xml start
                fail(ctx);
                in.skipBytes(in.readableBytes());
                return;
            } else if (!inCDATASection && readByte == '<') {
                openingBracketFound = true;

                if (i < bufferLength - 1) {
                    final byte peekAheadByte = in.getByte(i + 1);
                    if (peekAheadByte == '/') {
                        // found </, we must check if it is enclosed
                        int peekFurtherAheadIndex = i + 2;
                        while (peekFurtherAheadIndex <= bufferLength - 1) {
                            //if we have </ and enclosing > we can decrement openBracketsCount
                            if (in.getByte(peekFurtherAheadIndex) == '>') {
                                openBracketsCount--;
                                break;
                            }
                            peekFurtherAheadIndex++;
                        }
                    } else if (isValidStartCharForXmlElement(peekAheadByte)) {
                        atLeastOneXmlElementFound = true;
                        // char after < is a valid xml element start char,
                        // incrementing openBracketsCount
                        openBracketsCount++;
                    } else if (peekAheadByte == '!') {
                        if (isCommentBlockStart(in, i)) {
                            // <!-- comment --> start found
                            openBracketsCount++;
                        } else if (isCDATABlockStart(in, i)) {
                            // <![CDATA[ start found
                            openBracketsCount++;
                            inCDATASection = true;
                        }
                    } else if (peekAheadByte == '?') {
                        // <?xml ?> start found
                        openBracketsCount++;
                    }
                }
            } else if (!inCDATASection && readByte == '/') {
                if (i < bufferLength - 1 && in.getByte(i + 1) == '>') {
                    // found />, decrementing openBracketsCount
                    openBracketsCount--;
                }
            } else if (readByte == '>') {
                length = i + 1;

                if (i - 1 > -1) {
                    final byte peekBehindByte = in.getByte(i - 1);

                    if (!inCDATASection) {
                        if (peekBehindByte == '?') {
                            // an <?xml ?> tag was closed
                            openBracketsCount--;
                        } else if (peekBehindByte == '-' && i - 2 > -1 && in.getByte(i - 2) == '-') {
                            // a <!-- comment --> was closed
                            openBracketsCount--;
                        }
                    } else if (peekBehindByte == ']' && i - 2 > -1 && in.getByte(i - 2) == ']') {
                        // a <![CDATA[...]]> block was closed
                        openBracketsCount--;
                        inCDATASection = false;
                    }
                }

                if (atLeastOneXmlElementFound && openBracketsCount == 0) {
                    // xml is balanced, bailing out
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
            final ByteBuf frame =
                    extractFrame(in, readerIndex + leadingWhiteSpaceCount, xmlElementLength - leadingWhiteSpaceCount);
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

    /**
     * Asks whether the given byte is a valid
     * start char for an xml element name.
     * <p/>
     * Please refer to the
     * <a href="http://www.w3.org/TR/2004/REC-xml11-20040204/#NT-NameStartChar">NameStartChar</a>
     * formal definition in the W3C XML spec for further info.
     *
     * @param b the input char
     * @return true if the char is a valid start char
     */
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
