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

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Base class for web socket frames
 */
public abstract class WebSocketFrame {

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    private boolean finalFragment = true;

    /**
     * RSV1, RSV2, RSV3 used for extensions
     */
    private int rsv;

    /**
     * Contents of this frame
     */
    private ChannelBuffer binaryData;

    /**
     * Returns binary data
     */
    public ChannelBuffer getBinaryData() {
        return binaryData;
    }

    /**
     * Sets the binary data for this frame
     */
    public void setBinaryData(ChannelBuffer binaryData) {
        this.binaryData = binaryData;
    }

    /**
     * Flag to indicate if this frame is the final fragment in a message. The first fragment (frame) may also be the
     * final fragment.
     */
    public boolean isFinalFragment() {
        return finalFragment;
    }

    public void setFinalFragment(boolean finalFragment) {
        this.finalFragment = finalFragment;
    }

    /**
     * Bits used for extensions to the standard.
     */
    public int getRsv() {
        return rsv;
    }

    public void setRsv(int rsv) {
        this.rsv = rsv;
    }

}
