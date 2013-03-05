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
package org.jboss.netty.handler.codec.frame;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;

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
 * Please note that this decoder is not suitable for
 * xml streaming protocols such as
 * <a href="http://xmpp.org/rfcs/rfc6120.html">XMPP</a>,
 * where an initial xml element opens the stream and only
 * gets closed at the end of the session, although this class
 * could probably allow for such type of message flow with
 * minor modifications.
 */
public class XmlFrameDecoder extends FrameDecoder {

    private final int maxFrameLength;

    public XmlFrameDecoder(int maxFrameLength) {
        if (maxFrameLength < 1) {
            throw new IllegalArgumentException("maxFrameLength must be a positive int");
        }
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) {
        boolean openingBracketFound = false;
        boolean atLeastOneXmlElementFound = false;
        long openBracketsCount = 0;
        int length = 0;
        int leadingWhiteSpaceCount = 0;
        final int bufferLength = buffer.writerIndex();

        if (bufferLength > maxFrameLength) {
            // bufferLength exceeded maxFrameLength; dropping frame
            fail(ctx, bufferLength);
            buffer.skipBytes(buffer.readableBytes());
            return null;
        }

        for (int i = buffer.readerIndex(); i < bufferLength; i++) {
            final byte readByte = buffer.getByte(i);
            if (!openingBracketFound && Character.isWhitespace(readByte)) {
                // xml has not started and whitespace char found
                leadingWhiteSpaceCount++;
            } else if (!openingBracketFound && readByte != '<') {
                // garbage found before xml start
                fail(ctx);
                buffer.skipBytes(buffer.readableBytes());
                return null;
            } else if (readByte == '<') {
                openingBracketFound = true;

                if (i < bufferLength - 1) {
                    final byte peekAheadByte = buffer.getByte(i + 1);
                    if (peekAheadByte == '/') {
                        // found </, decrementing openBracketsCount
                        openBracketsCount--;
                    } else if (isValidStartCharForXmlElement(peekAheadByte)) {
                        atLeastOneXmlElementFound = true;
                        // char after < is a valid xml element start char,
                        // incrementing openBracketsCount
                        openBracketsCount++;
                    } else if (peekAheadByte == '!' && i < bufferLength - 3
                            && buffer.getByte(i + 2) == '-'
                            && buffer.getByte(i + 3) == '-') {
                        // <!-- comment --> start found
                        openBracketsCount++;
                    } else if (peekAheadByte == '?') {
                        // <?xml ?> start found
                        openBracketsCount++;
                    }
                }
            } else if (readByte == '/') {
                if (i < bufferLength - 1 && buffer.getByte(i + 1) == '>') {
                    // found />, decrementing openBracketsCount
                    openBracketsCount--;
                }
            } else if (readByte == '>') {
                length = i + 1;

                if (i - 1 > -1) {
                    final byte peekBehindByte = buffer.getByte(i - 1);

                    if (peekBehindByte == '?') {
                        // an <?xml ?> tag was closed
                        openBracketsCount--;
                    } else if (peekBehindByte == '-' && i - 2 > -1 && buffer.getByte(i - 2) == '-') {
                        // a <!-- comment --> was closed
                        openBracketsCount--;
                    }
                }

                if (openingBracketFound && atLeastOneXmlElementFound && openBracketsCount == 0) {
                    // xml is balanced, bailing out
                    break;
                }
            }
        }

        final int readerIndex = buffer.readerIndex();

        if (openBracketsCount == 0 && length > 0) {
            final ChannelBuffer frame =
                    extractFrame(buffer, readerIndex + leadingWhiteSpaceCount, length - leadingWhiteSpaceCount);
            buffer.skipBytes(length);
            return frame;
        }
        return null;
    }

    private void fail(ChannelHandlerContext ctx, long frameLength) {
        if (frameLength > 0) {
            Channels.fireExceptionCaught(
                    ctx.getChannel(),
                    new TooLongFrameException(
                            "frame length exceeds " + maxFrameLength +
                                    ": " + frameLength + " - discarded"));
        } else {
            Channels.fireExceptionCaught(
                    ctx.getChannel(),
                    new TooLongFrameException(
                            "frame length exceeds " + maxFrameLength +
                                    " - discarding"));
        }
    }

    private static void fail(ChannelHandlerContext ctx) {
        Channels.fireExceptionCaught(
                ctx.getChannel(),
                new CorruptedFrameException("frame contains content before the xml starts")
        );
    }

    private static ChannelBuffer extractFrame(ChannelBuffer buffer, int index, int length) {
        ChannelBuffer frame = buffer.factory().getBuffer(length);
        frame.writeBytes(buffer, index, length);
        return frame;
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
    @SuppressWarnings("UnnecessaryParentheses")
    private static boolean isValidStartCharForXmlElement(final byte b) {
        return (b >= 'a' && b <= 'z')
                || (b >= 'A' && b <= 'Z')
                || b == ':'
                || b == '_';
    }

}
