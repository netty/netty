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

import io.netty.buffer.PooledByteBufAllocator.Arena;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

public class PooledHeapByteBuf extends PooledByteBuf<byte[]> {
    public PooledHeapByteBuf(Arena<byte[]> arena, int maxCapacity) {
        super(arena, maxCapacity);
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
        return (short) (memory[idx(index)] << 8 | memory[idx(index + 1)] & 0xFF);
    }

    @Override
    public int getUnsignedMedium(int index) {
        checkIndex(index, 3);
        return (memory[idx(index)]     & 0xff) << 16 |
               (memory[idx(index + 1)] & 0xff) <<  8 |
                memory[idx(index + 2)] & 0xff;
    }

    @Override
    public int getInt(int index) {
        return 0;
    }

    @Override
    public long getLong(int index) {
        return 0;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        return null;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        return null;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return 0;
    }

    @Override
    public ByteBuf setByte(int index, int value) {
        return null;
    }

    @Override
    public ByteBuf setShort(int index, int value) {
        return null;
    }

    @Override
    public ByteBuf setMedium(int index, int value) {
        return null;
    }

    @Override
    public ByteBuf setInt(int index, int value) {
        return null;
    }

    @Override
    public ByteBuf setLong(int index, long value) {
        return null;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        return null;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        return null;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return 0;
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return 0;
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return null;
    }

    @Override
    public boolean hasNioBuffer() {
        return false;
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return null;
    }

    @Override
    public boolean hasNioBuffers() {
        return false;
    }

    @Override
    public ByteBuffer[] nioBuffers(int offset, int length) {
        return new ByteBuffer[0];
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public byte[] array() {
        return new byte[0];
    }

    @Override
    public int arrayOffset() {
        return 0;
    }

    @Override
    public ByteBuffer internalNioBuffer() {
        return null;
    }

    @Override
    public ByteBuffer[] internalNioBuffers() {
        return new ByteBuffer[0];
    }

    private int idx(int index) {
        return offset + index;
    }

    private void checkIndex(int index) {
        assert !isFreed();
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d (expected: 0-%d)", index, length - 1));
        }
    }

    private void checkIndex(int index, int fieldLength) {
        assert !isFreed();
        if (index < 0 || index > length - fieldLength) {
            throw new IndexOutOfBoundsException(String.format(
                    "index: %d (expected: 0-%d)", index, length - fieldLength));
        }
    }
}