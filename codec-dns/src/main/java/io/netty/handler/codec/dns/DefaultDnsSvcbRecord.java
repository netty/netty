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

import io.netty.util.internal.StringUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * The default {@link DnsSvcbRecord} implementation.
 */
public final class DefaultDnsSvcbRecord extends AbstractDnsRecord implements DnsSvcbRecord {

    private final int priority;
    private final String targetName;
    private final Map<Integer, byte[]> parameters;

    /**
     * Creates a new SVCB record.
     *
     * @param name the domain name
     * @param dnsClass the class of the record, see {@link DnsRecord} for constants
     * @param timeToLive the TTL value of the record
     * @param priority the priority
     * @param targetName the target name
     * @param parameters the service parameters
     */
    public DefaultDnsSvcbRecord(String name, int dnsClass, long timeToLive,
                                int priority, String targetName, Map<Integer, byte[]> parameters) {
        super(name, DnsRecordType.SVCB, dnsClass, timeToLive);
        this.priority = priority & 0xffff;
        this.targetName = checkNotNull(targetName, "targetName");
        this.parameters = copyParameters(parameters);
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public String targetName() {
        return targetName;
    }

    @Override
    public Map<Integer, byte[]> parameters() {
        return copyParameters(parameters);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof DnsSvcbRecord)) {
            return false;
        }
        if (!super.equals(obj)) {
            return false;
        }
        DnsSvcbRecord that = (DnsSvcbRecord) obj;
        return timeToLive() == that.timeToLive() &&
               priority == that.priority() &&
               targetName.equalsIgnoreCase(that.targetName()) &&
               parametersEqual(parameters, that.parameters());
    }

    @Override
    public int hashCode() {
        int hashCode = super.hashCode();
        hashCode = 31 * hashCode + (int) (timeToLive() ^ (timeToLive() >>> 32));
        hashCode = 31 * hashCode + priority;
        hashCode = 31 * hashCode + targetName.toLowerCase().hashCode();
        hashCode = 31 * hashCode + parametersHashCode(parameters);
        return hashCode;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder(64).append(StringUtil.simpleClassName(this)).append('(');
        buf.append(name().isEmpty() ? "<root>" : name())
           .append(' ')
           .append(timeToLive())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(' ')
                      .append(priority)
                      .append(' ')
                      .append(targetName)
                      .append(' ')
                      .append(parametersToString(parameters))
                      .append(')');

        return buf.toString();
    }

    private static Map<Integer, byte[]> copyParameters(Map<Integer, byte[]> parameters) {
        checkNotNull(parameters, "parameters");
        Map<Integer, byte[]> copy = new LinkedHashMap<Integer, byte[]>(parameters.size());
        for (Entry<Integer, byte[]> entry : parameters.entrySet()) {
            Integer key = checkNotNull(entry.getKey(), "key");
            byte[] value = checkNotNull(entry.getValue(), "value");
            copy.put(key & 0xffff, value.clone());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static boolean parametersEqual(Map<Integer, byte[]> left, Map<Integer, byte[]> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (Entry<Integer, byte[]> entry : left.entrySet()) {
            byte[] other = right.get(entry.getKey());
            if (other == null || !Arrays.equals(entry.getValue(), other)) {
                return false;
            }
        }
        return true;
    }

    private static int parametersHashCode(Map<Integer, byte[]> parameters) {
        int hashCode = 0;
        for (Entry<Integer, byte[]> entry : parameters.entrySet()) {
            hashCode = 31 * hashCode + entry.getKey().hashCode();
            hashCode = 31 * hashCode + Arrays.hashCode(entry.getValue());
        }
        return hashCode;
    }

    private static String parametersToString(Map<Integer, byte[]> parameters) {
        StringBuilder sb = new StringBuilder(32).append('{');
        for (Entry<Integer, byte[]> entry : parameters.entrySet()) {
            sb.append(entry.getKey())
              .append('=')
              .append(Arrays.toString(entry.getValue()))
              .append(',');
        }
        if (sb.length() > 1) {
            sb.setLength(sb.length() - 1);
        }
        return sb.append('}').toString();
    }
}
