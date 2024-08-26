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

import java.util.Objects;

@UnstableApi
public final class DistributionPoint implements DerWriter.WritableSequence {
    final GeneralName fullName;
    final GeneralName issuer;

    public DistributionPoint(GeneralName fullName, GeneralName issuer) {
        this.fullName = Objects.requireNonNull(fullName, "fullName");
        this.issuer = issuer;
    }

    public void writeTo(DerWriter writer) {
        writer.writeSequence(this);
    }

    @Override
    public void writeSequence(DerWriter writer) {
        GeneralNames fullNames = new GeneralNames(fullName);
        writer.writeExplicit(DerWriter.TAG_CONTEXT | DerWriter.TAG_CONSTRUCTED,
                w -> fullNames.writeTo(DerWriter.TAG_CONSTRUCTED | DerWriter.TAG_CONTEXT, w));
        if (issuer != null) {
            GeneralNames issuerNames = new GeneralNames(issuer);
            writer.writeExplicit(DerWriter.TAG_CONTEXT | 2,
                    w -> issuerNames.writeTo(w));
        }
    }
}
