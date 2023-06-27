/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.ssl;

/**
 * SSL/TLS protocols
 */
public final class SslProtocols {

    /**
     * SSL v2 Hello
     *
     * @deprecated SSLv2Hello is no longer secure. Consider using {@link #TLS_v1_2} or {@link #TLS_v1_3}
     */
    @Deprecated
    public static final String SSL_v2_HELLO = "SSLv2Hello";

    /**
     * SSL v2
     *
     * @deprecated SSLv2 is no longer secure. Consider using {@link #TLS_v1_2} or {@link #TLS_v1_3}
     */
    @Deprecated
    public static final String SSL_v2 = "SSLv2";

    /**
     * SSLv3
     *
     * @deprecated SSLv3 is no longer secure. Consider using {@link #TLS_v1_2} or {@link #TLS_v1_3}
     */
    @Deprecated
    public static final String SSL_v3 = "SSLv3";

    /**
     * TLS v1
     *
     * @deprecated TLSv1 is no longer secure. Consider using {@link #TLS_v1_2} or {@link #TLS_v1_3}
     */
    @Deprecated
    public static final String TLS_v1 = "TLSv1";

    /**
     * TLS v1.1
     *
     * @deprecated TLSv1.1 is no longer secure. Consider using {@link #TLS_v1_2} or {@link #TLS_v1_3}
     */
    @Deprecated
    public static final String TLS_v1_1 = "TLSv1.1";

    /**
     * TLS v1.2
     */
    public static final String TLS_v1_2 = "TLSv1.2";

    /**
     * TLS v1.3
     */
    public static final String TLS_v1_3 = "TLSv1.3";

    private SslProtocols() {
        // Prevent outside initialization
    }
}
