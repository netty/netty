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
package io.netty.handler.codec.memcache.ascii.request;

import io.netty.handler.codec.memcache.ascii.AbstractAsciiMemcacheMessage;
import io.netty.handler.codec.memcache.ascii.AsciiMemcacheRequest;

public class AsciiMemcacheStoreRequest extends AbstractAsciiMemcacheMessage implements AsciiMemcacheRequest {

    private final StorageCommand cmd;
    private final String key;
    private final int length;

    private int flags;
    private int expiration;
    private boolean noreply;
    private long cas;

    public AsciiMemcacheStoreRequest(final StorageCommand cmd, final String key, final int length) {
        this.cmd = cmd;
        this.key = key;
        this.length = length;

        flags = 0;
        expiration = 0;
        cas = 0;
        noreply = false;
    }

    public AsciiMemcacheStoreRequest setFlags(int flags) {
        this.flags = flags;
        return this;
    }

    public AsciiMemcacheStoreRequest setExpiration(int expiration) {
        this.expiration = expiration;
        return this;
    }

    public AsciiMemcacheStoreRequest setNoreply(boolean noreply) {
        this.noreply = noreply;
        return this;
    }

    public AsciiMemcacheStoreRequest setCas(long cas) {
        this.cas = cas;
        return this;
    }

    public StorageCommand getCmd() {
        return cmd;
    }

    public String getKey() {
        return key;
    }

    public int getLength() {
        return length;
    }

    public int getFlags() {
        return flags;
    }

    public int getExpiration() {
        return expiration;
    }

    public boolean getNoreply() {
        return noreply;
    }

    public long getCas() {
        return cas;
    }

    public static enum StorageCommand {
        SET("set"),
        ADD("add"),
        REPLACE("replace"),
        APPEND("append"),
        PREPEND("prepend"),
        CAS("cas");

        private final String value;

        StorageCommand(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}
