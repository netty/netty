/*
 * Copyright 2016 The Netty Project
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

package io.netty.channel.unix;

/**
 * User credentials discovered for the peer unix domain socket.
 *
 * The PID, UID and GID of the user connected on the other side of the unix domain socket
 * For details see:
 * <a href=http://man7.org/linux/man-pages/man7/socket.7.html>SO_PEERCRED</a>
 */
public final class PeerCredentials {
    private final int pid;
    private final int uid;
    private final int gid;

    // These values are set by JNI via Socket.peerCredentials()
    PeerCredentials(int p, int u, int g) {
        pid = p;
        uid = u;
        gid = g;
    }

    public int pid() {
        return pid;
    }

    public int uid() {
        return uid;
    }

    public int gid() {
        return gid;
    }

    @Override
    public String toString() {
        return "UserCredentials["
        + "pid=" + pid
        + "; uid=" + uid
        + "; gid=" + gid
        + "]";
    }
}
