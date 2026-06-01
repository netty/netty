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

import io.netty.util.concurrent.FastThreadLocal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

final class Hmac {

    private static final String ALGORITHM = "HmacSHA256";

    // Two independent keys so that CID signing and reset-token signing are
    // cryptographically decoupled: an observer cannot derive one output from
    // the other even when both use the same input value (RFC 9000 §21.11).
    private static final byte[] CID_KEY   = new byte[32];
    private static final byte[] TOKEN_KEY = new byte[32];

    static {
        SecureRandom rng = new SecureRandom();
        rng.nextBytes(CID_KEY);
        rng.nextBytes(TOKEN_KEY);
    }

    private static final FastThreadLocal<Mac> CID_MACS = new FastThreadLocal<Mac>() {
        @Override
        protected Mac initialValue() {
            return newMac(CID_KEY);
        }
    };

    private static final FastThreadLocal<Mac> TOKEN_MACS = new FastThreadLocal<Mac>() {
        @Override
        protected Mac initialValue() {
            return newMac(TOKEN_KEY);
        }
    };

    private static Mac newMac(byte[] key) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(keySpec);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ByteBuffer sign(Mac mac, ByteBuffer input, int outLength) {
        mac.reset();
        mac.update(input);
        byte[] signBytes = mac.doFinal();
        if (signBytes.length != outLength) {
            signBytes = Arrays.copyOf(signBytes, outLength);
        }
        return ByteBuffer.wrap(signBytes);
    }

    static ByteBuffer signCid(ByteBuffer input, int outLength) {
        return sign(CID_MACS.get(), input, outLength);
    }

    static ByteBuffer signToken(ByteBuffer input, int outLength) {
        return sign(TOKEN_MACS.get(), input, outLength);
    }

    private Hmac() { }
}
