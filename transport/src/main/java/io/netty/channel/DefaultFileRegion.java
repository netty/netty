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

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Default {@link FileRegion} implementation which transfer data from a {@link FileChannel}.
 *
 * Be aware that the {@link FileChannel} will be automatically closed once {@link #refCnt()} returns
 * {@code 0}.
 */
public class DefaultFileRegion extends AbstractReferenceCounted implements FileRegion {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultFileRegion.class);

    private final FileChannel file;
    private final long position;
    private final long count;
    private long transfered;

    /**
     * Create a new instance
     *
     * @param file      the {@link FileChannel} which should be transfered
     * @param position  the position from which the transfer should start
     * @param count     the number of bytes to transfer
     */
    public DefaultFileRegion(FileChannel file, long position, long count) {
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
        this.position = position;
        this.count = count;
    }

    @Override
    public long position() {
        return position;
    }

    @Override
    public long count() {
        return count;
    }

    @Override
    public long transfered() {
        return transfered;
    }

    @Override
    public long transferTo(WritableByteChannel target, long position) throws IOException {
        long count = this.count - position;
        if (count < 0 || position < 0) {
            throw new IllegalArgumentException(
                    "position out of range: " + position +
                    " (expected: 0 - " + (this.count - 1) + ')');
        }
        if (count == 0) {
            return 0L;
        }

        long written = file.transferTo(this.position + position, count, target);
        if (written > 0) {
            transfered += written;
        }
        return written;
    }

    @Override
    protected void deallocate() {
        try {
            file.close();
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a file.", e);
            }
        }
    }

    @Override
    public FileRegion retain() {
        super.retain();
        return this;
    }

    @Override
    public FileRegion retain(int increment) {
        super.retain(increment);
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
}
