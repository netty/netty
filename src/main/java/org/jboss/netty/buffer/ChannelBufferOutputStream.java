/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.buffer;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An {@link OutputStream} which writes data to a {@link ChannelBuffer}.
 * <p>
 * A write operation against this stream will occur at the {@code writerIndex}
 * of its underlying buffer and the {@code writerIndex} will increase during
 * the write operation.
 * <p>
 * This stream implements {@link DataOutput} for your convenience.
 * The endianness of the stream is not always big endian but depends on
 * the endianness of the underlying buffer.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 *
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 *
 * @see ChannelBufferInputStream
 * @apiviz.uses org.jboss.netty.buffer.ChannelBuffer
 */
public class ChannelBufferOutputStream extends OutputStream implements DataOutput {

    private final ChannelBuffer buffer;
    private final int startIndex;
    private final DataOutputStream utf8out = new DataOutputStream(this);

    /**
     * Creates a new stream which writes data to the specified {@code buffer}.
     */
    public ChannelBufferOutputStream(ChannelBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        this.buffer = buffer;
        startIndex = buffer.writerIndex();
    }

    /**
     * Returns the number of written bytes by this stream so far.
     */
    public int writtenBytes() {
        return buffer.writerIndex() - startIndex;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }

        buffer.writeBytes(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        buffer.writeBytes(b);
    }

    @Override
    public void write(int b) throws IOException {
        buffer.writeByte((byte) b);
    }

    public void writeBoolean(boolean v) throws IOException {
        write(v? (byte) 1 : (byte) 0);
    }

    public void writeByte(int v) throws IOException {
        write(v);
    }

    public void writeBytes(String s) throws IOException {
        int len = s.length();
        for (int i = 0; i < len; i ++) {
            write((byte) s.charAt(i));
        }
    }

    public void writeChar(int v) throws IOException {
        writeShort((short) v);
    }

    public void writeChars(String s) throws IOException {
        int len = s.length();
        for (int i = 0 ; i < len ; i ++) {
            writeChar(s.charAt(i));
        }
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeInt(int v) throws IOException {
        buffer.writeInt(v);
    }

    public void writeLong(long v) throws IOException {
        buffer.writeLong(v);
    }

    public void writeShort(int v) throws IOException {
        buffer.writeShort((short) v);
    }

    public void writeUTF(String s) throws IOException {
        utf8out.writeUTF(s);
    }

    /**
     * Returns the buffer where this stream is writing data.
     */
    public ChannelBuffer buffer() {
        return buffer;
    }
}
