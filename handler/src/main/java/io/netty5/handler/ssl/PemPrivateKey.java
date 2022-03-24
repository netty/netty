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

import static io.netty5.buffer.api.DefaultBufferAllocators.offHeapAllocator;

import java.security.PrivateKey;

import javax.security.auth.Destroyable;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.BufferAllocator;
import io.netty5.buffer.api.BufferHolder;
import io.netty5.util.CharsetUtil;

/**
 * This is a special purpose implementation of a {@link PrivateKey} which allows the
 * user to pass PEM/PKCS#8 encoded key material straight into {@link OpenSslContext}
 * without having to parse and re-encode bytes in Java land.
 * <p>
 * All methods other than what's implemented in {@link PemEncoded} and {@link Destroyable}
 * throw {@link UnsupportedOperationException}s.
 *
 * @see PemEncoded
 * @see OpenSslContext
 * @see #valueOf(byte[])
 * @see #valueOf(Buffer)
 */
public final class PemPrivateKey extends BufferHolder<PemPrivateKey> implements PrivateKey, PemEncoded {
    private static final long serialVersionUID = 7978017465645018936L;

    private static final byte[] BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\n".getBytes(CharsetUtil.US_ASCII);
    private static final byte[] END_PRIVATE_KEY = "\n-----END PRIVATE KEY-----\n".getBytes(CharsetUtil.US_ASCII);

    private static final String PKCS8_FORMAT = "PKCS#8";

    /**
     * Creates a {@link PemEncoded} value from the {@link PrivateKey}.
     */
    static PemEncoded toPEM(BufferAllocator allocator, PrivateKey key) {
        // We can take a shortcut if the private key happens to be already
        // PEM/PKCS#8 encoded. This is the ideal case and reason why all
        // this exists. It allows the user to pass pre-encoded bytes straight
        // into OpenSSL without having to do any of the extra work.
        if (key instanceof PemEncoded) {
            return ((PemEncoded) key).copy();
        }

        byte[] bytes = key.getEncoded();
        if (bytes == null) {
            throw new IllegalArgumentException(key.getClass().getName() + " does not support encoding");
        }

        return toPEM(allocator, bytes);
    }

    static PemEncoded toPEM(BufferAllocator allocator, byte[] bytes) {
        try (Buffer encoded = allocator.copyOf(bytes);
             Buffer base64 = SslUtils.toBase64(allocator, encoded)) {
            try  {
                int size = BEGIN_PRIVATE_KEY.length + base64.readableBytes() + END_PRIVATE_KEY.length;

                boolean success = false;
                final Buffer pem = allocator.allocate(size);
                try {
                    pem.writeBytes(BEGIN_PRIVATE_KEY);
                    pem.writeBytes(base64);
                    pem.writeBytes(END_PRIVATE_KEY);

                    PemValue value = new PemValue(pem, true);
                    success = true;
                    return value;
                } finally {
                    // Make sure we never leak that PEM ByteBuf if there's an Exception.
                    if (!success) {
                        SslUtils.zeroout(pem);
                        pem.close();
                    }
                }
            } finally {
                SslUtils.zeroout(base64);
                SslUtils.zeroout(encoded);
            }
        }
    }

    /**
     * Creates a {@link PemPrivateKey} from raw {@code byte[]}.
     *
     * ATTENTION: It's assumed that the given argument is a PEM/PKCS#8 encoded value.
     * No input validation is performed to validate it.
     */
    public static PemPrivateKey valueOf(byte[] key) {
        return valueOf(offHeapAllocator().copyOf(key));
    }

    /**
     * Creates a {@link PemPrivateKey} from raw {@link Buffer}.
     *
     * ATTENTION: It's assumed that the given argument is a PEM/PKCS#8 encoded value.
     * No input validation is performed to validate it.
     */
    public static PemPrivateKey valueOf(Buffer key) {
        return new PemPrivateKey(key);
    }

    private PemPrivateKey(Buffer content) {
        super(content.makeReadOnly());
    }

    @Override
    public boolean isSensitive() {
        return true;
    }

    @Override
    public Buffer content() {
        if (!isAccessible()) {
            throw new IllegalStateException("PemPrivateKey is closed.");
        }

        return getBuffer();
    }

    @Override
    public PemPrivateKey copy() {
        Buffer buffer = getBuffer();
        return new PemPrivateKey(buffer.copy(buffer.readerOffset(), buffer.readableBytes(), true));
    }

    @Override
    protected PemPrivateKey receive(Buffer buf) {
        return new PemPrivateKey(buf);
    }

    @Override
    public void close() {
        // Private Keys are sensitive. We need to zero the bytes
        // before we're releasing the underlying Buffer
        // TODO cannot do this when the buffer is read-only
//        SslUtils.zeroout(getBuffer());
        super.close();
    }

    @Override
    public byte[] getEncoded() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getAlgorithm() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getFormat() {
        return PKCS8_FORMAT;
    }

    @Override
    public void destroy() {
        close();
    }

    @Override
    public boolean isDestroyed() {
        return !isAccessible();
    }
}
