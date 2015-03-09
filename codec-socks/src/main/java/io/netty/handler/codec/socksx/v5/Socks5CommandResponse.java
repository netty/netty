/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

/**
 * A response to a SOCKS5 request detail message, as defined in
 * <a href="http://tools.ietf.org/html/rfc1928#section-6">the section 6, RFC1928</a>.
 */
public interface Socks5CommandResponse extends Socks5Message {

    /**
     * Returns the status of this response.
     */
    Socks5CommandStatus status();

    /**
     * Returns the address type of the {@code BND.ADDR} field of this response.
     */
    Socks5AddressType bndAddrType();

    /**
     * Returns the {@code BND.ADDR} field of this response.
     */
    String bndAddr();

    /**
     * Returns the {@code BND.PORT} field of this response.
     */
    int bndPort();
}
