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

import io.netty.util.NetUtil;
import io.netty.util.internal.UnstableApi;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.security.auth.x500.X500Principal;

/**
 * Encodes a limited set of GeneralName types, no decoding is supported.
 * See ITU-T X.509 (10/2019) Section 9.3.2.1.
 */
@UnstableApi
public final class GeneralName {
    private final byte[] der;

    private GeneralName(byte[] der) {
        this.der = der;
    }

    public byte[] getEncoded() {
        return der.clone();
    }

    public static GeneralName otherName(String oid, byte[] value) {
        try {
            return new GeneralName(new org.bouncycastle.asn1.x509.GeneralName(
                    org.bouncycastle.asn1.x509.GeneralName.otherName,
                    new DERSequence(new ASN1Encodable[]{
                            new ASN1ObjectIdentifier(oid),
                            new DERTaggedObject(true, 0, ASN1Primitive.fromByteArray(value))
                    })).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeneralName rfc822Name(String emailAddress) {
        try {
            return new GeneralName(new org.bouncycastle.asn1.x509.GeneralName(
                    org.bouncycastle.asn1.x509.GeneralName.rfc822Name,
                    emailAddress)
                    .getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeneralName dnsName(String dnsName) {
        URI uri = URI.create("ip://" + dnsName);
        String host = uri.getHost();
        try {
            return new GeneralName(new org.bouncycastle.asn1.x509.GeneralName(
                    org.bouncycastle.asn1.x509.GeneralName.dNSName,
                    host)
                    .getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeneralName directoryName(String x500Name) {
        return directoryName(new X500Principal(x500Name));
    }

    public static GeneralName directoryName(X500Principal name) {
        try {
            return new GeneralName(new DERTaggedObject(true, 4,
                    ASN1Primitive.fromByteArray(name.getEncoded())).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeneralName uriName(String uri) throws URISyntaxException {
        return uriName(new URI(uri));
    }

    public static GeneralName uriName(URI uri) {
        try {
            return new GeneralName(new org.bouncycastle.asn1.x509.GeneralName(
                    org.bouncycastle.asn1.x509.GeneralName.uniformResourceIdentifier,
                    uri.toASCIIString()).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeneralName ipAddress(String ipAddress) {
        if (!NetUtil.isValidIpV4Address(ipAddress) && !NetUtil.isValidIpV6Address(ipAddress)) {
            throw new IllegalArgumentException("Not a valid IP address: " + ipAddress);
        }
        try {
            return new GeneralName(new org.bouncycastle.asn1.x509.GeneralName(
                    org.bouncycastle.asn1.x509.GeneralName.iPAddress,
                    ipAddress).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static GeneralName registeredId(String oid) {
        try {
            return new GeneralName(new org.bouncycastle.asn1.x509.GeneralName(
                    org.bouncycastle.asn1.x509.GeneralName.registeredID,
                    oid).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
