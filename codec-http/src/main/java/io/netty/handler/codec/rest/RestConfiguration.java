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
package io.netty.handler.codec.rest;

import java.security.Key;
import java.util.HashMap;

/**
 * The configuration for REST handler
 */
public class RestConfiguration {
    /**
     * SERVER REST interface using time limit (default: no limit <= 0)
     */
    long restTimeLimit;

    /**
     * SERVER REST interface using signature of each request using the hmacSha
     */
    boolean restSignature;

    /**
     * Key for signature in SHA-256
     */
    Key hmacSha;

    /**
     * Associated Hmac algorithm as HmacSHA256
     */
    String hmacAlgorithm;

    /**
     * SERVER REST interface possible key for Signature
     */
    String restPrivateKey;

    /**
     * Associated RestMethod Handlers
     */
    HashMap<String, RestMethodHandler> restHashMap = new HashMap<String, RestMethodHandler>();

    /**
     * @return the restTimeLimit
     */
    public long restTimeLimit() {
        return restTimeLimit;
    }

    /**
     * @param restTimeLimit the restTimeLimit to set in ms (maximum delay with currentTime, 0 meaning no check)
     */
    public void setRestTimeLimit(long restTimeLimit) {
        this.restTimeLimit = restTimeLimit;
    }

    /**
     * @return the restPrivateKey
     */
    public String restPrivateKey() {
        return restPrivateKey;
    }

    /**
     * @param restPrivateKey the possible private key to use (not passed to the wire but used in signature) 
     */
    public void setRestPrivateKey(String restPrivateKey) {
        this.restPrivateKey = restPrivateKey;
    }

    /**
     * @return the restSignature
     */
    public boolean restSignature() {
        return restSignature;
    }

    /**
     * @param restSignature True if the Rest protocol use a signature
     */
    public void setRestSignature(boolean restSignature) {
        this.restSignature = restSignature;
    }

    /**
     * @return the hmacSha
     */
    public Key hmacSha() {
        return hmacSha;
    }

    /**
     * @param hmacSha the hmacSha to set
     */
    public void setHmacSha(Key hmacSha) {
        this.hmacSha = hmacSha;
    }

    /**
     * @return the hmacAlgorithm
     */
    public String hmacAlgorithm() {
        return hmacAlgorithm;
    }

    /**
     * @param hmacAlgorithm the hmacAlgorithm to set
     */
    public void setHmacAlgorithm(String hmacAlgorithm) {
        this.hmacAlgorithm = hmacAlgorithm;
    }

    /**
     * @return the restHashMap
     */
    public HashMap<String, RestMethodHandler> restMethodHandler() {
        return restHashMap;
    }

    /**
     * @param restHashMap the restHashMap to set
     */
    public void setRestMethodHandler(HashMap<String, RestMethodHandler> restHashMap) {
        this.restHashMap = restHashMap;
    }

    /**
     * @param handler RestMethodHandler added
     */
    public void addRestMethodHandler(RestMethodHandler handler) {
        this.restHashMap.put(handler.path(), handler);
    }

    /**
     * @return the {@link RestMethodHandler} associated with the name
     */
    public RestMethodHandler getRestMethodHandler(String name) {
        return restHashMap.get(name);
    }
}
