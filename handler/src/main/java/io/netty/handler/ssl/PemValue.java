/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.AbstractReferenceCounted;
import io.netty.util.IllegalReferenceCountException;
import io.netty.util.internal.ObjectUtil;

/**
 * A PEM encoded value.
 *
 * @see PemEncoded
 * @see PemPrivateKey#toPEM(ByteBufAllocator, boolean, java.security.PrivateKey)
 * @see PemX509Certificate#toPEM(ByteBufAllocator, boolean, java.security.cert.X509Certificate[])
 */
class PemValue extends AbstractReferenceCounted implements PemEncoded {

    private final ByteBuf content;

    private final boolean sensitive;

    public PemValue(ByteBuf content, boolean sensitive) {
        this.content = ObjectUtil.checkNotNull(content, "content");
        this.sensitive = sensitive;
    }

    @Override
    public boolean isSensitive() {
        return sensitive;
    }

    @Override
    public ByteBuf content() {
        int count = refCnt();
        if (count <= 0) {
            throw new IllegalReferenceCountException(count);
        }

        return content;
    }

    @Override
    public PemValue copy() {
        return replace(content.copy());
    }

    @Override
    public PemValue duplicate() {
        return replace(content.duplicate());
    }

    @Override
    public PemValue retainedDuplicate() {
        return replace(content.retainedDuplicate());
    }

    @Override
    public PemValue replace(ByteBuf content) {
        return new PemValue(content, sensitive);
    }

    @Override
    public PemValue touch() {
        return (PemValue) super.touch();
    }

    @Override
    public PemValue touch(Object hint) {
        content.touch(hint);
        return this;
    }

    @Override
    public PemValue retain() {
        return (PemValue) super.retain();
    }

    @Override
    public PemValue retain(int increment) {
        return (PemValue) super.retain(increment);
    }

    @Override
    protected void deallocate() {
        if (sensitive) {
            SslUtils.zeroout(content);
        }
        content.release();
    }
}
