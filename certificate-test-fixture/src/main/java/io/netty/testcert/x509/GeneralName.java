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
import io.netty.util.NetUtil;

import java.net.URI;
import java.net.URISyntaxException;
import javax.security.auth.x500.X500Principal;

/**
 * Encodes a limited set of GeneralName types, no decoding is supported.
 * See ITU-T X.509 (10/2019) Section 9.3.2.1.
 */
public final class GeneralName {
    private static final int RFC822_NAME = 1;
    private static final int DNS_NAME = 2;
    private static final int URI_NAME = 6;
    private static final int IP_ADDRESS = 7;
    private static final int REGISTERED_ID = 8;

    private final byte[] der;

    private GeneralName(byte[] der) {
        this.der = der;
    }

    public void writeTo(DerWriter writer) {
        writer.writeRawDER(der);
    }

    public static GeneralName otherName(String oid, byte[] value) {
        try (DerWriter der = new DerWriter()) {
            der.writeObjectIdentifier(oid);
            der.writeRawDER(value);
            return new GeneralName(der.getBytes());
        }
    }

    public static GeneralName rfc822Name(String emailAddress) {
        if (emailAddress.indexOf('@') == -1 || emailAddress.endsWith("@") || emailAddress.endsWith("@.")) {
            throw new IllegalArgumentException("Invalid email address: " + emailAddress);
        }
        try (DerWriter der = new DerWriter()) {
            der.writeIA5String(RFC822_NAME| DerWriter.TAG_CONTEXT, emailAddress);
            return new GeneralName(der.getBytes());
        }
    }

    public static GeneralName dnsName(String dnsName) {
        URI uri = URI.create("ip://" + dnsName);
        String host = uri.getHost();
        try (DerWriter der = new DerWriter()) {
            der.writeIA5String(DNS_NAME| DerWriter.TAG_CONTEXT, host);
            return new GeneralName(der.getBytes());
        }
    }

    public static GeneralName directoryName(String x500Name) {
        return directoryName(new X500Principal(x500Name));
    }

    public static GeneralName directoryName(X500Principal x500Principal) {
        return new GeneralName(x500Principal.getEncoded());
    }

    public static GeneralName uriName(String uri) throws URISyntaxException {
        return uriName(new URI(uri));
    }

    public static GeneralName uriName(URI uri) {
        try (DerWriter der = new DerWriter()) {
            der.writeIA5String(URI_NAME| DerWriter.TAG_CONTEXT, uri.toASCIIString());
            return new GeneralName(der.getBytes());
        }
    }

    public static GeneralName ipAddress(String ipAddress) {
        if (!NetUtil.isValidIpV4Address(ipAddress) && !NetUtil.isValidIpV6Address(ipAddress)) {
            throw new IllegalArgumentException("Not a valid IP address: " + ipAddress);
        }
        try (DerWriter der = new DerWriter()) {
            der.writeOctetString(IP_ADDRESS| DerWriter.TAG_CONTEXT, NetUtil.createByteArrayFromIpAddressString(ipAddress));
            return new GeneralName(der.getBytes());
        }
    }

    public static GeneralName registeredId(String oid) {
        try (DerWriter der = new DerWriter()) {
            der.writeObjectIdentifier(REGISTERED_ID| DerWriter.TAG_CONTEXT, oid);
            return new GeneralName(der.getBytes());
        }
    }
}
