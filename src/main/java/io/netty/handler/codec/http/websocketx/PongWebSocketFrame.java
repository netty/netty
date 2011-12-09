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
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBuffers;

/**
 * Web Socket frame containing binary data
 * 
 * @author <a href="http://www.veebsbraindump.com/">Vibul Imtarnasan</a>
 */
public class PongWebSocketFrame extends WebSocketFrame {

    /**
     * Creates a new empty pong frame.
     */
    public PongWebSocketFrame() {
        this.setBinaryData(ChannelBuffers.EMPTY_BUFFER);
    }

    /**
     * Creates a new pong frame with the specified binary data.
     * 
     * @param binaryData
     *            the content of the frame.
     */
    public PongWebSocketFrame(ChannelBuffer binaryData) {
        this.setBinaryData(binaryData);
    }

    /**
     * Creates a new pong frame with the specified binary data
     * 
     * @param finalFragment
     *            flag indicating if this frame is the final fragment
     * @param rsv
     *            reserved bits used for protocol extensions
     * @param binaryData
     *            the content of the frame.
     */
    public PongWebSocketFrame(boolean finalFragment, int rsv, ChannelBuffer binaryData) {
        this.setFinalFragment(finalFragment);
        this.setRsv(rsv);
        this.setBinaryData(binaryData);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(data: " + getBinaryData() + ')';
    }

}
