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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.StringUtil;

/**
 * http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html#publish
 */
public class PublishMessage extends Message implements ByteBufHolder {

    public PublishMessage(FixedHeader fixedHeader, PublishVariableHeader variableHeader, ByteBuf payload) {
        super(fixedHeader, variableHeader, payload);
    }

    @Override
    public PublishVariableHeader getVariableHeader() {
        return (PublishVariableHeader) super.getVariableHeader();
    }

    @Override
    public ByteBuf getPayload() {
        return content();
    }

    @Override
    public ByteBuf content() {
        final ByteBuf data = (ByteBuf) super.getPayload();
        if (data.refCnt() <= 0) {
            throw new IllegalReferenceCountException(data.refCnt());
        }
        return data;
    }

    @Override
    public ByteBufHolder copy() {
        return new PublishMessage(getFixedHeader(), getVariableHeader(), content().copy());
    }

    @Override
    public ByteBufHolder duplicate() {
        return new PublishMessage(getFixedHeader(), getVariableHeader(), content().duplicate());
    }

    @Override
    public int refCnt() {
        return content().refCnt();
    }

    @Override
    public ByteBufHolder retain() {
        content().retain();
        return this;
    }

    @Override
    public ByteBufHolder retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public ByteBufHolder touch() {
        content().touch();
        return this;
    }

    @Override
    public ByteBufHolder touch(Object hint) {
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

    @Override
    public String toString() {
        return StringUtil.simpleClassName(this) + '(' + content().toString() + ')';
    }
}
