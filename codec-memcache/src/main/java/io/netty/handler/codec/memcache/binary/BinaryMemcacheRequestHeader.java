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
package io.netty.handler.codec.memcache.binary;

/**
 * Extends the common {@link BinaryMemcacheMessageHeader} header fields with hose who can only show up in
 * {@link BinaryMemcacheRequest} messages.
 * <p/>
 * <p>Note that while the additional field in the request is called "reserved", it can still be used for a custom
 * memcached implementation. It will not be mirrored back like the
 * {@link io.netty.handler.codec.memcache.binary.BinaryMemcacheRequestHeader#getOpaque()} field, because in the
 * {@link BinaryMemcacheResponseHeader}, the status field is set there instead.</p>
 */
public interface BinaryMemcacheRequestHeader extends BinaryMemcacheMessageHeader {

    /**
     * Returns the reserved field value.
     *
     * @return the reserved field value.
     */
    short getReserved();

    /**
     * Sets the reserved field value.
     *
     * @param reserved the reserved field value.
     */
    BinaryMemcacheRequestHeader setReserved(short reserved);

}
