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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.netty.util.internal.ObjectUtil.checkPositiveOrZero;

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.net.IDN;

/**
 * Default {@link DnsSoaRecord} implementation.
 */
@UnstableApi
public class DefaultDnsSoaRecord extends AbstractDnsRecord implements DnsSoaRecord {
    private final String primaryNameServer;
    private final String responsibleAuthorityMailbox;
    private final int serialNumber;
    private final int refreshInterval;
    private final int retryInterval;
    private final int expireLimit;
    private final int minimumTTL;

    /**
     * Creates a new SOA record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, usually one of the following:
     *                 <ul>
     *                     <li>{@link #CLASS_IN}</li>
     *                     <li>{@link #CLASS_CSNET}</li>
     *                     <li>{@link #CLASS_CHAOS}</li>
     *                     <li>{@link #CLASS_HESIOD}</li>
     *                     <li>{@link #CLASS_NONE}</li>
     *                     <li>{@link #CLASS_ANY}</li>
     *                 </ul>
     * @param timeToLive the TTL value of the record
     * @param primaryNameServer the name server that was the original or primary source of data for this zone.
     * @param responsibleAuthorityMailbox the mailbox of the person responsible for this zone.
     * @param serialNumber the version number of the original copy of the zone.
     * @param refreshInterval the time interval before the zone should be refreshed.
     * @param retryInterval the time interval that should elapse before a failed refresh should be retried.
     * @param expireLimit the time value that specifies the upper limit on the time interval
     * @param minimumTTL the minimum TTL field that should be exported with any RR from this zone.
     */
    public DefaultDnsSoaRecord(
            String name, int dnsClass, long timeToLive,
            String primaryNameServer, String responsibleAuthorityMailbox,
            int serialNumber, int refreshInterval, int retryInterval, int expireLimit, int minimumTTL) {
        super(name, DnsRecordType.SOA, dnsClass, timeToLive);
        this.primaryNameServer = IDN.toASCII(checkNotNull(primaryNameServer, "primaryNameServer"));
        this.responsibleAuthorityMailbox = IDN.toASCII(
                checkNotNull(responsibleAuthorityMailbox, "responsibleAuthorityMailbox"));
        this.serialNumber = checkPositiveOrZero(serialNumber, "serialNumber");
        this.refreshInterval = checkPositiveOrZero(refreshInterval, "refreshInterval");
        this.retryInterval = checkPositiveOrZero(retryInterval, "retryInterval");
        this.expireLimit = checkPositiveOrZero(expireLimit, "expireLimit");
        this.minimumTTL = checkPositiveOrZero(minimumTTL, "minimumTTL");
    }

    @Override
    public String primaryNameServer() { return primaryNameServer; }

    @Override
    public String responsibleAuthorityMailbox() { return responsibleAuthorityMailbox; }

    @Override
    public int serialNumber() { return serialNumber; }

    @Override
    public int refreshInterval() { return refreshInterval; }

    @Override
    public int retryInterval() { return retryInterval; }

    @Override
    public int expireLimit() { return expireLimit; }

    @Override
    public int minimumTTL() { return minimumTTL; }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false;  }
        if (!super.equals(o)) { return false;  }

        DefaultDnsSoaRecord that = (DefaultDnsSoaRecord) o;

        if (serialNumber != that.serialNumber) { return false;  }
        if (refreshInterval != that.refreshInterval) { return false;  }
        if (retryInterval != that.retryInterval) { return false;  }
        if (expireLimit != that.expireLimit) { return false;  }
        if (minimumTTL != that.minimumTTL) { return false;  }
        if (!primaryNameServer.equals(that.primaryNameServer)) { return false;  }
        return responsibleAuthorityMailbox.equals(that.responsibleAuthorityMailbox);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + primaryNameServer.hashCode();
        result = 31 * result + responsibleAuthorityMailbox.hashCode();
        result = 31 * result + serialNumber;
        result = 31 * result + refreshInterval;
        result = 31 * result + retryInterval;
        result = 31 * result + expireLimit;
        result = 31 * result + minimumTTL;
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');

        buf.append(name().isEmpty()? "<root>" : name())
                .append(' ')
                .append(timeToLive())
                .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                .append(' ')
                .append(type().name());

        buf.append(' ')
                .append(primaryNameServer)
                .append(' ')
                .append(responsibleAuthorityMailbox)
                .append(' ')
                .append(serialNumber)
                .append(' ')
                .append(refreshInterval)
                .append(' ')
                .append(retryInterval)
                .append(' ')
                .append(expireLimit)
                .append(' ')
                .append(minimumTTL);

        return buf.toString();
    }
}
