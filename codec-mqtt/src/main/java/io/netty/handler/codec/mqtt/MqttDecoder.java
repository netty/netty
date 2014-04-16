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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.mqtt.MqttDecoder.DecoderState;
import io.netty.handler.codec.mqtt.messages.ConnAckVariableHeader;
import io.netty.handler.codec.mqtt.messages.ConnectPayload;
import io.netty.handler.codec.mqtt.messages.ConnectVariableHeader;
import io.netty.handler.codec.mqtt.messages.FixedHeader;
import io.netty.handler.codec.mqtt.messages.Message;
import io.netty.handler.codec.mqtt.messages.MessageIdVariableHeader;
import io.netty.handler.codec.mqtt.messages.MessageType;
import io.netty.handler.codec.mqtt.messages.PublishVariableHeader;
import io.netty.handler.codec.mqtt.messages.SubAckPayload;
import io.netty.handler.codec.mqtt.messages.SubscribePayload;
import io.netty.handler.codec.mqtt.messages.TopicSubscription;
import io.netty.handler.codec.mqtt.messages.UnsubscribePayload;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttConstants.*;

/**
 * Decodes Mqtt messages from bytes, following the protocl specification v3.1
 * as described here http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html
 */
public class MqttDecoder extends ReplayingDecoder<DecoderState> {

    /**
     * States of the decoder.
     * We start at READ_FIXED_HEADER, followed by
     * READ_VARIABLE_HEADER and finally READ_PAYLOAD.
     */
    public enum DecoderState {
        READ_FIXED_HEADER,
        READ_VARIABLE_HEADER,
        READ_PAYLOAD,
    }

    private FixedHeader fixedHeader;
    private Object variableHeader;
    private Object payload;
    private int bytesRemainingInVariablePart = -1;

    public MqttDecoder() {
        super(DecoderState.READ_FIXED_HEADER);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch (state()) {
            case READ_FIXED_HEADER:
                fixedHeader = decodeFixedHeader(buffer);
                bytesRemainingInVariablePart = fixedHeader.getRemainingLength();
                checkpoint(DecoderState.READ_VARIABLE_HEADER);
                // fall through

            case READ_VARIABLE_HEADER:
                final DecodeResult<?> decodedVariableHeader = decodeVariableHeader(buffer, fixedHeader);
                variableHeader = decodedVariableHeader.value;
                bytesRemainingInVariablePart -= decodedVariableHeader.numberOfBytesConsumed;
                checkpoint(DecoderState.READ_PAYLOAD);
                // fall through

            case READ_PAYLOAD:
                final DecodeResult<?> decodedPayload =
                        decodePayload(
                                buffer,
                                fixedHeader.getMessageType(),
                                bytesRemainingInVariablePart,
                                variableHeader);
                payload = decodedPayload.value;
                bytesRemainingInVariablePart -= decodedPayload.numberOfBytesConsumed;
                if (bytesRemainingInVariablePart != 0) {
                    throw new IllegalStateException(
                            String.format(
                                    "Non-zero bytes remaining (%d).Should never happen. "
                                            + "Message type: %d. Channel: %x",
                                    bytesRemainingInVariablePart,
                                    fixedHeader.getMessageType(),
                                    ctx.channel().id()));
                }
                checkpoint(DecoderState.READ_FIXED_HEADER);
                Message message = MessageFactory.create(fixedHeader, variableHeader, payload);
                fixedHeader = null;
                variableHeader = null;
                payload = null;
                bytesRemainingInVariablePart = -1;
                out.add(message);
                return;

            default:
                throw new Error("Shouldn't reach here");
        }
    }

    /**
     * Decodes the fixed header.
     * It's one byte for the flags and then
     * variable bytes for the remaining length.
     *
     * @param buffer the buffer to decode from
     * @return the fixed header
     */
    private static FixedHeader decodeFixedHeader(ByteBuf buffer) {
        short b1 = buffer.readUnsignedByte();

        int messageType = b1 >> 4;
        boolean dupFlag = (b1 & 0x08) == 0x08;
        int qosLevel = (b1 & 0x06) >> 1;
        boolean retain = (b1 & 0x01) != 0;

        int remainingLength = 0;
        int multiplier = 1;
        short digit;
        int loops = 0;
        do {
            digit = buffer.readUnsignedByte();
            remainingLength += (digit & 127) * multiplier;
            multiplier *= 128;
            loops++;
        } while ((digit & 128) != 0 && loops < 4);

        // MQTT protocol limits Remaining Length to 4 bytes
        if (loops == 4 && (digit & 128) != 0) {
            throw new IllegalStateException(
                    String.format(
                            "Failed to read fixed header of MQTT message. " +
                                    "It has more than 4 digits for Remaining Length. " +
                                    "MessageType: %d. Closing channel",
                            messageType));
        }

        return new FixedHeader(messageType, dupFlag, qosLevel, retain, remainingLength);
    }

    /**
     * Decodes the variable header (if any)
     * @param buffer the buffer to decode from
     * @param fixedHeader FixedHeader of the same message
     * @return the variable header
     */
    private static DecodeResult<?> decodeVariableHeader(ByteBuf buffer, FixedHeader fixedHeader) {
        switch (fixedHeader.getMessageType()) {
            case MessageType.CONNECT:
                return decodeConnectionVariableHeader(buffer);

            case MessageType.CONNACK:
                return decodeConnAckVariableHeader(buffer);

            case MessageType.SUBSCRIBE:
            case MessageType.UNSUBSCRIBE:
            case MessageType.SUBACK:
            case MessageType.UNSUBACK:
            case MessageType.PUBACK:
            case MessageType.PUBREC:
            case MessageType.PUBCOMP:
            case MessageType.PUBREL:
                return decodeMessageIdVariableHeader(buffer);

            case MessageType.PUBLISH:
                return decodePublishVariableHeader(buffer, fixedHeader);

            case MessageType.PINGREQ:
            case MessageType.PINGRESP:
            case MessageType.DISCONNECT:
                // Empty variable header
                return new DecodeResult<Object>(null, 0);

            default:
                return new DecodeResult<Object>(null, 0);
        }
    }

    private static DecodeResult<ConnectVariableHeader> decodeConnectionVariableHeader(ByteBuf buffer) {
        final DecodeResult<String> protoString = decodeString(buffer);
        if (!PROTOCOL_NAME.equals(protoString.value)) {
            throw new IllegalStateException(PROTOCOL_NAME + " signature is missing. Closing channel.");
        }

        int numberOfBytesConsumed = protoString.numberOfBytesConsumed;

        final byte version = buffer.readByte();
        final int b1 = buffer.readUnsignedByte();
        numberOfBytesConsumed += 2;

        final DecodeResult<Integer> keepAlive = decodeMsbLsb(buffer);
        numberOfBytesConsumed += keepAlive.numberOfBytesConsumed;

        final boolean hasUserName = (b1 & 0x80) == 0x80;
        final boolean hasPassword = (b1 & 0x40) == 0x40;
        final boolean willRetain = (b1 & 0x20) == 0x20;
        final int willQos = (b1 & 0x18) >> 3;
        final boolean willFlag = (b1 & 0x04) == 0x04;
        final boolean cleanSession = (b1 & 0x02) == 0x02;

        final ConnectVariableHeader connectVariableHeader = new ConnectVariableHeader(
                PROTOCOL_NAME,
                version,
                hasUserName,
                hasPassword,
                willRetain,
                willQos,
                willFlag,
                cleanSession,
                keepAlive.value);
        return new DecodeResult<ConnectVariableHeader>(connectVariableHeader, numberOfBytesConsumed);
    }

    private static DecodeResult<ConnAckVariableHeader> decodeConnAckVariableHeader(ByteBuf buffer) {
        buffer.readUnsignedByte(); // reserved byte
        byte returnCode = buffer.readByte();
        final int numberOfBytesConsumed = 2;
        final ConnAckVariableHeader connAckVariableHeader = new ConnAckVariableHeader(returnCode);
        return new DecodeResult<ConnAckVariableHeader>(connAckVariableHeader, numberOfBytesConsumed);
    }

    private static DecodeResult<MessageIdVariableHeader> decodeMessageIdVariableHeader(ByteBuf buffer) {
        final DecodeResult<Integer> messageId = decodeMsbLsb(buffer);
        return new DecodeResult<MessageIdVariableHeader>(
                new MessageIdVariableHeader(messageId.value),
                messageId.numberOfBytesConsumed);
    }

    private static DecodeResult<PublishVariableHeader> decodePublishVariableHeader(
            ByteBuf buffer,
            FixedHeader fixedHeader) {
        final DecodeResult<String> decodedTopic = decodeString(buffer);
        int numberOfBytesConsumed = decodedTopic.numberOfBytesConsumed;

        int messageId = -1;
        if (fixedHeader.getQosLevel() > 0) {
            final DecodeResult<Integer> decodedMessageId = decodeMsbLsb(buffer);
            messageId = decodedMessageId.value;
            numberOfBytesConsumed += decodedMessageId.numberOfBytesConsumed;
        }
        final PublishVariableHeader publishVariableHeader =
                new PublishVariableHeader(decodedTopic.value, messageId);
        return new DecodeResult<PublishVariableHeader>(publishVariableHeader, numberOfBytesConsumed);
    }

    /**
     * Decodes the payload.
     *
     * @param buffer the buffer to decode from
     * @param messageType  type of the message being decoded
     * @param bytesRemainingInVariablePart bytes remaining
     * @param variableHeader variable header of the same message
     * @return the payload
     */
    private static DecodeResult<?> decodePayload(
            ByteBuf buffer,
            int messageType,
            int bytesRemainingInVariablePart,
            Object variableHeader) {
        switch (messageType) {
            case MessageType.CONNECT:
                return decodeConnectionPayload(buffer, (ConnectVariableHeader) variableHeader);

            case MessageType.SUBSCRIBE:
                return decodeSubscribePayload(buffer, bytesRemainingInVariablePart);

            case MessageType.SUBACK:
                return decodeSubackPayload(buffer, bytesRemainingInVariablePart);

            case MessageType.UNSUBSCRIBE:
                return decodeUnsubscribePayload(buffer, bytesRemainingInVariablePart);

            case MessageType.PUBLISH:
                return decodePublishPayload(buffer, bytesRemainingInVariablePart);

            default:
                // unknown payload , no byte consumed
                return new DecodeResult<Object>(null, 0);
        }
    }

    private static DecodeResult<ConnectPayload> decodeConnectionPayload(
            ByteBuf buffer,
            ConnectVariableHeader connectVariableHeader) {
        final DecodeResult<String> decodedClientId = decodeString(buffer, 1, 23);
        int numberOfBytesConsumed = decodedClientId.numberOfBytesConsumed;

        DecodeResult<String> decodedWillTopic = null;
        DecodeResult<String> decodedWillMessage = null;
        if (connectVariableHeader.isWillFlag()) {
            decodedWillTopic = decodeString(buffer, 0, 32767);
            numberOfBytesConsumed += decodedWillTopic.numberOfBytesConsumed;
            decodedWillMessage = decodeAsciiString(buffer);
            numberOfBytesConsumed += decodedWillMessage.numberOfBytesConsumed;
        }
        DecodeResult<String> decodedUserName = null;
        DecodeResult<String> decodedPassword = null;
        if (connectVariableHeader.hasUserName()) {
            decodedUserName = decodeString(buffer);
            numberOfBytesConsumed += decodedUserName.numberOfBytesConsumed;
        }
        if (connectVariableHeader.hasPassword()) {
            decodedPassword = decodeString(buffer);
            numberOfBytesConsumed += decodedPassword.numberOfBytesConsumed;
        }

        final ConnectPayload connectPayload =
                new ConnectPayload(
                        decodedClientId.value,
                        decodedWillTopic.value,
                        decodedWillMessage.value,
                        decodedUserName.value,
                        decodedPassword.value);
        return new DecodeResult<ConnectPayload>(connectPayload, numberOfBytesConsumed);
    }

    private static DecodeResult<SubscribePayload> decodeSubscribePayload(
            ByteBuf buffer,
            int bytesRemainingInVariablePart) {
        final List<TopicSubscription> subscribeTopics = new ArrayList<TopicSubscription>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final DecodeResult<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            int qos = buffer.readUnsignedByte() & 0x03;
            numberOfBytesConsumed++;
            subscribeTopics.add(new TopicSubscription(decodedTopicName.value, qos));
        }
        return new DecodeResult<SubscribePayload>(new SubscribePayload(subscribeTopics), numberOfBytesConsumed);
    }

    private static DecodeResult<SubAckPayload> decodeSubackPayload(ByteBuf buffer, int bytesRemainingInVariablePart) {
        final List<Integer> grantedQos = new ArrayList<Integer>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            int qos = buffer.readUnsignedByte() & 0x03;
            numberOfBytesConsumed++;
            grantedQos.add(qos);
        }
        return new DecodeResult<SubAckPayload>(new SubAckPayload(grantedQos), numberOfBytesConsumed);
    }

    private static DecodeResult<UnsubscribePayload> decodeUnsubscribePayload(
            ByteBuf buffer,
            int bytesRemainingInVariablePart) {
        final List<String> unsubscribeTopics = new ArrayList<String>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final DecodeResult<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            unsubscribeTopics.add(decodedTopicName.value);
        }
        return new DecodeResult<UnsubscribePayload>(
                new UnsubscribePayload(unsubscribeTopics),
                numberOfBytesConsumed);
    }

    private static DecodeResult<ByteBuf> decodePublishPayload(ByteBuf buffer, int bytesRemainingInVariablePart) {
        ByteBuf b = buffer.readBytes(bytesRemainingInVariablePart);
        return new DecodeResult<ByteBuf>(b, bytesRemainingInVariablePart);
    }

    private static DecodeResult<String> decodeString(ByteBuf buffer) {
        return decodeString(buffer, 0, Integer.MAX_VALUE);
    }

    private static DecodeResult<String> decodeAsciiString(ByteBuf buffer) {
        DecodeResult<String> decodeResult = decodeString(buffer, 0, Integer.MAX_VALUE);
        final String s = decodeResult.value;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return new DecodeResult<String>(null, decodeResult.numberOfBytesConsumed);
            }
        }
        return new DecodeResult<String>(s, decodeResult.numberOfBytesConsumed);
    }

    private static DecodeResult<String> decodeString(ByteBuf buffer, int minBytes, int maxBytes) {
        final DecodeResult<Integer> decodedSize = decodeMsbLsb(buffer);
        int size = decodedSize.value;
        int numberOfBytesConsumed = decodedSize.numberOfBytesConsumed;
        if (size < minBytes || size > maxBytes) {
            buffer.skipBytes(size);
            numberOfBytesConsumed += size;
            return new DecodeResult<String>(null, numberOfBytesConsumed);
        }
        ByteBuf buf = buffer.readBytes(size);
        numberOfBytesConsumed += size;
        return new DecodeResult<String>(buf.toString(CharsetUtil.UTF_8), numberOfBytesConsumed);
    }

    private static DecodeResult<Integer> decodeMsbLsb(ByteBuf buffer) {
        return decodeMsbLsb(buffer, 0, 65535);
    }

    private static DecodeResult<Integer> decodeMsbLsb(ByteBuf buffer, int min, int max) {
        short msbSize = buffer.readUnsignedByte();
        short lsbSize = buffer.readUnsignedByte();
        final int numberOfBytesConsumed = 2;
        int result = msbSize << 8 | lsbSize;
        if (result < min || result > max) {
            result = -1;
        }
        return new DecodeResult<Integer>(result, numberOfBytesConsumed);
    }

    private static final class DecodeResult<T> {

        private final T value;
        private final int numberOfBytesConsumed;

        DecodeResult(T value, int numberOfBytesConsumed) {
            this.value = value;
            this.numberOfBytesConsumed = numberOfBytesConsumed;
        }
    }
}
