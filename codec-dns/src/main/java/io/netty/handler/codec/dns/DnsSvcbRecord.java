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

import java.util.List;

/**
 * An SVCB (Service Binding) record, as defined by
 * <a href="https://www.rfc-editor.org/rfc/rfc9460.html">RFC 9460</a>. The same interface represents records of type
 * {@link DnsRecordType#SVCB} and {@link DnsRecordType#HTTPS}, which share the same format.
 * <p>
 * A record is either in AliasMode ({@link #priority()} is {@code 0}), aliasing the service to
 * {@link #targetName()}, or in ServiceMode ({@link #priority()} is greater than {@code 0}), describing an
 * alternative endpoint for the service and its connection parameters ({@link #params()}).
 * <p>
 * This interface only exposes the content of the record. Interpreting it, such as choosing between records,
 * following aliases, checking that the params are self-consistent or ignoring unrecognized params, is left to
 * the code using the record.
 */
public interface DnsSvcbRecord extends DnsRecord {

    /**
     * Returns the SvcPriority of this record, in the range {@code [0, 65535]}. {@code 0} means AliasMode. Any
     * other value means ServiceMode, where a smaller value indicates a more preferred record.
     */
    int priority();

    /**
     * Returns the TargetName of this record: the domain name of the alternative endpoint in ServiceMode, or of
     * the alias in AliasMode.
     * <p>
     * The value {@code "."} has a special meaning: in AliasMode, the service is not available or does not exist;
     * in ServiceMode, the owner name of this record ({@link #name()}) is the effective TargetName.
     */
    String targetName();

    /**
     * Returns the SvcParams of this record. A decoded record lists them in strictly increasing key order.
     * See {@link SvcParam} for the params that have a dedicated class.
     */
    List<SvcParam> params();
}
