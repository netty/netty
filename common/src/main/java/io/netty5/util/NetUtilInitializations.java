/*
 * Copyright 2020 The Netty Project
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
package io.netty5.util;

import io.netty5.util.internal.BoundedInputStream;
import io.netty5.util.internal.PlatformDependent;
import io.netty5.util.internal.SocketUtils;
import io.netty5.util.internal.SystemPropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

final class NetUtilInitializations {
    /**
     * The logger being used by this class
     */
    private static final Logger logger = LoggerFactory.getLogger(NetUtilInitializations.class);

    private NetUtilInitializations() {
    }

    static Inet4Address createLocalhost4() {
        byte[] LOCALHOST4_BYTES = {127, 0, 0, 1};

        Inet4Address localhost4 = null;
        try {
            localhost4 = (Inet4Address) InetAddress.getByAddress("localhost", LOCALHOST4_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e);
        }

        return localhost4;
    }

    static Inet6Address createLocalhost6() {
        byte[] LOCALHOST6_BYTES = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

        Inet6Address localhost6 = null;
        try {
            localhost6 = (Inet6Address) InetAddress.getByAddress("localhost", LOCALHOST6_BYTES);
        } catch (Exception e) {
            // We should not get here as long as the length of the address is correct.
            PlatformDependent.throwException(e);
        }

        return localhost6;
    }

    static Collection<NetworkInterface> networkInterfaces() {
        List<NetworkInterface> networkInterfaces = new ArrayList<NetworkInterface>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    networkInterfaces.add(interfaces.nextElement());
                }
            }
        } catch (SocketException e) {
            logger.warn("Failed to retrieve the list of available network interfaces", e);
        } catch (NullPointerException e) {
            if (!PlatformDependent.isAndroid()) {
                throw e;
            }
            // Might happen on earlier version of Android.
            // See https://developer.android.com/reference/java/net/NetworkInterface#getNetworkInterfaces()
        }
        return Collections.unmodifiableList(networkInterfaces);
    }

    static NetworkIfaceAndInetAddress determineLoopback(
            Collection<NetworkInterface> networkInterfaces, Inet4Address localhost4, Inet6Address localhost6) {
        // Retrieve the list of available network interfaces.
        List<NetworkInterface> ifaces = new ArrayList<>();
        for (NetworkInterface iface: networkInterfaces) {
            // Use the interface with proper INET addresses only.
            if (SocketUtils.addressesFromNetworkInterface(iface).hasMoreElements()) {
                ifaces.add(iface);
            }
        }

        // Find the first loopback interface available from its INET address (127.0.0.1 or ::1)
        // Note that we do not use NetworkInterface.isLoopback() in the first place because it takes long time
        // on a certain environment. (e.g. Windows with -Djava.net.preferIPv4Stack=true)
        NetworkInterface loopbackIface = null;
        InetAddress loopbackAddr = null;
        loop: for (NetworkInterface iface: ifaces) {
            for (Enumeration<InetAddress> i = SocketUtils.addressesFromNetworkInterface(iface); i.hasMoreElements();) {
                InetAddress addr = i.nextElement();
                if (addr.isLoopbackAddress()) {
                    // Found
                    loopbackIface = iface;
                    loopbackAddr = addr;
                    break loop;
                }
            }
        }

        // If failed to find the loopback interface from its INET address, fall back to isLoopback().
        if (loopbackIface == null) {
            try {
                for (NetworkInterface iface: ifaces) {
                    if (iface.isLoopback()) {
                        Enumeration<InetAddress> i = SocketUtils.addressesFromNetworkInterface(iface);
                        if (i.hasMoreElements()) {
                            // Found the one with INET address.
                            loopbackIface = iface;
                            loopbackAddr = i.nextElement();
                            break;
                        }
                    }
                }

                if (loopbackIface == null) {
                    logger.warn("Failed to find the loopback interface");
                }
            } catch (SocketException e) {
                logger.warn("Failed to find the loopback interface", e);
            }
        }

        if (loopbackIface != null) {
            // Found the loopback interface with an INET address.
            logger.debug(
                    "Loopback interface: {} ({}, {})",
                    loopbackIface.getName(), loopbackIface.getDisplayName(), loopbackAddr.getHostAddress());
        } else {
            // Could not find the loopback interface, but we can't leave LOCALHOST as null.
            // Use LOCALHOST6 or LOCALHOST4, preferably the IPv6 one.
            if (loopbackAddr == null) {
                try {
                    if (NetworkInterface.getByInetAddress(localhost6) != null) {
                        logger.debug("Using hard-coded IPv6 localhost address: {}", localhost6);
                        loopbackAddr = localhost6;
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    if (loopbackAddr == null) {
                        logger.debug("Using hard-coded IPv4 localhost address: {}", localhost4);
                        loopbackAddr = localhost4;
                    }
                }
            }
        }

        return new NetworkIfaceAndInetAddress(loopbackIface, loopbackAddr);
    }

    static int determineMaxSoConn() {
        // Determine the default somaxconn (server socket backlog) value of the platform.
        // The known defaults:
        // - Windows NT Server 4.0+: 200
        // - Mac OS X: 128
        // - Linux kernel > 5.4 : 4096
        int somaxconn;
        if (PlatformDependent.isWindows()) {
            somaxconn = 200;
        } else if (PlatformDependent.isOsx()) {
            somaxconn = 128;
        } else {
            somaxconn = 4096;
        }
        File file = new File("/proc/sys/net/core/somaxconn");
        BufferedReader in = null;
        try {
            if (file.exists()) {
                in = new BufferedReader(new InputStreamReader(
                        new BoundedInputStream(new FileInputStream(file))));
                somaxconn = Integer.parseInt(in.readLine());
                if (logger.isDebugEnabled()) {
                    logger.debug("{}: {}", file, somaxconn);
                }
            } else {
                // Try to get from sysctl
                Integer tmp = null;
                if (SystemPropertyUtil.getBoolean("io.netty5.net.somaxconn.trySysctl", false)) {
                    tmp = sysctlGetInt("kern.ipc.somaxconn");
                    if (tmp == null) {
                        tmp = sysctlGetInt("kern.ipc.soacceptqueue");
                        if (tmp != null) {
                            somaxconn = tmp;
                        }
                    } else {
                        somaxconn = tmp;
                    }
                }

                if (tmp == null) {
                    logger.debug("Failed to get SOMAXCONN from sysctl and file {}. Default: {}", file,
                                 somaxconn);
                }
            }
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to get SOMAXCONN from sysctl and file {}. Default: {}",
                             file, somaxconn, e);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                    // Ignored.
                }
            }
        }
        return somaxconn;
    }

    /**
     * This will execute <a href ="https://www.freebsd.org/cgi/man.cgi?sysctl(8)">sysctl</a> with the {@code sysctlKey}
     * which is expected to return the numeric value for for {@code sysctlKey}.
     *
     * @param sysctlKey The key which the return value corresponds to.
     * @return The <a href ="https://www.freebsd.org/cgi/man.cgi?sysctl(8)">sysctl</a> value for {@code sysctlKey}.
     */
    private static Integer sysctlGetInt(String sysctlKey) throws IOException {
        Process process = new ProcessBuilder("sysctl", sysctlKey).start();
        try {
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(new BoundedInputStream(is));
            try (BufferedReader br = new BufferedReader(isr)) {
                String line = br.readLine();
                if (line != null && line.startsWith(sysctlKey)) {
                    for (int i = line.length() - 1; i > sysctlKey.length(); --i) {
                        if (!Character.isDigit(line.charAt(i))) {
                            return Integer.valueOf(line.substring(i + 1));
                        }
                    }
                }
                return null;
            }
        } finally {
            process.destroy();
        }
    }

    static final class NetworkIfaceAndInetAddress {
        private final NetworkInterface iface;
        private final InetAddress address;

        NetworkIfaceAndInetAddress(NetworkInterface iface, InetAddress address) {
            this.iface = iface;
            this.address = address;
        }

        public NetworkInterface iface() {
            return iface;
        }

        public InetAddress address() {
            return address;
        }
    }
}
