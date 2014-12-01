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
import static io.netty.handler.codec.dns.DnsClass.IN;
import io.netty.util.internal.StringUtil;
import java.nio.charset.Charset;

/**
 * The DNS question class which represents a question being sent to a server via
 * a query, or the question being duplicated and sent back in a response.
 * Usually a message contains a single question, and DNS servers often don't
 * support multiple questions in a single query.
 */
public final class DnsQuestion {

    private final String name;
    private final DnsType type;
    private final DnsClass clazz;

    /**
     * Constructs a question with the default class IN (Internet).
     *
     * @param name the domain name being queried i.e. "www.example.com"
     * @param type the question type, which represents the type of
     * {@link DnsResource} record that should be returned
     */
    public DnsQuestion(String name, DnsType type) {
        this(name, type, IN);
    }

    /**
     * Constructs a question with the given class.
     *
     * @param name the domain name being queried i.e. "www.example.com"
     * @param type the question type, which represents the type of
     * {@link DnsResource} record that should be returned
     * @param qClass the class of a DNS record
     */
    public DnsQuestion(String name, DnsType type, DnsClass qClass) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (qClass == null) {
            throw new NullPointerException("qClass");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be left blank.");
        }
        this.name = name;
        this.type = type;
        this.clazz = qClass;
    }

    /**
     * The name this question is asking about
     */
    public String name() {
        return name;
    }

    /**
     * The DNS class
     */
    public DnsClass dnsClass() {
        return clazz;
    }

    /**
     * The type of this question, such as A or AAAA
     */
    public DnsType type() {
        return type;
    }

    /**
     * Write this question into a {@link ByteBuf}.
     *
     * @param nameWriter Object which writes names, possibly using DNS
     * compression pointers
     * @param into The buffer to write the bytes into
     * @param charset The character set, typically UTF-8
     */
    public void writeTo(NameWriter nameWriter, ByteBuf into, Charset charset) {
        nameWriter.writeName(name(), into, charset);
        into.writeShort(type().intValue());
        into.writeShort(dnsClass().intValue());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + name.hashCode();
        hash = 67 * hash + type.hashCode();
        hash = 67 * hash + clazz.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        final DnsQuestion other = (DnsQuestion) obj;
        if (this.type != other.type && (this.type == null || !this.type.equals(other.type))) {
            return false;
        }
        if (this.clazz != other.clazz && (this.clazz == null || !this.clazz.equals(other.clazz))) {
            return false;
        }
        if ((this.name == null) ? (other.name != null) : !this.name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return new StringBuilder(128).append(StringUtil.simpleClassName(this))
                .append("(name: ").append(name())
                .append(", type: ").append(type())
                .append(", class: ").append(dnsClass())
                .append(')').toString();
    }
}
