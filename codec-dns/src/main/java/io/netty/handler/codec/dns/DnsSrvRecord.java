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
 * A <a href="https://tools.ietf.org/html/rfc2782">SRV</a> record.
 * <p>
 * A record type is defined to store a generalized service location record,
 * used for newer protocols instead of creating protocol-specific records such as MX.
 */
@UnstableApi
public interface DnsSrvRecord extends DnsRecord {

    /**
     * Returns the priority of this target host.
     */
    int priority();

    /**
     * Returns the weight field specifies a relative weight for entries with the same priority.
     */
    int weight();

    /**
     * Returns the port on this target host of this service.
     */
    int port();

    /**
     * Returns the domain name of the target host this SRV record resolves to.
     */
    String target();

}
