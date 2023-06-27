/*
 * Copyright 2016 The Netty Project
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

package io.netty.channel.unix;

import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.EmptyArrays.EMPTY_INTS;

/**
 * User credentials discovered for the peer unix domain socket.
 *
 * The PID, UID and GID of the user connected on the other side of the unix domain socket
 * For details see:
 * <a href=https://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
 */
@UnstableApi
public final class PeerCredentials {
    private final int pid;
    private final int uid;
    private final int[] gids;

    // These values are set by JNI via Socket.peerCredentials()
    PeerCredentials(int p, int u, int... gids) {
        pid = p;
        uid = u;
        this.gids = gids == null ? EMPTY_INTS : gids;
    }

    /**
     * Get the PID of the peer process.
     * <p>
     * This is currently not populated on MacOS and BSD based systems.
     * @return The PID of the peer process.
     */
    public int pid() {
        return pid;
    }

    public int uid() {
        return uid;
    }

    public int[] gids() {
        return gids.clone();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("UserCredentials[pid=").append(pid).append("; uid=").append(uid).append("; gids=[");
        if (gids.length > 0) {
            sb.append(gids[0]);
            for (int i = 1; i < gids.length; ++i) {
                sb.append(", ").append(gids[i]);
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
