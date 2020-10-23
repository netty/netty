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
package io.netty.microbench.http;

import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.SECONDS)
public class ClientCookieDecoderBenchmark {

    private static final String COOKIE_STRING =
            "__Host-user_session_same_site=fgfMsM59vJTpZg88nxqKkIhgOt0ADF8LX8wjMMbtcb4IJMufWCnCcXORhbo9QMuyiybdtx; " +
                    "path=/; expires=Mon, 28 Nov 2016 13:56:01 GMT; secure; HttpOnly";

    @Benchmark
    public Cookie decodeCookieWithRfc1123ExpiresField() {
        return ClientCookieDecoder.STRICT.decode(COOKIE_STRING);
    }
}
