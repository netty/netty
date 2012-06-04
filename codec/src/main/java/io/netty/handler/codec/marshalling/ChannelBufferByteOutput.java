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
package io.netty.handler.codec.marshalling;

import io.netty.buffer.ChannelBuffer;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.buffer.ChannelBuffers;

import java.io.IOException;

import org.jboss.marshalling.ByteOutput;

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

    @Override
    public void close() throws IOException {
        // Nothing todo
    }

    @Override
    public void flush() throws IOException {
        // nothing to do
    }

    @Override
    public void write(int b) throws IOException {
        buffer.writeByte(b);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        buffer.writeBytes(bytes);
    }

    @Override
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
