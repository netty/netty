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
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.codec.mqtt.MqttDecoder.DecoderState;
import io.netty.handler.codec.mqtt.SubscriptionOption.RetainedHandlingPolicy;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;

import static io.netty.handler.codec.mqtt.MqttCodecUtil.*;
import static io.netty.handler.codec.mqtt.SubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE;
import static io.netty.handler.codec.mqtt.SubscriptionOption.onlyFromQos;

/**
 * Decodes Mqtt messages from bytes, following MQTT v5 protocol specification
 *
 */
public final class MqttDecoderV5 extends MqttDecoder {

    public MqttDecoderV5(IVariableHeaderDecoder headerDecoder) {
        super(headerDecoder);
    }

    public MqttDecoderV5(int maxBytesInMessage, IVariableHeaderDecoder headerDecoder) {
        super(maxBytesInMessage, headerDecoder);
    }

    @Override
    protected Result<MqttSubscribePayload> decodeSubscribePayload(
            ByteBuf buffer,
            int bytesRemainingInVariablePart) {
        final List<MqttTopicSubscription> subscribeTopics = new ArrayList<MqttTopicSubscription>();
        int numberOfBytesConsumed = 0;
        while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
            final Result<String> decodedTopicName = decodeString(buffer);
            numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
            final short optionByte = buffer.readUnsignedByte();
            MqttQoS qos = MqttQoS.valueOf(optionByte & 0x03);
            boolean noLocal = ((optionByte & 0x04) >> 2) == 1;
            boolean retainAsPublished = ((optionByte & 0x08) >> 3) == 1;
            RetainedHandlingPolicy retainHandling = RetainedHandlingPolicy.valueOf(optionByte & 0x30 >> 4);

            final SubscriptionOption subscriptionOption = new SubscriptionOption(qos, noLocal, retainAsPublished,
                    retainHandling);

            numberOfBytesConsumed++;
            subscribeTopics.add(new MqttTopicSubscription(decodedTopicName.value, subscriptionOption));
        }
        return new Result<MqttSubscribePayload>(new MqttSubscribePayload(subscribeTopics), numberOfBytesConsumed);
    }
}
