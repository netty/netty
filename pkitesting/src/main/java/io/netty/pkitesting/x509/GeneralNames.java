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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;

@UnstableApi
public final class GeneralNames {
    private GeneralNames() {
    }

    public static byte[] generalNames(Collection<GeneralName> names) {

        org.bouncycastle.asn1.x509.GeneralName[] namesArray = new org.bouncycastle.asn1.x509.GeneralName[names.size()];
        int i = 0;
        for (GeneralName name : names) {
            namesArray[i] = org.bouncycastle.asn1.x509.GeneralName.getInstance(name.getEncoded());
            i++;
        }
        org.bouncycastle.asn1.x509.GeneralNames generalNames = new org.bouncycastle.asn1.x509.GeneralNames(namesArray);
        try {
            return generalNames.getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
