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
    private XmlState stat = XmlState.INIT;
    private XmlState lastStat = XmlState.INIT;
    private boolean closeing;
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
                    "Xml MaxFrameLength exceeds %d,%d", maxFrameLength, wrtIdx));
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
            if (stat == XmlState.INIT) {
                if (c == '<') {
                    if ((idx + 1) >= wrtIdx) {
                        return;
                    }
                    char c_next = (char) in.getByte(idx + 1);
                    if (c_next == '?') {
                        lastStat = stat;
                        stat = XmlState.HEADER;
                        idx++;
                    } else if (c_next == '!') {
                        lastStat = stat;
                        stat = XmlState.COMMENT;
                        idx++;
                    } else {
                        stat = XmlState.HEAD;
                        openCount++;
                        nodeCount++;
                    }
                    inXmlFrame = true;
                } else if (Character.isWhitespace(c)) {
                    if (!inXmlFrame) {
                        in.readerIndex(idx + 1);
                    }
                } else {
                    fail(ctx,
                            in,
                            String.format(
                                    "Xml_Document_ERR: XmlDocument can't start with[%s]",
                                    c));
                }
            } else if (stat == XmlState.HEADER) {
                if (c == '>' && in.getByte(idx - 1) == '?') {
                    stat = lastStat;
                }
            } else if (stat == XmlState.COMMENT) {
                if (c == '-') {
                    if ((idx + 2) >= wrtIdx) {
                        return;
                    }
                    // closed the xml comment
                    if (in.getByte(idx + 1) == '-'
                            && in.getByte(idx + 2) == '>') {
                        idx += 2;
                        stat = lastStat;
                    }
                }
            } else if (stat == XmlState.HEAD) {
                // found the attribute <XmlNodeid="nodeid"></XmlNode>
                if (c == '=') {
                    if ((idx + 1) >= wrtIdx) {
                        return;
                    }
                    if (in.getByte(idx + 1) == '"') {
                        idx++;
                        lastStat = stat;
                        stat = XmlState.ATTR;
                    }
                } else if (c == '>') {
                    // found the closed tag node head
                    // found the closed tag in head like <Xml />
                    if (in.getByte(idx - 1) == '/') {
                        openCount--;
                    }
                    stat = XmlState.BODY;
                }
            } else if (stat == XmlState.ATTR) {
                // found closed attribute
                if (c == '"' && in.getByte(idx - 1) != '\\') {
                    stat = lastStat;
                }
            } else if (stat == XmlState.BODY) {
                if (c == '<') {
                    if ((idx + 1) >= wrtIdx) {
                        return;
                    }
                    byte c_next = in.getByte(idx + 1);
                    if (c_next == '/') {
                        closeing = true;
                        stat = XmlState.END;
                        idx++;
                    } else if (c_next == '!') {
                        if ((idx + 3) >= wrtIdx) {
                            return;
                        }
                        // <!-- comment --> start found
                        if (in.getByte(idx + 2) == '-'
                                && in.getByte(idx + 3) == '-') {
                            lastStat = stat;
                            stat = XmlState.COMMENT;
                            idx += 3;
                            continue;
                        }
                        if ((idx + 8) >= wrtIdx) {
                            return;
                        }
                        // <![CDATA[ start found
                        if (in.getByte(idx + 2) == '['
                                && in.getByte(idx + 3) == 'C'
                                && in.getByte(idx + 4) == 'D'
                                && in.getByte(idx + 5) == 'A'
                                && in.getByte(idx + 6) == 'T'
                                && in.getByte(idx + 7) == 'A'
                                && in.getByte(idx + 8) == '[') {
                            lastStat = stat;
                            stat = XmlState.CDATA;
                            idx += 8;
                        }
                    } else {
                        // found the child node start tag <Xml><Child></Child></Xml>
                        openCount++;
                        stat = XmlState.HEAD;
                    }
                }
            } else if (stat == XmlState.CDATA) {
                if ((idx + 2) >= wrtIdx) {
                    return;
                }
                // closed cdata
                if (in.getByte(idx) == ']' && in.getByte(idx + 1) == ']'
                        && in.getByte(idx + 2) == '>') {
                    idx += 2;
                    stat = lastStat;
                }
            } else if (stat == XmlState.END) {
                if (c == '>') {
                    if (closeing) {
                        closeing = false;
                        stat = XmlState.BODY;
                        if (idx > 1 && in.getByte(idx - 1) == '/'
                                && in.getByte(idx - 2) == '<') {
                            continue;
                        }
                        openCount--;
                    } else {
                        fail(ctx, in, "Xml_Document_ERR: > not safe close");
                    }
                }
            }
            if (openCount == 0 && nodeCount > 0 && stat != XmlState.INIT) {
                int length = idx + 1 - in.readerIndex();
                stat = XmlState.INIT;
                lastStat = stat;
                nodeCount = 0;
                inXmlFrame = false;
                if (length > 0) {
                    out.add(in.copy(in.readerIndex(), length));
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
     * reset the stat and throw DecoderException
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

    /**
     * reset the stat
     */
    private void reset() {
        stat = XmlState.INIT;
        lastStat = stat;
        idx = 0;
        openCount = 0;
        nodeCount = 0;
        closeing = false;
        inXmlFrame = false;
    }
}
