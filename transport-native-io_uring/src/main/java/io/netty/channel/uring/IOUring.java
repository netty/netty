/*
 * Copyright 2020 The Netty Project
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
package io.netty.channel.uring;

import io.netty.channel.unix.FileDescriptor;
import io.netty.util.internal.SystemPropertyUtil;

final class IOUring {

    private static final Throwable UNAVAILABILITY_CAUSE;
    static final int IO_POLL = 6;
    static final int IO_TIMEOUT = 11;
    static final int OP_ACCEPT = 13;
    static final int OP_READ = 22;
    static final int OP_WRITE = 23;
    static final int OP_POLL_REMOVE = 7;

    static final int POLLMASK_LINK = 1;
    static final int POLLMASK_OUT = 4;
    static final int POLLMASK_RDHUP = 8192;

    static {
        Throwable cause = null;

        if (SystemPropertyUtil.getBoolean("io.netty.transport.noNative", false)) {
            cause = new UnsupportedOperationException(
                    "Native transport was explicit disabled with -Dio.netty.transport.noNative=true");
        } else {
            RingBuffer ringBuffer = null;
            try {
                ringBuffer = Native.createRingBuffer();
            } catch (Throwable t) {
                cause = t;
            } finally {
                if (ringBuffer != null) {
                    try {
                        ringBuffer.close();
                    } catch (Exception ignore) {
                        // ignore
                    }
                }
            }
        }

        UNAVAILABILITY_CAUSE = cause;
    }

    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    private IOUring() {
    }
}
