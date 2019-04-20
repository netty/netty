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

public class DefaultEDNS0OptionDecoder implements EDNS0OptionDecoder {
    @Override
    public EDNS0Option decodeOption(ByteBuf in) {
        short optionCode = in.readShort();
        short optionLength = in.readShort();
        EDNS0OptionDataDecoder<? extends EDNS0Option> optionDataDecoder =
                EDNS0OptionDataCodecs.optionDataDecoder(optionCode);
        if (optionDataDecoder == null) {
            throw new UnsupportedMessageTypeException("option code: " + optionCode);
        }
        int offset = in.readerIndex();
        ByteBuf optionData = in.retainedDuplicate().setIndex(offset, offset + optionLength);
        EDNS0Option option = optionDataDecoder.decodeOptionData(optionData);
        in.readerIndex(offset + optionLength);
        return option;
    }
}
