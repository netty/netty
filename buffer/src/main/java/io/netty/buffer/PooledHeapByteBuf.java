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

import io.netty.util.Recycler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;

final class PooledHeapByteBuf extends PooledByteBuf<byte[]> {

    private static final Recycler<PooledHeapByteBuf> RECYCLER = new Recycler<PooledHeapByteBuf>() {
        @Override
        protected PooledHeapByteBuf newObject(Handle handle) {
            return new PooledHeapByteBuf(handle, 0);
        }
    };

    static PooledHeapByteBuf newInstance(int maxCapacity) {
        PooledHeapByteBuf buf = RECYCLER.get();
        buf.setRefCnt(1);
        buf.maxCapacity(maxCapacity);
        return buf;
    }

    private PooledHeapByteBuf(Recycler.Handle recyclerHandle, int maxCapacity) {
        super(recyclerHandle, maxCapacity);
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    protected byte _getByte(int index) {
        return HeapByteBufUtil._getByte(memory, idx(index));
    }

    @Override
    protected short _getShort(int index) {
        return HeapByteBufUtil._getShort(memory, idx(index));
    }

    @Override
    protected int _getUnsignedMedium(int index) {
        return HeapByteBufUtil._getUnsignedMedium(memory, idx(index));
    }

    @Override
    protected int _getInt(int index) {
        return HeapByteBufUtil._getInt(memory, idx(index));
    }

    @Override
    protected long _getLong(int index) {
        return HeapByteBufUtil._getLong(memory, idx(index));
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuf dst, int dstIndex, int length) {
        HeapByteBufUtil.getBytes(this, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, byte[] dst, int dstIndex, int length) {
        HeapByteBufUtil.getBytes(this, index, dst, dstIndex, length);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, ByteBuffer dst) {
        HeapByteBufUtil.getBytes(this, index, dst);
        return this;
    }

    @Override
    public ByteBuf getBytes(int index, OutputStream out, int length) throws IOException {
        HeapByteBufUtil.getBytes(this, index, out, length);
        return this;
    }

    @Override
    public int getBytes(int index, GatheringByteChannel out, int length) throws IOException {
        return HeapByteBufUtil.getBytes(this, index, out, length);
    }

    @Override
    public int readBytes(GatheringByteChannel out, int length) throws IOException {
        return HeapByteBufUtil.readBytes(this, out, length);
    }

    @Override
    protected void _setByte(int index, int value) {
        HeapByteBufUtil._setByte(memory, idx(index), value);
    }

    @Override
    protected void _setShort(int index, int value) {
        HeapByteBufUtil._setShort(memory, idx(index), value);
    }

    @Override
    protected void _setMedium(int index, int   value) {
        HeapByteBufUtil._setMedium(memory, idx(index), value);
    }

    @Override
    protected void _setInt(int index, int   value) {
        HeapByteBufUtil._setInt(memory, idx(index), value);
    }

    @Override
    protected void _setLong(int index, long  value) {
        HeapByteBufUtil._setLong(memory, idx(index), value);
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuf src, int srcIndex, int length) {
        HeapByteBufUtil.setBytes(this, index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, byte[] src, int srcIndex, int length) {
        HeapByteBufUtil.setBytes(this, index, src, srcIndex, length);
        return this;
    }

    @Override
    public ByteBuf setBytes(int index, ByteBuffer src) {
        HeapByteBufUtil.setBytes(this, index, src);
        return this;
    }

    @Override
    public int setBytes(int index, InputStream in, int length) throws IOException {
        return HeapByteBufUtil.setBytes(this, index, in, length);
    }

    @Override
    public int setBytes(int index, ScatteringByteChannel in, int length) throws IOException {
        return HeapByteBufUtil.setBytes(this, index, in, length);
    }

    @Override
    public ByteBuf copy(int index, int length) {
        return HeapByteBufUtil.copy(this, index, length);
    }

    @Override
    public int nioBufferCount() {
        return 1;
    }

    @Override
    public ByteBuffer[] nioBuffers(int index, int length) {
        return new ByteBuffer[] { nioBuffer(index, length) };
    }

    @Override
    public ByteBuffer nioBuffer(int index, int length) {
        return HeapByteBufUtil.nioBuffer(this, index, length);
    }

    @Override
    public ByteBuffer internalNioBuffer(int index, int length) {
        return HeapByteBufUtil.internalNioBuffer(this, index, length);
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
    public boolean hasMemoryAddress() {
        return false;
    }

    @Override
    public long memoryAddress() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected ByteBuffer newInternalNioBuffer(byte[] memory) {
        return ByteBuffer.wrap(memory);
    }

    @Override
    protected Recycler<?> recycler() {
        return RECYCLER;
    }
}
