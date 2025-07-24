/*
 * Copyright 2025 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.socksx.v5;

/**
 * A SOCKS5 subnegotiation request for private authentication.
 * <p>
 * RFC 1928 reserves method codes 0x80-0xFE for private authentication methods.
 * This interface provides a base implementation for method 0x80, but can be extended
 * for other private authentication methods by implementing custom encoders/decoders.
 * </p>
 *
 * @see <a href="https://www.ietf.org/rfc/rfc1928.txt">RFC 1928 Section 3</a>
 */
public interface Socks5PrivateAuthRequest extends Socks5Message {

    /**
     * Returns the private token of this request.
     * <p>
     * For custom subnegotiation protocols, this could be extended by adding
     * additional methods in a subinterface.
     * </p>
     *
     * @return the private authentication token
     */
    byte[] privateToken();
}
