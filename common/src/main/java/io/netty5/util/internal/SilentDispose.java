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
package io.netty5.util.internal;

import io.netty5.util.ReferenceCounted;
import io.netty5.util.Resource;
import io.netty5.util.internal.logging.InternalLogger;

/**
 * Utility class for disposing of {@linkplain Resource resources} without propagating any exception that
 * {@link Resource#close()} might throw.
 */
public final class SilentDispose {
    /**
     * Attempt to dispose of whatever the given object is.
     * <p>
     * This method works similarly to {@link Resource#dispose(Object)}, except any exception thrown will be logged
     * instead of propagated.
     * <p>
     * If the object is {@link AutoCloseable}, such as anything that implements {@link Resource},
     * then it will be closed.
     * If the object is {@link ReferenceCounted}, then it will be released once.
     * <p>
     * Any exceptions caused by this will be logged using the given logger.
     * The exception will be logged at log-level {@link io.netty5.util.internal.logging.InternalLogLevel#WARN WARN}.
     *
     * @param obj The object to dispose of.
     * @param logger The logger to use for recording any exceptions thrown by the disposal.
     */
    public static void dispose(Object obj, InternalLogger logger) {
        try {
            Resource.dispose(obj);
        } catch (Throwable throwable) {
            logger.warn("Failed to dispose object: {}.", obj, throwable);
        }
    }

    private SilentDispose() {
    }
}
