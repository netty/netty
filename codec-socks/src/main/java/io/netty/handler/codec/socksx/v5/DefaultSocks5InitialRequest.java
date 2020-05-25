/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.socksx.v5;

import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The default {@link Socks5InitialRequest}.
 */
public class DefaultSocks5InitialRequest extends AbstractSocks5Message implements Socks5InitialRequest {

    private final List<Socks5AuthMethod> authMethods;

    public DefaultSocks5InitialRequest(Socks5AuthMethod... authMethods) {
        ObjectUtil.checkNotNull(authMethods, "authMethods");

        List<Socks5AuthMethod> list = new ArrayList<Socks5AuthMethod>(authMethods.length);
        for (Socks5AuthMethod m: authMethods) {
            if (m == null) {
                break;
            }
            list.add(m);
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("authMethods is empty");
        }

        this.authMethods = Collections.unmodifiableList(list);
    }

    public DefaultSocks5InitialRequest(Iterable<Socks5AuthMethod> authMethods) {
        ObjectUtil.checkNotNull(authMethods, "authSchemes");

        List<Socks5AuthMethod> list = new ArrayList<Socks5AuthMethod>();
        for (Socks5AuthMethod m: authMethods) {
            if (m == null) {
                break;
            }
            list.add(m);
        }

        if (list.isEmpty()) {
            throw new IllegalArgumentException("authMethods is empty");
        }

        this.authMethods = Collections.unmodifiableList(list);
    }

    @Override
    public List<Socks5AuthMethod> authMethods() {
        return authMethods;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(StringUtil.simpleClassName(this));

        DecoderResult decoderResult = decoderResult();
        if (!decoderResult.isSuccess()) {
            buf.append("(decoderResult: ");
            buf.append(decoderResult);
            buf.append(", authMethods: ");
        } else {
            buf.append("(authMethods: ");
        }
        buf.append(authMethods());
        buf.append(')');

        return buf.toString();
    }
}
