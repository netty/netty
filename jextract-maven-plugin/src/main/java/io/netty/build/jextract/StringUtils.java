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
package io.netty.build.jextract;

import org.jetbrains.annotations.Nullable;

/**
 * Small string helpers shared across the plugin so every class agrees on what "blank" means.
 * Having one definition avoids the trap of a header rejected as blank in one class but accepted as
 * a whitespace-only value in another.
 */
final class StringUtils {

    private StringUtils() {
    }

    /**
     * A value is blank when it is {@code null} or contains only whitespace. This is the single
     * notion of "empty" used everywhere in the plugin, for required-parameter validation, for
     * "source not configured" checks, and for rejecting empty {@code --include-*} symbols.
     */
    static boolean isBlank(@Nullable final String value) {
        return value == null || value.isBlank();
    }
}
