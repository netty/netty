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

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

@UnstableApi
public final class GeneralNames implements DerWriter.WritableSequence {
    private final Collection<GeneralName> names;

    private GeneralNames(Collection<GeneralName> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Names cannot be empty");
        }
        this.names = names;
    }

    public GeneralNames(GeneralName name) {
        names = Collections.singletonList(Objects.requireNonNull(name, "name"));
    }

    public void writeTo(DerWriter writer) {
        writer.writeSequence(this);
    }

    public void writeTo(int tag, DerWriter writer) {
        writer.writeSequence(tag, this);
    }

    public static byte[] generalNames(Collection<GeneralName> names) {
        try (DerWriter der = new DerWriter()) {
            new GeneralNames(names).writeTo(der);
            return der.getBytes();
        }
    }

    @Override
    public void writeSequence(DerWriter writer) {
        names.forEach(n -> n.writeTo(writer));
    }
}
