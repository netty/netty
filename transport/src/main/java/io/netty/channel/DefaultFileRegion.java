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

import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Default {@link FileRegion} implementation which transfer data from a {@link FileChannel} or {@link File}.
 *
 * Be aware that the {@link FileChannel} will be automatically closed once {@link #refCnt()} returns {@code 0}.
 */
public class DefaultFileRegion extends AbstractFileRegion {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultFileRegion.class);

    private static final AtomicIntegerFieldUpdater<DefaultFileRegion> refCntUpdater;

    static {
        AtomicIntegerFieldUpdater<DefaultFileRegion> updater = PlatformDependent.newAtomicIntegerFieldUpdater(
                DefaultFileRegion.class, "refCnt");
        if (updater == null) {
            updater = AtomicIntegerFieldUpdater.newUpdater(DefaultFileRegion.class, "refCnt");
        }
        refCntUpdater = updater;
    }

    private volatile int refCnt = 1;

    private final File f;
    private FileChannel file;

    /**
     * Create a new instance
     *
     * @param file
     *            the {@link FileChannel} which should be transfered
     * @param position
     *            the position from which the transfer should start
     * @param count
     *            the number of bytes to transfer
     */
    public DefaultFileRegion(FileChannel file, long position, long count) {
        super(position, count);
        if (file == null) {
            throw new NullPointerException("file");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must be >= 0 but was " + position);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0 but was " + count);
        }
        this.file = file;
        f = null;
    }

    /**
     * Create a new instance using the given {@link File}. The {@link File} will be opened lazily or explicitly via
     * {@link #open()}.
     *
     * @param f
     *            the {@link File} which should be transfered
     * @param position
     *            the position from which the transfer should start
     * @param count
     *            the number of bytes to transfer
     */
    public DefaultFileRegion(File f, long position, long count) {
        super(position, count);
        if (f == null) {
            throw new NullPointerException("f");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position must be >= 0 but was " + position);
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0 but was " + count);
        }
        this.f = f;
    }

    @Override
    public int refCnt() {
        return refCnt;
    }

    @Override
    public DefaultFileRegion retain() {
        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, 1);
            }
            if (refCnt == Integer.MAX_VALUE) {
                throw new IllegalReferenceCountException(Integer.MAX_VALUE, 1);
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + 1)) {
                break;
            }
        }
        return this;
    }

    @Override
    public DefaultFileRegion retain(int increment) {
        if (increment <= 0) {
            throw new IllegalArgumentException("increment: " + increment + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, 1);
            }
            if (refCnt > Integer.MAX_VALUE - increment) {
                throw new IllegalReferenceCountException(refCnt, increment);
            }
            if (refCntUpdater.compareAndSet(this, refCnt, refCnt + increment)) {
                break;
            }
        }
        return this;
    }

    @Override
    public DefaultFileRegion touch() {
        return this;
    }

    @Override
    public DefaultFileRegion touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt == 0) {
                throw new IllegalReferenceCountException(0, -1);
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - 1)) {
                if (refCnt == 1) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }

    @Override
    public boolean release(int decrement) {
        if (decrement <= 0) {
            throw new IllegalArgumentException("decrement: " + decrement + " (expected: > 0)");
        }

        for (;;) {
            int refCnt = this.refCnt;
            if (refCnt < decrement) {
                throw new IllegalReferenceCountException(refCnt, -decrement);
            }

            if (refCntUpdater.compareAndSet(this, refCnt, refCnt - decrement)) {
                if (refCnt == decrement) {
                    deallocate();
                    return true;
                }
                return false;
            }
        }
    }

    private void deallocate() {
        FileChannel file = this.file;

        if (file == null) {
            return;
        }
        this.file = null;

        try {
            file.close();
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a file.", e);
            }
        }
    }

    /**
     * Returns {@code true} if the {@link FileRegion} has a open file-descriptor
     */
    public boolean isOpen() {
        return file != null;
    }

    /**
     * Explicitly open the underlying file-descriptor if not done yet.
     */
    public void open() throws IOException {
        if (!isOpen() && refCnt() > 0) {
            // Only open if this DefaultFileRegion was not released yet.
            file = new RandomAccessFile(f, "r").getChannel();
        }
    }

    @Override
    public FileChannel channel() throws IOException {
        // Call open to make sure fc is initialized. This is a no-op if we called it before.
        open();
        return file;
    }

    @Override
    public FileRegion unwrap() {
        return null;
    }
}
