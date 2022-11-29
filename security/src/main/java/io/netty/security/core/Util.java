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
package io.netty.security.core;

import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketChannel;
import io.netty.security.core.standards.StandardFiveTuple;
import io.netty.util.internal.ObjectUtil;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Objects;

public final class Util {
    private static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);

    public static int prefixToSubnetMaskV4(int cidrPrefix) {
        return (int) (-1L << 32 - cidrPrefix);
    }

    public static BigInteger prefixToSubnetMaskV6(int cidrPrefix) {
        return MINUS_ONE.shiftLeft(128 - cidrPrefix);
    }

    /**
     * Copy of {@link Integer#compare(int, int)}
     */
    public static int compareIntegers(int x, int y) {
        return x < y ? -1 : x == y ? 0 : 1;
    }

    /**
     * Copy of {@link Objects#hash(Object...)}
     */
    public static int hash(Object... values) {
        return Arrays.hashCode(values);
    }

    /**
     * Create a new {@link StandardFiveTuple} from {@link SocketChannel}
     *
     * @param socketChannel {@link SocketChannel} to use
     * @return new {@link StandardFiveTuple} instance
     */
    public static FiveTuple generateFiveTupleFrom(SocketChannel socketChannel) {
        ObjectUtil.checkNotNull(socketChannel, "SocketChannel");

        if (socketChannel.localAddress() instanceof InetSocketAddress &&
                socketChannel.remoteAddress() instanceof InetSocketAddress) {
            InetSocketAddress local = socketChannel.localAddress();
            InetSocketAddress remote = socketChannel.remoteAddress();

            return StandardFiveTuple.from(Protocol.TCP, remote.getPort(), local.getPort(),
                    StaticIpAddress.of(remote.getAddress()), StaticIpAddress.of(local.getAddress()));
        } else {
            throw new IllegalArgumentException("SocketChannel addresses (local and remote) " +
                    "must be InetSocketAddress");
        }
    }

    /**
     * Create a new {@link StandardFiveTuple} from {@link DatagramChannel}
     *
     * @param datagramChannel {@link DatagramChannel} to use
     * @return new {@link StandardFiveTuple} instance
     */
    public static StandardFiveTuple generateFiveTupleFrom(DatagramChannel datagramChannel) {
        ObjectUtil.checkNotNull(datagramChannel, "DatagramChannel");

        if (datagramChannel.localAddress() instanceof InetSocketAddress &&
                datagramChannel.remoteAddress() instanceof InetSocketAddress) {
            InetSocketAddress local = datagramChannel.localAddress();
            InetSocketAddress remote = datagramChannel.remoteAddress();

            return StandardFiveTuple.from(Protocol.UDP, remote.getPort(), local.getPort(),
                    StaticIpAddress.of(remote.getAddress()), StaticIpAddress.of(local.getAddress()));
        } else {
            throw new IllegalArgumentException("DatagramChannel addresses (local and remote) " +
                    "must be InetSocketAddress");
        }
    }

    /**
     * Create a new {@link StandardFiveTuple} from {@link DatagramPacket}
     *
     * @param datagramPacket {@link DatagramChannel} to use
     * @return new {@link StandardFiveTuple} instance
     */
    public static StandardFiveTuple generateFiveTupleFrom(DatagramPacket datagramPacket) {
        ObjectUtil.checkNotNull(datagramPacket, "DatagramChannel");

        if (datagramPacket.recipient() instanceof InetSocketAddress &&
                datagramPacket.sender() instanceof InetSocketAddress) {
            InetSocketAddress local = datagramPacket.recipient();
            InetSocketAddress remote = datagramPacket.sender();

            return StandardFiveTuple.from(Protocol.UDP, remote.getPort(), local.getPort(),
                    StaticIpAddress.of(remote.getAddress()), StaticIpAddress.of(local.getAddress()));
        } else {
            throw new IllegalArgumentException("DatagramPacket addresses (local and remote) " +
                    "must be InetSocketAddress");
        }
    }

    /**
     * Convert Hex {@link String} to byte array
     *
     * @param str Hex string
     * @return byte array
     */
    public static byte[] hexStringToByteArray(String str) {
        final int len = str.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(str.charAt(i), 16) << 4) + Character.digit(str.charAt(i + 1), 16));
        }
        return data;
    }

    private Util() {
        // Prevent outside initialization
    }
}
