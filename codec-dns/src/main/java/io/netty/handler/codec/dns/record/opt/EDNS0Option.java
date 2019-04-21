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

/**
 * The variable part of an OPT RR may contain zero or more options in the RDATA, each option MUST be treated as a bit
 * field,  and each option is encoded as:
 *
 * <pre>
 *             +0 (MSB)                            +1 (LSB)
 *   +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 * 0: |                          OPTION-CODE                          |
 *   +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 * 2: |                         OPTION-LENGTH                         |
 *   +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 * 4: |                                                               |
 *    /                          OPTION-DATA                          /
 *    /                                                               /
 *   +---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+---+
 * </pre>
 * <p>
 * {@link EDNS0Option} defined the basic frame of the option.
 *
 * @see <a href="https://tools.ietf.org/html/rfc6891">https://tools.ietf.org/html/rfc6891</a>
 */
public interface EDNS0Option {
    // Assigned by the Expert Review process as defined by the DNSEXT working group and the IESG.
    EDNS0OptionCode optionCode();
}
