/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Payload for MQTT unsuback message as in V5.
 */
public final class MqttUnsubAckPayload {

    private final List<Short> unsubscribeReasonCodes;

    private static final MqttUnsubAckPayload EMPTY = new MqttUnsubAckPayload();

    public static MqttUnsubAckPayload withEmptyDefaults(MqttUnsubAckPayload payload) {
        if (payload == null) {
            return EMPTY;
        } else {
            return payload;
        }
    }

    public MqttUnsubAckPayload(short... unsubscribeReasonCodes) {
        ObjectUtil.checkNotNull(unsubscribeReasonCodes, "unsubscribeReasonCodes");

        List<Short> list = new ArrayList<Short>(unsubscribeReasonCodes.length);
        for (Short v: unsubscribeReasonCodes) {
            list.add(v);
        }
        this.unsubscribeReasonCodes = Collections.unmodifiableList(list);
    }

    public MqttUnsubAckPayload(Iterable<Short> unsubscribeReasonCodes) {
        ObjectUtil.checkNotNull(unsubscribeReasonCodes, "unsubscribeReasonCodes");

        List<Short> list = new ArrayList<Short>();
        for (Short v: unsubscribeReasonCodes) {
            ObjectUtil.checkNotNull(v, "unsubscribeReasonCode");
            list.add(v);
        }
        this.unsubscribeReasonCodes = Collections.unmodifiableList(list);
    }

    public List<Short> unsubscribeReasonCodes() {
        return unsubscribeReasonCodes;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
                .append('[')
                .append("unsubscribeReasonCodes=").append(unsubscribeReasonCodes)
                .append(']')
                .toString();
    }
}
