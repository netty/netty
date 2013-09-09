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
 * Represents an MX (mail exchanger) record, which contains a mail server
 * responsible for accepting e-mail and a preference value for prioritizing mail
 * servers if multiple servers exist.
 */
public class MailExchangerRecord {

    private final int priority;
    private final String name;

    /**
     * Constructs an MX (mail exchanger) record.
     *
     * @param priority
     *            the priority of the mail exchanger, lower is more preferred
     * @param name
     *            the e-mail address in the format admin.example.com, which
     *            represents admin@example.com
     */
    public MailExchangerRecord(int priority, String name) {
        this.priority = priority;
        this.name = name;
    }

    /**
     * Returns the priority of the mail exchanger, lower is more preferred.
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns the mail exchanger (an e-mail address) in the format
     * admin.example.com, which represents admin@example.com.
     */
    public String name() {
        return name;
    }

}
