/*
 * Copyright 2023 The Netty Project
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

package io.netty.handler.codec.mqtt;

/**
 * Provides a set of enumeration that exposes standard MQTT 5 reason codes used by various messages.
 * */
public final class MqttReasonCodes {

    private MqttReasonCodes() {
    }

    /**
     * @return the corresponding enum value to the hex value.
     * */
    private static <E> E valueOfHelper(byte b, E[] values) {
        try {
            return values[b & 0xFF];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("unknown reason code: " + b);
        }
    }

    /**
     * Reason codes for MQTT Disconnect message.
     */
    public enum Disconnect {
        NORMAL_DISCONNECT((byte) 0x00), //sent by: client or server
        DISCONNECT_WITH_WILL_MESSAGE((byte) 0x04), //sent by: client
        UNSPECIFIED_ERROR((byte) 0x80), //sent by: client or server
        MALFORMED_PACKET((byte) 0x81), //sent by: client or server
        PROTOCOL_ERROR((byte) 0x82), //sent by: client or server
        IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83), //sent by: client or server
        NOT_AUTHORIZED((byte) 0x87), //sent by: server
        SERVER_BUSY((byte) 0x89), //sent by: server
        SERVER_SHUTTING_DOWN((byte) 0x8B), //sent by: server
        KEEP_ALIVE_TIMEOUT((byte) 0x8D), //sent by: Server
        SESSION_TAKEN_OVER((byte) 0x8E), //sent by: Server
        TOPIC_FILTER_INVALID((byte) 0x8F), //sent by: Server
        TOPIC_NAME_INVALID((byte) 0x90), //sent by: Client or Server
        RECEIVE_MAXIMUM_EXCEEDED((byte) 0x93), //sent by: Client or Server
        TOPIC_ALIAS_INVALID((byte) 0x94), //sent by: Client or Server
        PACKET_TOO_LARGE((byte) 0x95), //sent by: Client or Server
        MESSAGE_RATE_TOO_HIGH((byte) 0x96), //sent by: Client or Server
        QUOTA_EXCEEDED((byte) 0x97), //sent by: Client or Server
        ADMINISTRATIVE_ACTION((byte) 0x98), //sent by: Client or Server
        PAYLOAD_FORMAT_INVALID((byte) 0x99), //sent by: Client or Server
        RETAIN_NOT_SUPPORTED((byte) 0x9A), //sent by: Server
        QOS_NOT_SUPPORTED((byte) 0x9B), //sent by: Server
        USE_ANOTHER_SERVER((byte) 0x9C), //sent by: Server
        SERVER_MOVED((byte) 0x9D), //sent by: Server
        SHARED_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0x9E), //sent by: Server
        CONNECTION_RATE_EXCEEDED((byte) 0x9F), //sent by: Server
        MAXIMUM_CONNECT_TIME((byte) 0xA0), //sent by: Server
        SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED((byte) 0xA1), //sent by: Server
        WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0xA2); //sent by: Server

        protected static final Disconnect[] VALUES;

        static {
            Disconnect[] values = values();
            VALUES = new Disconnect[163];
            for (Disconnect code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        Disconnect(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static Disconnect valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT Auth message.
     */
    public enum Auth {
        SUCCESS((byte) 0x00), //sent by: Server
        CONTINUE_AUTHENTICATION((byte) 0x18), //sent by: Client or Server
        REAUTHENTICATE((byte) 0x19); //sent by: Client

        private static final Auth[] VALUES;

        static {
            Auth[] values = values();
            VALUES = new Auth[26];
            for (Auth code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        Auth(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static Auth valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT PubAck message.
     */
    public enum PubAck {
        SUCCESS((byte) 0x00),
        NO_MATCHING_SUBSCRIBERS((byte) 0x10),
        UNSPECIFIED_ERROR((byte) 0x80),
        IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
        NOT_AUTHORIZED((byte) 0x87),
        TOPIC_NAME_INVALID((byte) 0x90),
        PACKET_IDENTIFIER_IN_USE((byte) 0x91),
        QUOTA_EXCEEDED((byte) 0x97),
        PAYLOAD_FORMAT_INVALID((byte) 0x99);

        private static final PubAck[] VALUES;

        static {
            PubAck[] values = values();
            VALUES = new PubAck[154];
            for (PubAck code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        PubAck(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static PubAck valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT PubRec message.
     */
    public enum PubRec {
        SUCCESS((byte) 0x00),
        NO_MATCHING_SUBSCRIBERS((byte) 0x10),
        UNSPECIFIED_ERROR((byte) 0x80),
        IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
        NOT_AUTHORIZED((byte) 0x87),
        TOPIC_NAME_INVALID((byte) 0x90),
        PACKET_IDENTIFIER_IN_USE((byte) 0x91),
        QUOTA_EXCEEDED((byte) 0x97),
        PAYLOAD_FORMAT_INVALID((byte) 0x99);

        private static final PubRec[] VALUES;

        static {
            PubRec[] values = values();
            VALUES = new PubRec[154];
            for (PubRec code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        PubRec(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static PubRec valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT PubRel message.
     */
    public enum PubRel {
        SUCCESS((byte) 0x00),
        PACKET_IDENTIFIER_NOT_FOUND((byte) 0x92);

        private static final PubRel[] VALUES;

        static {
            PubRel[] values = values();
            VALUES = new PubRel[147];
            for (PubRel code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        PubRel(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static PubRel valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT PubComp message.
     */
    public enum PubComp {
        SUCCESS((byte) 0x00),
        PACKET_IDENTIFIER_NOT_FOUND((byte) 0x92);

        private static final PubComp[] VALUES;

        static {
            PubComp[] values = values();
            VALUES = new PubComp[147];
            for (PubComp code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        PubComp(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static PubComp valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT SubAck message.
     */
    public enum SubAck {
        GRANTED_QOS_0((byte) 0x00),
        GRANTED_QOS_1((byte) 0x01),
        GRANTED_QOS_2((byte) 0x02),
        UNSPECIFIED_ERROR((byte) 0x80),
        IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
        NOT_AUTHORIZED((byte) 0x87),
        TOPIC_FILTER_INVALID((byte) 0x8F),
        PACKET_IDENTIFIER_IN_USE((byte) 0x91),
        QUOTA_EXCEEDED((byte) 0x97),
        SHARED_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0x9E),
        SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED((byte) 0xA1),
        WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED((byte) 0xA2);

        private static final SubAck[] VALUES;

        static {
            SubAck[] values = values();
            VALUES = new SubAck[163];
            for (SubAck code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        SubAck(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static SubAck valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }

    /**
     * Reason codes for MQTT UnsubAck message.
     */
    public enum UnsubAck {
        SUCCESS((byte) 0x00),
        NO_SUBSCRIPTION_EXISTED((byte) 0x11),
        UNSPECIFIED_ERROR((byte) 0x80),
        IMPLEMENTATION_SPECIFIC_ERROR((byte) 0x83),
        NOT_AUTHORIZED((byte) 0x87),
        TOPIC_FILTER_INVALID((byte) 0x8F),
        PACKET_IDENTIFIER_IN_USE((byte) 0x91);

        private static final UnsubAck[] VALUES;

        static {
            UnsubAck[] values = values();
            VALUES = new UnsubAck[146];
            for (UnsubAck code : values) {
                final int unsignedByte = code.byteValue & 0xFF;
                // Suppress a warning about out of bounds access since the enum contains only correct values
                VALUES[unsignedByte] = code;    //  [java/index-out-of-bounds]
            }
        }

        private final byte byteValue;

        UnsubAck(byte byteValue) {
            this.byteValue = byteValue;
        }

        /**
         * @return the value number corresponding to the constant.
         * */
        public byte byteValue() {
            return byteValue;
        }

        /**
         * @param b the number to decode.
         * @return the enum value corresponding to the number.
         * */
        public static UnsubAck valueOf(byte b) {
            return valueOfHelper(b, VALUES);
        }
    }
}
