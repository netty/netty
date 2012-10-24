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
package io.netty.handler.fileregion;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

public class DefaultFileRegion implements FileRegion {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultFileRegion.class);

    private final FileChannel file;
    private final long position;
    private final long count;

    public DefaultFileRegion(FileChannel file, long position, long count) {
        this.file = file;
        this.position = position;
        this.count = count;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public long getCount() {
        return count;
    }

    @Override
    public long transferTo(WritableByteChannel target, long position) throws IOException {
        long count = this.count - position;
        if (count < 0 || position < 0) {
            throw new IllegalArgumentException(
                    "position out of range: " + position +
                    " (expected: 0 - " + (this.count - 1) + ")");
        }
        if (count == 0) {
            return 0L;
        }

        return file.transferTo(this.position + position, count, target);
    }

    @Override
    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a file.", e);
            }
        }
    }
}
