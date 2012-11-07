package io.netty.codec.socks;

import io.netty.buffer.ByteBuf;

public abstract class SocksMessage {
    private final MessageType messageType;
    private ProtocolVersion protocolVersion = ProtocolVersion.SOCKS5;

    public SocksMessage(MessageType messageType) {
        this.messageType = messageType;
    }

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

        private AuthScheme(byte b) {
            this.b = b;
        }

        public static AuthScheme fromByte(byte b) {
            for (AuthScheme code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return null;
        }

        public byte getByteValue() {
            return this.b;
        }
    }

    public enum CmdType {

        CONNECT((byte) 0x01),
        BIND((byte) 0x02),
        UDP((byte) 0x03),
        UNKNOWN((byte) 0xff);

        private final byte b;

        private CmdType(byte b) {
            this.b = b;
        }

        public static CmdType fromByte(byte b) {
            for (CmdType code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return null;
        }

        public byte getByteValue() {
            return this.b;
        }
    }

    public enum AddressType {

        IPv4((byte) 0x01),
        DOMAIN((byte) 0x03),
        IPv6((byte) 0x04),
        UNKNOWN((byte) 0xff);

        private final byte b;

        private AddressType(byte b) {
            this.b = b;
        }

        public static AddressType fromByte(byte b) {
            for (AddressType code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return null;
        }

        public byte getByteValue() {
            return this.b;
        }
    }

    public enum AuthStatus {

        SUCCESS((byte) 0x00),
        FAILURE((byte) 0xff);

        private final byte b;

        private AuthStatus(byte b) {
            this.b = b;
        }

        public static AuthStatus fromByte(byte b) {
            for (AuthStatus code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return null;
        }

        public byte getByteValue() {
            return this.b;
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

        private CmdStatus(byte b) {
            this.b = b;
        }

        public static CmdStatus fromByte(byte b) {
            for (CmdStatus code : values()) {
                if (code.b == b) {
                    return code;
                }
            }
            return null;
        }

        public byte getByteValue() {
            return this.b;
        }
    }

    public enum ProtocolVersion {
        SOCKS4a((byte) 0x04),
        SOCKS5((byte) 0x05),
        UNKNOWN((byte) 0xff);

        private final byte b;

        private ProtocolVersion(byte b) {
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
            return this.b;
        }
    }


    public ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(ProtocolVersion protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public abstract void encodeAsByteBuf(ByteBuf byteBuf);
}
