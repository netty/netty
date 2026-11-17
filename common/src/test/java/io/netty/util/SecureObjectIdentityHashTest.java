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
package io.netty.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

class SecureObjectIdentityHashTest {
    SecureObjectIdentityHash hashes = new SecureObjectIdentityHash();

    @RepeatedTest(100)
    public void sameObjectMustGetSameHashForRepeatedCalls() {
        Object a = new Object();
        byte[] first = hashes.getIdentityHash(a);
        byte[] second = hashes.getIdentityHash(a);
        assertThat(first).isEqualTo(second);
    }

    @RepeatedTest(100)
    public void differentObjectsMustHaveDifferentHashCodes() {
        Object a = new Object();
        Object b = new Object();
        assertThat(hashes.getIdentityHash(a)).isNotEqualTo(hashes.getIdentityHash(b));
    }

    @Test
    public void hashSizeIs32Bytes() {
        assertThat(hashes.getHashLengthBytes()).isEqualTo(32);
        assertThat(hashes.getIdentityHash(new Object())).hasSize(32);
    }

    @Test
    public void mustUpdateMessageDigestWithSameIdentityHash() throws NoSuchAlgorithmException {
        Object a = new Object();
        MessageDigest first = MessageDigest.getInstance("SHA-256");
        MessageDigest second = MessageDigest.getInstance("SHA-256");
        first.update(hashes.getIdentityHash(a));
        hashes.updateWithIdentityHash(second, a);
        assertThat(second.digest()).isEqualTo(first.digest());
    }

    @Test
    public void repeatedDigestUpdatesMustUseSameIdentityHash() throws NoSuchAlgorithmException {
        Object a = new Object();
        MessageDigest first = MessageDigest.getInstance("SHA-256");
        MessageDigest second = MessageDigest.getInstance("SHA-256");
        hashes.updateWithIdentityHash(first, a);
        hashes.updateWithIdentityHash(second, a);
        byte[] expected = first.digest();
        assertThat(second.digest()).isEqualTo(expected);
        MessageDigest third = MessageDigest.getInstance("SHA-256");
        third.update(hashes.getIdentityHash(a));
        assertThat(third.digest()).isEqualTo(expected);
    }
}
