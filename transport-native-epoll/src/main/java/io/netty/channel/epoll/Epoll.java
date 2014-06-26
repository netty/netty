/*
 * Copyright 2014 The Netty Project
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
package io.netty.channel.epoll;

/**
 * Tells if <a href="http://netty.io/wiki/native-transports.html">{@code netty-transport-native-epoll}</a> is supported.
 */
public final class Epoll {

    private static final boolean IS_AVAILABLE;

    static  {
        boolean available;
        int epollFd = -1;
        int eventFd = -1;
        try {
            epollFd = Native.epollCreate();
            eventFd = Native.eventFd();
            available = true;
        } catch (Throwable cause) {
            // ignore
            available = false;
        } finally {
            if (epollFd != -1) {
                try {
                    Native.close(epollFd);
                } catch (Exception ignore) {
                    // ignore
                }
            }
            if (eventFd != -1) {
                try {
                    Native.close(eventFd);
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
        IS_AVAILABLE = available;
    }

    /**
     * Returns {@code true} if and only if the
     * <a href="http://netty.io/wiki/native-transports.html">{@code netty-transport-native-epoll}</a> can be used.
     */
    public static boolean isAvailable() {
        return IS_AVAILABLE;
    }

    private Epoll() { }
}
