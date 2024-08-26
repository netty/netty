/*
 * Copyright 2024 The Netty Project
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
package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;
import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class Extension implements DerWriter.WritableSequence {
    private final String extnId; // An OBJECT IDENTIFIER
    private final boolean critical;
    private final byte[] extnValue; // Contents of the value OCTET STRING

    public Extension(String extnId, boolean critical, byte[] extnValue) {
        this.extnId = extnId;
        this.critical = critical;
        this.extnValue = extnValue;
    }

    public void encode(DerWriter writer) {
        writer.writeSequence(this);
    }

    @Override
    public void writeSequence(DerWriter writer) {
        writer.writeObjectIdentifier(extnId);
        writer.writeBoolean(critical);
        writer.writeOctetString(extnValue);
    }
}
