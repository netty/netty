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

public final class WriteBufferWaterMark {

    private static final int DEFAULT_WRITEBUFFER_LOW_WATERMARK = 32 * 1024;
    private static final int DEFAULT_WRITEBUFFER_HIGH_WATERMARK = 64 * 1024;

    private final int writeBufferLowWaterMark;
    private final int writeBufferHighWaterMark;

    public WriteBufferWaterMark() {
        this(DEFAULT_WRITEBUFFER_LOW_WATERMARK, DEFAULT_WRITEBUFFER_HIGH_WATERMARK);
    }

    public WriteBufferWaterMark(int writeBufferLowWaterMark, int writeBufferHighWaterMark) {
        super();
        if (writeBufferLowWaterMark < 0) {
            throw new IllegalArgumentException("writeBufferLowWaterMark must be >= 0");
        }
        if (writeBufferHighWaterMark < writeBufferLowWaterMark) {
            throw new IllegalArgumentException("writeBufferHighWaterMark cannot be less than " + "writeBufferLowWaterMark (" + writeBufferLowWaterMark + "): " + writeBufferHighWaterMark);
        }
        this.writeBufferLowWaterMark = writeBufferLowWaterMark;
        this.writeBufferHighWaterMark = writeBufferHighWaterMark;
    }

    public int writeBufferLowWaterMark() {
        return writeBufferLowWaterMark;
    }

    public int writeBufferHighWaterMark() {
        return writeBufferHighWaterMark;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("WriteBufferWaterMark [writeBufferLowWaterMark=");
        builder.append(writeBufferLowWaterMark);
        builder.append(", writeBufferHighWaterMark=");
        builder.append(writeBufferHighWaterMark);
        builder.append("]");
        return builder.toString();
    }

}
