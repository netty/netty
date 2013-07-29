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
package io.netty.dns.decoder.record;

/**
 * Represents an SOA (start of authority) record, which defines global
 * parameters for a zone (domain). There can only be one SOA record per zone.
 */
public class StartOfAuthorityRecord {

    private final String primaryNameServer;
    private final String responsiblePerson;
    private final long serial;
    private final int refreshTime;
    private final int retryTime;
    private final int expireTime;
    private final long minimumTtl;

    /**
     * Constructs an SOA (start of authority) record.
     *
     * @param primaryNameServer
     *            any name server that will respond authoritatively for the
     *            domain
     * @param responsiblePerson
     *            e-mail address of person responsible for this zone
     * @param serial
     *            a serial number that must be incremented when changes are
     *            made. Recommended format is YYYYMMDDnn. For example, if the
     *            primary name server is changed on June 19, 2013, then the
     *            serial would be 2013061901. If it is changed again on the same
     *            day it would be 2013061902
     * @param refreshTime
     *            number of seconds a secondary name server waits, after getting
     *            a copy of the zone, before it checks the zone again for
     *            changes
     * @param retryTime
     *            number of seconds to wait after a failed refresh attempt
     *            before another attempt to refresh is made
     * @param expireTime
     *            number of seconds secondary name server can hold information
     *            before it is considered not authoritative
     * @param minimumTtl
     *            number of seconds that records in the zone are valid for (if a
     *            record has a higher TTL, it overrides this value which is just
     *            a minimum)
     */
    public StartOfAuthorityRecord(String primaryNameServer, String responsiblePerson, long serial, int refreshTime,
            int retryTime, int expireTime, long minimumTtl) {
        this.primaryNameServer = primaryNameServer;
        this.responsiblePerson = responsiblePerson;
        this.serial = serial;
        this.refreshTime = refreshTime;
        this.retryTime = retryTime;
        this.expireTime = expireTime;
        this.minimumTtl = minimumTtl;
    }

    /**
     * Returns the primary name server.
     */
    public String primaryNameServer() {
        return primaryNameServer;
    }

    /**
     * Returns the responsible person's e-mail.
     */
    public String responsiblePerson() {
        return responsiblePerson;
    }

    /**
     * Returns the zone's serial number, usually in format YYYYMMDDnn.
     */
    public long serial() {
        return serial;
    }

    /**
     * Returns time between refreshes for secondary name servers.
     */
    public int refreshTime() {
        return refreshTime;
    }

    /**
     * Returns time between retries for failed refresh attempts.
     */
    public int retryTime() {
        return retryTime;
    }

    /**
     * Returns time before information stored in secondary name servers becomes
     * non authoritative.
     */
    public int expireTime() {
        return expireTime;
    }

    /**
     * Returns the minimum TTL for records in the zone (if the record has a
     * higher TTL, that value should be used as the TTL).
     */
    public long minimumTtl() {
        return minimumTtl;
    }

}
