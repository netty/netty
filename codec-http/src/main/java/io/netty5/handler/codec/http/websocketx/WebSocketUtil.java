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
package io.netty5.handler.codec.http.websocketx;

import io.netty5.util.CharsetUtil;
import io.netty5.util.concurrent.FastThreadLocal;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A utility class mainly for use by web sockets.
 */
final class WebSocketUtil {

    private static final String V13_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final FastThreadLocal<MessageDigest> SHA1 = new FastThreadLocal<>() {
        @Override
        protected MessageDigest initialValue() throws Exception {
            try {
                // Try to get a MessageDigest that uses SHA1.
                // Suppress a warning about weak hash algorithm
                // since it's defined in https://datatracker.ietf.org/doc/html/rfc6455#section-10.8.
                return MessageDigest.getInstance("SHA-1"); // lgtm [java/weak-cryptographic-algorithm]
            } catch (NoSuchAlgorithmException e) {
                // This shouldn't happen! How old is the computer ?
                throw new InternalError("SHA-1 not supported on this platform - Outdated ?");
            }
        }
    };

    /**
     * Performs a SHA-1 hash on the specified data.
     *
     * @param data The data to hash
     * @return the hashed data
     */
    static byte[] sha1(byte[] data) {
        MessageDigest sha1Digest = SHA1.get();
        sha1Digest.reset();
        return sha1Digest.digest(data);
    }

    /**
     * Performs base64 encoding on the specified data.
     *
     * @param data The data to encode
     * @return an encoded string containing the data
     */
    static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Creates an arbitrary number of random bytes.
     *
     * @param size the number of random bytes to create
     * @return an array of random bytes
     */
    static byte[] randomBytes(int size) {
        var bytes = new byte[size];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    static String calculateV13Accept(String nonce) {
        String concat = nonce + V13_ACCEPT_GUID;
        byte[] sha1 = WebSocketUtil.sha1(concat.getBytes(CharsetUtil.US_ASCII));
        return WebSocketUtil.base64(sha1);
    }

    private WebSocketUtil() {
    }
}
