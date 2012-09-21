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

import java.io.IOException;
import java.net.ServerSocket;

public class TestUtils {

    private static int START_PORT = 20000;
    private static int END_PORT = 30000;

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
                START_PORT = start + 1;
                return start;
            } catch (IOException e) {
                // ignore
            }

        }
        throw new RuntimeException("Unable to find a free port....");
    }

    private TestUtils() { }
}