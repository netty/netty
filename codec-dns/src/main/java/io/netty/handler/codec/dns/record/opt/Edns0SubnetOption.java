/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.record.opt;

import java.net.InetAddress;

import static io.netty.util.internal.ObjectUtil.*;

/**
 * Extension Mechanisms for DNS (EDNS0) <a href="https://tools.ietf.org/html/rfc7871">client subnet</a> option
 */
public class Edns0SubnetOption implements Edns0Option {
    private final short family;
    private final byte sourcePrefixLength;
    private final byte scopePrefixLength;
    private final InetAddress address;

    public Edns0SubnetOption(short family, byte sourcePrefixLength, byte scopePrefixLength, InetAddress address) {
        this.family = family;
        this.sourcePrefixLength = sourcePrefixLength;
        this.scopePrefixLength = scopePrefixLength;
        this.address = checkNotNull(address, "address");
    }

    @Override
    public short optionCode() {
        return Edns0Option.OPTION_CODE_EDNS0_SUBNET;
    }

    public short family() {
        return family;
    }

    public byte sourcePrefixLength() {
        return sourcePrefixLength;
    }

    public byte scopePrefixLength() {
        return scopePrefixLength;
    }

    public InetAddress address() {
        return address;
    }
}
