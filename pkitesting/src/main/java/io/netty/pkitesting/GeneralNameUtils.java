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
package io.netty.pkitesting;

import io.netty.util.NetUtil;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.x509.GeneralName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.security.auth.x500.X500Principal;

/**
 * Encodes a limited set of GeneralName types, no decoding is supported.
 * See ITU-T X.509 (10/2019) Section 9.3.2.1, or RFC 5280 Section 4.2.1.6.
 */
final class GeneralNameUtils {
    private GeneralNameUtils() {
    }

    static GeneralName otherName(String oid, byte[] value) {
        try {
            DERSequence wrappedValue = new DERSequence(new ASN1Encodable[]{
                    new ASN1ObjectIdentifier(oid),
                    new DERTaggedObject(true, 0, ASN1Primitive.fromByteArray(value))
            });
            return new GeneralName(GeneralName.otherName, wrappedValue);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static GeneralName rfc822Name(String emailAddress) {
        return new GeneralName(GeneralName.rfc822Name, emailAddress);
    }

    static GeneralName dnsName(String dnsName) {
        URI uri = URI.create("ip://" + dnsName);
        String host = uri.getHost();
        return new GeneralName(GeneralName.dNSName, host);
    }

    static GeneralName directoryName(String x500Name) {
        return directoryName(new X500Principal(x500Name));
    }

    static GeneralName directoryName(X500Principal name) {
        try {
            return new GeneralName(GeneralName.directoryName, ASN1Primitive.fromByteArray(name.getEncoded()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static GeneralName uriName(String uri) throws URISyntaxException {
        return uriName(new URI(uri));
    }

    static GeneralName uriName(URI uri) {
        return new GeneralName(GeneralName.uniformResourceIdentifier, uri.toASCIIString());
    }

    static GeneralName ipAddress(String ipAddress) {
        if (!NetUtil.isValidIpV4Address(ipAddress) && !NetUtil.isValidIpV6Address(ipAddress)) {
            throw new IllegalArgumentException("Not a valid IP address: " + ipAddress);
        }
        return new GeneralName(GeneralName.iPAddress, ipAddress);
    }

    static GeneralName registeredId(String oid) {
        return new GeneralName(GeneralName.registeredID, oid);
    }
}
