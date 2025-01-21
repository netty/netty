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

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Payload of the {@link MqttSubAckMessage}
 */
public class MqttSubAckPayload {

    private final List<MqttReasonCodes.SubAck> reasonCodes;

    public MqttSubAckPayload(int... reasonCodes) {
        ObjectUtil.checkNotNull(reasonCodes, "reasonCodes");

        List<MqttReasonCodes.SubAck> list = new ArrayList<MqttReasonCodes.SubAck>(reasonCodes.length);
        for (int v: reasonCodes) {
            list.add(MqttReasonCodes.SubAck.valueOf((byte) (v & 0xFF)));
        }
        this.reasonCodes = Collections.unmodifiableList(list);
    }

    public MqttSubAckPayload(MqttReasonCodes.SubAck... reasonCodes) {
        ObjectUtil.checkNotNull(reasonCodes, "reasonCodes");

        List<MqttReasonCodes.SubAck> list = new ArrayList<MqttReasonCodes.SubAck>(reasonCodes.length);
        list.addAll(Arrays.asList(reasonCodes));
        this.reasonCodes = Collections.unmodifiableList(list);
    }

    public MqttSubAckPayload(Iterable<Integer> reasonCodes) {
        ObjectUtil.checkNotNull(reasonCodes, "reasonCodes");
        List<MqttReasonCodes.SubAck> list = new ArrayList<MqttReasonCodes.SubAck>();
        for (Integer v: reasonCodes) {
            if (v == null) {
                break;
            }
            list.add(MqttReasonCodes.SubAck.valueOf(v.byteValue()));
        }
        this.reasonCodes = Collections.unmodifiableList(list);
    }

    public List<Integer> grantedQoSLevels() {
        List<Integer> qosLevels = new ArrayList<Integer>(reasonCodes.size());
        for (MqttReasonCodes.SubAck code: reasonCodes) {
            if ((code.byteValue() & 0xFF) > MqttQoS.EXACTLY_ONCE.value()) {
                qosLevels.add(MqttQoS.FAILURE.value());
            } else {
                qosLevels.add(code.byteValue() & 0xFF);
            }
        }
        return qosLevels;
    }

    public List<Integer> reasonCodes() {
        return typedReasonCodesToOrdinal();
    }

    private List<Integer> typedReasonCodesToOrdinal() {
        List<Integer> intCodes = new ArrayList<Integer>(reasonCodes.size());
        for (MqttReasonCodes.SubAck code: reasonCodes) {
            intCodes.add(code.byteValue() & 0xFF);
        }
        return intCodes;
    }

    public List<MqttReasonCodes.SubAck> typedReasonCodes() {
        return reasonCodes;
    }

    @Override
    public String toString() {
        return new StringBuilder(StringUtil.simpleClassName(this))
            .append('[')
            .append("reasonCodes=").append(reasonCodes)
            .append(']')
            .toString();
    }
}
