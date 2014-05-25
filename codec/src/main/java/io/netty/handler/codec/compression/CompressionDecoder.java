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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.internal.ObjectUtil;

/**
 * Decompresses a {@link ByteBuf} using one of compression algorithms.
 */
public abstract class CompressionDecoder extends ByteToMessageDecoder {

    private final CompressionFormat format;

    protected CompressionDecoder(CompressionFormat format) {
        ObjectUtil.checkNotNull(format, "format");
        this.format = format;
    }

    /**
     * Returns a format of current compression algorithm.
     */
    public final CompressionFormat format() {
        return format;
    }

    /**
     * Returns {@code true} if and only if the end of the compressed stream has been reached.
     */
    public abstract boolean isClosed();
}
