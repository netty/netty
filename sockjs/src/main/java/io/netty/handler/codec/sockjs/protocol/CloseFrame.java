/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.sockjs.util.ArgumentUtil;
import io.netty.util.CharsetUtil;

/**
 * This frame is sent from the server if the client requests data from a closed connection.
 */
public class CloseFrame implements Frame {

    private final int statusCode;
    private final String statusMsg;

    public CloseFrame(final int statusCode, final String statusMsg) {
        ArgumentUtil.checkNotNull(statusMsg, "statusMsg");
        this.statusCode = statusCode;
        this.statusMsg = statusMsg;
    }

    public int statusCode() {
        return statusCode;
    }

    public String statusMsg() {
        return statusMsg;
    }

    @Override
    public ByteBuf content() {
        return Unpooled.copiedBuffer("c[" + statusCode + ",\"" + statusMsg + "\"]", CharsetUtil.UTF_8);
    }

    @Override
    public String toString() {
        return "CloseFrame[statusCode=" + statusCode + ", statusMsg='" + statusMsg + "']";
    }

}
