/*
 * Copyright 2020 The Netty Project
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
package io.netty.microbench.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

public enum ByteBufType {
    HEAP {
        @Override
        ByteBuf newBuffer(byte[] bytes) {
            return Unpooled.wrappedBuffer(bytes, 0, bytes.length);
        }
    },
    COMPOSITE {
        @Override
        ByteBuf newBuffer(byte[] bytes) {
            CompositeByteBuf buf = Unpooled.compositeBuffer();
            int length = bytes.length;
            int offset = 0;
            int capacity = length / 8; // 8 buffers per composite

            while (length > 0) {
                buf.addComponent(true, Unpooled.wrappedBuffer(bytes, offset, Math.min(length, capacity)));
                length -= capacity;
                offset += capacity;
            }
            return buf;
        }
    },
    DIRECT {
        @Override
        ByteBuf newBuffer(byte[] bytes) {
            return Unpooled.directBuffer(bytes.length).writeBytes(bytes);
        }
    };
    abstract ByteBuf newBuffer(byte[] bytes);
}
