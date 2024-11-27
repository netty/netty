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
import io.netty.buffer.ByteBufHolder;

/**
 * See <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#publish">MQTTV3.1/publish</a>
 */
public class MqttPublishMessage extends MqttMessage implements ByteBufHolder {

    public MqttPublishMessage(
            MqttFixedHeader mqttFixedHeader,
            MqttPublishVariableHeader variableHeader,
            ByteBuf payload) {
        super(mqttFixedHeader, variableHeader, payload);
    }

    @Override
    public MqttPublishVariableHeader variableHeader() {
        return (MqttPublishVariableHeader) super.variableHeader();
    }

    @Override
    public ByteBuf payload() {
        return content();
    }

    @Override
    public ByteBuf content() {
        return (ByteBuf) super.payload();
    }

    @Override
    public MqttPublishMessage copy() {
        return replace(content().copy());
    }

    @Override
    public MqttPublishMessage duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public MqttPublishMessage retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public MqttPublishMessage replace(ByteBuf content) {
        return new MqttPublishMessage(fixedHeader(), variableHeader(), content);
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public MqttPublishMessage retain() {
        content().retain();
        return this;
    }

    @Override
    public MqttPublishMessage retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public MqttPublishMessage touch() {
        content().touch();
        return this;
    }

    @Override
    public MqttPublishMessage touch(Object hint) {
        content().touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return content().release();
    }

    @Override
    public boolean release(int decrement) {
        return content().release(decrement);
    }

}
