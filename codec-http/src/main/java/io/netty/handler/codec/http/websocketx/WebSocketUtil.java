/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A utility class mainly for use by web sockets
 */
final class WebSocketUtil {

    // Suppress a warning about weak hash algorithm since it's defined in draft-ietf-hybi-thewebsocketprotocol-00
    @SuppressWarnings("lgtm[java/weak-cryptographic-algorithm]")
    private static final FastThreadLocal<MessageDigest> MD5 = new FastThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() throws Exception {
            try {
                //Try to get a MessageDigest that uses MD5
                return MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                //This shouldn't happen! How old is the computer?
                throw new InternalError("MD5 not supported on this platform - Outdated?");
            }
        }
    };

    // Suppress a warning about weak hash algorithm since it's defined in draft-ietf-hybi-thewebsocketprotocol-00
    @SuppressWarnings("lgtm[java/weak-cryptographic-algorithm]")
    private static final FastThreadLocal<MessageDigest> SHA1 = new FastThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() throws Exception {
            try {
                //Try to get a MessageDigest that uses SHA1
                return MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                //This shouldn't happen! How old is the computer?
                throw new InternalError("SHA-1 not supported on this platform - Outdated?");
            }
        }
    };

    /**
     * Performs a MD5 hash on the specified data
     *
     * @param data The data to hash
     * @return The hashed data
     */
    static byte[] md5(byte[] data) {
        // TODO(normanmaurer): Create md5 method that not need MessageDigest.
        return digest(MD5, data);
    }

    /**
     * Performs a SHA-1 hash on the specified data
     *
     * @param data The data to hash
     * @return The hashed data
     */
    static byte[] sha1(byte[] data) {
        // TODO(normanmaurer): Create sha1 method that not need MessageDigest.
        return digest(SHA1, data);
    }

    private static byte[] digest(FastThreadLocal<MessageDigest> digestFastThreadLocal, byte[] data) {
        MessageDigest digest = digestFastThreadLocal.get();
        digest.reset();
        return digest.digest(data);
    }

    /**
     * Performs base64 encoding on the specified data
     *
     * @param data The data to encode
     * @return An encoded string containing the data
     */
    @SuppressJava6Requirement(reason = "Guarded with java version check")
    static String base64(byte[] data) {
        if (PlatformDependent.javaVersion() >= 8) {
            return java.util.Base64.getEncoder().encodeToString(data);
        }
        String encodedString;
        ByteBuf encodedData = Unpooled.wrappedBuffer(data);
        try {
            ByteBuf encoded = Base64.encode(encodedData);
            try {
                encodedString = encoded.toString(CharsetUtil.UTF_8);
            } finally {
                encoded.release();
            }
        } finally {
            encodedData.release();
        }
        return encodedString;
    }

    /**
     * Creates an arbitrary number of random bytes
     *
     * @param size the number of random bytes to create
     * @return An array of random bytes
     */
    static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        PlatformDependent.threadLocalRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * Generates a pseudo-random number
     *
     * @param minimum The minimum allowable value
     * @param maximum The maximum allowable value
     * @return A pseudo-random number
     */
    static int randomNumber(int minimum, int maximum) {
        assert minimum < maximum;
        double fraction = PlatformDependent.threadLocalRandom().nextDouble();

        // the idea here is that nextDouble gives us a random value
        //
        //              0 <= fraction <= 1
        //
        // the distance from min to max declared as
        //
        //              dist = max - min
        //
        // satisfies the following
        //
        //              min + dist = max
        //
        // taking into account
        //
        //         0 <= fraction * dist <= dist
        //
        // we've got
        //
        //       min <= min + fraction * dist <= max
        return (int) (minimum + fraction * (maximum - minimum));
    }

    /**
     * A private constructor to ensure that instances of this class cannot be made
     */
    private WebSocketUtil() {
        // Unused
    }
}
