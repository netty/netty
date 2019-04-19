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

package io.netty.handler.codec.dns.record.opt;

public interface Edns0Option {
    // EDNS0 option code code
    short OPTION_CODE_EDNS0_LLQ = 0x1;
    short OPTION_CODE_EDNS0_UL = 0x2;
    short OPTION_CODE_EDNS0_NSID = 0x3;
    short OPTION_CODE_EDNS0_DAU = 0x5;
    short OPTION_CODE_EDNS0_DHU = 0x6;
    short OPTION_CODE_EDNS0_N3U = 0x7;
    short OPTION_CODE_EDNS0_SUBNET = 0x8;
    short OPTION_CODE_EDNS0_EXPIRE = 0x9;
    short OPTION_CODE_EDNS0_COOKIE = 0xa;
    short OPTION_CODE_EDNS0_TCPKEEPALIVE = 0xb;
    short OPTION_CODE_EDNS0_PADDING = 0xc;

    short optionCode();
}
