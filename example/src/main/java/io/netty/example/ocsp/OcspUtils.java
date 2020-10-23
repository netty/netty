/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.ocsp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.BERTags;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.x509.extension.X509ExtensionUtil;

import io.netty.util.CharsetUtil;

public final class OcspUtils {
    /**
     * The OID for OCSP responder URLs.
     *
     * https://www.alvestrand.no/objectid/1.3.6.1.5.5.7.48.1.html
     */
    private static final ASN1ObjectIdentifier OCSP_RESPONDER_OID
        = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1").intern();

    private static final String OCSP_REQUEST_TYPE = "application/ocsp-request";

    private static final String OCSP_RESPONSE_TYPE = "application/ocsp-response";

    private OcspUtils() {
    }

    /**
     * Returns the OCSP responder {@link URI} or {@code null} if it doesn't have one.
     */
    public static URI ocspUri(X509Certificate certificate) throws IOException {
        byte[] value = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
        if (value == null) {
            return null;
        }

        ASN1Primitive authorityInfoAccess = X509ExtensionUtil.fromExtensionValue(value);
        if (!(authorityInfoAccess instanceof DLSequence)) {
            return null;
        }

        DLSequence aiaSequence = (DLSequence) authorityInfoAccess;
        DERTaggedObject taggedObject = findObject(aiaSequence, OCSP_RESPONDER_OID, DERTaggedObject.class);
        if (taggedObject == null) {
            return null;
        }

        if (taggedObject.getTagNo() != BERTags.OBJECT_IDENTIFIER) {
            return null;
        }

        byte[] encoded = taggedObject.getEncoded();
        int length = (int) encoded[1] & 0xFF;
        String uri = new String(encoded, 2, length, CharsetUtil.UTF_8);
        return URI.create(uri);
    }

    private static <T> T findObject(DLSequence sequence, ASN1ObjectIdentifier oid, Class<T> type) {
        for (ASN1Encodable element : sequence) {
            if (!(element instanceof DLSequence)) {
                continue;
            }

            DLSequence subSequence = (DLSequence) element;
            if (subSequence.size() != 2) {
                continue;
            }

            ASN1Encodable key = subSequence.getObjectAt(0);
            ASN1Encodable value = subSequence.getObjectAt(1);

            if (key.equals(oid) && type.isInstance(value)) {
                return type.cast(value);
            }
        }

        return null;
    }

    /**
     * TODO: This is a very crude and non-scalable HTTP client to fetch the OCSP response from the
     * CA's OCSP responder server. It's meant to demonstrate the basic building blocks on how to
     * interact with the responder server and you should consider using Netty's HTTP client instead.
     */
    public static OCSPResp request(URI uri, OCSPReq request, long timeout, TimeUnit unit) throws IOException {
        byte[] encoded = request.getEncoded();

        URL url = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout((int) unit.toMillis(timeout));
            connection.setReadTimeout((int) unit.toMillis(timeout));
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("host", uri.getHost());
            connection.setRequestProperty("content-type", OCSP_REQUEST_TYPE);
            connection.setRequestProperty("accept", OCSP_RESPONSE_TYPE);
            connection.setRequestProperty("content-length", String.valueOf(encoded.length));

            OutputStream out = connection.getOutputStream();
            try {
                out.write(encoded);
                out.flush();

                InputStream in = connection.getInputStream();
                try {
                    int code = connection.getResponseCode();
                    if (code != HttpsURLConnection.HTTP_OK) {
                        throw new IOException("Unexpected status-code=" + code);
                    }

                    String contentType = connection.getContentType();
                    if (!contentType.equalsIgnoreCase(OCSP_RESPONSE_TYPE)) {
                        throw new IOException("Unexpected content-type=" + contentType);
                    }

                    int contentLength = connection.getContentLength();
                    if (contentLength == -1) {
                        // Probably a terrible idea!
                        contentLength = Integer.MAX_VALUE;
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try {
                        byte[] buffer = new byte[8192];
                        int length = -1;

                        while ((length = in.read(buffer)) != -1) {
                            baos.write(buffer, 0, length);

                            if (baos.size() >= contentLength) {
                                break;
                            }
                        }
                    } finally {
                        baos.close();
                    }
                    return new OCSPResp(baos.toByteArray());
                } finally {
                    in.close();
                }
            } finally {
                out.close();
            }
        } finally {
            connection.disconnect();
        }
    }
}
