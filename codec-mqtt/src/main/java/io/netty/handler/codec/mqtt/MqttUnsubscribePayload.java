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

import io.netty.util.internal.StringUtil;

import java.util.Collections;
import java.util.List;

/**
 * Payload of the {@link MqttUnsubscribeMessage}
 */
public final class MqttUnsubscribePayload {

    private final List<String> topics;

    public MqttUnsubscribePayload(List<String> topics) {
        this.topics = Collections.unmodifiableList(topics);
    }

    public List<String> topics() {
        return topics;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(StringUtil.simpleClassName(this)).append('[');
        for (int i = 0; i < topics.size() - 1; i++) {
            builder.append("topicName = ").append(topics.get(i)).append(", ");
        }
        builder.append("topicName = ").append(topics.get(topics.size() - 1))
               .append(']');
        return builder.toString();
    }
}
