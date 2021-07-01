/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.h2new;

final class Http2Flags {
    static final short END_STREAM = 0x1;
    static final short END_HEADERS = 0x4;
    static final short ACK = 0x1;
    static final short PADDED = 0x8;
    static final short PRIORITY = 0x20;

    private Http2Flags() {
        // no instances.
    }

    static boolean endOfStream(short flags) {
        return isFlagSet(flags, END_STREAM);
    }

    static boolean endOfHeaders(short flags) {
        return isFlagSet(flags, END_HEADERS);
    }

    static boolean priorityPresent(short flags) {
        return isFlagSet(flags, PRIORITY);
    }

    static boolean isAck(short flags) {
        return isFlagSet(flags, ACK);
    }

    static boolean paddingPresent(short flags) {
        return isFlagSet(flags, PADDED);
    }

    /**
     * Gets the length in bytes of the padding presence field expected in the payload. This is
     * determined by the {@link #paddingPresent(short)} flag.
     */
    static int paddingPresenceFieldLength(short flags) {
        return paddingPresent(flags) ? 1 : 0;
    }

    /**
     * Gets the length in bytes of the padding presence field expected in the payload. This is
     * determined by the {@link #paddingPresent(short)} flag.
     */
    static int numPriorityBytes(short flags) {
        return priorityPresent(flags) ? 5 : 0;
    }

    static short setFlag(short flags, boolean on, short mask) {
        if (on) {
            return flags |= mask;
        } else {
            return flags &= ~mask;
        }
    }

    static boolean isFlagSet(short flags, short mask) {
        return (flags & mask) != 0;
    }
}
