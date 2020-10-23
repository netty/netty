/*
 * Copyright 2015 The Netty Project
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

import io.netty.internal.tcnative.SessionTicketKey;

/**
 * Session Ticket Key
 */
public final class OpenSslSessionTicketKey {

    /**
     * Size of session ticket key name
     */
    public static final int NAME_SIZE = SessionTicketKey.NAME_SIZE;
    /**
     * Size of session ticket key HMAC key
     */
    public static final int HMAC_KEY_SIZE = SessionTicketKey.HMAC_KEY_SIZE;
    /**
     * Size of session ticket key AES key
     */
    public static final int AES_KEY_SIZE = SessionTicketKey.AES_KEY_SIZE;
    /**
     * Size of session ticker key
     */
    public static final int TICKET_KEY_SIZE = SessionTicketKey.TICKET_KEY_SIZE;

    final SessionTicketKey key;

    /**
     * Construct a OpenSslSessionTicketKey.
     *
     * @param name the name of the session ticket key
     * @param hmacKey the HMAC key of the session ticket key
     * @param aesKey the AES key of the session ticket key
     */
    public OpenSslSessionTicketKey(byte[] name, byte[] hmacKey, byte[] aesKey) {
        key = new SessionTicketKey(name.clone(), hmacKey.clone(), aesKey.clone());
    }

    /**
     * Get name.
     * @return the name of the session ticket key
     */
    public byte[] name() {
        return key.getName().clone();
    }

    /**
     * Get HMAC key.
     * @return the HMAC key of the session ticket key
     */
    public byte[] hmacKey() {
        return key.getHmacKey().clone();
    }

    /**
     * Get AES Key.
     * @return the AES key of the session ticket key
     */
    public byte[] aesKey() {
        return key.getAesKey().clone();
    }
}
