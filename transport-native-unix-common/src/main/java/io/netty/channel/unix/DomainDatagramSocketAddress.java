/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.unix;

import io.netty.util.CharsetUtil;

/**
 * Act as special {@link DomainSocketAddress} to be able to easily pass all needed data from JNI without the need
 * to create more objects then needed.
 * <p>
 * <strong>Internal usage only!</strong>
 */
public final class DomainDatagramSocketAddress extends DomainSocketAddress {

    private static final long serialVersionUID = -5925732678737768223L;

    private final DomainDatagramSocketAddress localAddress;
    // holds the amount of received bytes
    private final int receivedAmount;

    public DomainDatagramSocketAddress(byte[] socketPath, int receivedAmount,
                                       DomainDatagramSocketAddress localAddress) {
        super(new String(socketPath, CharsetUtil.UTF_8));
        this.localAddress = localAddress;
        this.receivedAmount = receivedAmount;
    }

    public DomainDatagramSocketAddress localAddress() {
        return localAddress;
    }

    public int receivedAmount() {
        return receivedAmount;
    }
}
