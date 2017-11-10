package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.mqtt.MqttEncoder.PacketSection;

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

            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                return encodePubReplyMessage(byteBufAllocator, message);

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

    private static PacketSection encodeVariableHeaderWithProperties(ByteBufAllocator byteBufAllocator,
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
            switch (property.propertyId) {
                case 0x01: // Payload Format Indicator => Byte
                case 0x17: // Request Problem Information
                case 0x19: // Request Response Information
                case 0x24: // Maximum QoS
                case 0x25: // Retain Available
                case 0x28: // Wildcard Subscription Available
                case 0x29: // Subscription Identifier Available
                case 0x2A: // Shared Subscription Available
                    final byte bytePropValue = ((MqttProperties.IntegerProperty) property).value.byteValue();
                    propertiesBuf.writeByte(bytePropValue);
                    break;
                case 0x13: // Server Keep Alive => Two Byte Integer
                case 0x21: // Receive Maximum
                case 0x22: // Topic Alias Maximum
                case 0x23: // Topic Alias
                    final short twoBytesInPropValue = ((MqttProperties.IntegerProperty) property).value.shortValue();
                    propertiesBuf.writeShort(twoBytesInPropValue);
                    break;
                case 0x02: // Publication Expiry Interval => Four Byte Integer
                case 0x11: // Session Expiry Interval
                case 0x18: // Will Delay Interval
                case 0x27: // Maximum Packet Size
                    final int fourBytesIntPropValue = ((MqttProperties.IntegerProperty) property).value;
                    propertiesBuf.writeInt(fourBytesIntPropValue);
                    break;
                case 0x0B: // Subscription Identifier => Variable Byte Integer
                    final int vbi = ((MqttProperties.IntegerProperty) property).value;
                    EncodersUtils.writeVariableLengthInt(propertiesBuf, vbi);
                    break;
                case 0x03: // Content Type => UTF-8 Encoded String
                case 0x08: // Response Topic
                case 0x12: // Assigned Client Identifier
                case 0x15: // Authentication Method
                case 0x1A: // Response Information
                case 0x1C: // Server Reference
                case 0x1F: // Reason String
                case 0x26: // User Property
                    final String strPropValue = ((MqttProperties.StringProperty) property).value;
                    EncodersUtils.writeUTF8String(propertiesBuf, strPropValue);
                    break;
                case 0x09: // Correlation Data => Binary Data
                case 0x16: // Authentication Data
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
//        buf.writeByte(2);
        EncodersUtils.writeVariableLengthInt(buf, 2 +propertiesSection.bufferSize);
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

    private static PacketSection encodeVariableHeaderWithPropeties(ByteBufAllocator byteBufAllocator,
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

        int variableHeaderBufferSize = 3 + propertiesSection.bufferSize; // variable part only has a message id, reason code and properties
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
                ((MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader());

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


    private static ByteBuf encodeSubscribeMessage(
            ByteBufAllocator byteBufAllocator,
            MqttSubscribeMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        final MqttMessageIdPlusPropertiesVariableHeader variableHeader =
                ((MqttMessageIdPlusPropertiesVariableHeader) message.variableHeader());

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
}
