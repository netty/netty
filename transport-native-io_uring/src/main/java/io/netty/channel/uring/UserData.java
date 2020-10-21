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

final class UserData {
    private UserData() {
    }

    static long encode(int fd, byte op, short data) {
        return ((long) data << 48) | ((op & 0xFFL)  << 32) | fd & 0xFFFFFFFFL;
    }

    static void decode(int res, int flags, long udata, IOUringCompletionQueueCallback callback) {
        int fd = (int) (udata & 0xFFFFFFFFL);
        byte op = (byte) ((udata >>>= 32) & 0xFFL);
        short data = (short) (udata >>> 16);
        callback.handle(fd, res, flags, op, data);
    }
}
