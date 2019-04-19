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

package io.netty.handler.codec.dns.rdata.opt;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.dns.record.opt.Edns0LlqOption;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

public class Edns0LlqOptionDataCodec implements Edns0OptionDataCodec<Edns0LlqOption> {
    public static final Edns0LlqOptionDataCodec DEFAULT = new Edns0LlqOptionDataCodec();

    @Override
    public Edns0LlqOption decodeOptionData(ByteBuf optionData) {
        checkShortReadable(optionData, "version");
        short version = optionData.readShort();
        checkShortReadable(optionData, "opcode");
        short opcode = optionData.readShort();
        checkShortReadable(optionData, "error code");
        short errCode = optionData.readShort();
        checkLongReadable(optionData, "id");
        long id = optionData.readLong();
        checkIntReadable(optionData, "lease life");
        int leaseLife = optionData.readInt();
        return new Edns0LlqOption(version, opcode, errCode, id, leaseLife);
    }

    @Override
    public void encodeOptionData(Edns0LlqOption option, ByteBuf out) {
        out.writeShort(option.version())
           .writeShort(option.opcode())
           .writeShort(option.errCode())
           .writeLong(option.id())
           .writeInt(option.leaseLife());
    }
}
