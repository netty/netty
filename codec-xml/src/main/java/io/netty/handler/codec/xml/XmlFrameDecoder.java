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

import static io.netty.util.internal.ObjectUtil.checkPositive;

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
 * <a href="https://xmpp.org/rfcs/rfc6120.html">XMPP</a>,
 * where an initial xml element opens the stream and only
 * gets closed at the end of the session, although this class
 * could probably allow for such type of message flow with
 * minor modifications.
 */
public class XmlFrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;

    private boolean openingBracketFound;
    private boolean atLeastOneXmlElementFound;
    private boolean inCDATASection;
    private boolean inCommentBlock;
    private boolean inProcessingInstruction;
    private boolean inClosingTag;
    private long openBracketsCount;

    /**
     * Index, relative to the current reader index, of the byte right after the last {@code '>'}
     * seen so far. Netty's cumulation buffer may be compacted (rebasing both the reader index and
     * the bytes) in between two {@link #decode(ChannelHandlerContext, ByteBuf, List)} invocations,
     * which would silently invalidate an absolute buffer index stored across calls; storing this
     * relative to the reader index keeps it valid across such compactions, just like {@link
     * #scanOffset}.
     */
    private int length;
    private int leadingWhiteSpaceCount;

    /**
     * Index, relative to the current reader index, of the next byte that still needs to be scanned.
     * This lets {@link #decode(ChannelHandlerContext, ByteBuf, List)} resume scanning where the
     * previous invocation left off instead of rescanning the whole accumulated buffer every time,
     * which would otherwise make a slowly trickled, never-balanced element run in quadratic time.
     */
    private int scanOffset;

    public XmlFrameDecoder(int maxFrameLength) {
        this.maxFrameLength = checkPositive(maxFrameLength, "maxFrameLength");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        final int bufferLength = in.writerIndex();

        if (bufferLength > maxFrameLength) {
            // bufferLength exceeded maxFrameLength; dropping frame
            in.skipBytes(in.readableBytes());
            resetState();
            fail(bufferLength);
            return;
        }

        final int readerIndex = in.readerIndex();
        int i;
        scan:
        for (i = readerIndex + scanOffset; i < bufferLength; i++) {
            final byte readByte = in.getByte(i);
            if (!openingBracketFound && Character.isWhitespace(readByte)) {
                // xml has not started and whitespace char found
                leadingWhiteSpaceCount++;
            } else if (!openingBracketFound && readByte != '<') {
                // garbage found before xml start
                fail(ctx);
                in.skipBytes(in.readableBytes());
                resetState();
                return;
            } else if (inClosingTag && readByte == '<') {
                fail(ctx);
                in.skipBytes(in.readableBytes());
                resetState();
                return;
            } else if (!inCDATASection && !inCommentBlock && !inProcessingInstruction && readByte == '<') {
                openingBracketFound = true;

                if (i < bufferLength - 1) {
                    final byte peekAheadByte = in.getByte(i + 1);
                    if (peekAheadByte == '/') {
                        // found </, we must check if it is enclosed
                        inClosingTag = true;
                    } else if (isValidStartCharForXmlElement(peekAheadByte)) {
                        atLeastOneXmlElementFound = true;
                        // char after < is a valid xml element start char,
                        // incrementing openBracketsCount
                        openBracketsCount++;
                    } else if (peekAheadByte == '!') {
                        final BangMarkupKind markupKind = classifyBangMarkup(in, i, bufferLength);
                        switch (markupKind) {
                            case INDETERMINATE:
                                // Not enough data has been received yet to tell whether this is
                                // a <!-- comment -->, a <![CDATA[ block, or some other markup
                                // declaration (e.g. <!DOCTYPE ...> or an unknown <!x>); wait for
                                // more bytes before resuming the scan from this position.
                                break scan;
                            case COMMENT:
                                // <!-- comment --> start found
                                openBracketsCount++;
                                inCommentBlock = true;
                                break;
                            case CDATA:
                                // <![CDATA[ start found
                                openBracketsCount++;
                                inCDATASection = true;
                                break;
                            case NONE:
                                // some other markup declaration (e.g. <!DOCTYPE ...> or <!x>);
                                // treated as inert content, same as everything else between tags.
                                break;
                            default:
                                throw new Error();
                        }
                    } else if (peekAheadByte == '?') {
                        // <?xml ?> start found
                        openBracketsCount++;
                        inProcessingInstruction = true;
                    }
                } else {
                    // not enough data yet to peek at the byte following '<'; wait for more
                    // and resume the scan from this same position.
                    break;
                }
            } else if (!inCDATASection && !inCommentBlock && !inProcessingInstruction && readByte == '/') {
                if (i < bufferLength - 1) {
                    if (in.getByte(i + 1) == '>') {
                        // found />, decrementing openBracketsCount
                        openBracketsCount--;
                    }
                } else {
                    // not enough data yet to peek at the byte following '/'; wait for more
                    // and resume the scan from this same position.
                    break;
                }
            } else if (readByte == '>') {
                length = i + 1 - readerIndex;

                if (i - 1 > -1) {
                    final byte peekBehindByte = in.getByte(i - 1);

                    if (inCommentBlock) {
                        if (peekBehindByte == '-' && i - 2 > -1 && in.getByte(i - 2) == '-') {
                            // a <!-- comment --> was closed
                            openBracketsCount--;
                            inCommentBlock = false;
                        }
                    } else if (inProcessingInstruction) {
                        if (peekBehindByte == '?') {
                            // an <?xml ?> tag was closed
                            openBracketsCount--;
                            inProcessingInstruction = false;
                        }
                    } else if (inClosingTag) {
                        openBracketsCount--;
                        inClosingTag = false;
                    } else if (!inCDATASection) {
                        if (peekBehindByte == '?') {
                            // an <?xml ?> tag was closed
                            openBracketsCount--;
                        } else if (peekBehindByte == '-' && i - 2 > -1 && in.getByte(i - 2) == '-') {
                            // a <!-- comment --> was closed
                            openBracketsCount--;
                        }
                    } else if (inCDATASection && peekBehindByte == ']' && i - 2 > -1 && in.getByte(i - 2) == ']') {
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

        int xmlElementLength = length;

        if (openBracketsCount == 0 && xmlElementLength > 0) {
            if (readerIndex + xmlElementLength >= bufferLength) {
                xmlElementLength = in.readableBytes();
            }
            final ByteBuf frame =
                    extractFrame(in, readerIndex + leadingWhiteSpaceCount, xmlElementLength - leadingWhiteSpaceCount);
            in.skipBytes(xmlElementLength);
            // a full element was extracted; reset all parser state (including the scan offset)
            // so the next invocation starts scanning a fresh element from the current reader index.
            resetState();
            out.add(frame);
        } else {
            // no complete, balanced element yet; remember how far we scanned so the next
            // invocation resumes from here instead of rescanning the whole buffer.
            scanOffset = i - readerIndex;
        }
    }

    private void resetState() {
        openingBracketFound = false;
        atLeastOneXmlElementFound = false;
        inCDATASection = false;
        inCommentBlock = false;
        inProcessingInstruction = false;
        inClosingTag = false;
        openBracketsCount = 0;
        length = 0;
        leadingWhiteSpaceCount = 0;
        scanOffset = 0;
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
     * <a href="https://www.w3.org/TR/2004/REC-xml11-20040204/#NT-NameStartChar">NameStartChar</a>
     * formal definition in the W3C XML spec for further info.
     *
     * @param b the input char
     * @return true if the char is a valid start char
     */
    private static boolean isValidStartCharForXmlElement(final byte b) {
        return b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z' || b == ':' || b == '_';
    }

    /**
     * The outcome of classifying a {@code '<!'} construct against the {@code <!--} and
     * {@code <![CDATA[} start markers.
     */
    private enum BangMarkupKind {
        /** Not enough data has been received yet to determine the kind of construct. */
        INDETERMINATE,
        /** Neither a comment nor a CDATA start, e.g. {@code <!DOCTYPE ...>} or {@code <!x>}. */
        NONE,
        /** A {@code <!-- comment -->} start. */
        COMMENT,
        /** A {@code <![CDATA[} start. */
        CDATA
    }

    private static final byte[] CDATA_START_SUFFIX = {'C', 'D', 'A', 'T', 'A', '['};

    /**
     * Classifies the markup declaration starting at {@code in.getByte(i) == '<'} followed by
     * {@code '!'}, matching it incrementally against the {@code <!--} and {@code <![CDATA[}
     * prefixes so a genuinely different declaration (e.g. {@code <!DOCTYPE ...>} or an unknown
     * {@code <!x>}) is recognised as such immediately, without waiting for bytes that would only
     * be needed to confirm a comment or CDATA start.
     */
    private static BangMarkupKind classifyBangMarkup(final ByteBuf in, final int i, final int bufferLength) {
        final int commentOrCDATAMarker = i + 2;
        if (commentOrCDATAMarker >= bufferLength) {
            return BangMarkupKind.INDETERMINATE;
        }
        final byte b = in.getByte(commentOrCDATAMarker);
        if (b == '-') {
            final int secondDash = i + 3;
            if (secondDash >= bufferLength) {
                return BangMarkupKind.INDETERMINATE;
            }
            return in.getByte(secondDash) == '-' ? BangMarkupKind.COMMENT : BangMarkupKind.NONE;
        }
        if (b == '[') {
            for (int k = 0; k < CDATA_START_SUFFIX.length; k++) {
                final int idx = i + 3 + k;
                if (idx >= bufferLength) {
                    return BangMarkupKind.INDETERMINATE;
                }
                if (in.getByte(idx) != CDATA_START_SUFFIX[k]) {
                    return BangMarkupKind.NONE;
                }
            }
            return BangMarkupKind.CDATA;
        }
        return BangMarkupKind.NONE;
    }

}
