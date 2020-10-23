/*
 * Copyright 2017 The Netty Project
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

package io.netty.util;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.SystemPropertyUtil;

import java.util.Locale;

/**
 * A utility class for wrapping calls to {@link Runtime}.
 */
public final class NettyRuntime {

    /**
     * Holder class for available processors to enable testing.
     */
    static class AvailableProcessorsHolder {

        private int availableProcessors;

        /**
         * Set the number of available processors.
         *
         * @param availableProcessors the number of available processors
         * @throws IllegalArgumentException if the specified number of available processors is non-positive
         * @throws IllegalStateException    if the number of available processors is already configured
         */
        synchronized void setAvailableProcessors(final int availableProcessors) {
            ObjectUtil.checkPositive(availableProcessors, "availableProcessors");
            if (this.availableProcessors != 0) {
                final String message = String.format(
                        Locale.ROOT,
                        "availableProcessors is already set to [%d], rejecting [%d]",
                        this.availableProcessors,
                        availableProcessors);
                throw new IllegalStateException(message);
            }
            this.availableProcessors = availableProcessors;
        }

        /**
         * Get the configured number of available processors. The default is {@link Runtime#availableProcessors()}.
         * This can be overridden by setting the system property "io.netty.availableProcessors" or by invoking
         * {@link #setAvailableProcessors(int)} before any calls to this method.
         *
         * @return the configured number of available processors
         */
        @SuppressForbidden(reason = "to obtain default number of available processors")
        synchronized int availableProcessors() {
            if (this.availableProcessors == 0) {
                final int availableProcessors =
                        SystemPropertyUtil.getInt(
                                "io.netty.availableProcessors",
                                Runtime.getRuntime().availableProcessors());
                setAvailableProcessors(availableProcessors);
            }
            return this.availableProcessors;
        }
    }

    private static final AvailableProcessorsHolder holder = new AvailableProcessorsHolder();

    /**
     * Set the number of available processors.
     *
     * @param availableProcessors the number of available processors
     * @throws IllegalArgumentException if the specified number of available processors is non-positive
     * @throws IllegalStateException    if the number of available processors is already configured
     */
    @SuppressWarnings("unused,WeakerAccess") // this method is part of the public API
    public static void setAvailableProcessors(final int availableProcessors) {
        holder.setAvailableProcessors(availableProcessors);
    }

    /**
     * Get the configured number of available processors. The default is {@link Runtime#availableProcessors()}. This
     * can be overridden by setting the system property "io.netty.availableProcessors" or by invoking
     * {@link #setAvailableProcessors(int)} before any calls to this method.
     *
     * @return the configured number of available processors
     */
    public static int availableProcessors() {
        return holder.availableProcessors();
    }

    /**
     * No public constructor to prevent instances from being created.
     */
    private NettyRuntime() {
    }
}
