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
package io.netty5.channel.socket.nio;

import io.netty5.channel.socket.DomainSocketAddress;
import io.netty5.channel.socket.SocketProtocolFamily;

import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.file.Path;

final class NioChannelUtil {

    static boolean isDomainSocket(ProtocolFamily family) {
        if (family instanceof StandardProtocolFamily) {
            return "UNIX".equals(family.name());
        }
        if (family instanceof SocketProtocolFamily) {
            return family == SocketProtocolFamily.UNIX;
        }
        return false;
    }

    static SocketAddress toDomainSocketAddress(SocketAddress address) {
        if (address instanceof UnixDomainSocketAddress unixDomainSocketAddress) {
            Path path = unixDomainSocketAddress.getPath();
            return new DomainSocketAddress(path.toFile());
        }

        return address;
    }

    static SocketAddress toUnixDomainSocketAddress(SocketAddress address) {
        if (address instanceof DomainSocketAddress domainSocketAddress) {
            return UnixDomainSocketAddress.of(domainSocketAddress.path());
        }

        return address;
    }

    static ProtocolFamily toJdkFamily(ProtocolFamily family) {
        if (family instanceof SocketProtocolFamily) {
            return ((SocketProtocolFamily) family).toJdkFamily();
        }
        return family;
    }

    private NioChannelUtil() { }
}
