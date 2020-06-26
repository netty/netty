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

public final class Native {
    public static native long io_uring_setup(int entries);

    public static native long getSQE(long io_uring);

    public static native long getQC(long io_uring);

    public static native int read(long io_uring, long fd, long eventId, long bufferAddress, int pos,
                                            int limit);

    public static native int write(long io_uring, long fd, long eventId, long bufferAddress, int pos,
                                             int limit);

    public static native int accept(long io_uring, long fd, byte[] addr);

    //return id
    public static native long wait_cqe(long io_uring);

    public static native long deleteCqe(long io_uring, long cqeAddress);

    public static native long getEventId(long cqeAddress);

    public static native int getRes(long cqeAddress);

    public static native long close(long io_uring);

    public static native long submit(long io_uring);
}
