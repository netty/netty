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
 * An abstract class that defines a SocksMessage, providing common properties for
 * {@link SocksRequest} and {@link SocksResponse}.
 *
 * @see SocksRequest
 * @see SocksResponse
 */

public abstract class SocksMessage {
    private final MessageType messageType;
    private final ProtocolVersion protocolVersion = ProtocolVersion.SOCKS5;

    protected SocksMessage(MessageType messageType) {
        if (messageType == null) {
            throw new NullPointerException("messageType");
        }
        this.messageType = messageType;
    }

    /**
     * Returns the {@link MessageType} of this {@link SocksMessage}
     *
     * @return The {@link MessageType} of this {@link SocksMessage}
     */
    public MessageType getMessageType() {
        return messageType;
    }

    public enum MessageType {
        REQUEST,
        RESPONSE,
        UNKNOWN
    }

    public enum AuthScheme {
        NO_AUTH((byte) 0x00),
        AUTH_GSSAPI((byte) 0x01),
        AUTH_PASSWORD((byte) 0x02),
        UNKNOWN((byte) 0xff);

        private final byte b;

        AuthScheme(byte b) {
            this.b = b;
        }

        public static AuthScheme fromByte(byte b) {
            for (AuthScheme code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return UNKNOWN;
        }

        public byte getByteValue() {
            return b;
        }
    }

    public enum CmdType {
        CONNECT((byte) 0x01),
        BIND((byte) 0x02),
        UDP((byte) 0x03),
        UNKNOWN((byte) 0xff);

        private final byte b;

        CmdType(byte b) {
            this.b = b;
        }

        public static CmdType fromByte(byte b) {
            for (CmdType code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return UNKNOWN;
        }

        public byte getByteValue() {
            return b;
        }
    }

    public enum AddressType {
        IPv4((byte) 0x01),
        DOMAIN((byte) 0x03),
        IPv6((byte) 0x04),
        UNKNOWN((byte) 0xff);

        private final byte b;

        AddressType(byte b) {
            this.b = b;
        }

        public static AddressType fromByte(byte b) {
            for (AddressType code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return UNKNOWN;
        }

        public byte getByteValue() {
            return b;
        }
    }

    public enum AuthStatus {
        SUCCESS((byte) 0x00),
        FAILURE((byte) 0xff);

        private final byte b;

        AuthStatus(byte b) {
            this.b = b;
        }

        public static AuthStatus fromByte(byte b) {
            for (AuthStatus code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return FAILURE;
        }

        public byte getByteValue() {
            return b;
        }
    }

    public enum CmdStatus {
        SUCCESS((byte) 0x00),
        FAILURE((byte) 0x01),
        FORBIDDEN((byte) 0x02),
        NETWORK_UNREACHABLE((byte) 0x03),
        HOST_UNREACHABLE((byte) 0x04),
        REFUSED((byte) 0x05),
        TTL_EXPIRED((byte) 0x06),
        COMMAND_NOT_SUPPORTED((byte) 0x07),
        ADDRESS_NOT_SUPPORTED((byte) 0x08),
        UNASSIGNED((byte) 0xff);

        private final byte b;

        CmdStatus(byte b) {
            this.b = b;
        }

        public static CmdStatus fromByte(byte b) {
            for (CmdStatus code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return UNASSIGNED;
        }

        public byte getByteValue() {
            return b;
        }
    }

    public enum ProtocolVersion {
        SOCKS4a((byte) 0x04),
        SOCKS5((byte) 0x05),
        UNKNOWN((byte) 0xff);

        private final byte b;

        ProtocolVersion(byte b) {
            this.b = b;
        }

        public static ProtocolVersion fromByte(byte b) {
            for (ProtocolVersion code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return UNKNOWN;
        }

        public byte getByteValue() {
            return b;
        }
    }

    public enum SubnegotiationVersion {
        AUTH_PASSWORD((byte) 0x01),
        UNKNOWN((byte) 0xff);

        private final byte b;

        SubnegotiationVersion(byte b) {
            this.b = b;
        }

        public static SubnegotiationVersion fromByte(byte b) {
            for (SubnegotiationVersion code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return UNKNOWN;
        }

        public byte getByteValue() {
            return b;
        }
    }

    /**
     * Returns the {@link ProtocolVersion} of this {@link SocksMessage}
     *
     * @return The {@link ProtocolVersion} of this {@link SocksMessage}
     */
    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Encode socks message into its byte representation and write it into byteBuf
     *
     * @see ChannelBuffer
     */
    public abstract void encodeAsByteBuf(ChannelBuffer channelBuffer) throws Exception;
}
