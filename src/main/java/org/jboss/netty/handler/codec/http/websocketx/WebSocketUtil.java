/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.netty.handler.codec.http.websocketx;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.base64.Base64;
import org.jboss.netty.util.CharsetUtil;

/**
 * TODO Document me.
 */
final class WebSocketUtil {

    /**
     * Performs an MD5 hash
     *
     * @param bytes
     *            Data to hash
     * @return Hashed data
     */
    static byte[] md5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return md.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("MD5 not supported on this platform");
        }
    }

    /**
     * Performs an SHA-1 hash
     *
     * @param bytes
     *            Data to hash
     * @return Hashed data
     */
    static byte[] sha1(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            return md.digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-1 not supported on this platform");
        }
    }

    /**
     * Base 64 encoding
     *
     * @param bytes
     *            Bytes to encode
     * @return encoded string
     */
    static String base64(byte[] bytes) {
        ChannelBuffer hashed = ChannelBuffers.wrappedBuffer(bytes);
        return Base64.encode(hashed).toString(CharsetUtil.UTF_8);
    }

    /**
     * Creates some random bytes
     *
     * @param size
     *            Number of random bytes to create
     * @return random bytes
     */
    static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];

        for (int i = 0; i < size; i++) {
            bytes[i] = (byte) randomNumber(0, 255);
        }

        return bytes;
    }

    /**
     * Generates a random number
     *
     * @param min
     *            Minimum value
     * @param max
     *            Maximum value
     * @return Random number
     */
    static int randomNumber(int min, int max) {
        return (int) (Math.random() * max + min);
    }


    private WebSocketUtil() {
        // Unused
    }
}
