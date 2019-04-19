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

import io.netty.handler.codec.dns.record.opt.Edns0Option;
import io.netty.handler.codec.dns.rdata.opt.DefaultEdns0OptionDecoder.Edns0SubnetOptionDataCodec;

import java.util.HashMap;
import java.util.Map;

public final class Edns0OptionDataCodecs {
    private static final Map<Short, Edns0OptionDataCodec<? extends Edns0Option>> CODECS =
            new HashMap<Short, Edns0OptionDataCodec<? extends Edns0Option>>();

    static {
        CODECS.put(Edns0Option.OPTION_CODE_EDNS0_LLQ, Edns0LlqOptionDataCodec.DEFAULT);
        CODECS.put(Edns0Option.OPTION_CODE_EDNS0_SUBNET, Edns0SubnetOptionDataCodec.DEFAULT);
    }

    private Edns0OptionDataCodecs() {
        // Private constructor for factory class
    }

    public static Edns0OptionDataDecoder<? extends Edns0Option> optionDataDecoder(short type) {
        return CODECS.get(type);
    }

    public static Edns0OptionDataEncoder<? extends Edns0Option> optionDataEncoder(short type) {
        return CODECS.get(type);
    }

    public static Edns0OptionDataCodec<? extends Edns0Option> optionDataCodec(short type) {
        return CODECS.get(type);
    }
}
