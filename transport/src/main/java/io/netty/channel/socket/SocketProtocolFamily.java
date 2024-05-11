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
package io.netty.channel.socket;

import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;

/**
 * {@link ProtocolFamily} implementation that is used by the different transport implementations.
 */
public enum SocketProtocolFamily implements ProtocolFamily {
    /**
     * IPv4
     */
    INET,
    /**
     * IPv6
     */
    INET6,
    /**
     * Unix Domain Socket
     */
    UNIX;

    /**
     * Return a {@link ProtocolFamily} that is "known" by the JDK itself and represent the same as
     * {@link SocketProtocolFamily}.
     *
     * @return the JDK {@link ProtocolFamily}.
     * @throws UnsupportedOperationException if it can't be converted.
     */
    public ProtocolFamily toJdkFamily() {
        switch (this) {
            case INET:
                return StandardProtocolFamily.INET;
            case INET6:
                return StandardProtocolFamily.INET6;
            case UNIX:
                // Just use valueOf as we compile with Java11. If the JDK does not support unix domain sockets, this
                // will throw.
                return StandardProtocolFamily.valueOf("UNIX");
            default:
                throw new UnsupportedOperationException(
                        "ProtocolFamily cant be converted to something that is known by the JDKi: " + this);
        }
    }

    /**
     * Return the {@link SocketProtocolFamily} for the given {@link ProtocolFamily} if possible.
     *
     * @param family    the JDK {@link ProtocolFamily} to convert.
     * @return          the {@link SocketProtocolFamily}.
     * @throws          UnsupportedOperationException if it can't be converted.
     */
    public static SocketProtocolFamily of(ProtocolFamily family) {
        if (family instanceof StandardProtocolFamily) {
            switch ((StandardProtocolFamily) family) {
                case INET:
                    return INET;
                case INET6:
                    return INET6;
                default:
                    // Just compare the name as we compile with Java11
                    if (UNIX.name().equals(family.name())) {
                        return UNIX;
                    }
                    // Fall-through
            }
        } else if (family instanceof SocketProtocolFamily) {
            return (SocketProtocolFamily) family;
        }
        throw new UnsupportedOperationException("ProtocolFamily is not supported: " + family);
    }
}
