/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.handler.codec.CorruptedFrameException;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsSvcbRecord} implementation, for records of type {@link DnsRecordType#SVCB} and
 * {@link DnsRecordType#HTTPS}.
     * <p>
 * Instances are created with the constructors, by a {@link DefaultDnsRecordDecoder} with SVCB decoding enabled
 * (see {@link DefaultDnsRecordDecoder#DefaultDnsRecordDecoder(SvcbDecoderConfig)}), or from a {@link DnsRawRecord} with
 * {@link #DefaultDnsSvcbRecord(DnsRawRecord)}.
 * <p>
 * The constructors do not check the params against each other: {@link DefaultDnsRecordEncoder} checks key order,
 * duplicate keys and self-consistency when encoding the record. This record is not reference counted.
 */
@UnstableApi
public class DefaultDnsSvcbRecord extends AbstractDnsRecord implements DnsSvcbRecord {

    private static final Comparator<SvcParam> KEY_COMPARATOR = new Comparator<SvcParam>() {
        @Override
        public int compare(SvcParam a, SvcParam b) {
            return Integer.compare(a.key(), b.key());
        }
    };

    private final int priority;
    private final String targetName;
    private final List<SvcParam> params;

    /**
     * Creates a new record of type {@link DnsRecordType#SVCB} and class {@link DnsRecord#CLASS_IN}.
     *
     * @param name the domain name
     * @param timeToLive the TTL value of the record
     * @param priority the SvcPriority, in the range {@code [0, 65535]}; {@code 0} means AliasMode
     * @param targetName the TargetName
     * @param params the SvcParams, in any order; the list is copied and sorted by key
     */
    public DefaultDnsSvcbRecord(String name, long timeToLive, int priority, String targetName,
                                List<SvcParam> params) {
        this(name, DnsRecord.CLASS_IN, timeToLive, priority, targetName, params);
    }

    /**
     * Creates a new record of type {@link DnsRecordType#SVCB}.
     *
     * @param name the domain name
     * @param dnsClass the class of the record
     * @param timeToLive the TTL value of the record
     * @param priority the SvcPriority, in the range {@code [0, 65535]}; {@code 0} means AliasMode
     * @param targetName the TargetName
     * @param params the SvcParams, in any order; the list is copied and sorted by key
     */
    public DefaultDnsSvcbRecord(String name, int dnsClass, long timeToLive, int priority, String targetName,
                                List<SvcParam> params) {
        this(name, DnsRecordType.SVCB, dnsClass, timeToLive, priority, targetName, params);
    }

    /**
     * Creates a new record of class {@link DnsRecord#CLASS_IN}.
     *
     * @param name the domain name
     * @param type the type of the record, {@link DnsRecordType#SVCB} or {@link DnsRecordType#HTTPS}
     * @param timeToLive the TTL value of the record
     * @param priority the SvcPriority, in the range {@code [0, 65535]}; {@code 0} means AliasMode
     * @param targetName the TargetName
     * @param params the SvcParams, in any order; the list is copied and sorted by key
     */
    public DefaultDnsSvcbRecord(String name, DnsRecordType type, long timeToLive, int priority, String targetName,
                                List<SvcParam> params) {
        this(name, type, DnsRecord.CLASS_IN, timeToLive, priority, targetName, params);
    }

    /**
     * Creates a new record.
     *
     * @param name the domain name
     * @param type the type of the record, {@link DnsRecordType#SVCB} or {@link DnsRecordType#HTTPS}
     * @param dnsClass the class of the record
     * @param timeToLive the TTL value of the record
     * @param priority the SvcPriority, in the range {@code [0, 65535]}; {@code 0} means AliasMode
     * @param targetName the TargetName
     * @param params the SvcParams, in any order; the list is copied and sorted by key
     */
    public DefaultDnsSvcbRecord(String name, DnsRecordType type, int dnsClass, long timeToLive, int priority,
                                String targetName, List<SvcParam> params) {
        super(name, checkSvcbType(type), dnsClass, timeToLive);
        if (priority < 0 || priority > 65535) {
            throw new IllegalArgumentException("Priority must be in the range 0-65535");
        }
        this.priority = priority;
        this.targetName = checkNotNull(targetName, "targetName");
        List<SvcParam> copy = new ArrayList<SvcParam>(checkNotNull(params, "params"));
        for (SvcParam param : copy) {
            checkNotNull(param, "param");
        }
        Collections.sort(copy, KEY_COMPARATOR);
        this.params = Collections.unmodifiableList(copy);
    }

    /**
     * Creates a new record by parsing the RDATA of a {@link DnsRawRecord} of type {@link DnsRecordType#SVCB} or
     * {@link DnsRecordType#HTTPS}.
     * The name, type, class and TTL are taken from {@code record}. The content of {@code record} is only read: its
     * reader index is not modified and {@code record} is not released.
     *
     * @param record the raw record
     * @throws IllegalArgumentException if {@code record} is not of type {@link DnsRecordType#SVCB} or
     *                                  {@link DnsRecordType#HTTPS}
     * @throws CorruptedFrameException if the RDATA of {@code record} is malformed
     */
    public DefaultDnsSvcbRecord(DnsRawRecord record) {
        this(DnsSvcbUtil.decodeRecord(record, SvcbDecoderConfig.DEFAULT_MAX_PARAMS,
                SvcbDecoderConfig.DEFAULT_MAX_ALPN_IDS, SvcbDecoderConfig.DEFAULT_MAX_HINT_ADDRESSES));
    }

    /**
     * Creates a new record by parsing the RDATA of a {@link DnsRawRecord} like
     * {@link #DefaultDnsSvcbRecord(DnsRawRecord)}, with the limits of {@code config}.
     *
     * @param record the raw record
     * @param config the limits of the decoding
     * @throws IllegalArgumentException if {@code record} is not of type {@link DnsRecordType#SVCB} or
     *                                  {@link DnsRecordType#HTTPS}
     * @throws CorruptedFrameException if the RDATA of {@code record} is malformed or exceeds a limit of {@code config}
     */
    public DefaultDnsSvcbRecord(DnsRawRecord record, SvcbDecoderConfig config) {
        this(DnsSvcbUtil.decodeRecord(record, checkNotNull(config, "config").maxParams(), config.maxAlpnIds(),
                config.maxHintAddresses()));
    }

    private DefaultDnsSvcbRecord(DefaultDnsSvcbRecord parsed) {
        this(parsed.name(), parsed.type(), parsed.dnsClass(), parsed.timeToLive(), parsed.priority(),
                parsed.targetName(), parsed.params());
    }

    /**
     * Returns whether the params of this record are self-consistent
     * (<a href="https://www.rfc-editor.org/rfc/rfc9460.html#section-2.4.3">RFC 9460, Section 2.4.3</a>):
     * every key listed by a {@link SvcParam.Mandatory} param is present, and {@link SvcParam.NoDefaultAlpn}
     * is only present together with {@link SvcParam.Alpn}. A {@code mandatory} param given as
     * {@link SvcParam.Unknown} is not checked.
     */
    public boolean isSelfConsistent() {
        return determineSelfConsistency() == null;
    }

    /**
     * Returns why the params of this record are not self-consistent, or {@code null} if they are.
     */
    String determineSelfConsistency() {
        for (SvcParam param : params) {
            if (param instanceof SvcParam.Mandatory) {
                for (int mandatoryKey : ((SvcParam.Mandatory) param).keys()) {
                    if (!containsKey(params, mandatoryKey)) {
                        return "mandatory SvcParamKey not present: " + mandatoryKey;
                    }
                }
            }
        }
        if (containsKey(params, SvcParam.NO_DEFAULT_ALPN) && !containsKey(params, SvcParam.ALPN)) {
            return "no-default-alpn requires alpn";
        }
        return null;
    }

    private static DnsRecordType checkSvcbType(DnsRecordType type) {
        checkNotNull(type, "type");
        if (type != DnsRecordType.SVCB && type != DnsRecordType.HTTPS) {
            throw new IllegalArgumentException("type must be SVCB or HTTPS but was " + type);
        }
        return type;
    }

    private static boolean containsKey(List<SvcParam> params, int key) {
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).key() == key) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public String targetName() {
        return targetName;
    }

    /**
     * Returns the SvcParams of this record, sorted by key.
     */
    @Override
    public List<SvcParam> params() {
        return params;
    }

    /**
     * Returns {@code true} if {@code obj} is a {@link DnsSvcbRecord} with the same name, type, class, priority,
     * TargetName (compared ignoring case) and params. The TTL is not compared, as in {@link AbstractDnsRecord}.
     * The params are compared in their order, so records built with the same params in a different order are not
     * equal; decoded records always list their params in increasing key order.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsSvcbRecord)) {
            return false;
        }
        DnsSvcbRecord that = (DnsSvcbRecord) obj;
        // Not super.equals(obj), whose shortcut on its own cached hash code does not match the hashCode() of this
        // class.
        return type().intValue() == that.type().intValue() && dnsClass() == that.dnsClass()
                && name().equalsIgnoreCase(that.name()) && priority == that.priority()
                && targetName.equalsIgnoreCase(that.targetName()) && params.equals(that.params());
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = hash * 31 + priority;
        hash = hash * 31 + targetName.toLowerCase(Locale.ROOT).hashCode();
        return hash * 31 + params.hashCode();
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        final DnsRecordType type = type();
        buf.append(name().isEmpty()? "<root>" : name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type.name());

        buf.append(' ')
           .append(priority);

        buf.append(' ')
           .append(targetName);

        for (SvcParam param : params) {
            buf.append(' ').append(param);
        }

        return buf.toString();
    }
}
