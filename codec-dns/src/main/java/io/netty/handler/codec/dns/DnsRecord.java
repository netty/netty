/*
 * Copyright 2015 The Netty Project
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

/**
 * A DNS resource record.
 */
public interface DnsRecord {

    /**
     * DNS resource record class: {@code IN}
     */
    int CLASS_IN = 0x0001;

    /**
     * DNS resource record class: {@code CSNET}
     */
    int CLASS_CSNET = 0x0002;

    /**
     * DNS resource record class: {@code CHAOS}
     */
    int CLASS_CHAOS = 0x0003;

    /**
     * DNS resource record class: {@code HESIOD}
     */
    int CLASS_HESIOD = 0x0004;

    /**
     * DNS resource record class: {@code NONE}
     */
    int CLASS_NONE = 0x00fe;

    /**
     * DNS resource record class: {@code ANY}
     */
    int CLASS_ANY = 0x00ff;

    /**
     * Returns the name of this resource record.
     */
    String name();

    /**
     * Returns the type of this resource record.
     */
    DnsRecordType type();

    /**
     * Returns the class of this resource record.
     *
     * @return the class value, usually one of the following:
     *         <ul>
     *             <li>{@link #CLASS_IN}</li>
     *             <li>{@link #CLASS_CSNET}</li>
     *             <li>{@link #CLASS_CHAOS}</li>
     *             <li>{@link #CLASS_HESIOD}</li>
     *             <li>{@link #CLASS_NONE}</li>
     *             <li>{@link #CLASS_ANY}</li>
     *         </ul>
     */
    int dnsClass();

    /**
     * Returns the time to live after reading for this resource record.
     */
    long timeToLive();
}
