/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.compression;

import io.netty5.buffer.api.Buffer;

import java.nio.ByteBuffer;
import java.util.zip.Checksum;

import static java.util.Objects.requireNonNull;

/**
 * {@link Checksum} implementation which can directly act on a {@link Buffer}.
 */
class ByteBufChecksum implements Checksum {

    private final Checksum checksum;

    ByteBufChecksum(Checksum checksum) {
        this.checksum = requireNonNull(checksum);
    }

    @Override
    public void update(int b) {
        checksum.update(b);
    }

    @Override
    public void update(byte[] b, int off, int len) {
        checksum.update(b, off, len);
    }

    @Override
    public long getValue() {
        return checksum.getValue();
    }

    @Override
    public void reset() {
        checksum.reset();
    }

    /**
     * @see #update(byte[], int, int)
     */
    public void update(Buffer b, int off, int len) {
        int readerOffset = b.readerOffset();
        b.readerOffset(off);
        try {
            try (var iteration = b.forEachReadable()) {
                for (var c = iteration.first(); c != null; c = c.next()) {
                    ByteBuffer componentBuffer = c.readableBuffer();
                    if (componentBuffer.remaining() > len) {
                        componentBuffer.limit(componentBuffer.position() + len);
                        update(componentBuffer);
                        break;
                    } else {
                        len -= componentBuffer.remaining();
                        update(componentBuffer);
                    }
                }
            }
        } finally {
            b.readerOffset(readerOffset);
        }
    }
}
