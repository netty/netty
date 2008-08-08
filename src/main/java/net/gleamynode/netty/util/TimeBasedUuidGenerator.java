/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package net.gleamynode.netty.util;

import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 */
public class TimeBasedUuidGenerator {
    private static final AtomicInteger SEQUENCE = new AtomicInteger((int) System.nanoTime());
    private static final long NODE;

    static {
        // Generate nodeKey - we can't use MAC address to support Java 5.
        StringBuilder nodeKey = new StringBuilder(1024);

        //// Append host / IP address information.
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            nodeKey.append(localhost.getCanonicalHostName());
            nodeKey.append(':');
            nodeKey.append(String.valueOf(localhost.getHostAddress()));
        } catch (Exception e) {
            nodeKey.append("localhost:127.0.0.1");
        }

        //// Append standard system properties.
        appendSystemProperty(nodeKey, "java.version");
        appendSystemProperty(nodeKey, "java.home");
        appendSystemProperty(nodeKey, "java.vm.version");
        appendSystemProperty(nodeKey, "java.vm.vendor");
        appendSystemProperty(nodeKey, "java.vm.name");
        appendSystemProperty(nodeKey, "os.name");
        appendSystemProperty(nodeKey, "os.arch");
        appendSystemProperty(nodeKey, "os.version");
        appendSystemProperty(nodeKey, "user.name");

        //// Append the information from java.lang.Runtime.
        nodeKey.append(':');
        nodeKey.append(Runtime.getRuntime().availableProcessors());

        //// Finally, append the another distinguishable string (probably PID.)
        try {
            RuntimeMXBean rtb = ManagementFactory.getRuntimeMXBean();
            nodeKey.append(':');
            nodeKey.append(rtb.getName());
        } catch (Exception e) {
            // Ignore.
        }

        // Generate the digest of the nodeKey.
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("MD5 not supported");
        }

        byte[] nodeKeyDigest;
        try {
            nodeKeyDigest = md.digest(nodeKey.toString().getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new Error("UTF-8 is not found");
        }

        // Choose 5 bytes from the digest.
        // Please note that the first byte is always 1 (multicast address.)
        long node = 1;
        node = node << 8 | nodeKeyDigest[1] & 0xFF;
        node = node << 8 | nodeKeyDigest[4] & 0xFF;
        node = node << 8 | nodeKeyDigest[7] & 0xFF;
        node = node << 8 | nodeKeyDigest[10] & 0xFF;
        node = node << 8 | nodeKeyDigest[13] & 0xFF;

        // We're done.
        NODE = node;
    }

    private static void appendSystemProperty(StringBuilder buf, String key) {
        buf.append(':');
        buf.append(getSystemProperty(key));
    }

    private static String getSystemProperty(String key) {
        try {
            return System.getProperty(key, "null");
        } catch (Exception e) {
            return "null";
        }
    }

    public static UUID generate() {
        long time = System.currentTimeMillis();
        int clockSeq = TimeBasedUuidGenerator.SEQUENCE.getAndIncrement();

        long msb = (time & 0xFFFFFFFFL) << 32 | (time >>> 32 & 0xFFFF) << 16 |
                    time >>> 48 & 0xFFFF;
        long lsb = (long) clockSeq << 48 | NODE;

        // Set to version 1 (i.e. time-based UUID)
        msb = msb & 0xFFFFFFFFFFFF0FFFL | 0x0000000000001000L;

        // Set to IETF variant
        lsb = lsb & 0x3FFFFFFFFFFFFFFFL | 0x8000000000000000L;

        return new UUID(msb, lsb);
    }

    private TimeBasedUuidGenerator() {
        // Unused
    }
}
