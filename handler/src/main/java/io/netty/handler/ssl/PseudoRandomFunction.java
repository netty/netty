/*
 * Copyright 2019 The Netty Project
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

import io.netty.util.internal.EmptyArrays;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * This pseudorandom function (PRF) takes as input a secret, a seed, and
 * an identifying label and produces an output of arbitrary length.
 *
 * This is used by the TLS RFC to construct/deconstruct an array of bytes into
 * composite secrets.
 *
 * {@link <a href="https://tools.ietf.org/html/rfc5246">rfc5246</a>}
 */
final class PseudoRandomFunction {

    /**
     * Constructor never to be called.
     */
    private PseudoRandomFunction() {
    }

    /**
     * Use a single hash function to expand a secret and seed into an
     * arbitrary quantity of output.
     *
     * P_hash(secret, seed) = HMAC_hash(secret, A(1) + seed) +
     *                        HMAC_hash(secret, A(2) + seed) +
     *                        HMAC_hash(secret, A(3) + seed) + ...
     * where + indicates concatenation.
     * A() is defined as:
     *       A(0) = seed
     *       A(i) = HMAC_hash(secret, A(i-1))
     * @param secret The starting secret to use for expansion
     * @param label An ascii string without a length byte or trailing null character.
     * @param seed The seed of the hash
     * @param length The number of bytes to return
     * @param algo the hmac algorithm to use
     * @return The expanded secrets
     * @throws IllegalArgumentException if the algo could not be found.
     */
    static byte[] hash(byte[] secret, byte[] label, byte[] seed, int length, String algo) {
        if (length < 0) {
            throw new IllegalArgumentException("You must provide a length greater than zero.");
        }
        try {
            Mac hmac = Mac.getInstance(algo);
            hmac.init(new SecretKeySpec(secret, algo));
            /*
             * P_hash(secret, seed) = HMAC_hash(secret, A(1) + seed) +
             * HMAC_hash(secret, A(2) + seed) + HMAC_hash(secret, A(3) + seed) + ...
             * where + indicates concatenation. A() is defined as: A(0) = seed, A(i)
             * = HMAC_hash(secret, A(i-1))
             */

            int iterations = (int) Math.ceil(length / (double) hmac.getMacLength());
            byte[] expansion = EmptyArrays.EMPTY_BYTES;
            byte[] data = concat(label, seed);
            byte[] A = data;
            for (int i = 0; i < iterations; i++) {
                A = hmac.doFinal(A);
                expansion = concat(expansion, hmac.doFinal(concat(A, data)));
            }
            return Arrays.copyOf(expansion, length);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not find algo: " + algo, e);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
