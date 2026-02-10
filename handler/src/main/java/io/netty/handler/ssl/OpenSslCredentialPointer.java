/*
 * Copyright 2026 The Netty Project
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
package io.netty.handler.ssl;

import io.netty.util.IllegalReferenceCountException;

/**
 * Non-public interface that adds a method to get the raw pointer to the underlying credential object.
 */
interface OpenSslCredentialPointer extends OpenSslCredential {
    /**
     * Returns the native {@code SSL_CREDENTIAL} pointer address.
     *
     * @return the native pointer address
     * @throws IllegalReferenceCountException if the reference count is 0
     */
    long credentialAddress();
}
