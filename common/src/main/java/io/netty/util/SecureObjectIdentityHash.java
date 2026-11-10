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

import io.netty.util.internal.WeakIdentityHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A secure way to associate 256 bits of entropy with the <em>identity</em> of an object.
 * This is the {@link SecureRandom} version, or {@code SHA-256} version, of {@link System#identityHashCode(Object)}.
 * <p>
 * <strong>Note:</strong> You can create <em>domain separation</em> between the identity hashes by having more than
 * one instance of this class. Each instance will create their own distinct hashes for the same objects.
 * <p>
 * The way this works internally is by using a {@link WeakIdentityHashMap} that uses <em>object identity</em>
 * (instead of {@link Object#equals(Object)} and {@link Object#hashCode()}) to associate objects with a 32-byte
 * token.
 * The token values are derived from a {@link SecureRandom} instance.
 * <p>
 * This class is thread-safe. Proper synchronization is done internally, so using {@code synchronized} on instances
 * of this class has no effect on its operation.
 */
public final class SecureObjectIdentityHash {
    private static final int HASH_LENGTH_BYTES = 32; // 256 bits.
    private static final Function<Object, Token> CREATE_TOKEN = ignore -> new Token();

    private final SecureRandom random;
    private final Map<Object, Token> mapping;
    private final Token nullToken;

    /**
     * Create a new instance.
     */
    public SecureObjectIdentityHash() {
        this(new SecureRandom());
    }

    /**
     * Create a new instance, initialized with the given {@link SecureRandom} instance.
     * <p>
     * <strong>Note:</strong> Prefer the no-arg {@link SecureObjectIdentityHash()} constructor.
     * Only use this method if you have <em>very specific requirements</em> of your secure random generators.
     * @param random The {@link SecureRandom} instance to use, not {@code null}.
     */
    public SecureObjectIdentityHash(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
        mapping = new WeakIdentityHashMap<>();
        nullToken = new Token();
    }

    /**
     * Get the length of the hash values, in bytes.
     * @return The hash value length in bytes.
     */
    public int getHashLengthBytes() {
        return HASH_LENGTH_BYTES;
    }

    /**
     * Get the secure object identity hash for the given object.
     * <p>
     * It is possible to get an identity hash value for {@code null}, but beware that all {@code null} values get
     * the same identity hash value.
     * <p>
     * The returned byte array is a copy of the internal value, so modifying it does not influence subsequent
     * or concurrent calls.
     * @param obj The object to get the identity hash for. Can be {@code null}.
     * @return The identity hash value.
     */
    public byte @NotNull [] getIdentityHash(@Nullable Object obj) {
        Token token = getTokenFor(obj);
        return token.getValueCopy(random);
    }

    /**
     * Update the given {@link MessageDigest} with the hash value for the given object.
     * <p>
     * It is possible to get an identity hash value for {@code null}, but beware that all {@code null} values get
     * the same identity hash value.
     * <p>
     * The returned byte array is a copy of the internal value, so modifying it does not influence subsequent
     * or concurrent calls.
     * @param digest The {@link MessageDigest} to update with the hash of the given object.
     *              The digest receives a single {@link MessageDigest#update(byte[])} call,
     *              with no length prefix or suffix.
     * @param obj The object to get the identity hash for. Can be {@code null}.
     */
    public void updateWithIdentityHash(MessageDigest digest, @Nullable Object obj) {
        Token token = getTokenFor(obj);
        token.updateDigest(random, digest);
    }

    private Token getTokenFor(Object obj) {
        if (obj == null) {
            return nullToken;
        }
        synchronized (mapping) {
            return mapping.computeIfAbsent(obj, CREATE_TOKEN);
        }
    }

    private static final class Token {
        private volatile byte[] value;
        private int checksum;

        public byte[] getValueCopy(SecureRandom random) {
            byte[] value = getOrCreateTokenValue(random);
            return value.clone();
        }

        @SuppressWarnings({"AssertWithSideEffects", "ConstantValue"})
        public void updateDigest(SecureRandom random, MessageDigest digest) {
            byte[] value = getOrCreateTokenValue(random);
            digest.update(value);
            // Best-effort at detecting nefarious 'MessageDigest' implementations; blow away stored hash if detected.
            assert checksum == Arrays.hashCode(value) || (this.value = null) != null :
                "MessageDigest " + digest + " modified its input array! " +
                    "Switch to an implementation that does not do this";
        }

        private byte @NotNull [] getOrCreateTokenValue(SecureRandom random) {
            byte[] value = this.value;
            if (value == null) {
                synchronized (this) {
                    value = this.value;
                    if (value == null) {
                        value = new byte[HASH_LENGTH_BYTES];
                        random.nextBytes(value);
                        checksum = Arrays.hashCode(value);
                        this.value = value;
                    }
                }
            }
            return value;
        }
    }
}
