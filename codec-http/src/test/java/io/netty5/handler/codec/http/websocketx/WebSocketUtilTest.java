/*
 * Copyright 2018 The Netty Project
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class WebSocketUtilTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 4, 8, 16, 24, 48, 64, 128 })
    void testRandomBytes(int size) {
        var random1 = WebSocketUtil.randomBytes(size);
        var random2 = WebSocketUtil.randomBytes(size);
        assertEquals(size, random1.length);
        assertEquals(size, random2.length);
        assertFalse(Arrays.equals(random1, random2));
    }

    @Test
    void testBase64() {
        var random = WebSocketUtil.randomBytes(8);
        String expected = Base64.getEncoder().encodeToString(random);
        assertEquals(expected, WebSocketUtil.base64(random));
    }

    @Test
    void testCalculateV13Accept() throws Exception {
        var random = new byte[16];
        ThreadLocalRandom.current().nextBytes(random);
        String nonce = Base64.getEncoder().encodeToString(random);

        String concat = nonce + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
        byte[] sha1 = sha1Digest.digest(concat.getBytes(StandardCharsets.US_ASCII));
        String expectedAccept = Base64.getEncoder().encodeToString(sha1);

        assertEquals(expectedAccept, WebSocketUtil.calculateV13Accept(nonce));
    }
}
