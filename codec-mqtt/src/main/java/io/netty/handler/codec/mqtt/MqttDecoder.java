/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.mqtt.MqttDecoder.DecoderState;
import io.netty.handler.codec.mqtt.MqttProperties.IntegerProperty;
import io.netty.util.CharsetUtil;
import io.netty.util.Signal;
import io.netty.util.internal.ObjectUtil;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidClientId;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidMessageId;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.isValidPublishTopicName;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.resetUnusedFields;
import static io.netty.handler.codec.mqtt.MqttCodecUtil.validateFixedHeader;
import static io.netty.handler.codec.mqtt.MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE;
import static io.netty.handler.codec.mqtt.MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH;
import static io.netty.handler.codec.mqtt.MqttProperties.ASSIGNED_CLIENT_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.AUTHENTICATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.AUTHENTICATION_METHOD;
import static io.netty.handler.codec.mqtt.MqttProperties.CONTENT_TYPE;
import static io.netty.handler.codec.mqtt.MqttProperties.CORRELATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MAXIMUM_PACKET_SIZE;
import static io.netty.handler.codec.mqtt.MqttProperties.MAXIMUM_QOS;
import static io.netty.handler.codec.mqtt.MqttProperties.PAYLOAD_FORMAT_INDICATOR;
import static io.netty.handler.codec.mqtt.MqttProperties.PUBLICATION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.REASON_STRING;
import static io.netty.handler.codec.mqtt.MqttProperties.RECEIVE_MAXIMUM;
import static io.netty.handler.codec.mqtt.MqttProperties.REQUEST_PROBLEM_INFORMATION;
import static io.netty.handler.codec.mqtt.MqttProperties.REQUEST_RESPONSE_INFORMATION;
import static io.netty.handler.codec.mqtt.MqttProperties.RESPONSE_INFORMATION;
import static io.netty.handler.codec.mqtt.MqttProperties.RESPONSE_TOPIC;
import static io.netty.handler.codec.mqtt.MqttProperties.RETAIN_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.SERVER_KEEP_ALIVE;
import static io.netty.handler.codec.mqtt.MqttProperties.SERVER_REFERENCE;
import static io.netty.handler.codec.mqtt.MqttProperties.SESSION_EXPIRY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttProperties.SHARED_SUBSCRIPTION_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.SUBSCRIPTION_IDENTIFIER;
import static io.netty.handler.codec.mqtt.MqttProperties.SUBSCRIPTION_IDENTIFIER_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.TOPIC_ALIAS;
import static io.netty.handler.codec.mqtt.MqttProperties.TOPIC_ALIAS_MAXIMUM;
import static io.netty.handler.codec.mqtt.MqttProperties.USER_PROPERTY;
import static io.netty.handler.codec.mqtt.MqttProperties.WILDCARD_SUBSCRIPTION_AVAILABLE;
import static io.netty.handler.codec.mqtt.MqttProperties.WILL_DELAY_INTERVAL;
import static io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;

/**
 * Decodes Mqtt messages from bytes, following
 * the MQTT protocol specification
 * <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html">v3.1</a>
 * or
 * <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html">v5.0</a>, depending on the
 * version specified in the CONNECT message that first goes through the channel.
 */
public final class MqttDecoder extends ReplayingDecoder<DecoderState> {

    /**
     * States of the decoder.
     * We start at READ_FIXED_HEADER, followed by
     * READ_VARIABLE_HEADER and finally READ_PAYLOAD.
     */
    enum DecoderState {
        READ_FIXED_HEADER,
        READ_VARIABLE_HEADER,
        READ_PAYLOAD,
        BAD_MESSAGE,
    }

    private MqttFixedHeader mqttFixedHeader;
    private Object variableHeader;
    private int bytesRemainingInVariablePart;

    private final int maxBytesInMessage;
    private final int maxClientIdLength;
    private final boolean strictUtf8Validation;
    // Lazily-initialised UTF-8 decoder reused across calls in the same channel/decoder
    // instance. ReplayingDecoder is invoked from a single thread per channel, so a non
    // thread-safe CharsetDecoder is safe to cache here.
    private CharsetDecoder utf8Decoder;

    public MqttDecoder() {
      this(DEFAULT_MAX_BYTES_IN_MESSAGE, DEFAULT_MAX_CLIENT_ID_LENGTH, true);
    }

    public MqttDecoder(int maxBytesInMessage) {
        this(maxBytesInMessage, DEFAULT_MAX_CLIENT_ID_LENGTH, true);
    }

    public MqttDecoder(int maxBytesInMessage, int maxClientIdLength) {
        this(maxBytesInMessage, maxClientIdLength, true);
    }

    /**
     * Creates a new {@link MqttDecoder}.
     *
     * @param maxBytesInMessage     the maximum number of bytes a decoded message may consume.
     * @param maxClientIdLength     the maximum length of the Client Identifier (CONNECT payload).
     * @param strictUtf8Validation  if {@code true} (default), every UTF-8 Encoded String is
     *                              validated according to MQTT 3.1.1 and MQTT 5.0
     *                              malformed UTF-8 sequences (including
     *                              surrogates and overlong forms) and an embedded U+0000 are
     *                              rejected as a Malformed Packet. If {@code false}, the legacy
     *                              behaviour is preserved, malformed bytes are silently replaced
     *                              with {@code U+FFFD} and U+0000 is accepted.
     */
    public MqttDecoder(int maxBytesInMessage, int maxClientIdLength, boolean strictUtf8Validation) {
        super(DecoderState.READ_FIXED_HEADER);
        this.maxBytesInMessage = ObjectUtil.checkPositive(maxBytesInMessage, "maxBytesInMessage");
        this.maxClientIdLength = ObjectUtil.checkPositive(maxClientIdLength, "maxClientIdLength");
        this.strictUtf8Validation = strictUtf8Validation;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        switch (state()) {
            case READ_FIXED_HEADER: try {
                mqttFixedHeader = decodeFixedHeader(ctx, buffer);
                bytesRemainingInVariablePart = mqttFixedHeader.remainingLength();
                checkpoint(DecoderState.READ_VARIABLE_HEADER);
                // fall through
            } catch (Exception cause) {
                out.add(invalidMessage(cause));
                return;
            }

            case READ_VARIABLE_HEADER:  try {
                int bytesRemainingBeforeVariableHeader = bytesRemainingInVariablePart;
                boolean bailOut = false;
                try {
                    variableHeader = decodeVariableHeader(ctx, buffer, mqttFixedHeader);
                } catch (Signal signal) {
                    if (bytesRemainingBeforeVariableHeader > maxBytesInMessage) {
                        // We couldn't parse the complete message, and it's already too large.
                        // Swallow the Signal (we don't need more data) and instead bail out
                        // and throw the TooLongFrameException below.
                        bailOut = true;
                    } else {
                        // Ask for REPLAY if the current message is within maxBytesInMessage.
                        throw signal;
                    }
                }
                if (bailOut || bytesRemainingBeforeVariableHeader > maxBytesInMessage) {
                    buffer.skipBytes(actualReadableBytes());
                    throw new TooLongFrameException("message length exceeds " + maxBytesInMessage + ": "
                            + bytesRemainingBeforeVariableHeader);
                }
                checkpoint(DecoderState.READ_PAYLOAD);
                // fall through
            } catch (Exception cause) {
                out.add(invalidMessage(cause));
                return;
            }

            case READ_PAYLOAD: try {
                final Object decodedPayload =
                        decodePayload(
                                ctx,
                                buffer,
                                mqttFixedHeader.messageType(),
                                maxClientIdLength,
                                variableHeader);
                checkpoint(DecoderState.READ_FIXED_HEADER);
                MqttMessage message = MqttMessageFactory.newMessage(
                        mqttFixedHeader, variableHeader, decodedPayload);
                mqttFixedHeader = null;
                variableHeader = null;
                out.add(message);
                break;
            } catch (Exception cause) {
                out.add(invalidMessage(cause));
                return;
            }

            case BAD_MESSAGE:
                // Keep discarding until disconnection.
                buffer.skipBytes(actualReadableBytes());
                break;

            default:
                // Shouldn't reach here.
                throw new Error("Unexpected mqtt decoder state: " + state());
        }
    }

    private MqttMessage invalidMessage(Throwable cause) {
      checkpoint(DecoderState.BAD_MESSAGE);
      return MqttMessageFactory.newInvalidMessage(mqttFixedHeader, variableHeader, cause);
    }

    /**
     * Decodes the fixed header. It's one byte for the flags and then variable
     * bytes for the remaining length.
     *
     * @see
     * https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/errata01/os/mqtt-v3.1.1-errata01-os-complete.html#_Toc442180841
     *
     * @param buffer the buffer to decode from
     * @return the fixed header
     */
    private static MqttFixedHeader decodeFixedHeader(ChannelHandlerContext ctx, ByteBuf buffer) {
        short b1 = buffer.readUnsignedByte();

        MqttMessageType messageType = MqttMessageType.valueOf(b1 >> 4);
        boolean dupFlag = (b1 & 0x08) == 0x08;
        int qosLevel = (b1 & 0x06) >> 1;
        boolean retain = (b1 & 0x01) != 0;

        switch (messageType) {
            case PUBLISH:
                if (qosLevel == 3) {
                    throw new DecoderException("Illegal QOS Level in fixed header of PUBLISH message ("
                            + qosLevel + ')');
                }
                break;

            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (dupFlag) {
                    throw new DecoderException("Illegal BIT 3 in fixed header of " + messageType
                            + " message, must be 0, found 1");
                }
                if (qosLevel != 1) {
                    throw new DecoderException("Illegal QOS Level in fixed header of " + messageType
                            + " message, must be 1, found " + qosLevel);
                }
                if (retain) {
                    throw new DecoderException("Illegal BIT 0 in fixed header of " + messageType
                            + " message, must be 0, found 1");
                }
                break;

            case AUTH:
            case CONNACK:
            case CONNECT:
            case DISCONNECT:
            case PINGREQ:
            case PINGRESP:
            case PUBACK:
            case PUBCOMP:
            case PUBREC:
            case SUBACK:
            case UNSUBACK:
                if (dupFlag) {
                    throw new DecoderException("Illegal BIT 3 in fixed header of " + messageType
                            + " message, must be 0, found 1");
                }
                if (qosLevel != 0) {
                    throw new DecoderException("Illegal BIT 2 or 1 in fixed header of " + messageType
                            + " message, must be 0, found " + qosLevel);
                }
                if (retain) {
                    throw new DecoderException("Illegal BIT 0 in fixed header of " + messageType
                            + " message, must be 0, found 1");
                }
                break;
            default:
                throw new DecoderException("Unknown message type, do not know how to validate fixed header");
        }

        int remainingLength = parseRemainingLength(buffer, messageType);
        MqttFixedHeader decodedFixedHeader =
                new MqttFixedHeader(messageType, dupFlag, MqttQoS.valueOf(qosLevel), retain, remainingLength);
        return validateFixedHeader(ctx, resetUnusedFields(decodedFixedHeader));
    }

    private static int parseRemainingLength(ByteBuf buffer, MqttMessageType messageType) {
        int remainingLength = 0;
        int multiplier = 1;

        for (int i = 0; i < 4; i++) {
            short digit = buffer.readUnsignedByte();
            remainingLength += (digit & 127) * multiplier;

            if ((digit & 128) == 0) {
                return remainingLength;
            }

            multiplier *= 128;
        }

        // MQTT protocol limits Remaining Length to 4 bytes
        throw new DecoderException("remaining length exceeds 4 digits (" + messageType + ')');
    }

    /**
     * Decodes the variable header (if any)
     * @param buffer the buffer to decode from
     * @param mqttFixedHeader MqttFixedHeader of the same message
     * @return the variable header
     */
    private Object decodeVariableHeader(ChannelHandlerContext ctx, ByteBuf buffer, MqttFixedHeader mqttFixedHeader) {
        switch (mqttFixedHeader.messageType()) {
            case CONNECT:
                return decodeConnectionVariableHeader(ctx, buffer);

            case CONNACK:
                return decodeConnAckVariableHeader(ctx, buffer);

            case UNSUBSCRIBE:
            case SUBSCRIBE:
            case SUBACK:
            case UNSUBACK:
                return decodeMessageIdAndPropertiesVariableHeader(ctx, buffer);

            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case PUBREL:
                return decodePubReplyMessage(buffer);

            case PUBLISH:
                return decodePublishVariableHeader(ctx, buffer, mqttFixedHeader);

            case DISCONNECT:
            case AUTH:
                return decodeReasonCodeAndPropertiesVariableHeader(buffer);

            case PINGREQ:
            case PINGRESP:
                // Empty variable header
                return null;
            default:
                //shouldn't reach here
                throw new DecoderException("Unknown message type: " + mqttFixedHeader.messageType());
        }
    }

    private MqttConnectVariableHeader decodeConnectionVariableHeader(
            ChannelHandlerContext ctx,
            ByteBuf buffer) {
        final Result<String> protoString = decodeString(buffer);
        int numberOfBytesConsumed = protoString.numberOfBytesConsumed;

        final byte protocolLevel = buffer.readByte();
        numberOfBytesConsumed += 1;

        MqttVersion version = MqttVersion.fromProtocolNameAndLevel(protoString.value, protocolLevel);
        MqttCodecUtil.setMqttVersion(ctx, version);

        final int b1 = buffer.readUnsignedByte();
        numberOfBytesConsumed += 1;

        final int keepAlive = decodeMsbLsb(buffer);
        numberOfBytesConsumed += 2;

        final boolean hasUserName = (b1 & 0x80) == 0x80;
        final boolean hasPassword = (b1 & 0x40) == 0x40;
        final boolean willRetain = (b1 & 0x20) == 0x20;
        final int willQos = (b1 & 0x18) >> 3;
        final boolean willFlag = (b1 & 0x04) == 0x04;
        final boolean cleanSession = (b1 & 0x02) == 0x02;
        if (version == MqttVersion.MQTT_3_1_1 || version == MqttVersion.MQTT_5) {
            final boolean zeroReservedFlag = (b1 & 0x01) == 0x0;
            if (!zeroReservedFlag) {
                // MQTT v3.1.1: The Server MUST validate that the reserved flag in the CONNECT Control Packet is
                // set to zero and disconnect the Client if it is not zero.
                // See https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc385349230
                throw new DecoderException("non-zero reserved flag");
            }
        }

        final MqttProperties properties;
        if (version == MqttVersion.MQTT_5) {
            final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
            properties = propertiesResult.value;
            numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
        } else {
            properties = MqttProperties.NO_PROPERTIES;
        }

        bytesRemainingInVariablePart -= numberOfBytesConsumed;
        return new MqttConnectVariableHeader(
                version.protocolName(),
                version.protocolLevel(),
                hasUserName,
                hasPassword,
                willRetain,
                willQos,
                willFlag,
                cleanSession,
                keepAlive,
                properties);
    }

    private MqttConnAckVariableHeader decodeConnAckVariableHeader(
            ChannelHandlerContext ctx,
            ByteBuf buffer) {
        final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
        final boolean sessionPresent = (buffer.readUnsignedByte() & 0x01) == 0x01;
        byte returnCode = buffer.readByte();

        final MqttProperties properties;
        if (mqttVersion == MqttVersion.MQTT_5) {
            final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
            properties = propertiesResult.value;
            bytesRemainingInVariablePart -= 2 + propertiesResult.numberOfBytesConsumed;
        } else {
            properties = MqttProperties.NO_PROPERTIES;
            bytesRemainingInVariablePart -= 2;
        }

        return new MqttConnAckVariableHeader(MqttConnectReturnCode.valueOf(returnCode), sessionPresent, properties);
    }

    private MqttMessageIdAndPropertiesVariableHeader decodeMessageIdAndPropertiesVariableHeader(
            ChannelHandlerContext ctx,
            ByteBuf buffer) {
        final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
        final int packetId = decodeMessageId(buffer);

        if (mqttVersion == MqttVersion.MQTT_5) {
            final Result<MqttProperties> properties = decodeProperties(buffer);
            bytesRemainingInVariablePart -= 2 + properties.numberOfBytesConsumed;
            return new MqttMessageIdAndPropertiesVariableHeader(packetId, properties.value);
        } else {
            bytesRemainingInVariablePart -= 2;
            return new MqttMessageIdAndPropertiesVariableHeader(packetId,
                                                                MqttProperties.NO_PROPERTIES);
        }
    }

    private MqttPubReplyMessageVariableHeader decodePubReplyMessage(ByteBuf buffer) {
        final int packetId = decodeMessageId(buffer);

        final int packetIdNumberOfBytesConsumed = 2;
        if (bytesRemainingInVariablePart > 3) {
            final byte reasonCode = buffer.readByte();
            final Result<MqttProperties> properties = decodeProperties(buffer);
            bytesRemainingInVariablePart -= packetIdNumberOfBytesConsumed + 1 + properties.numberOfBytesConsumed;
            return new MqttPubReplyMessageVariableHeader(packetId,
                    reasonCode,
                    properties.value);
        } else if (bytesRemainingInVariablePart > 2) {
            final byte reasonCode = buffer.readByte();
            bytesRemainingInVariablePart -= packetIdNumberOfBytesConsumed + 1;
            return new MqttPubReplyMessageVariableHeader(packetId,
                    reasonCode,
                    MqttProperties.NO_PROPERTIES);
        } else {
            bytesRemainingInVariablePart -= packetIdNumberOfBytesConsumed;
            return new MqttPubReplyMessageVariableHeader(packetId,
                    (byte) 0,
                    MqttProperties.NO_PROPERTIES);
        }
    }

    private MqttReasonCodeAndPropertiesVariableHeader decodeReasonCodeAndPropertiesVariableHeader(
            ByteBuf buffer) {
        final byte reasonCode;
        final MqttProperties properties;
        if (bytesRemainingInVariablePart > 1) {
            reasonCode = buffer.readByte();
            final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
            properties = propertiesResult.value;
            bytesRemainingInVariablePart -= 1 + propertiesResult.numberOfBytesConsumed;
        } else if (bytesRemainingInVariablePart > 0) {
            reasonCode = buffer.readByte();
            properties = MqttProperties.NO_PROPERTIES;
            --bytesRemainingInVariablePart;
        } else {
            reasonCode = 0;
            properties = MqttProperties.NO_PROPERTIES;
        }

        return new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, properties);
    }

    private MqttPublishVariableHeader decodePublishVariableHeader(
            ChannelHandlerContext ctx,
            ByteBuf buffer,
            MqttFixedHeader mqttFixedHeader) {
        final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
        final Result<String> decodedTopic = decodeString(buffer);
        if (!isValidPublishTopicName(decodedTopic.value)) {
            throw new DecoderException("invalid publish topic name: " + decodedTopic.value + " (contains wildcards)");
        }
        int numberOfBytesConsumed = decodedTopic.numberOfBytesConsumed;

        int messageId = -1;
        if (mqttFixedHeader.qosLevel().value() > 0) {
            messageId = decodeMessageId(buffer);
            numberOfBytesConsumed += 2;
        }

        final MqttProperties properties;
        if (mqttVersion == MqttVersion.MQTT_5) {
            final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
            properties = propertiesResult.value;
            numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
        } else {
            properties = MqttProperties.NO_PROPERTIES;
        }

        bytesRemainingInVariablePart -= numberOfBytesConsumed;
        return new MqttPublishVariableHeader(decodedTopic.value, messageId, properties);
    }

    /**
     * @return messageId with numberOfBytesConsumed is 2
     */
    private static int decodeMessageId(ByteBuf buffer) {
        final int messageId = decodeMsbLsb(buffer);
        if (!isValidMessageId(messageId)) {
            throw new DecoderException("invalid messageId: " + messageId);
        }
        return messageId;
    }

    /**
     * Decodes the payload.
     *
     * @param buffer the buffer to decode from
     * @param messageType  type of the message being decoded
     * @param variableHeader variable header of the same message
     * @return the payload
     */
    private Object decodePayload(
            ChannelHandlerContext ctx,
            ByteBuf buffer,
            MqttMessageType messageType,
            int maxClientIdLength,
            Object variableHeader) {
        switch (messageType) {
            case CONNECT:
                return decodeConnectionPayload(buffer, maxClientIdLength, (MqttConnectVariableHeader) variableHeader);

            case SUBSCRIBE:
                return decodeSubscribePayload(buffer);

            case SUBACK:
                return decodeSubackPayload(buffer);

            case UNSUBSCRIBE:
                return decodeUnsubscribePayload(buffer);

            case UNSUBACK:
                return decodeUnsubAckPayload(ctx, buffer);

            case PUBLISH:
                return decodePublishPayload(buffer);

            default:
                // No payload for this message type. If the fixed header's Remaining Length
                // claimed bytes beyond what the variable header consumed (e.g. a PINGREQ
                // with non-zero Remaining Length), the frame is malformed.
                // See https://github.com/netty/netty/issues/16851
                validateNoBytesRemain(0);
                return null;
        }
    }

    private MqttConnectPayload decodeConnectionPayload(
            ByteBuf buffer,
            int maxClientIdLength,
            MqttConnectVariableHeader mqttConnectVariableHeader) {
        final Result<String> decodedClientId = decodeString(buffer);
        final String decodedClientIdValue = decodedClientId.value;
        final MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(mqttConnectVariableHeader.name(),
                (byte) mqttConnectVariableHeader.version());
        if (!isValidClientId(mqttVersion, maxClientIdLength, decodedClientIdValue)) {
            throw new MqttIdentifierRejectedException("invalid clientIdentifier: " + decodedClientIdValue);
        }
        int numberOfBytesConsumed = decodedClientId.numberOfBytesConsumed;

        Result<String> decodedWillTopic = null;
        byte[] decodedWillMessage = null;

        final MqttProperties willProperties;
        if (mqttConnectVariableHeader.isWillFlag()) {
            if (mqttVersion == MqttVersion.MQTT_5) {
                final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
                willProperties = propertiesResult.value;
                numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
            } else {
                willProperties = MqttProperties.NO_PROPERTIES;
            }
            decodedWillTopic = decodeString(buffer, 0, 32767);
            numberOfBytesConsumed += decodedWillTopic.numberOfBytesConsumed;
            decodedWillMessage = decodeByteArray(buffer);
            numberOfBytesConsumed += decodedWillMessage.length + 2;
        } else {
            willProperties = MqttProperties.NO_PROPERTIES;
        }
        Result<String> decodedUserName = null;
        byte[] decodedPassword = null;
        if (mqttConnectVariableHeader.hasUserName()) {
            decodedUserName = decodeString(buffer);
            numberOfBytesConsumed += decodedUserName.numberOfBytesConsumed;
        }
        if (mqttConnectVariableHeader.hasPassword()) {
            decodedPassword = decodeByteArray(buffer);
            numberOfBytesConsumed += decodedPassword.length + 2;
        }

        validateNoBytesRemain(numberOfBytesConsumed);
        return new MqttConnectPayload(
                        decodedClientId.value,
                        willProperties,
                        decodedWillTopic != null ? decodedWillTopic.value : null,
                        decodedWillMessage,
                        decodedUserName != null ? decodedUserName.value : null,
                        decodedPassword);
    }

    private MqttSubscribePayload decodeSubscribePayload(
            ByteBuf buffer) {
        final List<MqttTopicSubscription> subscribeTopics = new ArrayList<MqttTopicSubscription>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final Result<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            //See 3.8.3.1 Subscription Options of MQTT 5.0 specification for optionByte details
            final short optionByte = buffer.readUnsignedByte();

            MqttQoS qos = MqttQoS.valueOf(optionByte & 0x03);
            boolean noLocal = ((optionByte & 0x04) >> 2) == 1;
            boolean retainAsPublished = ((optionByte & 0x08) >> 3) == 1;
            RetainedHandlingPolicy retainHandling = RetainedHandlingPolicy.valueOf((optionByte & 0x30) >> 4);

            final MqttSubscriptionOption subscriptionOption = new MqttSubscriptionOption(qos,
                    noLocal,
                    retainAsPublished,
                    retainHandling);

            numberOfBytesConsumed++;
            subscribeTopics.add(new MqttTopicSubscription(decodedTopicName.value, subscriptionOption));
        }
        validateNoBytesRemain(numberOfBytesConsumed);
        return new MqttSubscribePayload(subscribeTopics);
    }

    private MqttSubAckPayload decodeSubackPayload(
            ByteBuf buffer) {
        int bytesRemainingInVariablePart = this.bytesRemainingInVariablePart;
        final List<Integer> grantedQos = new ArrayList<Integer>(bytesRemainingInVariablePart);
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            int reasonCode = buffer.readUnsignedByte();
            numberOfBytesConsumed++;
            grantedQos.add(reasonCode);
        }
        validateNoBytesRemain(numberOfBytesConsumed);
        return new MqttSubAckPayload(grantedQos);
    }

    private MqttUnsubAckPayload decodeUnsubAckPayload(
        ChannelHandlerContext ctx,
        ByteBuf buffer) {
        int bytesRemainingInVariablePart = this.bytesRemainingInVariablePart;
        final List<Short> reasonCodes = new ArrayList<Short>(bytesRemainingInVariablePart);
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            short reasonCode = buffer.readUnsignedByte();
            numberOfBytesConsumed++;
            reasonCodes.add(reasonCode);
        }
        validateNoBytesRemain(numberOfBytesConsumed);
        return new MqttUnsubAckPayload(reasonCodes);
    }

    private MqttUnsubscribePayload decodeUnsubscribePayload(
            ByteBuf buffer) {
        final List<String> unsubscribeTopics = new ArrayList<String>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final Result<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            unsubscribeTopics.add(decodedTopicName.value);
        }
        validateNoBytesRemain(numberOfBytesConsumed);
        return new MqttUnsubscribePayload(unsubscribeTopics);
    }

    private ByteBuf decodePublishPayload(ByteBuf buffer) {
        return buffer.readRetainedSlice(bytesRemainingInVariablePart);
    }

    private void validateNoBytesRemain(int numberOfBytesConsumed) {
        bytesRemainingInVariablePart -= numberOfBytesConsumed;
        if (bytesRemainingInVariablePart != 0) {
            throw new DecoderException(
                    "non-zero remaining payload bytes: " +
                    bytesRemainingInVariablePart + " (" + mqttFixedHeader.messageType() + ')');
        }
    }

    private Result<String> decodeString(ByteBuf buffer) {
        return decodeString(buffer, 0, Integer.MAX_VALUE);
    }

    private Result<String> decodeString(ByteBuf buffer, int minBytes, int maxBytes) {
        int size = decodeMsbLsb(buffer);
        int numberOfBytesConsumed = 2;
        if (size < minBytes || size > maxBytes) {
            buffer.skipBytes(size);
            numberOfBytesConsumed += size;
            return new Result<String>(null, numberOfBytesConsumed);
        }
        final String s;
        if (strictUtf8Validation) {
            s = readStrictUtf8(buffer, size);
        } else {
            s = buffer.readString(size, CharsetUtil.UTF_8);
        }
        numberOfBytesConsumed += size;
        return new Result<String>(s, numberOfBytesConsumed);
    }

    /**
     * Reads {@code length} bytes from {@code buffer} and decodes them as a strictly validated
     * UTF-8 Encoded String per MQTT 3.1.1 and MQTT 5.0.
     * Throws a {@link DecoderException} if the sequence is malformed or contains U+0000.
     */
    private String readStrictUtf8(ByteBuf buffer, int length) {
        if (length == 0) {
            return "";
        }
        final int readerIndex = buffer.readerIndex();
        final ByteBuffer nioBuf;
        if (buffer.nioBufferCount() == 1) {
            nioBuf = buffer.nioBuffer(readerIndex, length);
        } else {
            // Composite/multi-component buffer: copy out to ensure a contiguous view for the
            // CharsetDecoder. Strict UTF-8 validation requires examining all bytes anyway.
            byte[] tmp = new byte[length];
            buffer.getBytes(readerIndex, tmp);
            nioBuf = ByteBuffer.wrap(tmp);
        }
        if (utf8Decoder == null) {
            utf8Decoder = CharsetUtil.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        utf8Decoder.reset();
        final String s;
        try {
            s = utf8Decoder.decode(nioBuf).toString();
        } catch (CharacterCodingException e) {
            buffer.skipBytes(length);
            throw new DecoderException("invalid UTF-8 string in MQTT packet", e);
        }
        buffer.skipBytes(length);
        // The UTF-8 Encoded String MUST NOT include an encoding
        // of the null character U+0000. If received, this is a Malformed Packet.
        if (s.indexOf('\u0000') >= 0) {
            throw new DecoderException("MQTT UTF-8 Encoded String must not contain U+0000");
        }
        return s;
    }

    /**
     *
     * @return the decoded byte[], numberOfBytesConsumed = byte[].length + 2
     */
    private static byte[] decodeByteArray(ByteBuf buffer) {
        int size = decodeMsbLsb(buffer);
        byte[] bytes = new byte[size];
        buffer.readBytes(bytes);
        return bytes;
    }

    // packing utils to reduce the amount of garbage while decoding ints
    private static long packInts(int a, int b) {
        return (((long) a) << 32) | (b & 0xFFFFFFFFL);
    }

    private static int unpackA(long ints) {
        return (int) (ints >> 32);
    }

    private static int unpackB(long ints) {
        return (int) ints;
    }

    /**
     *  numberOfBytesConsumed = 2. return decoded result.
     */
    private static int decodeMsbLsb(ByteBuf buffer) {
        int min = 0;
        int max = 65535;
        short msbSize = buffer.readUnsignedByte();
        short lsbSize = buffer.readUnsignedByte();
        int result = msbSize << 8 | lsbSize;
        if (result < min || result > max) {
            result = -1;
        }
        return result;
    }

    /**
     * See 1.5.5 Variable Byte Integer section of MQTT 5.0 specification for encoding/decoding rules
     *
     * @param buffer the buffer to decode from
     * @return result pack with a = decoded integer, b = numberOfBytesConsumed. Need to unpack to read them.
     * @throws DecoderException if bad MQTT protocol limits Remaining Length
     */
    private static long decodeVariableByteInteger(ByteBuf buffer) {
        int remainingLength = 0;
        int multiplier = 1;

        for (int i = 0; i < 4; i++) {
            short digit = buffer.readUnsignedByte();
            remainingLength += (digit & 127) * multiplier;

            if ((digit & 128) == 0) {
                return packInts(remainingLength, i + 1);
            }

            multiplier *= 128;
        }

        throw new DecoderException("MQTT protocol limits Remaining Length to 4 bytes");
    }

    private static final class Result<T> {

        private final T value;
        private final int numberOfBytesConsumed;

        Result(T value, int numberOfBytesConsumed) {
            this.value = value;
            this.numberOfBytesConsumed = numberOfBytesConsumed;
        }
    }

    private Result<MqttProperties> decodeProperties(ByteBuf buffer) {
        final long propertiesLength = decodeVariableByteInteger(buffer);
        int totalPropertiesLength = unpackA(propertiesLength);
        int numberOfBytesConsumed = unpackB(propertiesLength);
        if (totalPropertiesLength > 0) {
            // Force an early REPLAY when the buffer does not yet have the full properties block,
            // so we don't repeatedly parse partial properties as data arrives. A direct
            // buffer.readableBytes() check is unusable here because ReplayingDecoderByteBuf
            // returns Integer.MAX_VALUE - readerIndex; touching the last byte via getByte()
            // routes through ReplayingDecoderByteBuf.checkIndex(), which throws REPLAY if the
            // buffer's writerIndex hasn't reached that position yet.
            buffer.getByte(buffer.readerIndex() + totalPropertiesLength - 1);
        }

        MqttProperties decodedProperties = new MqttProperties();
        while (numberOfBytesConsumed < totalPropertiesLength) {
            long propertyId = decodeVariableByteInteger(buffer);
            final int propertyIdValue = unpackA(propertyId);
            numberOfBytesConsumed += unpackB(propertyId);
            switch (propertyIdValue) {
                case PAYLOAD_FORMAT_INDICATOR:
                case REQUEST_PROBLEM_INFORMATION:
                case REQUEST_RESPONSE_INFORMATION:
                case MAXIMUM_QOS:
                case RETAIN_AVAILABLE:
                case WILDCARD_SUBSCRIPTION_AVAILABLE:
                case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
                case SHARED_SUBSCRIPTION_AVAILABLE:
                    final int b1 = buffer.readUnsignedByte();
                    numberOfBytesConsumed++;
                    decodedProperties.add(new IntegerProperty(propertyIdValue, b1));
                    break;
                case SERVER_KEEP_ALIVE:
                case RECEIVE_MAXIMUM:
                case TOPIC_ALIAS_MAXIMUM:
                case TOPIC_ALIAS:
                    final int int2BytesResult = decodeMsbLsb(buffer);
                    numberOfBytesConsumed += 2;
                    decodedProperties.add(new IntegerProperty(propertyIdValue, int2BytesResult));
                    break;
                case PUBLICATION_EXPIRY_INTERVAL:
                case SESSION_EXPIRY_INTERVAL:
                case WILL_DELAY_INTERVAL:
                case MAXIMUM_PACKET_SIZE:
                    final int maxPacketSize = buffer.readInt();
                    numberOfBytesConsumed += 4;
                    decodedProperties.add(new IntegerProperty(propertyIdValue, maxPacketSize));
                    break;
                case SUBSCRIPTION_IDENTIFIER:
                    long vbIntegerResult = decodeVariableByteInteger(buffer);
                    numberOfBytesConsumed += unpackB(vbIntegerResult);
                    decodedProperties.add(new IntegerProperty(propertyIdValue, unpackA(vbIntegerResult)));
                    break;
                case CONTENT_TYPE:
                case RESPONSE_TOPIC:
                case ASSIGNED_CLIENT_IDENTIFIER:
                case AUTHENTICATION_METHOD:
                case RESPONSE_INFORMATION:
                case SERVER_REFERENCE:
                case REASON_STRING:
                    final Result<String> stringResult = decodeString(buffer);
                    numberOfBytesConsumed += stringResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.StringProperty(propertyIdValue, stringResult.value));
                    break;
                case USER_PROPERTY:
                    final Result<String> keyResult = decodeString(buffer);
                    final Result<String> valueResult = decodeString(buffer);
                    numberOfBytesConsumed += keyResult.numberOfBytesConsumed;
                    numberOfBytesConsumed += valueResult.numberOfBytesConsumed;
                    decodedProperties.add(new MqttProperties.UserProperty(keyResult.value, valueResult.value));
                    break;
                case CORRELATION_DATA:
                case AUTHENTICATION_DATA:
                    final byte[] binaryDataResult = decodeByteArray(buffer);
                    numberOfBytesConsumed += binaryDataResult.length + 2;
                    decodedProperties.add(new MqttProperties.BinaryProperty(propertyIdValue, binaryDataResult));
                    break;
                default:
                    //shouldn't reach here
                    throw new DecoderException("Unknown property type: " + propertyIdValue);
            }
        }

        return new Result<MqttProperties>(decodedProperties, numberOfBytesConsumed);
    }
}
