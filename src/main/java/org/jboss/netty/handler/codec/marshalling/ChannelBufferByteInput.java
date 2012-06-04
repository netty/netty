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
package org.jboss.netty.handler.codec.marshalling;

import java.io.IOException;

import org.jboss.marshalling.ByteInput;
import org.jboss.netty.buffer.ChannelBuffer;

/**
 * {@link ByteInput} implementation which reads its data from a {@link ChannelBuffer}
 *
 *
 */
class ChannelBufferByteInput implements ByteInput {

    private final ChannelBuffer buffer;

    public ChannelBufferByteInput(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    public void close() throws IOException {
        // nothing to do
    }

    public int available() throws IOException {
        return buffer.readableBytes();
    }

    public int read() throws IOException {
        if (buffer.readable()) {
            return buffer.readByte() & 0xff;
        }
        return -1;
    }

    public int read(byte[] array) throws IOException {
        return read(array, 0, array.length);
    }

    public int read(byte[] dst, int dstIndex, int length) throws IOException {
        int available = available();
        if (available == 0) {
            return -1;
        }

        length = Math.min(available, length);
        buffer.readBytes(dst, dstIndex, length);
        return length;
    }

    public long skip(long bytes) throws IOException {
        int readable = buffer.readableBytes();
        if (readable < bytes) {
            bytes = readable;
        }
        buffer.readerIndex((int) (buffer.readerIndex() + bytes));
        return bytes;
    }

}
