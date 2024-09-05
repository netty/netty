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
public final class BasicConstraints {
    private BasicConstraints() {
    }

    public static byte[] withPathLength(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(w -> w.writeBoolean(true).writeInteger(length)).getBytes();
        }
    }

    public static byte[] isCa(boolean isCa) {
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(w -> w.writeBoolean(isCa)).getBytes();
        }
    }
}
