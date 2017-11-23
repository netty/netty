/*
 * Copyright 2017 The Netty Project
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
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * {@link FileRegionStream} implementations which provides a stream of {@link FileRegion}s
 * backed by a {@link FileChannel}.
 */
@UnstableApi
public final class DefaultFileRegionStream extends AbstractReferenceCounted implements FileRegionStream {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultFileRegionStream.class);

    private final FileChannel file;
    private final long endPosition;
    private long position;

    /**
     * Create a new instance
     *
     * @param file              the {@link FileChannel} which should be transfered
     * @param position          the position from which the transfer should start
     * @param count             the number of bytes to transfer
     */
    public DefaultFileRegionStream(FileChannel file, long position, long count) {
        this.file = checkNotNull(file, "file");
        this.position = checkPositiveOrZero(position, "position");
        endPosition = position + checkPositiveOrZero(count, "count");
    }

    @Override
    public FileRegion read(long bytes) {
        if (refCnt() == 0) {
            throw new IllegalReferenceCountException(0);
        }

        if (position + bytes > endPosition) {
            throw new IllegalArgumentException();
        }
        FileRegion region = new DefaultFileRegion(file, position, bytes, false);
        position += bytes;
        return region;
    }

    @Override
    public long readableBytes() {
        return endPosition - position;
    }

    @Override
    protected void deallocate() {
        try {
            file.close();
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close FileChannel.", e);
            }
        }
    }

    @Override
    public DefaultFileRegionStream touch(Object hint) {
        return this;
    }

    @Override
    public DefaultFileRegionStream retain() {
        super.retain();
        return this;
    }

    @Override
    public DefaultFileRegionStream retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public DefaultFileRegionStream touch() {
        super.touch();
        return this;
    }
}
