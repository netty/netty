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
package io.netty.pkitesting.x509;

import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class AlgorithmToOID {
    private AlgorithmToOID() {
    }

    public static String oidForAlgorithmName(String algorithmIdentifier) {
        switch (algorithmIdentifier) {
            case "SHA256withECDSA":
                return "1.2.840.10045.4.3.2";
            case "SHA384withECDSA":
                return "1.2.840.10045.4.3.3";
            case "SHA256withRSA":
                return "1.2.840.113549.1.1.11";
            case "SHA384withRSA":
                return "1.2.840.113549.1.1.12";
            case "Ed25519":
                return "1.3.101.112";
            case "Ed448":
                return "1.3.101.113";
            default:
                throw new UnsupportedOperationException("Algorithm not supported: " + algorithmIdentifier);
        }
    }
}
