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
 * A SOCKS5 subnegotiation response for private authentication.
 * <p>
 * This interface corresponds to the response for private authentication methods
 * in the range 0x80-0xFE as defined in RFC 1928. For custom private authentication
 * protocols, this interface can be extended with additional methods.
 * </p>
 *
 * @see <a href="https://www.ietf.org/rfc/rfc1928.txt">RFC 1928 Section 3</a>
 */
public interface Socks5PrivateAuthResponse extends Socks5Message {

    /**
     * Returns the status of this response.
     *
     * @return the authentication status
     */
    Socks5PrivateAuthStatus status();
}
