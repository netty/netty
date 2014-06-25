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
package io.netty.handler.codec.socks;

import io.netty.util.NetUtil;


/**
 * Implementation for Socks4
 */
public final class Socks4CmdRequest extends SocksCmdRequest {
    public Socks4CmdRequest(SocksCmdType cmdType, int port, String host) {
        super(SocksProtocolVersion.SOCKS4, SocksRequestType.CMD, cmdType, port);
        if (host == null) {
            throw new NullPointerException("host");
        }
        if (!SocksCmdType.BIND.equals(cmdType) && !SocksCmdType.CONNECT.equals(cmdType)) {
            throw new IllegalArgumentException("Incorrect SocksCmdType for Socks4!");
        }
        if (port < 0 || port >= 65536) {
            throw new IllegalArgumentException(port + " is not in bounds 0 < x < 65536");
        }
        if (!NetUtil.isValidIpV4Address(host)) {
            throw new IllegalArgumentException(host + " is not a valid IPv4 address");
        }
        this.host = host;
    }
}
