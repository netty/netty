/*
 * Copyright 2018 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.util.internal.UnstableApi;

/**
 * A <a href="https://tools.ietf.org/html/rfc1035#section-3.3.9">MX</a> record.
 * <p>
 * A record type is defined to maps a domain name to a list of message transfer agents for that domain.
 */
@UnstableApi
public interface DnsMxRecord extends DnsRecord {

    /**
     * Returns the preference given to this RR among others at the same owner.
     */
    int preference();

    /**
     * Returns the exchange hostname this MX record resolves to.
     */
    String exchange();

}
