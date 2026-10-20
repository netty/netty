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

/**
 * Intent-named factories for {@link JextractException}, kept out of the exception itself so each
 * throw site reads as the {@link JextractException.Category} it means, {@code buildFailure(...)} for
 * a user-actionable problem in the build's input, {@code executionError(...)} for an environment
 * problem where jextract reached no verdict. Mirrors the {@link ProcessStarter}/{@link ProcessStarters}
 * split in this module: the type, and the factory that produces it, live side by side.
 */
final class JextractExceptions {

    private JextractExceptions() {
    }

    /** A {@link JextractException.Category#BUILD_FAILURE}: a user-actionable problem in the input. */
    static JextractException buildFailure(final String message) {
        return new JextractException(message, JextractException.Category.BUILD_FAILURE);
    }

    /** A {@link JextractException.Category#BUILD_FAILURE} carrying the underlying cause. */
    static JextractException buildFailure(final String message, final Throwable cause) {
        return new JextractException(message, cause, JextractException.Category.BUILD_FAILURE);
    }

    /**
     * An {@link JextractException.Category#EXECUTION_ERROR}: an environment problem where jextract
     * reached no verdict.
     */
    static JextractException executionError(final String message) {
        return new JextractException(message, JextractException.Category.EXECUTION_ERROR);
    }

    /** An {@link JextractException.Category#EXECUTION_ERROR} carrying the underlying cause. */
    static JextractException executionError(final String message, final Throwable cause) {
        return new JextractException(message, cause, JextractException.Category.EXECUTION_ERROR);
    }
}
