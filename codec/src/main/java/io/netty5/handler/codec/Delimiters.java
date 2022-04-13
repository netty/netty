/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec;

import io.netty5.buffer.api.Buffer;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;

/**
 * A set of commonly used delimiters for {@link DelimiterBasedFrameDecoder}.
 */
public final class Delimiters {

    /**
     * Returns a {@code NUL (0x00)} delimiter, which could be used for
     * Flash XML socket or any similar protocols.
     */
    public static Buffer[] nulDelimiter() {
        return new Buffer[] {
                onHeapAllocator().copyOf(new byte[] { 0 }).makeReadOnly()
        };
    }

    /**
     * Returns {@code CR ('\r')} and {@code LF ('\n')} delimiters, which could
     * be used for text-based line protocols.
     */
    public static Buffer[] lineDelimiter() {
        return new Buffer[] {
                onHeapAllocator().copyOf(new byte[] { '\r', '\n' }).makeReadOnly(),
                onHeapAllocator().copyOf(new byte[] { '\n' }).makeReadOnly(),
        };
    }

    private Delimiters() {
        // Unused
    }
}
