/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocketx;

import java.io.UnsupportedEncodingException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

/**
 * Web Socket Frame for closing the connection
 */
public class CloseWebSocketFrame extends WebSocketFrame {
    /**
     * Creates a new empty close frame.
     */
    public CloseWebSocketFrame() {
        setBinaryData(ChannelBuffers.EMPTY_BUFFER);
    }

    /**
     * Creates a new empty close frame with closing status code and reason text
     * 
     * @param statusCode
     *            Integer status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(int statusCode, String reasonText) {
        this(true, 0, statusCode, reasonText);
    }

    /**
     * Creates a new close frame with no losing status code and no reason text
     * 
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv) {
        this(finalFragment, rsv, null);
    }

    /**
     * Creates a new close frame with closing status code and reason text
     * 
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param statusCode
     *            Integer status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. For
     *            example, <tt>1000</tt> indicates normal closure.
     * @param reasonText
     *            Reason text. Set to null if no text.
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv, int statusCode, String reasonText) {
        setFinalFragment(finalFragment);
        setRsv(rsv);

        byte[] reasonBytes = new byte[0];
        if (reasonText != null) {
            try {
                reasonBytes = reasonText.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                // This should never happen, anyway provide a fallback here
                reasonBytes = reasonText.getBytes();
            }
        }

        ChannelBuffer binaryData = ChannelBuffers.buffer(2 + reasonBytes.length);
        binaryData.writeShort(statusCode);
        if (reasonBytes.length > 0) {
            binaryData.writeBytes(reasonBytes);
        }

        binaryData.readerIndex(0);
        setBinaryData(binaryData);
    }

    /**
     * Creates a new close frame
     * 
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param binaryData
     *            the content of the frame. Must be 2 byte integer followed by optional UTF-8 encoded string.
     */
    public CloseWebSocketFrame(boolean finalFragment, int rsv, ChannelBuffer binaryData) {
        setFinalFragment(finalFragment);
        setRsv(rsv);
        if (binaryData == null) {
            setBinaryData(ChannelBuffers.EMPTY_BUFFER);
        } else {
            setBinaryData(binaryData);
        }
    }

    /**
     * Returns the closing status code as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a>. If
     * a status code is set, -1 is returned.
     */
    public int getStatusCode() {
        ChannelBuffer binaryData = this.getBinaryData();
        if (binaryData == null || binaryData.capacity() == 0) {
            return -1;
        }

        binaryData.readerIndex(0);
        int statusCode = binaryData.readShort();
        binaryData.readerIndex(0);

        return statusCode;
    }

    /**
     * Returns the reason text as per <a href="http://tools.ietf.org/html/rfc6455#section-7.4">RFC 6455</a> If a reason
     * text is not supplied, an empty string is returned.
     */
    public String getReasonText() {
        ChannelBuffer binaryData = this.getBinaryData();
        if (binaryData == null || binaryData.capacity() <= 2) {
            return "";
        }

        binaryData.readerIndex(2);
        String reasonText = binaryData.toString(CharsetUtil.UTF_8);
        binaryData.readerIndex(0);

        return reasonText;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
