/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
