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

import io.netty.buffer.ByteBuf;

/**
 * An socks cmd response.
 *
 * @see SocksCmdRequest
 * @see SocksCmdResponseDecoder
 */
public final class SocksCmdResponse extends SocksResponse {
    private final CmdStatus cmdStatus;

    private final AddressType addressType;
    // All arrays are initialized on construction time to 0/false/null remove array Initialization
    private static final byte[] IPv4_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00};
    private static final byte[] IPv6_HOSTNAME_ZEROED = {0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00};

    public SocksCmdResponse(CmdStatus cmdStatus, AddressType addressType) {
        super(SocksResponseType.CMD);
        if (cmdStatus == null) {
            throw new NullPointerException("cmdStatus");
        }
        if (addressType == null) {
            throw new NullPointerException("addressType");
        }
        this.cmdStatus = cmdStatus;
        this.addressType = addressType;
    }

    /**
     * Returns the {@link CmdStatus} of this {@link SocksCmdResponse}
     *
     * @return The {@link CmdStatus} of this {@link SocksCmdResponse}
     */
    public CmdStatus cmdStatus() {
        return cmdStatus;
    }

    /**
     * Returns the {@link AddressType} of this {@link SocksCmdResponse}
     *
     * @return The {@link AddressType} of this {@link SocksCmdResponse}
     */
    public AddressType addressType() {
        return addressType;
    }

    @Override
    public void encodeAsByteBuf(ByteBuf byteBuf) {
        byteBuf.writeByte(protocolVersion().getByteValue());
        byteBuf.writeByte(cmdStatus.getByteValue());
        byteBuf.writeByte(0x00);
        byteBuf.writeByte(addressType.getByteValue());
        switch (addressType) {
            case IPv4: {
                byteBuf.writeBytes(IPv4_HOSTNAME_ZEROED);
                byteBuf.writeShort(0);
                break;
            }
            case DOMAIN: {
                byteBuf.writeByte(1);   // domain length
                byteBuf.writeByte(0);   // domain value
                byteBuf.writeShort(0);  // port value
                break;
            }
            case IPv6: {
                byteBuf.writeBytes(IPv6_HOSTNAME_ZEROED);
                byteBuf.writeShort(0);
                break;
            }
        }
    }
}
