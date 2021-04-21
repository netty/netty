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

import javax.net.ssl.SSLEngine;

/**
 * Contains methods that can be used to detect if BouncyCastle is usable.
 */
final class BouncyCastle {

    private static final boolean BOUNCY_CASTLE_ON_CLASSPATH;

    static {
        boolean bcOnClasspath = false;
        try {
            Class.forName("org.bouncycastle.jsse.provider.BouncyCastleJsseProvider");
            bcOnClasspath = true;
        } catch (Throwable ignore) {
            // ignore
        }
        BOUNCY_CASTLE_ON_CLASSPATH = bcOnClasspath;
    }

    /**
     * Indicates whether or not BouncyCastle is available on the current system.
     */
    static boolean isAvailable() {
        return BOUNCY_CASTLE_ON_CLASSPATH;
    }

    /**
     * Indicates whether or not BouncyCastle is the underlying SSLEngine.
     */
    static boolean isInUse(SSLEngine engine) {
        return engine.getClass().getPackage().getName().startsWith("org.bouncycastle.jsse.provider");
    }

    private BouncyCastle() {
    }
}
