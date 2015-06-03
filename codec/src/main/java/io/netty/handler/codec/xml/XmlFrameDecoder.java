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
 * A couple examples will better help illustrate what this decoder actually
 * does.
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
 * Please note that this decoder is not suitable for xml streaming protocols
 * such as <a href="http://xmpp.org/rfcs/rfc6120.html">XMPP</a>, where an
 * initial xml element opens the stream and only gets closed at the end of the
 * session, although this class could probably allow for such type of message
 * flow with minor modifications.
 */
public class XmlFrameDecoder extends ByteToMessageDecoder {
    private enum XmlState {
        /**
         * the init state
         */
        INIT,
        /**
         * in the xml header like
         * <pre>
         * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
         * </pre>
         */
        HEADER,
        /**
         * in the xml comment
         * <pre>
         * &lt;!-- comment --&gt;
         * </pre>
         */
        COMMENT,
        /**
         * in the xml cdata
         * <pre>
         * &lt;![CDATA[the cdata text]]&gt;
         * </pre>
         */
        CDATA,
        /**
         * in the xml node start head tag
         * <pre>
         * <b>&lt;XmlNode ...&gt;</b>&lt;/XmlNode&gt;
         * </pre>
         */
        HEAD,
        /**
         * in the xml node attribute
         * <pre>
         * &lt;XmlNode name="in here"&gt;&lt;/XmlNode&gt;
         * </pre>
         */
        ATTR,
        /**
         * in the xml node body
         * <pre>
         * &lt;XmlNode&gt;...in here...&lt;/XmlNode&gt;
         * </pre>
         */
        BODY,
        /**
         * in the xml node end tag
         * <pre>
         * &lt;XmlNode&gt;<b>&lt;/XmlNode&gt;</b>
         * </pre>
         */
        END;
    }

    private int maxFrameLength;
    private int idx;
    private int openCount;
    private int nodeCount;
    private XmlState state = XmlState.INIT;
    private XmlState markState = XmlState.INIT;
    private boolean inXmlFrame;

    public XmlFrameDecoder() {
        this(1024 * 1024);
    }

    public XmlFrameDecoder(int maxFrameLength) {
        if (maxFrameLength < 1) {
            throw new IllegalArgumentException(
                    "maxFrameLength must be a positive int");
        }
        this.maxFrameLength = maxFrameLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in,
            List<Object> out) throws Exception {
        // index of next byte to process.
        int wrtIdx = in.writerIndex();
        if (wrtIdx > maxFrameLength) {
            // buffer size exceeded maxObjectLength; discarding the complete
            // buffer.
            in.skipBytes(in.readableBytes());
            reset();
            throw new TooLongFrameException(String.format(
                    "Xml MaxFrameLength exceeds(%d): %d", maxFrameLength, wrtIdx));
        }
        int idx = this.idx;
        if (idx > wrtIdx) {
            // reset if in is new or reset
            if (in.readerIndex() == 0) {
                idx = 0;
            }
        }
        for (/* use current idx */; idx < wrtIdx; idx++) {
            char c = (char) in.getByte(idx);
            if (state == XmlState.INIT) {
                if (c == '<') {
                    if ((idx + 1) >= wrtIdx) {
                        return;
                    }
                    char c_next = (char) in.getByte(idx + 1);
                    if (c_next == '?') {
                        setAndMarkState(XmlState.HEADER);
                        idx++;
                    } else if (c_next == '!') {
                        setAndMarkState(XmlState.COMMENT);
                        idx++;
                    } else {
                        state = XmlState.HEAD;
                        openCount++;
                        nodeCount++;
                    }
                    inXmlFrame = true;
                } else if (Character.isWhitespace(c)) {
                    if (!inXmlFrame) {
                        in.readerIndex(idx + 1);
                    }
                } else {
                    fail(ctx, in, String.format("An XML document can't start with: %s", c));
                }
            } else if (state == XmlState.HEADER) {
                if (c == '>' && in.getByte(idx - 1) == '?') {
                    state = markState;
                }
            } else if (state == XmlState.COMMENT) {
                if (c == '-') {
                    if ((idx + 2) >= wrtIdx) {
                        return;
                    }
                    // closed the xml comment
                    if (in.getByte(idx + 1) == '-' && in.getByte(idx + 2) == '>') {
                        idx += 2;
                        state = markState;
                    }
                }
            } else if (state == XmlState.HEAD) {
                // found the attribute <XmlNodeid="nodeid"></XmlNode>
                if (c == '=') {
                    if ((idx + 1) >= wrtIdx) {
                        return;
                    }
                    if (in.getByte(idx + 1) == '"') {
                        idx++;
                        setAndMarkState(XmlState.ATTR);
                    }
                } else if (c == '>') {
                    // found the closed tag node head
                    // found the closed tag in head like <Xml />
                    if (in.getByte(idx - 1) == '/') {
                        openCount--;
                    }
                    state = XmlState.BODY;
                } else if (c == '<') {
                    fail(ctx, in, "An Xml document Tag Error: find extra '<'");
                }
            } else if (state == XmlState.ATTR) {
                // found closed attribute
                if (c == '"' && in.getByte(idx - 1) != '\\') {
                    state = markState;
                }
            } else if (state == XmlState.BODY) {
                if (c == '<') {
                    if ((idx + 1) >= wrtIdx) {
                        return;
                    }
                    char c_next = (char) in.getByte(idx + 1);
                    if (c_next == '/') {
                        state = XmlState.END;
                        idx++;
                    } else if (c_next == '!') {
                        if ((idx + 3) >= wrtIdx) {
                            return;
                        }
                        // <!-- comment --> start found
                        if (isCommentBlockStart(in, idx)) {
                            setAndMarkState(XmlState.COMMENT);
                            idx += 3;
                            continue;
                        }
                        if ((idx + 8) >= wrtIdx) {
                            return;
                        }
                        // <![CDATA[ start found
                        if (isCDATABlockStart(in, idx)) {
                            setAndMarkState(XmlState.CDATA);
                            idx += 8;
                        }
                    } else {
                        // found the child node start tag <Xml><Child></Child></Xml>
                        openCount++;
                        state = XmlState.HEAD;
                    }
                } else if (c == '>') {
                    // <Xml><node></node>></Xml>
                    fail(ctx, in, "An Xml document Tag Error: find extra '>'");
                }
            } else if (state == XmlState.CDATA) {
                if ((idx + 2) >= wrtIdx) {
                    return;
                }
                // closed cdata
                if (in.getByte(idx) == ']' && in.getByte(idx + 1) == ']'
                        && in.getByte(idx + 2) == '>') {
                    idx += 2;
                    state = markState;
                }
            } else if (state == XmlState.END) {
                if (c == '>') {
                    state = XmlState.BODY;
                    if (idx > 1 && in.getByte(idx - 1) == '/'
                            && in.getByte(idx - 2) == '<') {
                        continue;
                    }
                    openCount--;
                }
            }
            if (openCount == 0 && nodeCount > 0 && state != XmlState.INIT) {
                int length = idx + 1 - in.readerIndex();
                setAndMarkState(XmlState.INIT);
                nodeCount = 0;
                inXmlFrame = false;
                if (length > 0) {
                    out.add(in.slice(in.readerIndex(), length).retain());
                    in.skipBytes(length);
                    idx = in.readerIndex();
                    break;
                }
            }
        }
        if (in.readableBytes() == 0) {
            this.idx = 0;
        } else {
            this.idx = idx;
        }
    }

    /**
     * reset the state and throw CorruptedFrameException
     * @param ctx
     * @param in
     * @param errorInfo
     */
    private void fail(ChannelHandlerContext ctx, ByteBuf in, String errorInfo)
            throws CorruptedFrameException {
        this.reset();
        in.skipBytes(in.readableBytes());
        throw new CorruptedFrameException(errorInfo);
    }
    private void setAndMarkState(XmlState newState) {
        this.markState = this.state;
        this.state = newState;
    }
    /**
     * reset the state
     */
    private void reset() {
        setAndMarkState(XmlState.INIT);
        idx = 0;
        openCount = 0;
        nodeCount = 0;
        inXmlFrame = false;
    }
    private static boolean isCommentBlockStart(final ByteBuf in, final int i) {
        return in.getByte(i + 2) == '-'
                && in.getByte(i + 3) == '-';
    }
    private static boolean isCDATABlockStart(final ByteBuf in, final int i) {
        return in.getByte(i + 2) == '['
                && in.getByte(i + 3) == 'C'
                && in.getByte(i + 4) == 'D'
                && in.getByte(i + 5) == 'A'
                && in.getByte(i + 6) == 'T'
                && in.getByte(i + 7) == 'A'
                && in.getByte(i + 8) == '[';
    }
}
