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

/**
 * Utility methods for creating {@link Http2Connection.PropertyKey}s.
 */
public final class Http2ConnectionPropertyKeys {

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
    public static Http2Connection.PropertyKey newKey() {
        return new Http2Connection.PropertyKey() { };
    }
}
