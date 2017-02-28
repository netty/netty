/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel.udt;

import com.barchart.udt.TypeUDT;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.udt.nio.NioUdtProvider;

/**
 * The message container that is used for {@link TypeUDT#DATAGRAM} messages.
 * @see NioUdtProvider#MESSAGE_CONNECTOR
 * @see NioUdtProvider#MESSAGE_RENDEZVOUS
 *
 * @deprecated The UDT transport is no longer maintained and will be removed.
 */
@Deprecated
public final class UdtMessage extends DefaultByteBufHolder {

    public UdtMessage(final ByteBuf data) {
        super(data);
    }

    @Override
    public UdtMessage copy() {
        return (UdtMessage) super.copy();
    }

    @Override
    public UdtMessage duplicate() {
        return (UdtMessage) super.duplicate();
    }

    @Override
    public UdtMessage retainedDuplicate() {
        return (UdtMessage) super.retainedDuplicate();
    }

    @Override
    public UdtMessage replace(ByteBuf content) {
        return new UdtMessage(content);
    }

    @Override
    public UdtMessage retain() {
        super.retain();
        return this;
    }

    @Override
    public UdtMessage retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public UdtMessage touch() {
        super.touch();
        return this;
    }

    @Override
    public UdtMessage touch(Object hint) {
        super.touch(hint);
        return this;
    }
}
