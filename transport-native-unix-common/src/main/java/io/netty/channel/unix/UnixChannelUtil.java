/*
 * Copyright 2017 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.PlatformDependent;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static io.netty.channel.unix.Limits.IOV_MAX;

public final class UnixChannelUtil {

    private UnixChannelUtil() {
    }

    /**
     * Checks if the specified buffer has memory address or is composed of n(n <= IOV_MAX) NIO direct buffers.
     * (We check this because otherwise we need to make it a new direct buffer.)
     */
    public static boolean isBufferCopyNeededForWrite(ByteBuf byteBuf) {
        return isBufferCopyNeededForWrite(byteBuf, IOV_MAX);
    }

    static boolean isBufferCopyNeededForWrite(ByteBuf byteBuf, int iovMax) {
        return !byteBuf.hasMemoryAddress() && (!byteBuf.isDirect() || byteBuf.nioBufferCount() > iovMax);
    }

    public static InetSocketAddress computeRemoteAddr(InetSocketAddress remoteAddr, InetSocketAddress osRemoteAddr) {
        if (osRemoteAddr != null) {
            if (PlatformDependent.javaVersion() >= 7) {
                try {
                    // Only try to construct a new InetSocketAddress if we using java >= 7 as getHostString() does not
                    // exists in earlier releases and so the retrieval of the hostname could block the EventLoop if a
                    // reverse lookup would be needed.
                    return new InetSocketAddress(InetAddress.getByAddress(remoteAddr.getHostString(),
                            osRemoteAddr.getAddress().getAddress()),
                            osRemoteAddr.getPort());
                } catch (UnknownHostException ignore) {
                    // Should never happen but fallback to osRemoteAddr anyway.
                }
            }
            return osRemoteAddr;
        }
        return remoteAddr;
    }
}
