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
package io.netty.handler.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.internal.ObjectUtil;

/**
 * A skeletal {@link CompressionEncoder} implementation.
 */
public abstract class AbstractCompressionEncoder extends MessageToByteEncoder<ByteBuf> implements CompressionEncoder {

    private final CompressionFormat format;

    /**
     * Default constructor for {@link AbstractCompressionEncoder} which will try to use a direct
     * {@link ByteBuf} as a target for the encoded messages.
     */
    protected AbstractCompressionEncoder(CompressionFormat format) {
        this(format, true);
    }

    /**
     * Creates an instance with specified preference of a target {@link ByteBuf} for the encoded messages.
     * It uses if compression algorithm needs a byte array as a target.
     *
     * @param preferDirect {@code true} if a direct {@link ByteBuf} should be tried to be used as a target
     *                     for the encoded messages. If {@code false} is used it will allocate a heap
     *                     {@link ByteBuf}, which is backed by an byte array.
     */
    protected AbstractCompressionEncoder(CompressionFormat format, boolean preferDirect) {
        super(preferDirect);
        this.format = ObjectUtil.checkNotNull(format, "format");
    }

    @Override
    public final CompressionFormat format() {
        return format;
    }
}
