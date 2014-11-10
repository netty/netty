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
package io.netty.handler.ssl;

/**
 * OpenSSL version of {@link ApplicationProtocolNegotiator}.
 */
public interface OpenSslApplicationProtocolNegotiator extends ApplicationProtocolNegotiator {
    // The current need for this interface is primarily for consistency with the JDK provider
    // How the OpenSsl provider will provide extensibility to control the application selection
    // and notification algorithms is not yet known (JNI, pure java, tcnative hooks, etc...).
    // OpenSSL itself is currently not in compliance with the specification for the 2 supported
    // protocols (ALPN, NPN) with respect to allowing the handshakes to fail during the application
    // protocol negotiation process.  Issue https://github.com/openssl/openssl/issues/188 has been created for this.
}
