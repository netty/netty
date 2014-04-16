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

package io.netty.handler.codec.mqtt.messages;

/**
 * Base class for all MQTT message types.
 */
public class Message {

    private final FixedHeader fixedHeader;
    private final Object variableHeader;
    private final Object payload;

    public Message(FixedHeader fixedHeader) {
        this(fixedHeader, null, null);
    }

    public Message(FixedHeader fixedHeader, Object variableHeader) {
        this(fixedHeader, variableHeader, null);
    }

    public Message(FixedHeader fixedHeader, Object variableHeader, Object payload) {
        this.fixedHeader = fixedHeader;
        this.variableHeader = variableHeader;
        this.payload = payload;
    }

    public FixedHeader getFixedHeader() {
        return fixedHeader;
    }

    public Object getVariableHeader() {
        return variableHeader;
    }

    public Object getPayload() {
        return payload;
    }
}
