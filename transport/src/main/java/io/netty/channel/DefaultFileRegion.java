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
package io.netty.channel;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.AbstractReadableObject;
import io.netty.buffer.ReadableObject;
import io.netty.buffer.SlicedReadableObject;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Default {@link FileRegion} implementation which transfer data from a {@link FileChannel} or {@link File}.
 *
 * Be aware that the {@link FileChannel} will be automatically closed once {@link #refCnt()} returns
 * {@code 0}.
 */
public class DefaultFileRegion extends AbstractReadableObject implements FileRegion {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultFileRegion.class);

    private File file;
    private FileChannel fileChannel;
    private long offset;
    private long readerLimit;
    private final AbstractReferenceCounted refCnt = new AbstractReferenceCounted() {
        @Override
        protected void deallocate() {
            FileChannel fileChannel = DefaultFileRegion.this.fileChannel;

            if (fileChannel == null) {
                return;
            }
            DefaultFileRegion.this.fileChannel = null;

            try {
                fileChannel.close();
            } catch (IOException e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Failed to close a file.", e);
                }
            }
        }

        @Override
        public ReferenceCounted touch(Object hint) {
            return this;
        }
    };

    /**
     * Create a new instance
     *
     * @param fileChannel      the {@link FileChannel} which should be transfered
     * @param position  the position from which the transfer should start
     * @param length     the number of bytes to transfer
     */
    public DefaultFileRegion(FileChannel fileChannel, long position, long length) {
        super(position);
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0 but was " + length);
        }
        if (Long.MAX_VALUE - length < readerPosition()) {
            throw new IllegalArgumentException("Overflow calculating end position");
        }

        this.fileChannel = checkNotNull(fileChannel, "fileChannel");
        file = null;
        readerLimit = position + length;
    }

    /**
     * Create a new instance using the given {@link File}. The {@link File} will be opened lazily or
     * explicitly via {@link #open()}.
     *
     * @param file         the {@link File} which should be transfered
     * @param position  the position from which the transfer should start
     * @param length     the number of bytes to transfer
     */
    public DefaultFileRegion(File file, long position, long length) {
        super(position);
        if (length < 0) {
            throw new IllegalArgumentException("length must be >= 0 but was " + length);
        }
        if (Long.MAX_VALUE - length < readerPosition()) {
            throw new IllegalArgumentException("Overflow calculating end position");
        }

        this.file = checkNotNull(file, "file");
        readerLimit = position + length;
    }

    @Override
    public FileRegion readerPosition(long readerPosition) {
        super.readerPosition(readerPosition);
        return this;
    }

    @Override
    public long readerLimit() {
        return readerLimit;
    }

    /**
     * Returns {@code true} if the {@link FileRegion} has a open file-descriptor
     */
    public boolean isOpen() {
        return fileChannel != null;
    }

    /**
     * Explicitly open the underlying file-descriptor if not done yet.
     */
    public void open() throws IOException {
        if (!isOpen() && refCnt() > 0) {
            // Only open if this DefaultFileRegion was not released yet.
            fileChannel = new RandomAccessFile(file, "r").getChannel();
        }
    }

    @Override
    public FileChannel channel() throws IOException {
        // Call open to make sure fc is initialized. This is a no-op if we called it before.
        open();
        return fileChannel;
    }

    @Override
    public FileRegion skipBytes(long length) {
        super.skipBytes(length);
        return this;
    }

    @Override
    public ReadableObject slice(long position, long length) {
        if (length < 0 || position < readerPosition() || readerLimit - length < position) {
            throw new IllegalArgumentException("Slice must be within readable region");
        }
        return new SlicedReadableObject(this, position, length);
    }

    @Override
    protected long readTo0(WritableByteChannel target, long pos, long length) throws IOException {
        long written = channel().transferTo(pos + offset, length, target);
        if (written > 0) {
            readerPosition(readerPosition() + written);
        }
        return written;
    }

    @Override
    public FileRegion discardReadBytes() {
        offset += readerPosition();
        readerPosition(0);
        readerLimit -= offset;
        return this;
    }

    /**
     * Does nothing.
     */
    @Override
    public FileRegion discardSomeReadBytes() {
        return this;
    }

    @Override
    public FileRegion unwrap() {
        return null;
    }

    @Override
    public FileRegion retain() {
        super.retain();
        return this;
    }

    @Override
    public FileRegion retain(int increment) {
        refCnt.retain(increment);
        return this;
    }

    @Override
    public FileRegion touch() {
        super.touch();
        return this;
    }

    @Override
    public FileRegion touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public int refCnt() {
        return refCnt.refCnt();
    }

    @Override
    protected boolean release0(int decrement) {
        return refCnt.release(decrement);
    }
}
