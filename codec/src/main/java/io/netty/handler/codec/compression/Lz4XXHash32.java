/*
 * Copyright 2019 The Netty Project
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
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.nio.ByteBuffer;
import java.util.zip.Checksum;

/**
 * A special-purpose {@link ByteBufChecksum} implementation for use with
 * {@link Lz4FrameEncoder} and {@link Lz4FrameDecoder}.
 *
 * {@link StreamingXXHash32#asChecksum()} has a particularly nasty implementation
 * of {@link Checksum#update(int)} that allocates a single-element byte array for
 * every invocation.
 *
 * In addition to that, it doesn't implement an overload that accepts a {@link ByteBuffer}
 * as an argument.
 *
 * Combined, this means that we can't use {@code ReflectiveByteBufChecksum} at all,
 * and can't use {@code SlowByteBufChecksum} because of its atrocious performance
 * with direct byte buffers (allocating an array and making a JNI call for every byte
 * checksummed might be considered sub-optimal by some).
 *
 * Block version of xxHash32 ({@link XXHash32}), however, does provide
 * {@link XXHash32#hash(ByteBuffer, int)} method that is efficient and does exactly
 * what we need, with a caveat that we can only invoke it once before having to reset.
 * This, however, is fine for our purposes, given the way we use it in
 * {@link Lz4FrameEncoder} and {@link Lz4FrameDecoder}:
 * {@code reset()}, followed by one {@code update()}, followed by {@code getValue()}.
 */
public final class Lz4XXHash32 extends ByteBufChecksum {

    private static final XXHash32 XXHASH32 = XXHashFactory.fastestInstance().hash32();

    private final int seed;
    private boolean used;
    private int value;

    @SuppressWarnings("WeakerAccess")
    public Lz4XXHash32(int seed) {
        this.seed = seed;
    }

    @Override
    public void update(int b) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(byte[] b, int off, int len) {
        if (used) {
            throw new IllegalStateException();
        }
        value = XXHASH32.hash(b, off, len, seed);
        used = true;
    }

    @Override
    public void update(ByteBuf b, int off, int len) {
        if (used) {
            throw new IllegalStateException();
        }
        if (b.hasArray()) {
            value = XXHASH32.hash(b.array(), b.arrayOffset() + off, len, seed);
        } else {
            value = XXHASH32.hash(CompressionUtil.safeNioBuffer(b, off, len), seed);
        }
        used = true;
    }

    @Override
    public long getValue() {
        if (!used) {
            throw new IllegalStateException();
        }
        /*
         * If you look carefully, you'll notice that the most significant nibble
         * is being discarded; we believe this to be a bug, but this is what
         * StreamingXXHash32#asChecksum() implementation of getValue() does,
         * so we have to retain this behaviour for compatibility reasons.
         */
        return value & 0xFFFFFFFL;
    }

    @Override
    public void reset() {
        used = false;
    }
}
