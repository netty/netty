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
 * A <a href="https://tools.ietf.org/html/rfc1035#section-3.3.1">CNAME</a> record.
 * <p>
 * A record type is defined to store <domain-name> which specifies the canonical or primary name for the owner.
 * The owner name is an alias.
 */
@UnstableApi
public interface DnsCNameRecord extends DnsRecord {
    /**
     * Returns the canonical or primary name this CNAME record resolves to.
     */
    String hostname();
}
