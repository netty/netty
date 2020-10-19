/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.sun.nio.sctp;

import java.net.SocketAddress;

public abstract class MessageInfo {
    static {
        UnsupportedOperatingSystemException.raise();
    }

    public static MessageInfo createOutgoing(Association association, SocketAddress address, int streamNumber) {
        return null;
    }

    public abstract SocketAddress address();
    public abstract int streamNumber();
    public abstract MessageInfo streamNumber(int streamNumber);
    public abstract int payloadProtocolID();
    public abstract MessageInfo payloadProtocolID(int ppid);
    public abstract boolean isComplete();
    public abstract boolean isUnordered();
    public abstract MessageInfo unordered(boolean b);

}
