/*
 * Copyright 2015 The Netty Project
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

import io.netty.util.internal.StringUtil;
import io.netty.util.internal.UnstableApi;

/**
 * The default {@link DnsQuestion} implementation.
 */
@UnstableApi
public class DefaultDnsQuestion extends AbstractDnsRecord implements DnsQuestion {

    /**
     * Creates a new {@link #CLASS_IN IN-class} question.
     *
     * @param name the domain name of the DNS question
     * @param type the type of the DNS question
     */
    public DefaultDnsQuestion(String name, DnsRecordType type) {
        super(name, type, 0);
    }

    /**
     * Creates a new question.
     *
     * @param name the domain name of the DNS question
     * @param type the type of the DNS question
     * @param dnsClass the class of the record, usually one of the following:
     *                 <ul>
     *                     <li>{@link #CLASS_IN}</li>
     *                     <li>{@link #CLASS_CSNET}</li>
     *                     <li>{@link #CLASS_CHAOS}</li>
     *                     <li>{@link #CLASS_HESIOD}</li>
     *                     <li>{@link #CLASS_NONE}</li>
     *                     <li>{@link #CLASS_ANY}</li>
     *                 </ul>
     */
    public DefaultDnsQuestion(String name, DnsRecordType type, int dnsClass) {
        super(name, type, dnsClass, 0);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(64);

        buf.append(StringUtil.simpleClassName(this))
           .append('(')
           .append(name())
           .append(' ');

        DnsMessageUtil.appendRecordClass(buf, dnsClass())
                      .append(' ')
                      .append(type().name())
                      .append(')');

        return buf.toString();
    }
}
