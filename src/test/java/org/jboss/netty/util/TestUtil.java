/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.netty.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jboss.netty.util.internal.ConversionUtil;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev$, $Date$
 */
@org.junit.Ignore
public final class TestUtil {

    private static final boolean ENABLED;
    private static final InetAddress LOCALHOST;

    static {
        String value = System.getProperty("exclude-timing-tests", "false").trim();
        if (value.length() == 0) {
            value = "true";
        }

        ENABLED = !ConversionUtil.toBoolean(value);
        if (!ENABLED) {
            System.err.println("Timing tests will be disabled as requested.");
        }

        InetAddress localhost = null;
        try {
            localhost = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            try {
                localhost = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
            } catch (UnknownHostException e1) {
                try {
                    localhost = InetAddress.getByAddress(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 });
                } catch (UnknownHostException e2) {
                    System.err.println("Failed to get the localhost.");
                    e2.printStackTrace();
                }
            }
        }

        LOCALHOST = localhost;
    }

    public static boolean isTimingTestEnabled() {
        return ENABLED;
    }

    public static InetAddress getLocalHost() {
        // We cache this because some machine takes almost forever to return
        // from InetAddress.getLocalHost().  I think it's due to the incorrect
        // /etc/hosts or /etc/resolve.conf.
        return LOCALHOST;
    }

    private TestUtil() {
        // Unused
    }
}
