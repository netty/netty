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
package org.jboss.netty.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;


/**
 */
@org.junit.Ignore
public final class TestUtil {

    private static final InetAddress LOCALHOST;
    private final static int START_PORT = 20000;
    private final static int END_PORT = 30000;

    static {
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

    public static InetAddress getLocalHost() {
        // We cache this because some machine takes almost forever to return
        // from InetAddress.getLocalHost().  I think it's due to the incorrect
        // /etc/hosts or /etc/resolve.conf.
        return LOCALHOST;
    }


    /**
     * Return a free port which can be used to bind to
     *
     * @return port
     */
    public static int getFreePort() {
        for(int start = START_PORT; start <= END_PORT; start++) {
            try {
                ServerSocket socket = new ServerSocket(start);
                socket.setReuseAddress(true);
                socket.close();
                return start;
            } catch (IOException e) {
                // ignore
            }

        }
        throw new RuntimeException("Unable to find a free port....");
    }
    private TestUtil() {
        // Unused
    }
}
