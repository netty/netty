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
 * @see {@link NioUdtProvider#MESSAGE_CONNECTOR}
 * @see {@link NioUdtProvider#MESSAGE_RENDEZVOUS}
 */
public final class UdtMessage extends DefaultByteBufHolder {

    public UdtMessage(final ByteBuf data) {
        super(data);
    }

    @Override
    public UdtMessage copy() {
        return new UdtMessage(content().copy());
    }

    @Override
    public UdtMessage duplicate() {
        return new UdtMessage(content().duplicate());
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
}
