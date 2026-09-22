/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility methods for creating {@link Http2Connection.PropertyKey}s.
 */
final class Http2ConnectionPropertyKeys {

    /**
     * Safe-guard against misuse: global keys are expected to be created rarely, typically once per use-case and
     * stored in a {@code static} field, so there should never be a legitimate need for more than this many.
     */
    private static final int MAX_GLOBAL_KEYS = 8;

    private static final AtomicInteger nextIndex = new AtomicInteger();

    private Http2ConnectionPropertyKeys() { }

    /**
     * Creates a new key that, unlike one returned by {@link Http2Connection#newKey()}, is not tied to a specific
     * {@link Http2Connection} instance. The returned key can be used with the streams of any {@link Http2Connection},
     * which makes it suitable for cases where a key needs to be shared, e.g. stored in a {@code static} field,
     * across multiple connections, or created before a {@link Http2Connection} instance is even available.
     * <p>
     * Accessing a property that is associated with a key returned by this method is slower than accessing one
     * associated with a key returned by {@link Http2Connection#newKey()}. Prefer the latter whenever a specific
     * {@link Http2Connection} instance is available up-front.
     */
    static Http2Connection.PropertyKey newGlobalKey() {
        int index = nextIndex.getAndIncrement();
        if (index >= MAX_GLOBAL_KEYS) {
            nextIndex.decrementAndGet();
            throw new IllegalStateException("Only " + MAX_GLOBAL_KEYS + " global keys can be created");
        }
        return new GlobalPropertyKey(index);
    }

    /**
     * The number of keys created via {@link #newGlobalKey()} so far, which also doubles as the exclusive upper
     * bound for {@link GlobalPropertyKey#index}.
     */
    static int keyCount() {
        return nextIndex.get();
    }

    /**
     * Implementation of {@link Http2Connection.PropertyKey} that specifies the index position of the property and,
     * unlike {@link DefaultHttp2Connection.DefaultPropertyKey}, is not tied to a specific {@link Http2Connection}.
     */
    static final class GlobalPropertyKey implements Http2Connection.PropertyKey {
        final int index;

        private GlobalPropertyKey(int index) {
            this.index = index;
        }
    }
}
