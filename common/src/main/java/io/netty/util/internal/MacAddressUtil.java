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

package io.netty.util.internal;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.netty.util.NetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

public final class MacAddressUtil {

    /**
     * Length of a valid MAC address.
     */
    public static final int MAC_ADDRESS_LENGTH = 8;

    private static final byte[] NOT_FOUND = { -1 };

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MacAddressUtil.class);

    /**
     * Obtains the best MAC address found on local network interfaces.
     * Generally speaking, an active network interface used on public
     * networks is better than a local network interface.
     *
     * @return byte array containing a MAC. null if no MAC can be found.
     */
    public static byte[] bestAvailableMac() {
        // Find the best MAC address available.
        byte[] bestMacAddr = NOT_FOUND;
        InetAddress bestInetAddr = NetUtil.LOCALHOST4;

        // Retrieve the list of available network interfaces.
        Map<NetworkInterface, InetAddress> ifaces = new LinkedHashMap<NetworkInterface, InetAddress>();
        try {
            for (Enumeration<NetworkInterface> i = NetworkInterface.getNetworkInterfaces(); i.hasMoreElements();) {
                NetworkInterface iface = i.nextElement();
                // Use the interface with proper INET addresses only.
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                if (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress()) {
                        ifaces.put(iface, a);
                    }
                }
            }
        } catch (SocketException e) {
            logger.warn("Failed to retrieve the list of available network interfaces", e);
        }

        for (Entry<NetworkInterface, InetAddress> entry: ifaces.entrySet()) {
            NetworkInterface iface = entry.getKey();
            InetAddress inetAddr = entry.getValue();
            if (iface.isVirtual()) {
                continue;
            }

            byte[] macAddr;
            try {
                macAddr = iface.getHardwareAddress();
            } catch (SocketException e) {
                logger.debug("Failed to get the hardware address of a network interface: {}", iface, e);
                continue;
            }

            boolean replace = false;
            int res = compareAddresses(bestMacAddr, macAddr);
            if (res < 0) {
                // Found a better MAC address.
                replace = true;
            } else if (res == 0) {
                // Two MAC addresses are of pretty much same quality.
                res = compareAddresses(bestInetAddr, inetAddr);
                if (res < 0) {
                    // Found a MAC address with better INET address.
                    replace = true;
                } else if (res == 0) {
                    // Cannot tell the difference.  Choose the longer one.
                    if (bestMacAddr.length < macAddr.length) {
                        replace = true;
                    }
                }
            }

            if (replace) {
                bestMacAddr = macAddr;
                bestInetAddr = inetAddr;
            }
        }

        if (bestMacAddr == NOT_FOUND) {
            return null;
        }

        switch (bestMacAddr.length) {
            case 6: // EUI-48 - convert to EUI-64
                byte[] newAddr = new byte[MAC_ADDRESS_LENGTH];
                System.arraycopy(bestMacAddr, 0, newAddr, 0, 3);
                newAddr[3] = (byte) 0xFF;
                newAddr[4] = (byte) 0xFE;
                System.arraycopy(bestMacAddr, 3, newAddr, 5, 3);
                bestMacAddr = newAddr;
                break;
            default: // Unknown
                bestMacAddr = Arrays.copyOf(bestMacAddr, MAC_ADDRESS_LENGTH);
        }

        return bestMacAddr;
    }

    /**
     * @param addr byte array of a MAC address.
     * @return hex formatted MAC address.
     */
    public static String formatAddress(byte[] addr) {
        StringBuilder buf = new StringBuilder(24);
        for (byte b: addr) {
            buf.append(String.format("%02x:", b & 0xff));
        }
        return buf.substring(0, buf.length() - 1);
    }

    /**
     * @return positive - current is better, 0 - cannot tell from MAC addr, negative - candidate is better.
     */
    private static int compareAddresses(byte[] current, byte[] candidate) {
        if (candidate == null) {
            return 1;
        }

        // Must be EUI-48 or longer.
        if (candidate.length < 6) {
            return 1;
        }

        // Must not be filled with only 0 and 1.
        boolean onlyZeroAndOne = true;
        for (byte b: candidate) {
            if (b != 0 && b != 1) {
                onlyZeroAndOne = false;
                break;
            }
        }

        if (onlyZeroAndOne) {
            return 1;
        }

        // Must not be a multicast address
        if ((candidate[0] & 1) != 0) {
            return 1;
        }

        // Prefer globally unique address.
        if ((current[0] & 2) == 0) {
            if ((candidate[0] & 2) == 0) {
                // Both current and candidate are globally unique addresses.
                return 0;
            } else {
                // Only current is globally unique.
                return 1;
            }
        } else {
            if ((candidate[0] & 2) == 0) {
                // Only candidate is globally unique.
                return -1;
            } else {
                // Both current and candidate are non-unique.
                return 0;
            }
        }
    }

    /**
     * @return positive - current is better, 0 - cannot tell, negative - candidate is better
     */
    private static int compareAddresses(InetAddress current, InetAddress candidate) {
        return scoreAddress(current) - scoreAddress(candidate);
    }

    private static int scoreAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return 0;
        }
        if (addr.isMulticastAddress()) {
            return 1;
        }
        if (addr.isLinkLocalAddress()) {
            return 2;
        }
        if (addr.isSiteLocalAddress()) {
            return 3;
        }

        return 4;
    }

    private MacAddressUtil() { }
}
