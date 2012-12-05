/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file tothe License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class PooledHeapByteBuf extends PooledByteBuf<byte[]> {

    PooledHeapByteBuf(int maxCapacity) {
        super(maxCapacity);
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    public byte getByte(int index) {
        checkIndex(index);
        return memory[idx(index)];
    }

    @Override
    public short getShort(int index) {
        checkIndex(index, 2);
        index = idx(index);
        return (short) (memory[index] << 8 | memory[index + 1] & 0xFF);
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        index = idx(index);
        return (memory[index]     & 0xff) << 16 |
               (memory[index + 1] & 0xff) <<  8 |
                memory[index + 2] & 0xff;
    }

    @Override
    public int getInt(int index) {
        checkIndex(index, 4);
        index = idx(index);
        return (memory[index]     & 0xff) << 24 |
               (memory[index + 1] & 0xff) << 16 |
               (memory[index + 2] & 0xff) <<  8 |
                memory[index + 3] & 0xff;
    }

    @Override
    public long getLong(int index) {
        checkIndex(index, 8);
        index = idx(index);
        return ((long) memory[index]     & 0xff) << 56 |
               ((long) memory[index + 1] & 0xff) << 48 |
               ((long) memory[index + 2] & 0xff) << 40 |
               ((long) memory[index + 3] & 0xff) << 32 |
               ((long) memory[index + 4] & 0xff) << 24 |
               ((long) memory[index + 5] & 0xff) << 16 |
               ((long) memory[index + 6] & 0xff) <<  8 |
                (long) memory[index + 7] & 0xff;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        checkIndex(index, length);
        if (dst.hasArray()) {
            getBytes(index, dst.array(), dst.arrayOffset() + dstIndex, length);
        } else {
            dst.setBytes(dstIndex, memory, idx(index), length);
        }
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        checkIndex(index, length);
        System.arraycopy(memory, idx(index), dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        checkIndex(index);
        dst.put(memory, idx(index), Math.min(capacity() - index, dst.remaining()));
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        checkIndex(index, length);
        out.write(memory, idx(index), length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        checkIndex(index, length);
        index = idx(index);
        return out.write((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length));
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        checkIndex(index);
        memory[idx(index)] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        checkIndex(index, 2);
        index = idx(index);
        memory[index]     = (byte) (value >>> 8);
        memory[index + 1] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setMedium(int index, int   value) {
        checkIndex(index, 3);
        index = idx(index);
        memory[index]     = (byte) (value >>> 16);
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setInt(int index, int   value) {
        checkIndex(index, 4);
        index = idx(index);
        memory[index]     = (byte) (value >>> 24);
        memory[index + 1] = (byte) (value >>> 16);
        memory[index + 2] = (byte) (value >>> 8);
        memory[index + 3] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setLong(int index, long  value) {
        checkIndex(index, 8);
        index = idx(index);
        memory[index]     = (byte) (value >>> 56);
        memory[index + 1] = (byte) (value >>> 48);
        memory[index + 2] = (byte) (value >>> 40);
        memory[index + 3] = (byte) (value >>> 32);
        memory[index + 4] = (byte) (value >>> 24);
        memory[index + 5] = (byte) (value >>> 16);
        memory[index + 6] = (byte) (value >>> 8);
        memory[index + 7] = (byte) value;
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        checkIndex(index, length);
        if (src.hasArray()) {
            setBytes(index, src.array(), src.arrayOffset() + srcIndex, length);
        } else {
            src.getBytes(srcIndex, memory, idx(index), length);
        }
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        checkIndex(index, length);
        System.arraycopy(src, srcIndex, memory, idx(index), length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        int length = src.remaining();
        checkIndex(index, length);
        src.get(memory, idx(index), length);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        checkIndex(index, length);
        return in.read(memory, idx(index), length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        checkIndex(index, length);
        index = idx(index);
        try {
            return in.read((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length));
        } catch (ClosedChannelException e) {
            return -1;
        }
    }

    @Override
    public ByteBuf copy(int index, int length) {
        checkIndex(index, length);
        ByteBuf copy = alloc().heapBuffer(length, maxCapacity());
        copy.writeBytes(memory, idx(index), length);
        return copy();
    }

    @Override
    public boolean hasNioBuffer() {
        return true;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        checkIndex(index, length);
        index = idx(index);
        return ((ByteBuffer) internalNioBuffer().clear().position(index).limit(index + length)).slice();
    }

    @Override
    public boolean hasNioBuffers() {
        return true;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public boolean hasArray() {
        return true;
    }

    @Override
    public byte[] array() {
        return memory;
    }

    @Override
    public int arrayOffset() {
        return offset;
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(byte[] memory) {
        return ByteBuffer.wrap(memory);
    }
}
