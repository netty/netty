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
package org.jboss.netty.handler.codec.socks;

import org.jboss.netty.buffer.ChannelBuffer;

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
    public CmdStatus getCmdStatus() {
        return cmdStatus;
    }

    /**
     * Returns the {@link AddressType} of this {@link SocksCmdResponse}
     *
     * @return The {@link AddressType} of this {@link SocksCmdResponse}
     */
    public AddressType getAddressType() {
        return addressType;
    }

    @Override
    public void encodeAsByteBuf(ChannelBuffer channelBuffer) {
        channelBuffer.writeByte(getProtocolVersion().getByteValue());
        channelBuffer.writeByte(cmdStatus.getByteValue());
        channelBuffer.writeByte(0x00);
        channelBuffer.writeByte(addressType.getByteValue());
        switch (addressType) {
            case IPv4: {
                channelBuffer.writeBytes(IPv4_HOSTNAME_ZEROED);
                channelBuffer.writeShort(0);
                break;
            }
            case DOMAIN: {
                channelBuffer.writeByte(1);   // domain length
                channelBuffer.writeByte(0);   // domain value
                channelBuffer.writeShort(0);  // port value
                break;
            }
            case IPv6: {
                channelBuffer.writeBytes(IPv6_HOSTNAME_ZEROED);
                channelBuffer.writeShort(0);
                break;
            }
        }
    }
}
