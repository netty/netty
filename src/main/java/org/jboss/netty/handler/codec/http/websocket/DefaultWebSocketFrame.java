/*
 * Copyright 2010 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocket;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

/**
 * The default {@link WebSocketFrame} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class DefaultWebSocketFrame implements WebSocketFrame {

    private int type;
    private ChannelBuffer binaryData;

    /**
     * Creates a new empty text frame.
     */
    public DefaultWebSocketFrame() {
        this(0, ChannelBuffers.EMPTY_BUFFER);
    }

    /**
     * Creates a new text frame from with the specified string.
     */
    public DefaultWebSocketFrame(String textData) {
        this(0, ChannelBuffers.copiedBuffer(textData, CharsetUtil.UTF_8));
    }

    /**
     * Creates a new frame with the specified frame type and the specified data.
     *
     * @param type
     *        the type of the frame. {@code 0} is the only allowed type currently.
     * @param binaryData
     *        the content of the frame.  If <tt>(type &amp; 0x80 == 0)</tt>,
     *        it must be encoded in UTF-8.
     *
     * @throws IllegalArgumentException
     *         if If <tt>(type &amp; 0x80 == 0)</tt> and the data is not encoded
     *         in UTF-8
     */
    public DefaultWebSocketFrame(int type, ChannelBuffer binaryData) {
        setData(type, binaryData);
    }

    public int getType() {
        return type;
    }

    public boolean isText() {
        return (getType() & 0x80) == 0;
    }

    public boolean isBinary() {
        return !isText();
    }

    public ChannelBuffer getBinaryData() {
        return binaryData;
    }

    public String getTextData() {
        return getBinaryData().toString(CharsetUtil.UTF_8);
    }

    public void setData(int type, ChannelBuffer binaryData) {
        if (binaryData == null) {
            throw new NullPointerException("binaryData");
        }

        if ((type & 0x80) == 0) {
            // If text, data should not contain 0xFF.
            int delimPos = binaryData.indexOf(
                    binaryData.readerIndex(), binaryData.writerIndex(), (byte) 0xFF);
            if (delimPos >= 0) {
                throw new IllegalArgumentException(
                        "a text frame should not contain 0xFF.");
            }
        }

        this.type = type & 0xFF;
        this.binaryData = binaryData;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
               "(type: " + getType() + ", " + "data: " + getBinaryData() + ')';
    }
}
