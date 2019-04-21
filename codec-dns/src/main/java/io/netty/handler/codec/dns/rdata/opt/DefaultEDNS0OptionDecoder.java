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
import io.netty.handler.codec.dns.record.opt.EDNS0Option;
import io.netty.handler.codec.dns.record.opt.EDNS0OptionCode;
import io.netty.handler.codec.dns.record.opt.EDNS0RawOption;

import static io.netty.handler.codec.dns.util.DnsDecodeUtil.*;

/**
 * Default EDNS0 option decoder.
 */
public class DefaultEDNS0OptionDecoder implements EDNS0OptionDecoder {
    @Override
    public EDNS0Option decodeOption(ByteBuf in) {
        checkShortReadable(in, "option code");
        short code = in.readShort();
        checkShortReadable(in, "option length");
        short optionLength = in.readShort();
        EDNS0OptionCode optionCode = EDNS0OptionCode.valueOf(code);

        int offset = in.readerIndex();
        ByteBuf optionData = in.duplicate().setIndex(offset, offset + optionLength);

        EDNS0OptionDataDecoder<? extends EDNS0Option> optionDataDecoder =
                EDNS0OptionDataCodecs.optionDataDecoder(optionCode);

        EDNS0Option option;
        if (optionDataDecoder != null) { // Supported option
            option = optionDataDecoder.decodeOptionData(optionData);
        } else {  // Not supported option
            option = new EDNS0RawOption(optionCode, optionData.retain());
        }
        in.readerIndex(offset + optionLength);
        return option;
    }
}
