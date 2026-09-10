/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.doh;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsRecordEncoder;
import io.netty.util.internal.ObjectUtil;

/**
 * Represents a DoH query encoder.
 */
public final class DohQueryEncoder extends MessageToByteEncoder<DnsQuery> {

    private final DnsQueryEncoder encoder;

    /**
     * Creates a new encoder with {@linkplain DnsRecordEncoder#DEFAULT the default record encoder}.
     */
    public DohQueryEncoder() {
        this(DnsRecordEncoder.DEFAULT);
    }

    /**
     * Creates a new encoder with the specified {@code recordEncoder}.
     */
    public DohQueryEncoder(DnsRecordEncoder recordEncoder) {
        ObjectUtil.checkNotNull(recordEncoder, "recordEncoder");

        this.encoder = new DnsQueryEncoder(recordEncoder);
    }

    @Override
    public void encode(ChannelHandlerContext ctx, DnsQuery msg, ByteBuf out) throws Exception {
        encoder.encode(msg, out);
    }
}
