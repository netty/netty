/*
 * Copyright 2012 The Netty Project
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
package io.netty.testsuite.util;

import io.netty.util.NetUtil;
import org.junit.rules.TestName;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public final class TestUtils {

    private static final int START_PORT = 32768;
    private static final int END_PORT = 65536;
    private static final int NUM_CANDIDATES = END_PORT - START_PORT;

    private static final List<Integer> PORTS = new ArrayList<Integer>();
    private static Iterator<Integer> portIterator;

    static {
        for (int i = START_PORT; i < END_PORT; i ++) {
            PORTS.add(i);
        }
        Collections.shuffle(PORTS);
    }

    private static int nextCandidatePort() {
        if (portIterator == null || !portIterator.hasNext()) {
            portIterator = PORTS.iterator();
        }
        return portIterator.next();
    }

    /**
     * Return a free port which can be used to bind to
     *
     * @return port
     */
    public static int getFreePort() {
        for (int i = 0; i < NUM_CANDIDATES; i ++) {
            int port = nextCandidatePort();
            try {
                // Ensure it is possible to bind on both wildcard and loopback.
                ServerSocket ss;
                ss = new ServerSocket();
                ss.setReuseAddress(false);
                ss.bind(new InetSocketAddress(port));
                ss.close();

                ss = new ServerSocket();
                ss.setReuseAddress(false);
                ss.bind(new InetSocketAddress(NetUtil.LOCALHOST, port));
                ss.close();

                return port;
            } catch (IOException e) {
                // ignore
            }
        }

        throw new RuntimeException("unable to find a free port");
    }

    /**
     * Return {@code true} if SCTP is supported by the running os.
     *
     */
    public static boolean isSctpSupported() {
        String os = System.getProperty("os.name").toLowerCase(Locale.UK);
        if ("unix".equals(os) || "linux".equals(os) || "sun".equals(os) || "solaris".equals(os)) {
            try {
                // Try to open a SCTP Channel, by using reflection to make it compile also on
                // operation systems that not support SCTP like OSX and Windows
                Class<?> sctpChannelClass = Class.forName("com.sun.nio.sctp.SctpChannel");
                Channel channel = (Channel) sctpChannelClass.getMethod("open").invoke(null);
                try {
                    channel.close();
                } catch (IOException e) {
                    // ignore
                }
            } catch (UnsupportedOperationException e) {
                // This exception may get thrown if the OS does not have
                // the shared libs installed.
                System.out.print("Not supported: " + e.getMessage());
                return false;
            } catch (Throwable t) {
                if (!(t instanceof IOException)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the method name of the current test.
     */
    public static String testMethodName(TestName testName) {
        String testMethodName = testName.getMethodName();
        if (testMethodName.contains("[")) {
            testMethodName = testMethodName.substring(0, testMethodName.indexOf('['));
        }
        return testMethodName;
    }

    private TestUtils() { }
}
