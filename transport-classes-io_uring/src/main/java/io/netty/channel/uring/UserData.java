/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

final class UserData {
    private UserData() {
    }

    /**
     * Encode the given data into a long that can be stored as udata.
     * layout
     *  63             32 31      24 23      16 15       0
     * [     id        ][ unused ][   op    ][   data    ]
     * @param id        the id.
     * @param op        the operation
     * @param data      the custom data
     * @return          the udata.
     */
    static long encode(int id, byte op, short data) {
        return ((long) id << Integer.SIZE) | ((op & 0xFFL) << Short.SIZE) | (data & 0xFFFFL);
    }

    static int decodeId(long udata) {
        return (int) (udata >>> Integer.SIZE);
    }

    static byte decodeOp(long udata) {
        return (byte) (udata >>> Short.SIZE);
    }

    static short decodeData(long udata) {
        return (short) udata;
    }
}
