/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.ssl;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps a native {@code SSL*} pointer to its {@link ReferenceCountedOpenSslEngine} so native OpenSSL callbacks
 * (certificate verification, private-key operations, certificate (de)compression) can recover the engine from the
 * raw pointer they are handed.
 * <p>
 * Engines are held weakly so a leaked engine is not pinned by the long-lived parent
 * {@link ReferenceCountedOpenSslContext} (a live engine is always strongly reachable via its {@link SslHandler}, and
 * on the stack during a callback, so weak retention never collects a usable one). For {@link SslProvider#OPENSSL}
 * this lets {@link OpenSslEngine#finalize()} reclaim the native {@code SSL*} without waiting for the whole context to
 * be collected; a leaked {@link SslProvider#OPENSSL_REFCNT} engine has no finalizer so its native memory still leaks,
 * but it becomes collectable and so is reported by the {@code ResourceLeakDetector} rather than pinned silently.
 * <p>
 * Entries are removed in {@link ReferenceCountedOpenSslEngine#shutdown()}, so the map does not grow in steady state.
 * A cleared {@link WeakReference} lingers only for a leaked {@code OPENSSL_REFCNT} engine (whose {@code SSL*} is never
 * freed, hence never reused); such a husk is tiny, dwarfed by the native memory it marks, and is deliberately left as
 * a heap-inspectable leak signal. Do not reap it (e.g. via a {@code ReferenceQueue}): {@link #get(long)} already
 * yields {@code null} for a cleared reference, so reaping would only erase that signal.
 */
final class OpenSslEngineMap {

    private final Map<Long, WeakReference<ReferenceCountedOpenSslEngine>> engines =
            new ConcurrentHashMap<Long, WeakReference<ReferenceCountedOpenSslEngine>>();

    void add(long ssl, ReferenceCountedOpenSslEngine engine) {
        // A fresh SSL_new() pointer maps to nothing yet: an SSL* is reused only after shutdown() removed its entry
        // (remove-before-freeSSL), and a husk survives only for a never-freed, never-reused SSL*.
        WeakReference<ReferenceCountedOpenSslEngine> prev =
                engines.put(ssl, new WeakReference<ReferenceCountedOpenSslEngine>(engine));
        assert prev == null : "OpenSslEngineMap already had an entry for SSL* 0x" + Long.toHexString(ssl);
    }

    void remove(long ssl) {
        engines.remove(ssl);
    }

    ReferenceCountedOpenSslEngine get(long ssl) {
        WeakReference<ReferenceCountedOpenSslEngine> ref = engines.get(ssl);
        return ref == null ? null : ref.get();
    }
}
