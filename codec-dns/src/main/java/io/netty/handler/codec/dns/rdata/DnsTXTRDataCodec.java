/*
 * Copyright 2019 The Netty Project
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

package io.netty.handler.codec.dns.rdata;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.record.DnsTXTRecord;

import java.nio.charset.Charset;

/**
 * Decoder for {@link DnsTXTRecord}.
 */
public class DnsTXTRDataCodec implements DnsRDataCodec<DnsTXTRecord> {
    public static final DnsTXTRDataCodec DEFAULT = new DnsTXTRDataCodec();

    @Override
    public DnsTXTRecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        String txt = rData.toString(Charset.forName("utf-8"));
        return new DnsTXTRecord(name, dnsClass, timeToLive, txt);
    }

    @Override
    public void encodeRData(DnsTXTRecord record, ByteBuf out) {
        out.writeCharSequence(record.txt(), Charset.forName("utf-8"));
    }
}
