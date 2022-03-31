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
package io.netty5.handler.ssl;

import io.netty5.buffer.api.Buffer;

/**
 * A marker interface for PEM encoded values.
 */
interface PemEncoded extends AutoCloseable {
    /**
     * Returns the {@link Buffer} with the PEM encoded contents of this value.
     * <p>
     * <strong>Note:</strong> The returned buffer should not be closed directly.
     * Instead, {@link #close()} should be called on this PEM encoded value instead.
     *
     * @return The underlying {@link Buffer} with the contents of this PEM encoded value.
     */
    Buffer content();

    /**
     * Return a copy of this PEM encoded value.
     *
     * @return A new identical copy of this PEM encoded value.
     */
    PemEncoded copy();

    /**
     * Close this PEM encoded value and free its related resources.
     */
    @Override
    void close();
}
