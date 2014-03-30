/*
 * Copyright 2013 The Netty Project
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
package io.netty.handler.codec.memcache.ascii.response;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheResponse;

public class AsciiMemcacheStoreResponse extends AbstractAsciiMemcacheMessage implements AsciiMemcacheResponse {

    private final StorageResponse response;

    public AsciiMemcacheStoreResponse(final StorageResponse response) {
        this.response = response;
    }

    public StorageResponse getResponse() {
        return response;
    }

    public static enum StorageResponse {
        STORED("STORED"),
        NOT_STORED("NOT_STORED"),
        EXISTS("EXISTS"),
        NOT_FOUND("NOT_FOUND");

        private final String value;

        StorageResponse(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
