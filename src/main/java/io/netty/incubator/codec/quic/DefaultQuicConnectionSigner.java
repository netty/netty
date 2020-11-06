
/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.quic;

import io.netty.buffer.ByteBuf;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

final class DefaultQuicConnectionSigner implements QuicConnectionIdSigner {

    private static final String ALGORITHM = "HMACSHA256";
    private final Mac mac;

    DefaultQuicConnectionSigner() {
        try {
            byte[] seed = KeyGenerator.getInstance(ALGORITHM).generateKey().getEncoded();
            mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(seed, ALGORITHM));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }

    @Override
    public byte[] sign(ByteBuf input) {
        mac.update(input.internalNioBuffer(input.readerIndex(), input.readableBytes()));
        final byte[] signed = mac.doFinal();
        return Arrays.copyOfRange(signed, 0, Quiche.QUICHE_MAX_CONN_ID_LEN);
    }
}
