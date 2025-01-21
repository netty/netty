/*
 * Copyright 2023 The Netty Project
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

package io.netty.channel.epoll;

import java.net.SocketAddress;

/**
 * A address for a
 * <a href="https://man7.org/linux/man-pages/man7/vsock.7.html">VM sockets (Linux VSOCK address family)</a>.
 */

public final class VSockAddress extends SocketAddress {
    private static final long serialVersionUID = 8600894096347158429L;

    public static final int VMADDR_CID_ANY = -1;
    public static final int VMADDR_CID_HYPERVISOR = 0;
    public static final int VMADDR_CID_LOCAL = 1;
    public static final int VMADDR_CID_HOST = 2;

    public static final int VMADDR_PORT_ANY = -1;

    private final int cid;
    private final int port;

    public VSockAddress(int cid, int port) {
        this.cid = cid;
        this.port = port;
    }

    public int getCid() {
        return cid;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "VSockAddress{" +
                "cid=" + cid +
                ", port=" + port +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VSockAddress)) {
            return false;
        }

        VSockAddress that = (VSockAddress) o;

        return cid == that.cid && port == that.port;
    }

    @Override
    public int hashCode() {
        int result = cid;
        result = 31 * result + port;
        return result;
    }
}
