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
 * EDNS0 client subnet option, The option is structured as follows:
 *
 * <pre>
 *                +0 (MSB)                            +1 (LSB)
 *     +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 *  0: |                          OPTION-CODE                          |
 *     +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 *  2: |                         OPTION-LENGTH                         |
 *     +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 *  4: |                            FAMILY                             |
 *     +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 *  6: |     SOURCE PREFIX-LENGTH      |     SCOPE PREFIX-LENGTH       |
 *     +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 *  8: |                           ADDRESS...                          /
 *     +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 * </pre>
 * <p>
 * {@link EDNS0SubnetOption} define the subnet option frame.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7871">https://tools.ietf.org/html/rfc7871</a>
 */
public class EDNS0SubnetOption implements EDNS0Option {
    private final short family;
    private final byte sourcePrefixLength;
    private final byte scopePrefixLength;
    private final InetAddress address;

    public EDNS0SubnetOption(short family, byte sourcePrefixLength, byte scopePrefixLength, InetAddress address) {
        this.family = family;
        this.sourcePrefixLength = sourcePrefixLength;
        this.scopePrefixLength = scopePrefixLength;
        this.address = checkNotNull(address, "address");
    }

    @Override
    public EDNS0OptionCode optionCode() {
        return EDNS0OptionCode.SUBNET;
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
