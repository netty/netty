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
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.dns.record.opt.EDNS0Option;

/**
 * EDNS0OptionDecoder is responsible for decoding an EDNS0 Option in a group from RData.
 */
public interface EDNS0OptionDecoder {
    EDNS0OptionDecoder DEFAULT = new DefaultEDNS0OptionDecoder();

    /**
     * Decode an EDNS0 option.
     *
     * @param in option byte data
     *
     * @return {@link EDNS0Option} after decoding
     *
     * @throws CorruptedFrameException if the option frame is broken
     */
    EDNS0Option decodeOption(ByteBuf in);
}
