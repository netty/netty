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
import io.netty.handler.codec.dns.rdata.opt.Edns0OptionDecoder;
import io.netty.handler.codec.dns.rdata.opt.Edns0OptionEncoder;
import io.netty.handler.codec.dns.record.DnsOPTRecord;
import io.netty.handler.codec.dns.record.opt.Edns0Option;

import java.util.LinkedList;
import java.util.List;

/**
 * Codec for {@link DnsOPTRecord}.
 */
public class DnsOPTRDataCodec implements DnsRDataCodec<DnsOPTRecord> {
    public static final DnsOPTRDataCodec DEFAULT = new DnsOPTRDataCodec();

    @Override
    public DnsOPTRecord decodeRData(String name, int dnsClass, long timeToLive, ByteBuf rData) {
        List<Edns0Option> edns0Options = new LinkedList<Edns0Option>();
        while (rData.isReadable()) {
            Edns0Option option = Edns0OptionDecoder.DEFAULT.decodeOption(rData);
            edns0Options.add(option);
        }
        return new DnsOPTRecord(name, dnsClass, timeToLive, edns0Options);
    }

    @Override
    public void encodeRData(DnsOPTRecord record, ByteBuf out) {
        List<Edns0Option> options = record.options();
        for (Edns0Option option : options) {
            Edns0OptionEncoder.DEFAULT.encodeOption(option, out);
        }
    }
}
