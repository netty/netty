/*
 * Copyright 2016 The Netty Project
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

/**
 * WriteBufferWaterMark is used to set low water mark (default value is
 * {@value #DEFAULT_WRITEBUFFER_LOW_WATERMARK} bytes) and high water
 * mark (default value is {@value #DEFAULT_WRITEBUFFER_HIGH_WATERMARK} bytes)
 * for write buffer.
 * <p>
 * If the number of bytes queued in the write buffer exceeds the
 * {@linkplain #writeBufferHighWaterMark high water mark}, {@link Channel#isWritable()}
 * will start to return {@code false}.
 * <p>
 * If the number of bytes queued in the write buffer exceeds the
 * {@linkplain #writeBufferHighWaterMark high water mark} and then
 * dropped down below the {@linkplain #writeBufferLowWaterMark low water mark}, 
 * {@link Channel#isWritable()} will start to return
 * {@code true} again.
 */
public final class WriteBufferWaterMark {

    private static final int DEFAULT_WRITEBUFFER_LOW_WATERMARK = 32 * 1024;
    private static final int DEFAULT_WRITEBUFFER_HIGH_WATERMARK = 64 * 1024;

    private final int writeBufferLowWaterMark;
    private final int writeBufferHighWaterMark;

    public WriteBufferWaterMark() {
        this(DEFAULT_WRITEBUFFER_LOW_WATERMARK, DEFAULT_WRITEBUFFER_HIGH_WATERMARK);
    }

    public WriteBufferWaterMark(int writeBufferLowWaterMark, int writeBufferHighWaterMark) {
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException("writeBufferLowWaterMark must be >= 0");
        }
        if (writeBufferHighWaterMark < writeBufferLowWaterMark) {
            throw new IllegalArgumentException(
                    "writeBufferHighWaterMark cannot be less than " +
                            "writeBufferLowWaterMark (" + writeBufferLowWaterMark + "): " +
                            writeBufferHighWaterMark);
        }
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
    }

    public int lowWaterMark() {
        return writeBufferLowWaterMark;
    }

    public int highWaterMark() {
        return writeBufferHighWaterMark;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(76);
        builder.append("WriteBufferWaterMark [low water mark=");
        builder.append(writeBufferLowWaterMark);
        builder.append(", high water mark=");
        builder.append(writeBufferHighWaterMark);
        builder.append("]");
        return builder.toString();
    }

}
