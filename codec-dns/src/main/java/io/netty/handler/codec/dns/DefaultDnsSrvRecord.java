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

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

@UnstableApi
public class DefaultDnsSrvRecord extends AbstractDnsRecord implements DnsSrvRecord {
    private final int priority;
    private final int weight;
    private final int port;
    private final String target;

    /**
     * Creates a new SRV record.
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
     * @param priority the priority of this target host.
     * @param weight the weight field specifies a relative weight for entries with the same priority.
     * @param port the port on this target host of this service.
     * @param target the domain name of the target host this SRV record resolves to.
     */
    public DefaultDnsSrvRecord(
            String name, int dnsClass, long timeToLive, int priority, int weight, int port, String target) {
        super(name, DnsRecordType.SRV, dnsClass, timeToLive);
        this.priority = priority;
        this.weight = weight;
        this.port = port;
        this.target = checkNotNull(target, "target");
    }

    @Override
    public int priority() { return priority; }

    @Override
    public int weight() { return weight; }

    @Override
    public int port() { return port; }

    @Override
    public String target() {
        return target;
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
                .append(priority)
                .append(' ')
                .append(weight)
                .append(' ')
                .append(port)
                .append(' ')
                .append(target);

        return buf.toString();
    }
}
