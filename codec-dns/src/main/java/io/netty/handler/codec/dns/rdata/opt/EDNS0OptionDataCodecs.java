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

import io.netty.handler.codec.dns.record.opt.EDNS0Option;
import io.netty.handler.codec.dns.record.opt.EDNS0OptionCode;

import java.util.HashMap;
import java.util.Map;

/**
 * EDNS0OptionDataCodecs is a factory class that provides the ability to get codecs by EDNS0 option code.
 */
public final class EDNS0OptionDataCodecs {
    private static final Map<EDNS0OptionCode, EDNS0OptionDataCodec<? extends EDNS0Option>> CODECS =
            new HashMap<EDNS0OptionCode, EDNS0OptionDataCodec<? extends EDNS0Option>>();

    static {
        CODECS.put(EDNS0OptionCode.LLQ, EDNS0LlqOptionDataCodec.DEFAULT);
        CODECS.put(EDNS0OptionCode.SUBNET, EDNS0SubnetOptionDataCodec.DEFAULT);
    }

    private EDNS0OptionDataCodecs() {
        // Private constructor for factory class
    }

    public static EDNS0OptionDataDecoder<? extends EDNS0Option> optionDataDecoder(EDNS0OptionCode code) {
        return CODECS.get(code);
    }

    public static EDNS0OptionDataEncoder<? extends EDNS0Option> optionDataEncoder(EDNS0OptionCode code) {
        return CODECS.get(code);
    }
}
