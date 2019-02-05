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

import static io.netty.util.internal.ObjectUtil.checkClosedInterval;
import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.DecoderResult;
import io.netty.util.internal.StringUtil;

/**
 * The default {@link Socks5PasswordAuthRequest}.
 */
public class DefaultSocks5PasswordAuthRequest extends AbstractSocks5Message implements Socks5PasswordAuthRequest {

    private final String username;
    private final String password;

    public DefaultSocks5PasswordAuthRequest(String username, String password) {
        requireNonNull(username, "username");
        requireNonNull(password, "password");

        checkClosedInterval(username.length(), 0, 255, "username");
        checkClosedInterval(password.length(), 0, 255, "password");

        this.username = username;
        this.password = password;
    }

    @Override
    public String username() {
        return username;
    }

    @Override
    public String password() {
        return password;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(StringUtil.simpleClassName(this));

        DecoderResult decoderResult = decoderResult();
        if (!decoderResult.isSuccess()) {
            buf.append("(decoderResult: ");
            buf.append(decoderResult);
            buf.append(", username: ");
        } else {
            buf.append("(username: ");
        }
        buf.append(username());
        buf.append(", password: ****)");

        return buf.toString();
    }
}
