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

/**
 * EDNS0OptionDataEncoder is responsible for encoding an EDNS0 option's data to {@link EDNS0Option}.
 *
 * @param <T> option that extends the superclass {@link EDNS0Option}
 */
public interface EDNS0OptionDataEncoder<T extends EDNS0Option> {
    /**
     * Encoding an EDNS0 option data, {@code out} buffer contain the option header that has been encoded.
     *
     * @param option EDNS0 option
     * @param out output buffer
     */
    void encodeOptionData(T option, ByteBuf out);
}
