/*
 * Copyright 2016 The Netty Project
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
package io.netty5.handler.ssl;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.Resource;
import io.netty5.buffer.api.Send;
import io.netty5.buffer.api.internal.ResourceSupport;
import io.netty5.buffer.api.internal.Statics;
import io.netty5.util.CharsetUtil;

import java.math.BigInteger;
import java.security.Principal;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.util.internal.ObjectUtil.checkNonEmpty;
import static java.util.Objects.requireNonNull;

/**
 * This is a special purpose implementation of a {@link X509Certificate} which allows
 * the user to pass PEM/PKCS#8 encoded data straight into {@link OpenSslContext} without
 * having to parse and re-encode bytes in Java land.
 *
 * All methods other than what's implemented in {@link PemEncoded}'s throw
 * {@link UnsupportedOperationException}s.
 *
 * @see PemEncoded
 * @see OpenSslContext
 * @see #valueOf(byte[])
 * @see #valueOf(Buffer)
 */
public final class PemX509Certificate extends X509Certificate implements PemEncoded, Resource<PemX509Certificate> {

    private static final byte[] BEGIN_CERT = "-----BEGIN CERTIFICATE-----\n".getBytes(CharsetUtil.US_ASCII);
    private static final byte[] END_CERT = "\n-----END CERTIFICATE-----\n".getBytes(CharsetUtil.US_ASCII);

    /**
     * Creates a {@link PemEncoded} value from the {@link X509Certificate}s.
     */
    static PemEncoded toPEM(BufferAllocator alloc, X509Certificate... chain) throws CertificateEncodingException {
        checkNonEmpty(chain, "chain");

        // We can take a shortcut if there is only one certificate, and
        // it already happens to be a PemEncoded instance. This is the
        // ideal case and reason why all this exists. It allows the user
        // to pass pre-encoded bytes straight into OpenSSL without having
        // to do any of the extra work.
        if (chain.length == 1) {
            X509Certificate first = chain[0];
            if (first instanceof PemEncoded) {
                return ((PemEncoded) first).copy();
            }
        }

        boolean success = false;
        Buffer pem = null;
        try {
            for (X509Certificate cert : chain) {

                if (cert == null) {
                    throw new IllegalArgumentException("Null element in chain: " + Arrays.toString(chain));
                }

                if (cert instanceof PemEncoded) {
                    pem = append(alloc, (PemEncoded) cert, chain.length, pem);
                } else {
                    pem = append(alloc, cert, chain.length, pem);
                }
            }

            PemValue value = new PemValue(pem);
            success = true;
            return value;
        } finally {
            // Make sure we never leak the PEM's ByteBuf in the event of an Exception
            if (!success && pem != null) {
                pem.close();
            }
        }
    }

    /**
     * Appends the {@link PemEncoded} value to the {@link Buffer} (last arg) and returns it.
     * If the {@link Buffer} didn't exist yet, an off-heap buffer will be created.
     */
    private static Buffer append(
            BufferAllocator alloc, PemEncoded encoded, int count, Buffer pem) {
        Buffer content = encoded.content();

        if (pem == null) {
            // see the other append() method
            pem = alloc.allocate(content.readableBytes() * count);
        } else {
            pem.ensureWritable(content.readableBytes());
        }

        int length = content.readableBytes();
        pem.skipWritable(length);
        content.copyInto(content.readerOffset(), pem, pem.writerOffset(), length);
        return pem;
    }

    /**
     * Appends the {@link X509Certificate} value to the {@link Buffer} (last arg) and returns it.
     * If the {@link Buffer} didn't exist yet, an off-heap buffer will be created.
     */
    private static Buffer append(BufferAllocator alloc, X509Certificate cert, int count, Buffer pem)
            throws CertificateEncodingException {

        try (Buffer encoded = alloc.copyOf(cert.getEncoded());
             Buffer base64 = SslUtils.toBase64(alloc, encoded)) {
            int length = BEGIN_CERT.length + base64.readableBytes() + END_CERT.length;
            if (pem == null) {
                // We try to approximate the buffer's initial size. The sizes of
                // certificates can vary a lot so it'll be off a bit depending
                // on the number of elements in the array (count argument).
                pem = alloc.allocate(length * count);
            } else {
                pem.ensureWritable(length);
            }

            pem.writeBytes(BEGIN_CERT);
            pem.writeBytes(base64);
            pem.writeBytes(END_CERT);
        }

        return pem;
    }

    /**
     * Creates a {@link PemX509Certificate} from raw {@code byte[]}.
     *
     * ATTENTION: It's assumed that the given argument is a PEM/PKCS#8 encoded value.
     * No input validation is performed to validate it.
     */
    public static PemX509Certificate valueOf(byte[] key) {
        return valueOf(offHeapAllocator().copyOf(key));
    }

    /**
     * Creates a {@link PemX509Certificate} from raw {@code ByteBuf}.
     *
     * ATTENTION: It's assumed that the given argument is a PEM/PKCS#8 encoded value.
     * No input validation is performed to validate it.
     */
    public static PemX509Certificate valueOf(Buffer key) {
        return new PemX509Certificate(key);
    }

    private final Buffer content;

    private PemX509Certificate(Buffer content) {
        this.content = requireNonNull(content, "content").makeReadOnly();
    }

    @Override
    public Buffer content() {
        if (!content.isAccessible()) {
            throw Statics.attachTrace((ResourceSupport<?, ?>) content,
                                      new IllegalStateException("PemX509Certificate is closed."));
        }

        return content;
    }

    @Override
    public void close() {
        content.close();
    }

    @Override
    public PemX509Certificate copy() {
        return new PemX509Certificate(content.copy(content.readerOffset(), content.readableBytes(), true));
    }

    @Override
    public Send<PemX509Certificate> send() {
        return content.send().map(PemX509Certificate.class, PemX509Certificate::new);
    }

    @Override
    public boolean isAccessible() {
        return content.isAccessible();
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasUnsupportedCriticalExtension() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getCriticalExtensionOIDs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<String> getNonCriticalExtensionOIDs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getExtensionValue(String oid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkValidity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void checkValidity(Date date) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigInteger getSerialNumber() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Principal getIssuerDN() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Principal getSubjectDN() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getNotBefore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Date getNotAfter() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getTBSCertificate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getSignature() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSigAlgName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSigAlgOID() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getSigAlgParams() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean[] getIssuerUniqueID() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean[] getSubjectUniqueID() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean[] getKeyUsage() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getBasicConstraints() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void verify(PublicKey key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void verify(PublicKey key, String sigProvider) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublicKey getPublicKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PemX509Certificate)) {
            return false;
        }

        PemX509Certificate other = (PemX509Certificate) o;
        return content.equals(other.content);
    }

    @Override
    public int hashCode() {
        return content.hashCode();
    }

    @Override
    public String toString() {
        return content.toString(CharsetUtil.UTF_8);
    }
}
