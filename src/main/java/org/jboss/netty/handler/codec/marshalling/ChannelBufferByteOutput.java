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

import org.jboss.marshalling.ByteOutput;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * {@link ByteOutput} implementation which writes the data to a {@link ChannelBuffer}
 *
 *
 */
class ChannelBufferByteOutput implements ByteOutput {

    private final ChannelBuffer buffer;

    /**
     * Create a new instance which use the given {@link ChannelBuffer}
     */
    public ChannelBufferByteOutput(ChannelBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Calls {@link #ChannelBufferByteOutput(ChannelBuffer)} with a dynamic {@link ChannelBuffer}
     */
    public ChannelBufferByteOutput(ChannelBufferFactory factory, int estimatedLength) {
        this(ChannelBuffers.dynamicBuffer(estimatedLength, factory));
    }

    public void close() throws IOException {
        // Nothing todo
    }

    public void flush() throws IOException {
        // nothing to do
    }

    public void write(int b) throws IOException {
        buffer.writeByte(b);
    }

    public void write(byte[] bytes) throws IOException {
        buffer.writeBytes(bytes);
    }

    public void write(byte[] bytes, int srcIndex, int length) throws IOException {
        buffer.writeBytes(bytes, srcIndex, length);
    }

    /**
     * Return the {@link ChannelBuffer} which contains the written content
     *
     */
    public ChannelBuffer getBuffer() {
        return buffer;
    }

}
