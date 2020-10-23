/*
 * Copyright 2012 The Netty Project
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
 * An initial SOCKS5 authentication method selection request, as defined in
 * <a href="https://tools.ietf.org/html/rfc1928#section-3">the section 3, RFC1928</a>.
 */
public interface Socks5InitialResponse extends Socks5Message {

    /**
     * Returns the {@code METHOD} field of this response.
     */
    Socks5AuthMethod authMethod();
}
