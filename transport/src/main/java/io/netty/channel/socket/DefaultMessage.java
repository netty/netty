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
package io.netty.channel.socket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Message;


public class DefaultMessage implements Message {
    private final ByteBuf data;

    public DefaultMessage(ByteBuf data) {
        if(data == null) {
            throw new NullPointerException("data");
        }
        this.data = data;
    }

    @Override
    public ByteBuf data() {
        if (data.isFreed()) {
            throw new IllegalStateException();
        }
        if (data.readable()) {
            return data.slice();
        } else {
            return Unpooled.EMPTY_BUFFER;
        }
    }

    @Override
    public void free() {
        if (!data().isFreed()) {
            data().free();
        }
    }
}
