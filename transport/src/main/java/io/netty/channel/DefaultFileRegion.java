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

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * Default {@link FileRegion} implementation which transfer data from a {@link FileChannel} or {@link File}.
 *
 * Be aware that the {@link FileChannel} will be automatically closed once {@link #refCnt()} returns
 * {@code 0}.
 */
public class DefaultFileRegion extends AbstractFileRegion {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultFileRegion.class);

    private File f;
    private FileChannel file;
    private final AbstractReferenceCounted refCnt = new AbstractReferenceCounted() {
        @Override
        protected void deallocate() {
            FileChannel file = DefaultFileRegion.this.file;

            if (file == null) {
                return;
            }
            DefaultFileRegion.this.file = null;

            try {
                file.close();
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
     * @param file      the {@link FileChannel} which should be transfered
     * @param position  the position from which the transfer should start
     * @param length     the number of bytes to transfer
     */
    public DefaultFileRegion(FileChannel file, long position, long length) {
        super(position, length);
        this.file = checkNotNull(file, "file");
        f = null;
    }

    /**
     * Create a new instance using the given {@link File}. The {@link File} will be opened lazily or
     * explicitly via {@link #open()}.
     *
     * @param f         the {@link File} which should be transfered
     * @param position  the position from which the transfer should start
     * @param length     the number of bytes to transfer
     */
    public DefaultFileRegion(File f, long position, long length) {
        super(position, length);
        this.f = checkNotNull(f, "f");
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

    @Override
    public FileRegion retain() {
        refCnt.retain();
        return this;
    }

    @Override
    public FileRegion retain(int increment) {
        refCnt.retain(increment);
        return this;
    }

    @Override
    public FileRegion touch() {
        return this;
    }

    @Override
    public FileRegion touch(Object hint) {
        return this;
    }

    @Override
    public int refCnt() {
        return refCnt.refCnt();
    }

    @Override
    public boolean release() {
        return refCnt.release();
    }

    @Override
    public boolean release(int decrement) {
        return refCnt.release(decrement);
    }
}
