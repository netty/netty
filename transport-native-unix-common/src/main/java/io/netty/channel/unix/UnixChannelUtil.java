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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import static io.netty.channel.unix.Limits.IOV_MAX;

public final class UnixChannelUtil {

    private static final Class<?> FILE_CHANNEL_IMPL_CLASS;

    static {
        Class<?> fileChannelImplClass;
        try {
            fileChannelImplClass = Class.forName("sun.nio.ch.FileChannelImpl", false, null);
        } catch (ClassNotFoundException ignore) {
            fileChannelImplClass = null;
        }
        FILE_CHANNEL_IMPL_CLASS = fileChannelImplClass;
    }

    private UnixChannelUtil() {
    }

    /**
     * Returns whether the given channel is an instance of {@code sun.nio.ch.FileChannelImpl}.
     */
    public static boolean isFileChannelImpl(FileChannel channel) {
        return FILE_CHANNEL_IMPL_CLASS != null && FILE_CHANNEL_IMPL_CLASS.isInstance(channel);
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

    /**
     * Delete the file at {@code path} but only if it is not a regular file, directory or symbolic link (i.e. it is
     * a socket inode as left behind by a bound domain socket). Symbolic links are never followed.
     *
     * @return {@code true} if the file was deleted.
     */
    public static boolean deleteDomainSocketFile(String path) {
        try {
            Path file = Paths.get(path);
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isOther()) {
                return false;
            }
            Files.delete(file);
            return true;
        } catch (IOException ignore) {
            return false;
        } catch (InvalidPathException ignore) {
            // For example abstract namespace addresses, which have no file system presence.
            return false;
        }
    }

    public static InetSocketAddress computeRemoteAddr(InetSocketAddress remoteAddr, InetSocketAddress osRemoteAddr) {
        if (osRemoteAddr != null) {
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
            return osRemoteAddr;
        }
        return remoteAddr;
    }
}
