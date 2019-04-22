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
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

/**
 * Decoder for {@link DnsTXTRecord}.
 */
public class DnsTXTRDataCodec implements DnsRDataCodec<DnsTXTRecord> {
    public static final DnsTXTRDataCodec DEFAULT = new DnsTXTRDataCodec();

    @Override
    public DnsTXTRecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        List<String> txt = new LinkedList<String>();
        int idx = rData.readerIndex();
        int wIdx = rData.writerIndex();
        while (idx < wIdx) {
            int len = rData.getUnsignedByte(idx++);
            txt.add(rData.toString(idx, len, CharsetUtil.UTF_8));
            idx += len;
        }
        return new DnsTXTRecord(name, dnsClass, timeToLive, txt);
    }

    @Override
    public void encodeRData(DnsTXTRecord record, ByteBuf out) {
        List<String> txt = record.txt();
        for (String s : txt) {
            out.writeByte(s.length());
            out.writeCharSequence(s, Charset.forName("utf-8"));
        }
    }
}
