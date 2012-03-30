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
package org.jboss.netty.handler.codec.redis;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class BulkReply extends Reply {
    static final char MARKER = '$';

    private final ChannelBuffer data;

    public BulkReply(byte[] data) {
        this(data == null? null : ChannelBuffers.wrappedBuffer(data));
    }

    public BulkReply(ChannelBuffer data) {
        this.data = data;
    }

    public ChannelBuffer data() {
        return data;
    }

    @Override
    void write(ChannelBuffer out) {
        out.writeByte(MARKER);
        if (data == null) {
            out.writeBytes(Command.NEG_ONE_AND_CRLF);
        } else {
            out.writeBytes(Command.numAndCRLF(data.readableBytes()));
            out.writeBytes(data, data.readerIndex(), data.readableBytes());
            out.writeBytes(Command.CRLF);
        }
    }
}
