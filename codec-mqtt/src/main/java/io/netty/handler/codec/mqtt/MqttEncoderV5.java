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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.mqtt.MqttEncoder.PacketSection;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;

import java.util.List;

/**
 * Encodes Mqtt messages into bytes following the protocol specification v5
 */
@ChannelHandler.Sharable
public final class MqttEncoderV5 extends MessageToMessageEncoder<MqttMessage> {

    public static final MqttEncoderV5 INSTANCE = new MqttEncoderV5();

    private MqttEncoderV5() { }

    @Override
    protected void encode(ChannelHandlerContext ctx, MqttMessage msg, List<Object> out) throws Exception {
        out.add(doEncode(ctx.alloc(), msg));
    }

    /**
     * This is the main encoding method.
     * It's only visible for testing.
     *
     * @param byteBufAllocator Allocates ByteBuf
     * @param message MQTT message to encode
     * @return ByteBuf with encoded bytes
     */
    static ByteBuf doEncode(ByteBufAllocator byteBufAllocator, MqttMessage message) {

        switch (message.fixedHeader().messageType()) {
            case CONNECT:
                return encodeConnectMessage(byteBufAllocator, (MqttConnectMessage) message);

            case CONNACK:
                return encodeConnAckMessage(byteBufAllocator, (MqttConnAckMessage) message);

            case PUBLISH:
                return encodePublishMessage(byteBufAllocator, (MqttPublishMessage) message);

            case SUBSCRIBE:
                return encodeSubscribeMessage(byteBufAllocator, (MqttSubscribeMessage) message);

            case SUBACK:
                return encodeSubAckMessage(byteBufAllocator, (MqttSubAckMessage) message);

            case UNSUBACK:
                return encodeUnsubAckMessage(byteBufAllocator, (MqttUnsubAckMessage) message);

            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                return encodePubReplyMessage(byteBufAllocator, message);

            case DISCONNECT:
                return encodeReasonCodePlusPropertiesMessage(byteBufAllocator, message.fixedHeader(),
                        ((MqttDisconnectMessage) message).variableHeader());

            case AUTH:
                return encodeReasonCodePlusPropertiesMessage(byteBufAllocator, message.fixedHeader(),
                        ((MqttAuthMessage) message).variableHeader());

            default:
                throw new IllegalArgumentException(
                        "Unknown message type: " + message.fixedHeader().messageType().value());
        }
    }

    private static ByteBuf encodeConnectMessage(
            ByteBufAllocator byteBufAllocator,
            MqttConnectMessage message) {

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttConnectVariableHeader variableHeader = message.variableHeader();

        // as MQTT 3.1 & 3.1.1 spec, If the User Name Flag is set to 0, the Password Flag MUST be set to 0
        if (!variableHeader.hasUserName() && variableHeader.hasPassword()) {
            throw new DecoderException("Without a username, the password MUST be not set");
        }

        PacketSection payloadSection = MqttEncoder.encodePayload(message, byteBufAllocator);

        // Variable header
        PacketSection variableHeaderSection = encodeVariableHeaderWithProperties(byteBufAllocator,
                variableHeader, payloadSection.bufferSize);
        int variablePartSize = variableHeaderSection.bufferSize + payloadSection.bufferSize;

        // Fixed header
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variablePartSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(mqttFixedHeader));

        buf.writeBytes(variableHeaderSection.byteBuf);

        // Payload
        buf.writeBytes(payloadSection.byteBuf);
        return buf;
    }

    private static PacketSection encodeVariableHeaderWithProperties(
            ByteBufAllocator byteBufAllocator,
            MqttConnectVariableHeader variableHeader,
            int payloadSize) {
        MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(variableHeader.name(),
                (byte) variableHeader.version());

        PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        byte[] protocolNameBytes = mqttVersion.protocolNameBytes();
        int variableHeaderBufferSize = 2 + protocolNameBytes.length + 4;
        variableHeaderBufferSize += propertiesSection.bufferSize;
        int variablePartSize = variableHeaderBufferSize + payloadSize;

        ByteBuf buf = byteBufAllocator.buffer(variableHeaderBufferSize);
        EncodersUtils.writeVariableLengthInt(buf, variablePartSize);

        buf.writeShort(protocolNameBytes.length);
        buf.writeBytes(protocolNameBytes);

        buf.writeByte(variableHeader.version());
        buf.writeByte(EncodersUtils.getConnVariableHeaderFlag(variableHeader));
        buf.writeShort(variableHeader.keepAliveTimeSeconds());

        // write the properties
        buf.writeBytes(propertiesSection.byteBuf);

        return new PacketSection(variableHeaderBufferSize, buf);
    }

    private static PacketSection encodeProperties(ByteBufAllocator byteBufAllocator,
                                                  MqttProperties mqttProperties) {
        ByteBuf propertiesHeaderBuf = byteBufAllocator.buffer();
        // encode also the Properties part
        ByteBuf propertiesBuf = byteBufAllocator.buffer();
        for (MqttProperties.MqttProperty property : mqttProperties.listAll()) {
            EncodersUtils.writeVariableLengthInt(propertiesBuf, property.propertyId);
            switch (MqttPropertyType.valueOf(property.propertyId)) {
                case PAYLOAD_FORMAT_INDICATOR:
                case REQUEST_PROBLEM_INFORMATION:
                case REQUEST_RESPONSE_INFORMATION:
                case MAXIMUM_QOS:
                case RETAIN_AVAILABLE:
                case WILDCARD_SUBSCRIPTION_AVAILABLE:
                case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                case SHARED_SUBSCRIPTION_AVAILABLE:
                    final byte bytePropValue = ((MqttProperties.IntegerProperty) property).value.byteValue();
                    propertiesBuf.writeByte(bytePropValue);
                    break;
                case SERVER_KEEP_ALIVE:
                case RECEIVE_MAXIMUM:
                case TOPIC_ALIAS_MAXIMUM:
                case TOPIC_ALIAS:
                    final short twoBytesInPropValue = ((MqttProperties.IntegerProperty) property).value.shortValue();
                    propertiesBuf.writeShort(twoBytesInPropValue);
                    break;
                case PUBLICATION_EXPIRY_INTERVAL:
                case SESSION_EXPIRY_INTERVAL:
                case WILL_DELAY_INTERVAL:
                case MAXIMUM_PACKET_SIZE:
                    final int fourBytesIntPropValue = ((MqttProperties.IntegerProperty) property).value;
                    propertiesBuf.writeInt(fourBytesIntPropValue);
                    break;
                case SUBSCRIPTION_IDENTIFIER:
                    final int vbi = ((MqttProperties.IntegerProperty) property).value;
                    EncodersUtils.writeVariableLengthInt(propertiesBuf, vbi);
                    break;
                case CONTENT_TYPE:
                case RESPONSE_TOPIC:
                case ASSIGNED_CLIENT_IDENTIFIER:
                case AUTHENTICATION_METHOD:
                case RESPONSE_INFORMATION:
                case SERVER_REFERENCE:
                case REASON_STRING:
                case USER_PROPERTY:
                    final String strPropValue = ((MqttProperties.StringProperty) property).value;
                    EncodersUtils.writeUTF8String(propertiesBuf, strPropValue);
                    break;
                case CORRELATION_DATA:
                case AUTHENTICATION_DATA:
                    final byte[] binaryPropValue = ((MqttProperties.BinaryProperty) property).value;
                    propertiesBuf.writeShort(binaryPropValue.length);
                    propertiesBuf.writeBytes(binaryPropValue, 0, binaryPropValue.length);
                    break;
            }
            EncodersUtils.writeVariableLengthInt(propertiesHeaderBuf, propertiesBuf.readableBytes());
            propertiesHeaderBuf.writeBytes(propertiesBuf);
        }

        int propertiesHeaderSize = propertiesHeaderBuf.readableBytes();
        return new PacketSection(propertiesHeaderSize, propertiesHeaderBuf);
    }

    private static ByteBuf encodeConnAckMessage(ByteBufAllocator byteBufAllocator, MqttConnAckMessage message) {
        PacketSection propertiesSection = encodeProperties(byteBufAllocator,
                message.variableHeader().properties());

        ByteBuf buf = byteBufAllocator.buffer(4 + propertiesSection.bufferSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(message.fixedHeader()));
        EncodersUtils.writeVariableLengthInt(buf, 2 + propertiesSection.bufferSize);
        buf.writeByte(message.variableHeader().isSessionPresent() ? 0x01 : 0x00);
        buf.writeByte(message.variableHeader().connectReturnCode().byteValue());
        buf.writeBytes(propertiesSection.byteBuf);
        return buf;
    }

    private static ByteBuf encodePublishMessage(
            ByteBufAllocator byteBufAllocator,
            MqttPublishMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttPublishVariableHeader variableHeader = message.variableHeader();
        ByteBuf payload = message.payload().duplicate();

        PacketSection variableHeaderSection = encodeVariableHeaderWithPropeties(byteBufAllocator,
                mqttFixedHeader, variableHeader);

        int payloadBufferSize = payload.readableBytes();
        int variablePartSize = variableHeaderSection.bufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variablePartSize);

        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(mqttFixedHeader));
        EncodersUtils.writeVariableLengthInt(buf, variablePartSize);

        buf.writeBytes(variableHeaderSection.byteBuf);

        buf.writeBytes(payload);

        return buf;
    }

    private static PacketSection encodeVariableHeaderWithPropeties(
            ByteBufAllocator byteBufAllocator,
            MqttFixedHeader mqttFixedHeader,
            MqttPublishVariableHeader variableHeader) {
        String topicName = variableHeader.topicName();
        byte[] topicNameBytes = EncodersUtils.encodeStringUtf8(topicName);

        PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        int variableHeaderBufferSize = 2 + topicNameBytes.length +
                (mqttFixedHeader.qosLevel().value() > 0 ? 2 : 0);
        variableHeaderBufferSize += propertiesSection.bufferSize;

        ByteBuf variableHeaderBuf = byteBufAllocator.buffer(variableHeaderBufferSize);
        variableHeaderBuf.writeShort(topicNameBytes.length);
        variableHeaderBuf.writeBytes(topicNameBytes);
        if (mqttFixedHeader.qosLevel().value() > 0) {
            variableHeaderBuf.writeShort(variableHeader.messageId());
        }

        // write the properties
        variableHeaderBuf.writeBytes(propertiesSection.byteBuf);

        return new PacketSection(variableHeaderBufferSize, variableHeaderBuf);
    }

    private static ByteBuf encodePubReplyMessage(
            ByteBufAllocator byteBufAllocator,
            MqttMessage message) {
        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttPubReplyMessageVariableHeader variableHeader = (MqttPubReplyMessageVariableHeader) message.variableHeader();
        int msgId = variableHeader.messageId();

        final PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        int variableHeaderBufferSize = 3 + propertiesSection.bufferSize;
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variableHeaderBufferSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variableHeaderBufferSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(mqttFixedHeader));
        EncodersUtils.writeVariableLengthInt(buf, variableHeaderBufferSize);
        buf.writeShort(msgId);
        buf.writeByte(variableHeader.reasonCode());
        buf.writeBytes(propertiesSection.byteBuf);

        return buf;
    }

    private static ByteBuf encodeSubAckMessage(
            ByteBufAllocator byteBufAllocator,
            MqttSubAckMessage message) {
        final MqttMessageIdPlusPropertiesVariableHeader variableHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader();

        final PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        int variableHeaderBufferSize = 2;
        int payloadBufferSize = message.payload().grantedQoSLevels().size();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize + propertiesSection.bufferSize;
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variablePartSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(message.fixedHeader()));
        EncodersUtils.writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(variableHeader.messageId());

        buf.writeBytes(propertiesSection.byteBuf);

        for (int qos : message.payload().grantedQoSLevels()) {
            buf.writeByte(qos);
        }

        return buf;
    }

    private static ByteBuf encodeUnsubAckMessage(
            ByteBufAllocator byteBufAllocator,
            MqttUnsubAckMessage message) {
        final MqttMessageIdPlusPropertiesVariableHeader variableHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader();

        final PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        int variableHeaderBufferSize = 2;
        int payloadBufferSize = message.payload().unsubscribeReasonCodes().size();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize + propertiesSection.bufferSize;
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variablePartSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(message.fixedHeader()));
        EncodersUtils.writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(variableHeader.messageId());

        buf.writeBytes(propertiesSection.byteBuf);

        for (Short reasonCode : message.payload().unsubscribeReasonCodes()) {
            buf.writeByte(reasonCode);
        }

        return buf;
    }

    private static ByteBuf encodeSubscribeMessage(
            ByteBufAllocator byteBufAllocator,
            MqttSubscribeMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        final MqttMessageIdPlusPropertiesVariableHeader variableHeader =
                (MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader();

        final PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        MqttFixedHeader mqttFixedHeader = message.fixedHeader();
        MqttSubscribePayload payload = message.payload();

        for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
            String topicName = topic.topicName();
            byte[] topicNameBytes = EncodersUtils.encodeStringUtf8(topicName);
            payloadBufferSize += 2 + topicNameBytes.length;
            payloadBufferSize += 1;
        }

        int variablePartSize = variableHeaderBufferSize + payloadBufferSize + propertiesSection.bufferSize;
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variablePartSize);

        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(mqttFixedHeader));
        EncodersUtils.writeVariableLengthInt(buf, variablePartSize);

        // Variable Header
        int messageId = variableHeader.messageId();
        buf.writeShort(messageId);
        buf.writeBytes(propertiesSection.byteBuf);

        // Payload
        for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
            String topicName = topic.topicName();
            EncodersUtils.writeUTF8String(buf, topicName);

            final SubscriptionOption option = topic.option();

            int ret = 0;
            ret |= option.retainHandling().value() << 4;
            if (option.isRetainAsPublished()) {
                ret |= 0x08;
            }
            if (option.isNoLocal()) {
                ret |= 0x04;
            }
            ret |= option.qos().value();

            buf.writeByte(ret);
        }

        return buf;
    }

    private static ByteBuf encodeReasonCodePlusPropertiesMessage(
            ByteBufAllocator byteBufAllocator,
            MqttFixedHeader mqttFixedHeader,
            MqttReasonCodePlusPropertiesVariableHeader variableHeader) {

        final PacketSection propertiesSection = encodeProperties(byteBufAllocator, variableHeader.properties());

        int variablePartSize = 1 + propertiesSection.bufferSize;
        int fixedHeaderBufferSize = 1 + EncodersUtils.getVariableLengthInt(variablePartSize);

        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(EncodersUtils.getFixedHeaderByte1(mqttFixedHeader));
        EncodersUtils.writeVariableLengthInt(buf, variablePartSize);

        // Variable Header
        buf.writeByte(variableHeader.reasonCode());
        buf.writeBytes(propertiesSection.byteBuf);

        return buf;
    }
}
