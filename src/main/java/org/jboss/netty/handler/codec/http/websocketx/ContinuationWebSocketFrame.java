/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec.http.websocketx;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

/**
 * Web Socket continuation frame containing continuation text or binary data. This is used for
 * fragmented messages where the contents of a messages is contained more than 1 frame.
 */
public class ContinuationWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty continuation frame.
     */
    public ContinuationWebSocketFrame() {
        setBinaryData(ChannelBuffers.EMPTY_BUFFER);
    }

    /**
     * Creates a new continuation frame with the specified binary data. The final fragment flag is
     * set to true.
     *
     * @param binaryData
     *            the content of the frame.
     */
    public ContinuationWebSocketFrame(ChannelBuffer binaryData) {
        setBinaryData(binaryData);
    }

    /**
     * Creates a new continuation frame with the specified binary data
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param binaryData
     *            the content of the frame.
     */
    public ContinuationWebSocketFrame(boolean finalFragment, int rsv, ChannelBuffer binaryData) {
        setFinalFragment(finalFragment);
        setRsv(rsv);
        setBinaryData(binaryData);
    }

    /**
     * Creates a new continuation frame with the specified text data
     *
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param text
     *            text content of the frame.
     */
    public ContinuationWebSocketFrame(boolean finalFragment, int rsv, String text) {
        setFinalFragment(finalFragment);
        setRsv(rsv);
        setText(text);
    }

    /**
     * Returns the text data in this frame
     */
    public String getText() {
        if (getBinaryData() == null) {
            return null;
        }
        return getBinaryData().toString(CharsetUtil.UTF_8);
    }

    /**
     * Sets the string for this frame
     *
     * @param text
     *            text to store
     */
    public void setText(String text) {
        if (text == null || text.length() == 0) {
            setBinaryData(ChannelBuffers.EMPTY_BUFFER);
        } else {
            setBinaryData(ChannelBuffers.copiedBuffer(text, CharsetUtil.UTF_8));
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(data: " + getBinaryData() + ')';
    }
}
