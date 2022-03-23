/*
 * Copyright 2022 The Netty Project
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
package io.netty5.buffer.api.internal;

import io.netty5.buffer.api.ReadableComponent;

import java.nio.ByteBuffer;

/**
 * Safety by-pass that let us get {@link java.nio.ByteBuffer}s from a {@link io.netty5.buffer.api.ReadableComponent}
 * that is not read-only.
 * <p>
 * This is for instance used by the {@code SslHandler}, because some {@link javax.net.ssl.SSLEngine} implementations
 * cannot unwrap or decode packets from read-only buffers.
 */
public interface NotReadOnlyReadableComponent {
    /**
     * Get a {@link ByteBuffer} instance for this memory component.
     * <p>
     * <strong>Note</strong> that unlike the {@link ReadableComponent#readableBuffer()} method, the {@link ByteBuffer}
     * returned here is writable.
     *
     * @return A new {@link ByteBuffer}, with its own position and limit, for this memory component.
     */
    ByteBuffer mutableReadableBuffer();
}
