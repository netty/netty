/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec.haproxy;

final class HAProxyConstants {

    /**
     * Command byte constants
     */
    static final byte COMMAND_LOCAL_BYTE = 0x00;
    static final byte COMMAND_PROXY_BYTE = 0x01;

    /**
     * Version byte constants
     */
    static final byte VERSION_ONE_BYTE = 0x10;
    static final byte VERSION_TWO_BYTE = 0x20;

    /**
     * Transport protocol byte constants
     */
    static final byte TRANSPORT_UNSPEC_BYTE = 0x00;
    static final byte TRANSPORT_STREAM_BYTE = 0x01;
    static final byte TRANSPORT_DGRAM_BYTE = 0x02;

    /**
     * Address family byte constants
     */
    static final byte AF_UNSPEC_BYTE = 0x00;
    static final byte AF_IPV4_BYTE = 0x10;
    static final byte AF_IPV6_BYTE = 0x20;
    static final byte AF_UNIX_BYTE = 0x30;

    /**
     * Transport protocol and address family byte constants
     */
    static final byte TPAF_UNKNOWN_BYTE = 0x00;
    static final byte TPAF_TCP4_BYTE = 0x11;
    static final byte TPAF_TCP6_BYTE = 0x21;
    static final byte TPAF_UDP4_BYTE = 0x12;
    static final byte TPAF_UDP6_BYTE = 0x22;
    static final byte TPAF_UNIX_STREAM_BYTE = 0x31;
    static final byte TPAF_UNIX_DGRAM_BYTE = 0x32;

    private HAProxyConstants() { }
}
