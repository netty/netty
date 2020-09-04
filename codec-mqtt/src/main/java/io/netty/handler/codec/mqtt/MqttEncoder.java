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

package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.util.internal.EmptyArrays;

import java.util.List;

import static io.netty.buffer.ByteBufUtil.*;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.getMqttVersion;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidClientId;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.setMqttVersion;

/**
 * Encodes Mqtt messages into bytes following the protocol specification v3.1
 * as described here <a href="http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html">MQTTV3.1</a>
 * or v5.0 as described here <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html">MQTTv5.0</a> -
 * depending on the version specified in the first CONNECT message that goes through the channel.
 */
@ChannelHandler.Sharable
public final class MqttEncoder extends MessageToMessageEncoder<MqttMessage> {

    public static final MqttEncoder INSTANCE = new MqttEncoder();

    private MqttEncoder() { }

    @Override
    protected void encode(ChannelHandlerContext ctx, MqttMessage msg, List<Object> out) throws Exception {
        out.add(doEncode(ctx, msg));
    }

    /**
     * This is the main encoding method.
     * It's only visible for testing.
     *
     * @param message MQTT message to encode
     * @return ByteBuf with encoded bytes
     */
    static ByteBuf doEncode(ChannelHandlerContext ctx,
                     MqttMessage message) {

        switch (message.fixedHeader().messageType()) {
            case CONNECT:
                return encodeConnectMessage(ctx, (MqttConnectMessage) message);

            case CONNACK:
                return encodeConnAckMessage(ctx, (MqttConnAckMessage) message);

            case PUBLISH:
                return encodePublishMessage(ctx, (MqttPublishMessage) message);

            case SUBSCRIBE:
                return encodeSubscribeMessage(ctx, (MqttSubscribeMessage) message);

            case UNSUBSCRIBE:
                return encodeUnsubscribeMessage(ctx,  (MqttUnsubscribeMessage) message);

            case SUBACK:
                return encodeSubAckMessage(ctx, (MqttSubAckMessage) message);

            case UNSUBACK:
                if (message instanceof MqttUnsubAckMessage) {
                    return encodeUnsubAckMessage(ctx, (MqttUnsubAckMessage) message);
                }
                return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(ctx.alloc(), message);

            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                return encodePubReplyMessage(ctx, message);

            case DISCONNECT:
            case AUTH:
                return encodeReasonCodePlusPropertiesMessage(ctx, message);

            case PINGREQ:
            case PINGRESP:
                return encodeMessageWithOnlySingleByteFixedHeader(ctx.alloc(), message);

            default:
                throw new IllegalArgumentException(
                        "Unknown message type: " + message.fixedHeader().messageType().value());
        }
    }

    private static ByteBuf encodeConnectMessage(
            ChannelHandlerContext ctx,
            MqttConnectMessage message) {
        int payloadBufferSize = 0;

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttConnectVariableHeader variableHeader = message.variableHeader();
        MqttConnectPayload payload = message.payload();
        MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(variableHeader.name(),
                (byte) variableHeader.version());
        setMqttVersion(ctx, mqttVersion);

        // as MQTT 3.1 & 3.1.1 spec, If the User Name Flag is set to 0, the Password Flag MUST be set to 0
        if (!variableHeader.hasUserName() && variableHeader.hasPassword()) {
            throw new EncoderException("Without a username, the password MUST be not set");
        }

        // Client id
        String clientIdentifier = payload.clientIdentifier();
        if (!isValidClientId(mqttVersion, clientIdentifier)) {
            throw new MqttIdentifierRejectedException("invalid clientIdentifier: " + clientIdentifier);
        }
        int clientIdentifierBytes = utf8Bytes(clientIdentifier);
        payloadBufferSize += 2 + clientIdentifierBytes;

        // Will topic and message
        String willTopic = payload.willTopic();
        int willTopicBytes = nullableUtf8Bytes(willTopic);
        byte[] willMessage = payload.willMessageInBytes();
        byte[] willMessageBytes = willMessage != null ? willMessage : EmptyArrays.EMPTY_BYTES;
        if (variableHeader.isWillFlag()) {
            payloadBufferSize += 2 + willTopicBytes;
            payloadBufferSize += 2 + willMessageBytes.length;
        }

        String userName = payload.userName();
        int userNameBytes = nullableUtf8Bytes(userName);
        if (variableHeader.hasUserName()) {
            payloadBufferSize += 2 + userNameBytes;
        }

        byte[] password = payload.passwordInBytes();
        byte[] passwordBytes = password != null ? password : EmptyArrays.EMPTY_BYTES;
        if (variableHeader.hasPassword()) {
            payloadBufferSize += 2 + passwordBytes.length;
        }

        // Fixed and variable header
        byte[] protocolNameBytes = mqttVersion.protocolNameBytes();
        ByteBuf propertiesBuf = encodePropertiesIfNeeded(
                mqttVersion,
                ctx.alloc(),
                message.variableHeader().properties());
        try {
            final ByteBuf willPropertiesBuf;
            if (variableHeader.isWillFlag()) {
                willPropertiesBuf = encodePropertiesIfNeeded(mqttVersion, ctx.alloc(), payload.willProperties());
                payloadBufferSize += willPropertiesBuf.readableBytes();
            } else {
                willPropertiesBuf = Unpooled.EMPTY_BUFFER;
            }
            try {
                int variableHeaderBufferSize = 2 + protocolNameBytes.length + 4 + propertiesBuf.readableBytes();

                int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
                int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
                ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variablePartSize);
                buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
                writeVariableLengthInt(buf, variablePartSize);

                buf.writeShort(protocolNameBytes.length);
                buf.writeBytes(protocolNameBytes);

                buf.writeByte(variableHeader.version());
                buf.writeByte(getConnVariableHeaderFlag(variableHeader));
                buf.writeShort(variableHeader.keepAliveTimeSeconds());
                buf.writeBytes(propertiesBuf);

                // Payload
                writeExactUTF8String(buf, clientIdentifier, clientIdentifierBytes);
                if (variableHeader.isWillFlag()) {
                    buf.writeBytes(willPropertiesBuf);
                    writeExactUTF8String(buf, willTopic, willTopicBytes);
                    buf.writeShort(willMessageBytes.length);
                    buf.writeBytes(willMessageBytes, 0, willMessageBytes.length);
                }
                if (variableHeader.hasUserName()) {
                    writeExactUTF8String(buf, userName, userNameBytes);
                }
                if (variableHeader.hasPassword()) {
                    buf.writeShort(passwordBytes.length);
                    buf.writeBytes(passwordBytes, 0, passwordBytes.length);
                }
                return buf;
            } finally {
                willPropertiesBuf.release();
            }
        } finally {
            propertiesBuf.release();
        }
    }

    private static int getConnVariableHeaderFlag(MqttConnectVariableHeader variableHeader) {
        int flagByte = 0;
        if (variableHeader.hasUserName()) {
            flagByte |= 0x80;
        }
        if (variableHeader.hasPassword()) {
            flagByte |= 0x40;
        }
        if (variableHeader.isWillRetain()) {
            flagByte |= 0x20;
        }
        flagByte |= (variableHeader.willQos() & 0x03) << 3;
        if (variableHeader.isWillFlag()) {
            flagByte |= 0x04;
        }
        if (variableHeader.isCleanSession()) {
            flagByte |= 0x02;
        }
        return flagByte;
    }

    private static ByteBuf encodeConnAckMessage(
            ChannelHandlerContext ctx,
            MqttConnAckMessage message) {
        final MqttVersion mqttVersion = getMqttVersion(ctx);
        ByteBuf propertiesBuf = encodePropertiesIfNeeded(mqttVersion,
                ctx.alloc(),
                message.variableHeader().properties());

        try {
            ByteBuf buf = ctx.alloc().buffer(4 + propertiesBuf.readableBytes());
            buf.writeByte(getFixedHeaderByte1(message.fixedHeader()));
            writeVariableLengthInt(buf, 2 + propertiesBuf.readableBytes());
            buf.writeByte(message.variableHeader().isSessionPresent() ? 0x01 : 0x00);
            buf.writeByte(message.variableHeader().connectReturnCode().byteValue());
            buf.writeBytes(propertiesBuf);
            return buf;
        } finally {
            propertiesBuf.release();
        }
    }

    private static ByteBuf encodeSubscribeMessage(
            ChannelHandlerContext ctx,
            MqttSubscribeMessage message) {
        MqttVersion mqttVersion = getMqttVersion(ctx);
        ByteBuf propertiesBuf = encodePropertiesIfNeeded(mqttVersion,
                ctx.alloc(),
                message.idAndPropertiesVariableHeader().properties());

        try {
            final int variableHeaderBufferSize = 2 + propertiesBuf.readableBytes();
            int payloadBufferSize = 0;

            MqttFixedHeader mqttFixedHeader = message.fixedHeader();
            MqttMessageIdVariableHeader variableHeader = message.variableHeader();
            MqttSubscribePayload payload = message.payload();

            for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
                String topicName = topic.topicName();
                int topicNameBytes = utf8Bytes(topicName);
                payloadBufferSize += 2 + topicNameBytes;
                payloadBufferSize += 1;
            }

            int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
            int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

            ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variablePartSize);
            buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
            writeVariableLengthInt(buf, variablePartSize);

            // Variable Header
            int messageId = variableHeader.messageId();
            buf.writeShort(messageId);
            buf.writeBytes(propertiesBuf);

            // Payload
            for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
                writeUnsafeUTF8String(buf, topic.topicName());
                final MqttSubscriptionOption option = topic.option();

                int optionEncoded = option.retainHandling().value() << 4;
                if (option.isRetainAsPublished()) {
                    optionEncoded |= 0x08;
                }
                if (option.isNoLocal()) {
                    optionEncoded |= 0x04;
                }
                optionEncoded |= option.qos().value();

                buf.writeByte(optionEncoded);
            }

            return buf;
        } finally {
            propertiesBuf.release();
        }
    }

    private static ByteBuf encodeUnsubscribeMessage(
            ChannelHandlerContext ctx,
            MqttUnsubscribeMessage message) {
        MqttVersion mqttVersion = getMqttVersion(ctx);
        ByteBuf propertiesBuf = encodePropertiesIfNeeded(mqttVersion,
                ctx.alloc(),
                message.idAndPropertiesVariableHeader().properties());

        try {
            final int variableHeaderBufferSize = 2 + propertiesBuf.readableBytes();
            int payloadBufferSize = 0;

            MqttFixedHeader mqttFixedHeader = message.fixedHeader();
            MqttMessageIdVariableHeader variableHeader = message.variableHeader();
            MqttUnsubscribePayload payload = message.payload();

            for (String topicName : payload.topics()) {
                int topicNameBytes = utf8Bytes(topicName);
                payloadBufferSize += 2 + topicNameBytes;
            }

            int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
            int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

            ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variablePartSize);
            buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
            writeVariableLengthInt(buf, variablePartSize);

            // Variable Header
            int messageId = variableHeader.messageId();
            buf.writeShort(messageId);
            buf.writeBytes(propertiesBuf);

            // Payload
            for (String topicName : payload.topics()) {
                writeUnsafeUTF8String(buf, topicName);
            }

            return buf;
        } finally {
            propertiesBuf.release();
        }
    }

    private static ByteBuf encodeSubAckMessage(
            ChannelHandlerContext ctx,
            MqttSubAckMessage message) {
        MqttVersion mqttVersion = getMqttVersion(ctx);
        ByteBuf propertiesBuf = encodePropertiesIfNeeded(mqttVersion,
                ctx.alloc(),
                message.idAndPropertiesVariableHeader().properties());
        try {
            int variableHeaderBufferSize = 2 + propertiesBuf.readableBytes();
            int payloadBufferSize = message.payload().grantedQoSLevels().size();
            int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
            int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
            ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variablePartSize);
            buf.writeByte(getFixedHeaderByte1(message.fixedHeader()));
            writeVariableLengthInt(buf, variablePartSize);
            buf.writeShort(message.variableHeader().messageId());
            buf.writeBytes(propertiesBuf);
            for (int qos : message.payload().grantedQoSLevels()) {
                buf.writeByte(qos);
            }

            return buf;
        } finally {
            propertiesBuf.release();
        }
    }

    private static ByteBuf encodeUnsubAckMessage(
            ChannelHandlerContext ctx,
            MqttUnsubAckMessage message) {
        if (message.variableHeader() instanceof  MqttMessageIdAndPropertiesVariableHeader) {
            MqttVersion mqttVersion = getMqttVersion(ctx);
            ByteBuf propertiesBuf = encodePropertiesIfNeeded(mqttVersion,
                    ctx.alloc(),
                    message.idAndPropertiesVariableHeader().properties());
            try {
                int variableHeaderBufferSize = 2 + propertiesBuf.readableBytes();
                int payloadBufferSize = message.payload().unsubscribeReasonCodes().size();
                int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
                int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
                ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variablePartSize);
                buf.writeByte(getFixedHeaderByte1(message.fixedHeader()));
                writeVariableLengthInt(buf, variablePartSize);
                buf.writeShort(message.variableHeader().messageId());
                buf.writeBytes(propertiesBuf);

                for (Short reasonCode : message.payload().unsubscribeReasonCodes()) {
                    buf.writeByte(reasonCode);
                }

                return buf;
            } finally {
                propertiesBuf.release();
            }
        } else {
            return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(ctx.alloc(), message);
        }
    }

    private static ByteBuf encodePublishMessage(
            ChannelHandlerContext ctx,
            MqttPublishMessage message) {
        MqttVersion mqttVersion = getMqttVersion(ctx);
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttPublishVariableHeader variableHeader = message.variableHeader();
        ByteBuf payload = message.payload().duplicate();

        String topicName = variableHeader.topicName();
        int topicNameBytes = utf8Bytes(topicName);

        ByteBuf propertiesBuf = encodePropertiesIfNeeded(mqttVersion,
                ctx.alloc(),
                message.variableHeader().properties());

        try {
            int variableHeaderBufferSize = 2 + topicNameBytes +
                    (mqttFixedHeader.qosLevel().value() > 0 ? 2 : 0) + propertiesBuf.readableBytes();
            int payloadBufferSize = payload.readableBytes();
            int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
            int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

            ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variablePartSize);
            buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
            writeVariableLengthInt(buf, variablePartSize);
            writeExactUTF8String(buf, topicName, topicNameBytes);
            if (mqttFixedHeader.qosLevel().value() > 0) {
                buf.writeShort(variableHeader.packetId());
            }
            buf.writeBytes(propertiesBuf);
            buf.writeBytes(payload);

            return buf;
        } finally {
            propertiesBuf.release();
        }
    }

    private static ByteBuf encodePubReplyMessage(ChannelHandlerContext ctx,
                                          MqttMessage message) {
        if (message.variableHeader() instanceof MqttPubReplyMessageVariableHeader) {
            MqttFixedHeader mqttFixedHeader = message.fixedHeader();
            MqttPubReplyMessageVariableHeader variableHeader =
                    (MqttPubReplyMessageVariableHeader) message.variableHeader();
            int msgId = variableHeader.messageId();

            final ByteBuf propertiesBuf;
            final boolean includeReasonCode;
            final int variableHeaderBufferSize;
            final MqttVersion mqttVersion = getMqttVersion(ctx);
            if (mqttVersion == MqttVersion.MQTT_5 &&
                    (variableHeader.reasonCode() != MqttPubReplyMessageVariableHeader.REASON_CODE_OK ||
                            !variableHeader.properties().isEmpty())) {
                propertiesBuf = encodeProperties(ctx.alloc(), variableHeader.properties());
                includeReasonCode = true;
                variableHeaderBufferSize = 3 + propertiesBuf.readableBytes();
            } else {
                propertiesBuf = Unpooled.EMPTY_BUFFER;
                includeReasonCode = false;
                variableHeaderBufferSize = 2;
            }

            try {
                final int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
                ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variableHeaderBufferSize);
                buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
                writeVariableLengthInt(buf, variableHeaderBufferSize);
                buf.writeShort(msgId);
                if (includeReasonCode) {
                    buf.writeByte(variableHeader.reasonCode());
                }
                buf.writeBytes(propertiesBuf);

                return buf;
            } finally {
                propertiesBuf.release();
            }
        } else {
            return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(ctx.alloc(), message);
        }
    }

    private static ByteBuf encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(
            ByteBufAllocator byteBufAllocator,
            MqttMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
        int msgId = variableHeader.messageId();

        int variableHeaderBufferSize = 2; // variable part only has a message id
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variableHeaderBufferSize);
        buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
        writeVariableLengthInt(buf, variableHeaderBufferSize);
        buf.writeShort(msgId);

        return buf;
    }

    private static ByteBuf encodeReasonCodePlusPropertiesMessage(
            ChannelHandlerContext ctx,
            MqttMessage message) {
        if (message.variableHeader() instanceof MqttReasonCodeAndPropertiesVariableHeader) {
            MqttVersion mqttVersion = getMqttVersion(ctx);
            MqttFixedHeader mqttFixedHeader = message.fixedHeader();
            MqttReasonCodeAndPropertiesVariableHeader variableHeader =
                    (MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();

            final ByteBuf propertiesBuf;
            final boolean includeReasonCode;
            final int variableHeaderBufferSize;
            if (mqttVersion == MqttVersion.MQTT_5 &&
                    (variableHeader.reasonCode() != MqttReasonCodeAndPropertiesVariableHeader.REASON_CODE_OK ||
                            !variableHeader.properties().isEmpty())) {
                propertiesBuf = encodeProperties(ctx.alloc(), variableHeader.properties());
                includeReasonCode = true;
                variableHeaderBufferSize = 1 + propertiesBuf.readableBytes();
            } else {
                propertiesBuf = Unpooled.EMPTY_BUFFER;
                includeReasonCode = false;
                variableHeaderBufferSize = 0;
            }

            try {
                final int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
                ByteBuf buf = ctx.alloc().buffer(fixedHeaderBufferSize + variableHeaderBufferSize);
                buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
                writeVariableLengthInt(buf, variableHeaderBufferSize);
                if (includeReasonCode) {
                    buf.writeByte(variableHeader.reasonCode());
                }
                buf.writeBytes(propertiesBuf);

                return buf;
            } finally {
                propertiesBuf.release();
            }
        } else {
            return encodeMessageWithOnlySingleByteFixedHeader(ctx.alloc(), message);
        }
    }

    private static ByteBuf encodeMessageWithOnlySingleByteFixedHeader(
            ByteBufAllocator byteBufAllocator,
            MqttMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        ByteBuf buf = byteBufAllocator.buffer(2);
        buf.writeByte(getFixedHeaderByte1(mqttFixedHeader));
        buf.writeByte(0);

        return buf;
    }

    private static ByteBuf encodePropertiesIfNeeded(MqttVersion mqttVersion,
                                             ByteBufAllocator byteBufAllocator,
                                             MqttProperties mqttProperties) {
        if (mqttVersion == MqttVersion.MQTT_5) {
            return encodeProperties(byteBufAllocator, mqttProperties);
        }
        return Unpooled.EMPTY_BUFFER;
    }

    private static ByteBuf encodeProperties(ByteBufAllocator byteBufAllocator,
                                            MqttProperties mqttProperties) {
        ByteBuf propertiesHeaderBuf = byteBufAllocator.buffer();
        // encode also the Properties part
        try {
            ByteBuf propertiesBuf = byteBufAllocator.buffer();
            try {
                for (MqttProperties.MqttProperty property : mqttProperties.listAll()) {
                    MqttProperties.MqttPropertyType propertyType =
                            MqttProperties.MqttPropertyType.valueOf(property.propertyId);
                    switch (propertyType) {
                        case PAYLOAD_FORMAT_INDICATOR:
                        case REQUEST_PROBLEM_INFORMATION:
                        case REQUEST_RESPONSE_INFORMATION:
                        case MAXIMUM_QOS:
                        case RETAIN_AVAILABLE:
                        case WILDCARD_SUBSCRIPTION_AVAILABLE:
                        case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                        case SHARED_SUBSCRIPTION_AVAILABLE:
                            writeVariableLengthInt(propertiesBuf, property.propertyId);
                            final byte bytePropValue = ((MqttProperties.IntegerProperty) property).value.byteValue();
                            propertiesBuf.writeByte(bytePropValue);
                            break;
                        case SERVER_KEEP_ALIVE:
                        case RECEIVE_MAXIMUM:
                        case TOPIC_ALIAS_MAXIMUM:
                        case TOPIC_ALIAS:
                            writeVariableLengthInt(propertiesBuf, property.propertyId);
                            final short twoBytesInPropValue =
                                    ((MqttProperties.IntegerProperty) property).value.shortValue();
                            propertiesBuf.writeShort(twoBytesInPropValue);
                            break;
                        case PUBLICATION_EXPIRY_INTERVAL:
                        case SESSION_EXPIRY_INTERVAL:
                        case WILL_DELAY_INTERVAL:
                        case MAXIMUM_PACKET_SIZE:
                            writeVariableLengthInt(propertiesBuf, property.propertyId);
                            final int fourBytesIntPropValue = ((MqttProperties.IntegerProperty) property).value;
                            propertiesBuf.writeInt(fourBytesIntPropValue);
                            break;
                        case SUBSCRIPTION_IDENTIFIER:
                            writeVariableLengthInt(propertiesBuf, property.propertyId);
                            final int vbi = ((MqttProperties.IntegerProperty) property).value;
                            writeVariableLengthInt(propertiesBuf, vbi);
                            break;
                        case CONTENT_TYPE:
                        case RESPONSE_TOPIC:
                        case ASSIGNED_CLIENT_IDENTIFIER:
                        case AUTHENTICATION_METHOD:
                        case RESPONSE_INFORMATION:
                        case SERVER_REFERENCE:
                        case REASON_STRING:
                            writeVariableLengthInt(propertiesBuf, property.propertyId);
                            writeEagerUTF8String(propertiesBuf, ((MqttProperties.StringProperty) property).value);
                            break;
                        case USER_PROPERTY:
                            final List<MqttProperties.StringPair> pairs =
                                    ((MqttProperties.UserProperties) property).value;
                            for (MqttProperties.StringPair pair : pairs) {
                                writeVariableLengthInt(propertiesBuf, property.propertyId);
                                writeEagerUTF8String(propertiesBuf, pair.key);
                                writeEagerUTF8String(propertiesBuf, pair.value);
                            }
                            break;
                        case CORRELATION_DATA:
                        case AUTHENTICATION_DATA:
                            writeVariableLengthInt(propertiesBuf, property.propertyId);
                            final byte[] binaryPropValue = ((MqttProperties.BinaryProperty) property).value;
                            propertiesBuf.writeShort(binaryPropValue.length);
                            propertiesBuf.writeBytes(binaryPropValue, 0, binaryPropValue.length);
                            break;
                        default:
                            //shouldn't reach here
                            throw new EncoderException("Unknown property type: " + propertyType);
                    }
                }
                writeVariableLengthInt(propertiesHeaderBuf, propertiesBuf.readableBytes());
                propertiesHeaderBuf.writeBytes(propertiesBuf);

                return propertiesHeaderBuf;
            } finally {
                propertiesBuf.release();
            }
        } catch (RuntimeException e) {
            propertiesHeaderBuf.release();
            throw e;
        }
    }

    private static int getFixedHeaderByte1(MqttFixedHeader header) {
        int ret = 0;
        ret |= header.messageType().value() << 4;
        if (header.isDup()) {
            ret |= 0x08;
        }
        ret |= header.qosLevel().value() << 1;
        if (header.isRetain()) {
            ret |= 0x01;
        }
        return ret;
    }

    private static void writeVariableLengthInt(ByteBuf buf, int num) {
        do {
            int digit = num % 128;
            num /= 128;
            if (num > 0) {
                digit |= 0x80;
            }
            buf.writeByte(digit);
        } while (num > 0);
    }

    private static int nullableUtf8Bytes(String s) {
        return s == null? 0 : utf8Bytes(s);
    }

    private static int nullableMaxUtf8Bytes(String s) {
        return s == null? 0 : utf8MaxBytes(s);
    }

    private static void writeExactUTF8String(ByteBuf buf, String s, int utf8Length) {
        buf.ensureWritable(utf8Length + 2);
        buf.writeShort(utf8Length);
        if (utf8Length > 0) {
            final int writtenUtf8Length = reserveAndWriteUtf8(buf, s, utf8Length);
            assert writtenUtf8Length == utf8Length;
        }
    }

    private static void writeEagerUTF8String(ByteBuf buf, String s) {
        final int maxUtf8Length = nullableMaxUtf8Bytes(s);
        buf.ensureWritable(maxUtf8Length + 2);
        final int writerIndex = buf.writerIndex();
        final int startUtf8String = writerIndex + 2;
        buf.writerIndex(startUtf8String);
        final int utf8Length = s != null? reserveAndWriteUtf8(buf, s, maxUtf8Length) : 0;
        buf.setShort(writerIndex, utf8Length);
    }

    private static void writeUnsafeUTF8String(ByteBuf buf, String s) {
        final int writerIndex = buf.writerIndex();
        final int startUtf8String = writerIndex + 2;
        // no need to reserve any capacity here, already done earlier: that's why is Unsafe
        buf.writerIndex(startUtf8String);
        final int utf8Length = s != null? reserveAndWriteUtf8(buf, s, 0) : 0;
        buf.setShort(writerIndex, utf8Length);
    }

    private static int getVariableLengthInt(int num) {
        int count = 0;
        do {
            num /= 128;
            count++;
        } while (num > 0);
        return count;
    }

}
