/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.ssl.util;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Generates a key specification for an (encrypted) private key.
 */
public abstract class KeySpecGenerator {

    /**
     * Generates a key specification for an (encrypted) private key.
     * @param password
     *            characters, if {@code null} or empty an unencrypted key is assumed
     * @param key
     *            bytes of the DER encoded private key
     * @return a key specification
     * @throws IOException
     *             if parsing {@code key} fails
     * @throws NoSuchAlgorithmException
     *             if the algorithm used to encrypt {@code key} is unkown
     * @throws NoSuchPaddingException
     *             if the padding scheme specified in the transformation (decryption) algorithm is unkown
     * @throws InvalidKeySpecException
     *             if the decryption key based on {@code password} cannot be generated
     * @throws InvalidKeyException
     *             if the decryption key based on {@code password} cannot be used to decrypt {@code key}
     * @throws InvalidAlgorithmParameterException
     *             if decryption algorithm parameters are somehow faulty
     */
    public static PKCS8EncodedKeySpec generateKeySpec(char[] password, byte[] key) throws IOException,
            NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException,
            InvalidAlgorithmParameterException {

        if (password == null || password.length == 0) {
            return new PKCS8EncodedKeySpec(key);
        }

        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(key);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        PBEKeySpec pbeKeySpec = new PBEKeySpec(password);
        SecretKey pbeKey = keyFactory.generateSecret(pbeKeySpec);

        Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
        cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());

        return encryptedPrivateKeyInfo.getKeySpec(cipher);
    }

}
