/*
 * Copyright 2015 The Netty Project
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
package io.netty.buffer;

import io.netty.util.IllegalReferenceCountException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.GatheringByteChannel;

/**
 * A skeletal implementation of a {@link ReadableObject}.
 */
public abstract class AbstractReadableObject implements ReadableObject {

    private long readerPosition;

    protected AbstractReadableObject(long readerPosition) {
        this.readerPosition = readerPosition;
    }

    @Override
    public long objectReaderPosition() {
        return readerPosition;
    }

    @Override
    public AbstractReadableObject objectReaderPosition(long readerPosition) {
        if (readerPosition < 0 || readerPosition > objectReaderLimit()) {
            throw new IndexOutOfBoundsException("readerPosition " + readerPosition);
        }
        this.readerPosition = readerPosition;
        return this;
    }

    @Override
    public boolean isObjectReadable() {
        return objectReadableBytes() > 0;
    }

    @Override
    public boolean isObjectReadable(long size) {
        return objectReadableBytes() >= size;
    }

    @Override
    public long objectReadableBytes() {
        return objectReaderLimit() - readerPosition;
    }

    @Override
    public ReadableObject sliceObject() {
        return sliceObject(readerPosition, objectReadableBytes());
    }

    @Override
    public ReadableObject skipObjectBytes(long length) {
        if (length < 0 || length > objectReadableBytes()) {
            throw new IllegalArgumentException("length must be within readable region: " + length);
        }
        readerPosition += length;
        return this;
    }

    @Override
    public ReadableObject sliceObject(long position, long length) {
        if (length < 0 || objectReaderLimit() - length < position) {
            throw new IllegalArgumentException("Slice must be within readable region");
        }
        return new SlicedReadableObject(this, position, length);
    }

    @Override
    public ReadableObject readObjectSlice(long length) {
        ReadableObject slice = sliceObject(readerPosition, length);
        readerPosition += length;
        return slice;
    }

    private void getCheck(long pos, long length) {
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }
        if (pos < 0 || length < 0 || objectReaderLimit() - length < pos) {
            throw new IndexOutOfBoundsException(String.format("pos: %d, length: %d", pos, length));
        }
    }

    @Override
    public long getObjectBytes(GatheringByteChannel out, long pos, long length) throws IOException {
        getCheck(pos, length);
        return length != 0 ? getObjectBytes0(out, pos, length) : 0L;
    }

    @Override
    public long readObjectBytes(GatheringByteChannel out, long length) throws IOException {
        long readBytes = getObjectBytes(out, readerPosition, length);
        readerPosition += readBytes;
        return readBytes;
    }

    @Override
    public ReadableObject getObjectBytes(OutputStream out, long pos, long length) throws IOException {
        getCheck(pos, length);
        if (length != 0) {
            getObjectBytes0(out, pos, length);
        }
        return this;
    }

    @Override
    public ReadableObject readObjectBytes(OutputStream out, long length) throws IOException {
        getObjectBytes(out, readerPosition, length);
        readerPosition += length;
        return this;
    }

    @Override
    public long readObjectBytes(SingleReadableObjectWriter out, long length) throws IOException {
        long readBytes = getObjectBytes(out, readerPosition, length);
        readerPosition += readBytes;
        return readBytes;
    }

    @Override
    public long getObjectBytes(SingleReadableObjectWriter out, long pos, long length) throws IOException {
        getCheck(pos, length);
        return length != 0 ? getObjectBytes0(out, pos, length) : 0L;
    }

    protected abstract long getObjectBytes0(GatheringByteChannel out, long pos, long length) throws IOException;

    protected abstract ReadableObject getObjectBytes0(OutputStream out, long pos, long length) throws IOException;

    protected abstract long getObjectBytes0(SingleReadableObjectWriter out, long pos, long length) throws IOException;

}
