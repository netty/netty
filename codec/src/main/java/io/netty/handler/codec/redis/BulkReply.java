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
package io.netty.handler.codec.redis;

import io.netty.buffer.ChannelBuffer;

import java.io.IOException;

public class BulkReply extends Reply {
    public static final char MARKER = '$';
    public final byte[] bytes;

    public BulkReply(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public void write(ChannelBuffer os) throws IOException {
        os.writeByte(MARKER);
        if (bytes == null) {
            os.writeBytes(Command.NEG_ONE_AND_CRLF);
        } else {
            os.writeBytes(Command.numAndCRLF(bytes.length));
            os.writeBytes(bytes);
            os.writeBytes(Command.CRLF);
        }
    }
}
