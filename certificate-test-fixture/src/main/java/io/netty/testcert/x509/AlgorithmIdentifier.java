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

public final class AlgorithmIdentifier {
    private AlgorithmIdentifier() {
    }

    public static void writeAlgorithmId(String algorithmIdentifier, DerWriter writer) {
        switch (algorithmIdentifier) {
            case "SHA256WITHECDSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.10045.4.3.2"));
                break;
            case "SHA384WITHECDSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.10045.4.3.3"));
                break;
            case "SHA256WITHRSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.113549.1.1.11"));
                break;
            case "SHA384WITHRSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.113549.1.1.12"));
                break;
            case "Ed25519":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.3.101.112"));
                break;
            case "Ed448":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.3.101.113"));
                break;
            default:
                throw new UnsupportedOperationException("Algorithm not supported: " + algorithmIdentifier);
        }
    }
}
