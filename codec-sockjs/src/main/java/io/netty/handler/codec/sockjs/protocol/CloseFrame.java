/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.sockjs.protocol;

import io.netty.buffer.DefaultByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
 * This frame is sent from the server if the client requests data from a closed connection.
 */
public class CloseFrame extends DefaultByteBufHolder implements Frame {

    private final int statusCode;
    private final String statusMsg;

    /**
     * Creates a new frame with the specified status code and message.
     *
     * @param statusCode the status code for this close frame.
     * @param statusMsg the status message for this close frame.
     */
    public CloseFrame(final int statusCode, final String statusMsg) {
        super(Unpooled.copiedBuffer("c[" + statusCode + ",\"" + statusMsg + "\"]", CharsetUtil.UTF_8));
        this.statusCode = statusCode;
        this.statusMsg = statusMsg;
    }

    /**
     * Returns the status code for this close frame.
     *
     * @return {@code int} the status code.
     */
    public int statusCode() {
        return statusCode;
    }

    /**
     * Returns the status message for this close frame.
     *
     * @return {@code String} the status message for this close frame.
     */
    public String statusMsg() {
        return statusMsg;
    }

    @Override
    public String toString() {
        return "CloseFrame[statusCode=" + statusCode + ", statusMsg='" + statusMsg + "']";
    }

}
