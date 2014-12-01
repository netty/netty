/*
 * Copyright 2014 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.StringUtil;
import java.nio.charset.Charset;

/**
 * Represents a mail exchanger record dns record.
 */
public final class MailExchangerDnsRecord extends DnsEntry {

    private final int preference;
    private final String mailExchanger;

    public MailExchangerDnsRecord(String name, long ttl, int preference, String mailExchanger) {
        this(name, DnsType.MX, ttl, preference, mailExchanger);
    }

    public MailExchangerDnsRecord(String name, DnsType type, long ttl,
            int preference, String mailExchanger) {
        this(name, type, DnsClass.IN, ttl, preference, mailExchanger);
    }

    public MailExchangerDnsRecord(String name, DnsType type, DnsClass dnsClass, long ttl,
            int preference, String mailExchanger) {
        super(name, type, dnsClass, ttl);
        if (mailExchanger == null) {
            throw new NullPointerException("mailExchanger");
        }
        this.preference = preference;
        this.mailExchanger = mailExchanger;
    }

    /**
     * The preference the mail server has among other MX records returned in a
     * response.
     *
     * @return the preference value
     */
    public int preference() {
        return preference;
    }

    /**
     * The mail exchanger host this record refers to.
     *
     * @return The mail exchange server name
     */
    public String mailExchanger() {
        return mailExchanger;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && o instanceof MailExchangerDnsRecord
                && ((MailExchangerDnsRecord) o).preference == preference
                && ((MailExchangerDnsRecord) o).mailExchanger.equals(mailExchanger);
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 23 * hash + this.preference;
        hash = 23 * hash + (this.mailExchanger != null ? this.mailExchanger.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(", ttl: ").append(timeToLive())
                .append(", preference: ").append(preference())
                .append(", mailExchanger: ").append(mailExchanger())
                .append(')').toString();
    }

    @Override
    protected void writePayload(NameWriter nameWriter, ByteBuf into, Charset charset) {
        into.writeShort(preference);
        nameWriter.writeName(mailExchanger, into, charset);
    }

    @Override
    public MailExchangerDnsRecord withTimeToLive(long seconds) {
        return new MailExchangerDnsRecord(name(), type(), dnsClass(), seconds, preference, mailExchanger);
    }
}
