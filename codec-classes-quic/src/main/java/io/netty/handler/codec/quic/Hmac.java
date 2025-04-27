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

    private static final FastThreadLocal<Mac> MACS = new FastThreadLocal<Mac>() {
        @Override
        protected Mac initialValue() {
            return newMac();
        }
    };

    private static final String ALGORITM = "HmacSHA256";
    private static final byte[] randomKey = new byte[16];

    static {
        new SecureRandom().nextBytes(randomKey);
    }

    private static Mac newMac() {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(randomKey, ALGORITM);
            Mac mac = Mac.getInstance(ALGORITM);
            mac.init(keySpec);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static ByteBuffer sign(ByteBuffer input, int outLength) {
        Mac mac = MACS.get();
        mac.reset();
        mac.update(input);
        byte[] signBytes = mac.doFinal();
        if (signBytes.length != outLength) {
            signBytes = Arrays.copyOf(signBytes, outLength);
        }
        return ByteBuffer.wrap(signBytes);
    }

    private Hmac() { }
}
