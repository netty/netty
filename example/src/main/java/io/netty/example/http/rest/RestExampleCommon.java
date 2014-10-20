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
package io.netty.example.http.rest;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.rest.RestConfiguration;
import io.netty.util.CharsetUtil;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;

/**
 * Global configuration for both Client and Server REST example
 */
public class RestExampleCommon {
    public final static String ALGO = "HmacSHA256";
    public final static String EXAMPLESHAKEY = "DD31A8BB42CF2A2F05EE1F43B8695241";
    public final static String EXAMPLESECRET = "secret";
    public final static int KEY_SIZE = 128;
    // Build configuration with/wo sign
    // Build handlers with actions/methods (simply respond ok if correctly called)
    public static RestConfiguration config;

    public static RestConfiguration getTestConfigurationWithSignature() {
        RestConfiguration configuration = new RestConfiguration();
        configuration.setRestPrivateKey(EXAMPLESECRET);
        configuration.setHmacSha(loadSecretKey(EXAMPLESHAKEY));
        configuration.setHmacAlgorithm(ALGO);
        configuration.setRestSignature(true);
        configuration.setRestTimeLimit(10000);
        configuration.addRestMethodHandler(new RestServerMethodHandler("test", false, configuration,
                HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT));
        configuration.addRestMethodHandler(new RestServerMethodHandler("test2", false, configuration,
                HttpMethod.HEAD, HttpMethod.PATCH, HttpMethod.TRACE));
        return configuration;
    }

    public static RestConfiguration getTestConfigurationNoSignature() {
        RestConfiguration configuration = new RestConfiguration();
        configuration.setRestSignature(false);
        configuration.setRestTimeLimit(10000);
        configuration.addRestMethodHandler(new RestServerMethodHandler("test", false, configuration,
                HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT));
        configuration.addRestMethodHandler(new RestServerMethodHandler("test2", false, configuration,
                HttpMethod.HEAD, HttpMethod.PATCH, HttpMethod.TRACE));
        return configuration;
    }

    /**
     * Example on how to save a SHA secret key
     * @throws IOException
     */
    public static void saveSecretKey(Key key, File file) throws IOException {
        byte[] bkey = key.getEncoded();
        String skey = toHexString(bkey);
        FileOutputStream outputStream = new FileOutputStream(file);
        try {
            outputStream.write(skey.getBytes(CharsetUtil.UTF_8));
            outputStream.flush();
        } finally {
            outputStream.close();
        }
    }

    /**
     * Example on how to load a SHA secret key
     * @throws IOException
     */
    public static Key loadSecretKey(File file) throws IOException {
        int len = (int) file.length();
        byte[] key = new byte[len];
        FileInputStream inputStream = null;
        inputStream = new FileInputStream(file);
        DataInputStream dis = new DataInputStream(inputStream);
        try {
            dis.readFully(key);
        } finally {
            dis.close();
        }
        String skey = new String(key, CharsetUtil.UTF_8);
        return loadSecretKey(skey);
    }

    /**
     * Example on how to load a SHA secret key
     * @param skey from a HEX form of a saved key
     */
    public static Key loadSecretKey(String skey) {
        byte[] realKey = hexStringToByteArray(skey);
        return new SecretKeySpec(realKey, RestExampleCommon.ALGO);
    }

    /**
     * Example on how to generate a SHA secret key
     * @throws Exception
     */
    public static Key generateKey() throws Exception {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(RestExampleCommon.ALGO);
            keyGen.init(RestExampleCommon.KEY_SIZE);
            Key secretKey = keyGen.generateKey();
            return secretKey;
        } catch (Exception e) {
            System.err.println("GenerateKey Error " + e);
            throw e;
        }
    }

    /**
     * Simple array of bytes to String in HEX format converter
     */
    public static String toHexString(byte[] array) {
        return DatatypeConverter.printHexBinary(array);
    }

    /**
     * Simple String in HEX format to array of bytes converter
     */
    public static byte[] hexStringToByteArray(String s) {
        return DatatypeConverter.parseHexBinary(s);
    }
}
