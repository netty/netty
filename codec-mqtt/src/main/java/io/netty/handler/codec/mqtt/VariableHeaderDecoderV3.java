package io.netty.handler.codec.mqtt;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidMessageId;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidPublishTopicName;
import static io.netty.handler.codec.mqtt.MqttDecoder.*;

/**
 * Used to decode only the Variable Header part of MQTT packets, which is what differs between v3.1.1 and v5
 */
final class VariableHeaderDecoderV3 implements IVariableHeaderDecoder {
    /**
     * Decodes the variable header (if any)
     * @param buffer the buffer to decode from
     * @param mqttFixedHeader MqttFixedHeader of the same message
     * @return the variable header
     */
    @Override
    public MqttDecoder.Result<?> decodeVariableHeader(ByteBuf buffer, MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
                return decodeConnectionVariableHeader(buffer);

            case CONNACK:
                return decodeConnAckVariableHeader(buffer);

            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case SUBACK:
            case UNSUBACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case PUBREL:
                return decodeMessageIdVariableHeader(buffer);

            case PUBLISH:
                return decodePublishVariableHeader(buffer, mqttFixedHeader);

            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                // Empty variable header
                return new MqttDecoder.Result<Object>(null, 0);
        }
        return new MqttDecoder.Result<Object>(null, 0); //should never reach here
    }

    private static MqttDecoder.Result<MqttConnectVariableHeader> decodeConnectionVariableHeader(ByteBuf buffer) {
        final MqttDecoder.Result<String> protoString = decodeString(buffer);
        int numberOfBytesConsumed = protoString.numberOfBytesConsumed;

        final byte protocolLevel = buffer.readByte();
        numberOfBytesConsumed += 1;

        final MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(protoString.value, protocolLevel);

        final int b1 = buffer.readUnsignedByte();
        numberOfBytesConsumed += 1;

        final MqttDecoder.Result<Integer> keepAlive = decodeMsbLsb(buffer);
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
                MqttProperties.NO_PROPERTIES);
        return new MqttDecoder.Result<MqttConnectVariableHeader>(mqttConnectVariableHeader, numberOfBytesConsumed);
    }

    private static MqttDecoder.Result<MqttConnAckVariableHeader> decodeConnAckVariableHeader(ByteBuf buffer) {
        final boolean sessionPresent = (buffer.readUnsignedByte() & 0x01) == 0x01;
        byte returnCode = buffer.readByte();
        final int numberOfBytesConsumed = 2;
        final MqttConnAckVariableHeader mqttConnAckVariableHeader =
                new MqttConnAckVariableHeader(MqttConnectReturnCode.valueOf(returnCode), sessionPresent);
        return new MqttDecoder.Result<MqttConnAckVariableHeader>(mqttConnAckVariableHeader, numberOfBytesConsumed);
    }

    private static MqttDecoder.Result<MqttMessageIdVariableHeader> decodeMessageIdVariableHeader(ByteBuf buffer) {
        final MqttDecoder.Result<Integer> messageId = decodeMessageId(buffer);
        return new MqttDecoder.Result<MqttMessageIdVariableHeader>(
                MqttMessageIdVariableHeader.from(messageId.value),
                messageId.numberOfBytesConsumed);
    }

    private static MqttDecoder.Result<MqttPublishVariableHeader> decodePublishVariableHeader(
            ByteBuf buffer,
            MqttFixedHeader mqttFixedHeader) {
        final MqttDecoder.Result<String> decodedTopic = decodeString(buffer);
        if (!isValidPublishTopicName(decodedTopic.value)) {
            throw new DecoderException("invalid publish topic name: " + decodedTopic.value + " (contains wildcards)");
        }
        int numberOfBytesConsumed = decodedTopic.numberOfBytesConsumed;

        int messageId = -1;
        if (mqttFixedHeader.qosLevel().value() > 0) {
            final MqttDecoder.Result<Integer> decodedMessageId = decodeMessageId(buffer);
            messageId = decodedMessageId.value;
            numberOfBytesConsumed += decodedMessageId.numberOfBytesConsumed;
        }
        final MqttPublishVariableHeader mqttPublishVariableHeader =
                new MqttPublishVariableHeader(decodedTopic.value, messageId);
        return new MqttDecoder.Result<MqttPublishVariableHeader>(mqttPublishVariableHeader, numberOfBytesConsumed);
    }

    private static MqttDecoder.Result<Integer> decodeMessageId(ByteBuf buffer) {
        final MqttDecoder.Result<Integer> messageId = decodeMsbLsb(buffer);
        if (!isValidMessageId(messageId.value)) {
            throw new DecoderException("invalid messageId: " + messageId.value);
        }
        return messageId;
    }
}
