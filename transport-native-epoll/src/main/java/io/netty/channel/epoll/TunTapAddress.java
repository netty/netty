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

import java.net.SocketAddress;

/**
 * An abstract address object that identifies a tun or tap device to which
 * a {@link TunTapChannel} can be bound.
 */
public abstract class TunTapAddress extends SocketAddress {

    private String ifName;

    protected TunTapAddress(String ifName) {
        this.ifName = ifName;
    }

    /**
     * The name of the tun/tap device.
     */
    public String interfaceName() {
        return this.ifName;
    }

    /**
     * True if this address identifies a tap device (verses a tun device).
     */
    public abstract boolean isTapAddress();

    private static final long serialVersionUID = 0;
}
