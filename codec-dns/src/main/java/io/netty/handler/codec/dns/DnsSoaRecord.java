/*
 * Copyright 2017 The Netty Project
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

@UnstableApi
public interface DnsSoaRecord extends DnsRecord {

    /**
     * Returns the name server that was the original or primary source of data for this zone.
     */
    String primaryNameServer();

    /**
     * Returns the mailbox of the person responsible for this zone.
     */
    String responsibleAuthorityMailbox();

    /**
     * Returns the version number of the original copy of the zone.
     */
    int serialNumber();

    /**
     * Returns the time interval before the zone should be refreshed.
     */
    int refreshInterval();

    /**
     * Returns the time interval that should elapse before a failed refresh should be retried.
     */
    int retryInterval();

    /**
     * Returns the time value that specifies the upper limit on the time interval
     * that can elapse before the zone is no longer authoritative.
     */
    int expireLimit();

    /**
     * Returns the minimum TTL field that should be exported with any RR from this zone.
     */
    int minimumTTL();

}
