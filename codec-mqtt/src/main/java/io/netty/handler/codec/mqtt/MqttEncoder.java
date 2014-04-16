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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.mqtt.messages.ConnAckMessage;
import io.netty.handler.codec.mqtt.messages.ConnectMessage;
import io.netty.handler.codec.mqtt.messages.ConnectPayload;
import io.netty.handler.codec.mqtt.messages.ConnectVariableHeader;
import io.netty.handler.codec.mqtt.messages.FixedHeader;
import io.netty.handler.codec.mqtt.messages.Message;
import io.netty.handler.codec.mqtt.messages.MessageIdVariableHeader;
import io.netty.handler.codec.mqtt.messages.MessageType;
import io.netty.handler.codec.mqtt.messages.PublishMessage;
import io.netty.handler.codec.mqtt.messages.PublishVariableHeader;
import io.netty.handler.codec.mqtt.messages.SubAckMessage;
import io.netty.handler.codec.mqtt.messages.SubscribeMessage;
import io.netty.handler.codec.mqtt.messages.SubscribePayload;
import io.netty.handler.codec.mqtt.messages.TopicSubscription;
import io.netty.handler.codec.mqtt.messages.UnsubscribeMessage;
import io.netty.handler.codec.mqtt.messages.UnsubscribePayload;
import io.netty.util.CharsetUtil;

import java.util.List;

/**
 * Encodes Mqtt messages into bytes following the protocl specification v3.1
 * as described here <a href="http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html">MQTTV3.1</a>
 */
public class MqttEncoder extends MessageToMessageEncoder<Message> {

    public static final MqttEncoder DEFAUL_ENCODER = new MqttEncoder();

    private static final byte[] EMPTY = new byte[0];

    private static final byte[] CONNECT_VARIABLE_HEADER_START = new byte[] {0, 6, 'M', 'Q', 'I', 's', 'd', 'p'};

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
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
    static ByteBuf doEncode(ByteBufAllocator byteBufAllocator, Message message) {

        switch (message.getFixedHeader().getMessageType()) {
            case MessageType.CONNECT:
                return encodeConnectMessage(byteBufAllocator, (ConnectMessage) message);

            case MessageType.CONNACK:
                return encodeConnAckMessage(byteBufAllocator, (ConnAckMessage) message);

            case MessageType.PUBLISH:
                return encodePublishMessage(byteBufAllocator, (PublishMessage) message);

            case MessageType.SUBSCRIBE:
                return encodeSubscribeMessage(byteBufAllocator, (SubscribeMessage) message);

            case MessageType.UNSUBSCRIBE:
                return encodeUnsubscribeMessage(byteBufAllocator, (UnsubscribeMessage) message);

            case MessageType.SUBACK:
                return encodeSubAckMessage(byteBufAllocator, (SubAckMessage) message);

            case MessageType.UNSUBACK:
            case MessageType.PUBACK:
            case MessageType.PUBREC:
            case MessageType.PUBREL:
            case MessageType.PUBCOMP:
                return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(byteBufAllocator, message);

            case MessageType.PINGREQ:
            case MessageType.PINGRESP:
            case MessageType.DISCONNECT:
                return encodeMessageWithOnlySingleByteFixedHeader(byteBufAllocator, message);

            default:
                throw new IllegalArgumentException(
                        "Unknown message type: " + message.getFixedHeader().getMessageType());
        }
    }

    private static ByteBuf encodeConnectMessage(
            ByteBufAllocator byteBufAllocator,
            ConnectMessage message) {
        int variableHeaderBufferSize = 12;
        int payloadBufferSize = 0;

        FixedHeader fixedHeader = message.getFixedHeader();
        ConnectVariableHeader variableHeader = message.getVariableHeader();
        ConnectPayload payload = message.getPayload();

        // Client id
        String clientIdentifier = payload.getClientIdentifier();
        byte[] clientIdentifierBytes = encodeStringUtf8(clientIdentifier);
        payloadBufferSize += 2 + clientIdentifierBytes.length;

        // Will topic and message
        String willTopic = payload.getWillTopic();
        byte[] willTopicBytes = willTopic != null ? encodeStringUtf8(willTopic) : EMPTY;
        String willMessage = payload.getWillMessage();
        byte[] willMessageBytes = willMessage != null ? encodeStringUtf8(willMessage) : EMPTY;
        if (variableHeader.isWillFlag()) {
            payloadBufferSize += 2 + willTopicBytes.length;
            payloadBufferSize += 2 + willMessageBytes.length;
        }

        String userName = payload.getUserName();
        byte[] userNameBytes = userName != null ? encodeStringUtf8(userName) : EMPTY;
        if (variableHeader.hasUserName()) {
            payloadBufferSize += 2 + userNameBytes.length;
        }

        String password = payload.getPassword();
        byte[] passwordBytes = password != null ? encodeStringUtf8(password) : EMPTY;
        if (variableHeader.hasPassword()) {
            payloadBufferSize += 2 + passwordBytes.length;
        }

        // Fixed header
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(getFixedHeaderByte1(fixedHeader));
        writeVariableLengthInt(buf, variablePartSize);

        buf.writeBytes(CONNECT_VARIABLE_HEADER_START);

        buf.writeByte(variableHeader.getVersion());
        buf.writeByte(getConnVariableHeaderFlag(variableHeader));
        buf.writeShort(variableHeader.getKeepAliveTimeSeconds());

        // Payload
        buf.writeShort(clientIdentifierBytes.length);
        buf.writeBytes(clientIdentifierBytes, 0, clientIdentifierBytes.length);
        if (variableHeader.isWillFlag()) {
            buf.writeShort(willTopicBytes.length);
            buf.writeBytes(willTopicBytes, 0, willTopicBytes.length);
            buf.writeShort(willMessageBytes.length);
            buf.writeBytes(willMessageBytes, 0, willMessageBytes.length);
        }
        if (variableHeader.hasUserName()) {
            buf.writeShort(userNameBytes.length);
            buf.writeBytes(userNameBytes, 0, userNameBytes.length);
        }
        if (variableHeader.hasPassword()) {
            buf.writeShort(passwordBytes.length);
            buf.writeBytes(passwordBytes, 0, passwordBytes.length);
        }
        return buf;
    }

    private static int getConnVariableHeaderFlag(ConnectVariableHeader variableHeader) {
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
        flagByte |= (variableHeader.getWillQos() & 0x03) << 3;
        if (variableHeader.isWillFlag()) {
            flagByte |= 0x04;
        }
        if (variableHeader.isCleanSession()) {
            flagByte |= 0x02;
        }
        return flagByte;
    }

    private static ByteBuf encodeConnAckMessage(
            ByteBufAllocator byteBufAllocator,
            ConnAckMessage message) {
        ByteBuf buf = byteBufAllocator.buffer(4);
        buf.writeByte(getFixedHeaderByte1(message.getFixedHeader()));
        buf.writeByte(2);
        buf.writeByte(0);
        buf.writeByte(message.getVariableHeader().getConnectReturnCode());

        return buf;
    }

    private static ByteBuf encodeSubscribeMessage(
            ByteBufAllocator byteBufAllocator,
            SubscribeMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        FixedHeader fixedHeader = message.getFixedHeader();
        MessageIdVariableHeader variableHeader = message.getVariableHeader();
        SubscribePayload payload = message.getPayload();

        for (TopicSubscription topic : payload.getTopicSubscriptionList()) {
            String topicName = topic.getTopicName();
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            payloadBufferSize += 2 + topicNameBytes.length;
            payloadBufferSize += 1;
        }

        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(getFixedHeaderByte1(fixedHeader));
        writeVariableLengthInt(buf, variablePartSize);

        // Variable Header
        int messageId = variableHeader.getMessageId();
        buf.writeShort(messageId);

        // Payload
        for (TopicSubscription topic : payload.getTopicSubscriptionList()) {
            String topicName = topic.getTopicName();
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            buf.writeShort(topicNameBytes.length);
            buf.writeBytes(topicNameBytes, 0, topicNameBytes.length);
            buf.writeByte(topic.getQualityOfService());
        }

        return buf;
    }

    private static ByteBuf encodeUnsubscribeMessage(
            ByteBufAllocator byteBufAllocator,
            UnsubscribeMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = 0;

        FixedHeader fixedHeader = message.getFixedHeader();
        MessageIdVariableHeader variableHeader = message.getVariableHeader();
        UnsubscribePayload payload = message.getPayload();

        for (String topicName : payload.getTopics()) {
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            payloadBufferSize += 2 + topicNameBytes.length;
        }

        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(getFixedHeaderByte1(fixedHeader));
        writeVariableLengthInt(buf, variablePartSize);

        // Variable Header
        int messageId = variableHeader.getMessageId();
        buf.writeShort(messageId);

        // Payload
        for (String topicName : payload.getTopics()) {
            byte[] topicNameBytes = encodeStringUtf8(topicName);
            buf.writeShort(topicNameBytes.length);
            buf.writeBytes(topicNameBytes, 0, topicNameBytes.length);
        }

        return buf;
    }

    private static ByteBuf encodeSubAckMessage(
            ByteBufAllocator byteBufAllocator,
            SubAckMessage message) {
        int variableHeaderBufferSize = 2;
        int payloadBufferSize = message.getPayload().getGrantedQoSLevels().size();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(getFixedHeaderByte1(message.getFixedHeader()));
        writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(message.getVariableHeader().getMessageId());
        for (int qos : message.getPayload().getGrantedQoSLevels()) {
            buf.writeByte(qos);
        }

        return buf;
    }

    private static ByteBuf encodePublishMessage(
            ByteBufAllocator byteBufAllocator,
            PublishMessage message) {
        FixedHeader fixedHeader = message.getFixedHeader();
        PublishVariableHeader variableHeader = message.getVariableHeader();
        ByteBuf payload = message.getPayload().duplicate();

        String topicName = variableHeader.getTopicName();
        byte[] topicNameBytes = encodeStringUtf8(topicName);

        int variableHeaderBufferSize = 2 + topicNameBytes.length +
                (fixedHeader.getQosLevel() > 0 ? 2 : 0);
        int payloadBufferSize = payload.readableBytes();
        int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variablePartSize);
        buf.writeByte(getFixedHeaderByte1(fixedHeader));
        writeVariableLengthInt(buf, variablePartSize);
        buf.writeShort(topicNameBytes.length);
        buf.writeBytes(topicNameBytes);
        if (fixedHeader.getQosLevel() > 0) {
            buf.writeShort(variableHeader.getMessageId());
        }
        buf.writeBytes(payload);

        return buf;
    }

    private static ByteBuf encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(
            ByteBufAllocator byteBufAllocator,
            Message message) {
        FixedHeader fixedHeader = message.getFixedHeader();
        MessageIdVariableHeader variableHeader = (MessageIdVariableHeader) message.getVariableHeader();
        int msgId = variableHeader.getMessageId();

        int variableHeaderBufferSize = 2; // variable part only has a message id
        int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
        ByteBuf buf = byteBufAllocator.buffer(fixedHeaderBufferSize + variableHeaderBufferSize);
        buf.writeByte(getFixedHeaderByte1(fixedHeader));
        writeVariableLengthInt(buf, variableHeaderBufferSize);
        buf.writeShort(msgId);

        return buf;
    }

    private static ByteBuf encodeMessageWithOnlySingleByteFixedHeader(
            ByteBufAllocator byteBufAllocator,
            Message message) {
        FixedHeader fixedHeader = message.getFixedHeader();
        ByteBuf buf = byteBufAllocator.buffer(2);
        buf.writeByte(getFixedHeaderByte1(fixedHeader));
        buf.writeByte(0);

        return buf;
    }

    private static int getFixedHeaderByte1(FixedHeader header) {
        int ret = 0;
        ret |= header.getMessageType() << 4;
        if (header.isDup()) {
            ret |= 0x08;
        }
        ret |= header.getQosLevel() << 1;
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

    private static int getVariableLengthInt(int num) {
        int count = 0;
        do {
            num /= 128;
            count++;
        } while (num > 0);
        return count;
    }

    private static byte[] encodeStringUtf8(String s) {
      return s.getBytes(CharsetUtil.UTF_8);
    }

}
