package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidMessageId;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidPublishTopicName;
import static io.netty.handler.codec.mqtt.MqttDecoder.*;

/**
 * Used to decode only the Variable Header part of MQTT packets, which is what differs between v3.1.1 and v5
 */
final class VariableHeaderDecoderV5  implements IVariableHeaderDecoder {
    /**
     * Decodes the variable header (if any)
     * @param buffer the buffer to decode from
     * @param mqttFixedHeader MqttFixedHeader of the same message
     * @return the variable header
     */
    @Override
    public Result<?> decodeVariableHeader(ByteBuf buffer, MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
                return decodeConnectionVariableHeader(buffer);

            case CONNACK:
                return decodeConnAckVariableHeader(buffer);

            case UNSUBSCRIBE:
                return decodeMessageIdVariableHeader(buffer);

            case SUBSCRIBE:
            case SUBACK:
            case UNSUBACK:
                return decodeMessageIdPlusPropertiesVariableHeader(buffer);

            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case PUBREL:
                return decodePubReplyMessage(buffer);

            case PUBLISH:
                return decodePublishVariableHeader(buffer, mqttFixedHeader);

            case DISCONNECT:
                return decodeDisconnectVariableHeader(buffer);

            case PINGREQ:
            case PINGRESP:
                // Empty variable header
                return new Result<Object>(null, 0);
        }
        return new Result<Object>(null, 0); //should never reach here
    }

    private static Result<MqttConnectVariableHeader> decodeConnectionVariableHeader(ByteBuf buffer) {
        final Result<String> protoString = decodeString(buffer);
        int numberOfBytesConsumed = protoString.numberOfBytesConsumed;

        final byte protocolLevel = buffer.readByte();
        numberOfBytesConsumed += 1;

        final MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(protoString.value, protocolLevel);

        final int b1 = buffer.readUnsignedByte();
        numberOfBytesConsumed += 1;

        final Result<Integer> keepAlive = decodeMsbLsb(buffer);
        numberOfBytesConsumed += keepAlive.numberOfBytesConsumed;

        final boolean hasUserName = (b1 & 0x80) == 0x80;
        final boolean hasPassword = (b1 & 0x40) == 0x40;
        final boolean willRetain = (b1 & 0x20) == 0x20;
        final int willQos = (b1 & 0x18) >> 3;
        final boolean willFlag = (b1 & 0x04) == 0x04;
        final boolean cleanSession = (b1 & 0x02) == 0x02;
        if (mqttVersion == MqttVersion.MQTT_3_1_1) {
            final boolean zeroReservedFlag = (b1 & 0x01) == 0x0;
            if (!zeroReservedFlag) {
                // MQTT v3.1.1: The Server MUST validate that the reserved flag in the CONNECT Control Packet is
                // set to zero and disconnect the Client if it is not zero.
                // See http://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc385349230
                throw new DecoderException("non-zero reserved flag");
            }
        }

        final Result<MqttProperties> properties = decodeProperties(buffer);
        numberOfBytesConsumed += properties.numberOfBytesConsumed;

        final MqttConnectVariableHeader mqttConnectVariableHeader = new MqttConnectVariableHeader(
                mqttVersion.protocolName(),
                mqttVersion.protocolLevel(),
                hasUserName,
                hasPassword,
                willRetain,
                willQos,
                willFlag,
                cleanSession,
                keepAlive.value,
                properties.value);
        return new Result<MqttConnectVariableHeader>(mqttConnectVariableHeader, numberOfBytesConsumed);
    }

    private static Result<MqttConnAckVariableHeader> decodeConnAckVariableHeader(ByteBuf buffer) {
        final boolean sessionPresent = (buffer.readUnsignedByte() & 0x01) == 0x01;
        byte returnCode = buffer.readByte();
        int numberOfBytesConsumed = 2;

        final Result<MqttProperties> properties = decodeProperties(buffer);
        numberOfBytesConsumed += properties.numberOfBytesConsumed;

        final MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(MqttConnectReturnCode.valueOf(returnCode), sessionPresent,
                        properties.value);

        return new Result<MqttConnAckVariableHeader>(mqttConnAckVariableHeader, numberOfBytesConsumed);
    }

    private static Result<MqttMessageIdVariableHeader> decodeMessageIdVariableHeader(ByteBuf buffer) {
        final Result<Integer> messageId = decodeMessageId(buffer);
        return new Result<MqttMessageIdVariableHeader>(
                MqttMessageIdVariableHeader.from(messageId.value),
                messageId.numberOfBytesConsumed);
    }

    private static Result<MqttPublishVariableHeader> decodePublishVariableHeader(
            ByteBuf buffer,
            MqttFixedHeader mqttFixedHeader) {
        final Result<String> decodedTopic = decodeString(buffer);
        if (!isValidPublishTopicName(decodedTopic.value)) {
            throw new DecoderException("invalid publish topic name: " + decodedTopic.value + " (contains wildcards)");
        }
        int numberOfBytesConsumed = decodedTopic.numberOfBytesConsumed;

        int messageId = -1;
        if (mqttFixedHeader.qosLevel().value() > 0) {
            final Result<Integer> decodedMessageId = decodeMessageId(buffer);
            messageId = decodedMessageId.value;
            numberOfBytesConsumed += decodedMessageId.numberOfBytesConsumed;
        }
        final Result<MqttProperties> properties = decodeProperties(buffer);
        numberOfBytesConsumed += properties.numberOfBytesConsumed;

        final MqttPublishVariableHeader mqttPublishVariableHeader =
                new MqttPublishVariableHeader(decodedTopic.value, messageId, properties.value);
        return new Result<MqttPublishVariableHeader>(mqttPublishVariableHeader, numberOfBytesConsumed);
    }

    private static Result<MqttProperties> decodeProperties(ByteBuf buffer) {
        final Result<Integer> propertiesLength = decodeVariableByteInteger(buffer);
        int totalPropertiesLength = propertiesLength.value;
        int numberOfBytesConsumed = propertiesLength.numberOfBytesConsumed;

        MqttProperties decodedProperties = new MqttProperties();
        while (numberOfBytesConsumed < totalPropertiesLength) {
            Result<Integer> propertyId = decodeVariableByteInteger(buffer);
            numberOfBytesConsumed += propertyId.numberOfBytesConsumed;

            switch (propertyId.value) {
                case 0x01: // Payload Format Indicator => Byte
                case 0x17: // Request Problem Information
                case 0x19: // Request Response Information
                case 0x24: // Maximum QoS
                case 0x25: // Retain Available
                case 0x28: // Wildcard Subscription Available
                case 0x29: // Subscription Identifier Available
                case 0x2A: // Shared Subscription Available
                    final int b1 = buffer.readUnsignedByte();
                    numberOfBytesConsumed ++;
                    decodedProperties.add(new MqttProperties.IntegerProperty(propertyId.value, b1));
                    break;
                case 0x13: // Server Keep Alive => Two Byte Integer
                case 0x21: // Receive Maximum
                case 0x22: // Topic Alias Maximum
                case 0x23: // Topic Alias
                    final Result<Integer> int2BytesResult = decodeMsbLsb(buffer);
                    numberOfBytesConsumed += int2BytesResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.IntegerProperty(propertyId.value, int2BytesResult.value));
                    break;
                case 0x02: // Publication Expiry Interval => Four Byte Integer
                case 0x11: // Session Expiry Interval
                case 0x18: // Will Delay Interval
                case 0x27: // Maximum Packet Size
                    final Result<Integer> int4BytesResult = decode4bytesInteger(buffer);
                    numberOfBytesConsumed += int4BytesResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.IntegerProperty(propertyId.value, int4BytesResult.value));
                    break;
                case 0x0B: // Subscription Identifier => Variable Byte Integer
                    Result<Integer> vbIntegerResult = decodeVariableByteInteger(buffer);
                    numberOfBytesConsumed += vbIntegerResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.IntegerProperty(propertyId.value, vbIntegerResult.value));
                    break;
                case 0x03: // Content Type => UTF-8 Encoded String
                case 0x08: // Response Topic
                case 0x12: // Assigned Client Identifier
                case 0x15: // Authentication Method
                case 0x1A: // Response Information
                case 0x1C: // Server Reference
                case 0x1F: // Reason String
                case 0x26: // User Property
                    final Result<String> stringResult = decodeString(buffer);
                    numberOfBytesConsumed += stringResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.StringProperty(propertyId.value, stringResult.value));
                    break;
                case 0x09: // Correlation Data => Binary Data
                case 0x16: // Authentication Data
                    final Result<byte[]> binaryDataResult = decodeByteArray(buffer);
                    numberOfBytesConsumed += binaryDataResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.BinaryProperty(propertyId.value, binaryDataResult.value));
                    break;
            }
        }

        return new Result<MqttProperties>(decodedProperties, numberOfBytesConsumed);
    }

    private static Result<Integer> decodeMessageId(ByteBuf buffer) {
        final Result<Integer> messageId = decodeMsbLsb(buffer);
        if (!isValidMessageId(messageId.value)) {
            throw new DecoderException("invalid messageId: " + messageId.value);
        }
        return messageId;
    }

    private static Result<MqttPubReplyMessageVariableHeader> decodePubReplyMessage(ByteBuf buffer) {
        final Result<Integer> packetId = decodeMessageId(buffer);
        final byte reasonCode = (byte) buffer.readUnsignedByte();
        final Result<MqttProperties> properties = decodeProperties(buffer);
        final MqttPubReplyMessageVariableHeader mqttPubAckVariableHeader =
                new MqttPubReplyMessageVariableHeader(packetId.value, reasonCode, properties.value);

        return new Result<MqttPubReplyMessageVariableHeader>(
                mqttPubAckVariableHeader,
                packetId.numberOfBytesConsumed + 1 + properties.numberOfBytesConsumed);
    }

    private Result<MqttMessageIdPlusPropertiesVariableHeader> decodeMessageIdPlusPropertiesVariableHeader(
            ByteBuf buffer) {
        final Result<Integer> packetId = decodeMessageId(buffer);
        final Result<MqttProperties> properties = decodeProperties(buffer);
        final MqttMessageIdPlusPropertiesVariableHeader mqttVariableHeader =
                new MqttMessageIdPlusPropertiesVariableHeader(packetId.value, properties.value);

        return new Result<MqttMessageIdPlusPropertiesVariableHeader>(mqttVariableHeader,
                packetId.numberOfBytesConsumed + properties.numberOfBytesConsumed);
    }

    private static Result<MqttDisconnectVariableHeader> decodeDisconnectVariableHeader(ByteBuf buffer) {
        final short reasonCode = buffer.readUnsignedByte();
        final Result<MqttProperties> properties = decodeProperties(buffer);
        final MqttDisconnectVariableHeader mqttDisconnecrVariableHeader =
                new MqttDisconnectVariableHeader(reasonCode, properties.value);

        return new Result<MqttDisconnectVariableHeader>(
                mqttDisconnecrVariableHeader,
                 1 + properties.numberOfBytesConsumed);
    }
}
