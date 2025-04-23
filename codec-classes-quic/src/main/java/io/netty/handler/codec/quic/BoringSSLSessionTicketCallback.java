/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.util.internal.PlatformDependent;
import org.jetbrains.annotations.Nullable;

final class BoringSSLSessionTicketCallback {

    // As we dont assume to have a lot of keys configured we will just use an array for now as a data store.
    private volatile byte[][] sessionKeys;

    // Accessed via JNI.
    byte @Nullable [] findSessionTicket(byte @Nullable [] keyname) {
        byte[][] keys = this.sessionKeys;
        if (keys == null || keys.length == 0) {
            return null;
        }
        if (keyname == null) {
            return keys[0];
        }

        for (int i = 0; i < keys.length; i++) {
            byte[] key = keys[i];
            if (PlatformDependent.equals(keyname, 0, key, 1, keyname.length)) {
                return key;
            }
        }
        return null;
    }

    void setSessionTicketKeys(SslSessionTicketKey @Nullable [] keys) {
        if (keys != null && keys.length != 0) {
            byte[][] sessionKeys = new byte[keys.length][];
            for (int i = 0; i < keys.length; ++i) {
                SslSessionTicketKey key = keys[i];
                byte[] binaryKey = new byte[49];
                // We mark the first key as preferred by using 1 as byte marker
                binaryKey[0] = i == 0 ? (byte) 1 : (byte) 0;
                int dstCurPos = 1;
                System.arraycopy(key.name, 0, binaryKey, dstCurPos, 16);
                dstCurPos += 16;
                System.arraycopy(key.hmacKey, 0, binaryKey, dstCurPos, 16);
                dstCurPos += 16;
                System.arraycopy(key.aesKey, 0, binaryKey, dstCurPos, 16);
                sessionKeys[i] = binaryKey;
            }
            this.sessionKeys = sessionKeys;
        } else {
            sessionKeys = null;
        }
    }
}
