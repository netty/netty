/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.handler.codec.EmptyHeaders;
import io.netty.util.internal.UnstableApi;

@UnstableApi
public final class EmptyHttp2Headers
        extends EmptyHeaders<CharSequence, CharSequence, Http2Headers> implements Http2Headers {
    public static final EmptyHttp2Headers INSTANCE = new EmptyHttp2Headers();

    private EmptyHttp2Headers() {
    }

    @Override
    public EmptyHttp2Headers method(CharSequence method) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EmptyHttp2Headers scheme(CharSequence status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EmptyHttp2Headers authority(CharSequence authority) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EmptyHttp2Headers path(CharSequence path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public EmptyHttp2Headers status(CharSequence status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CharSequence method() {
        return get(PseudoHeaderName.METHOD.value());
    }

    @Override
    public CharSequence scheme() {
        return get(PseudoHeaderName.SCHEME.value());
    }

    @Override
    public CharSequence authority() {
        return get(PseudoHeaderName.AUTHORITY.value());
    }

    @Override
    public CharSequence path() {
        return get(PseudoHeaderName.PATH.value());
    }

    @Override
    public CharSequence status() {
        return get(PseudoHeaderName.STATUS.value());
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
        return false;
    }
}
