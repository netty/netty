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
 * {@link BinaryMemcacheResponse} messages.
 *
 * @see io.netty.handler.codec.memcache.binary.util.BinaryMemcacheResponseStatus
 */
public interface BinaryMemcacheResponseHeader extends BinaryMemcacheMessageHeader {

    /**
     * Returns the status of the response.
     *
     * @return the status of the response.
     */
    short getStatus();

    /**
     * Sets the status of the response.
     *
     * @param status the status to set.
     */
    BinaryMemcacheResponseHeader setStatus(short status);

}
