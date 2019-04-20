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
import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.netty.handler.codec.dns.record.opt.EDNS0Option;

public class DefaultEDNS0OptionEncoder implements EDNS0OptionEncoder {

    @Override
    public void encodeOption(EDNS0Option option, ByteBuf out) {
        out.writeShort(option.optionCode());
        out.writeShort(0);
        int optionDataOffset = out.writerIndex();

        EDNS0OptionDataEncoder optionDataEncoder = EDNS0OptionDataCodecs.optionDataEncoder(option.optionCode());
        if (optionDataEncoder == null) {
            throw new UnsupportedMessageTypeException("option code: " + option.optionCode());
        }
        optionDataEncoder.encodeOptionData(option, out);

        // Write the option length
        int optionDataLength = out.writerIndex() - optionDataOffset;
        int optionDataEnd = out.writerIndex();
        out.writerIndex(optionDataOffset - Short.BYTES);
        out.writeShort(optionDataLength);
        out.writerIndex(optionDataEnd);
    }
}
