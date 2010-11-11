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

/**
 * A Web Socket frame that represents either text or binary data.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2342 $, $Date: 2010-07-07 14:07:39 +0900 (Wed, 07 Jul 2010) $
 */
public interface WebSocketFrame {

    /**
     * Closing handshake message (<tt>0xFF, 0x00</tt>)
     */
    WebSocketFrame CLOSING_HANDSHAKE = new DefaultWebSocketFrame(0xFF, ChannelBuffers.EMPTY_BUFFER);

    /**
     * Returns the type of this frame.
     * <tt>0x00-0x7F</tt> means a text frame encoded in UTF-8, and
     * <tt>0x80-0xFF</tt> means a binary frame.  Currently, {@code 0} is the
     * only allowed type according to the specification.
     */
    int getType();

    /**
     * Returns {@code true} if and only if the content of this frame is a string
     * encoded in UTF-8.
     */
    boolean isText();

    /**
     * Returns {@code true} if and only if the content of this frame is an
     * arbitrary binary data.
     */
    boolean isBinary();

    /**
     * Returns the content of this frame as-is, with no UTF-8 decoding.
     */
    ChannelBuffer getBinaryData();

    /**
     * Converts the content of this frame into a UTF-8 string and returns the
     * converted string.
     */
    String getTextData();

    /**
     * Sets the type and the content of this frame.
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
    void setData(int type, ChannelBuffer binaryData);

    /**
     * Returns the string representation of this frame.  Please note that this
     * method is not identical to {@link #getTextData()}.
     */
    String toString();
}
