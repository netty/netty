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
package io.netty.handler.codec.socksx.v4;

/**
 * A SOCKS4a {@code CONNECT} or {@code BIND} request.
 */
public interface Socks4CommandRequest extends Socks4Message {

    /**
     * Returns the type of this request.
     */
    Socks4CommandType type();

    /**
     * Returns the {@code USERID} field of this request.
     */
    String userId();

    /**
     * Returns the {@code DSTIP} field of this request.
     */
    String dstAddr();

    /**
     * Returns the {@code DSTPORT} field of this request.
     */
    int dstPort();
}
