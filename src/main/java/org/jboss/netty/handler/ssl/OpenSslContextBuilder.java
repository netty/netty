/*
 * Copyright 2014 The Netty Project
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
package org.jboss.netty.handler.ssl;

import org.jboss.netty.util.internal.StringUtil;

import javax.net.ssl.SSLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builds a new OpenSSL context object.
 */
public class OpenSslContextBuilder {

    private long aprPool;
    private OpenSslBufferPool bufPool;
    private String certPath;
    private String keyPath;
    private List<String> cipherSpec;
    private int sessionCacheSize;
    private int sessionTimeout;
    private String keyPassword;
    private String caPath;
    private String nextProtos;

    public OpenSslContextBuilder() {
        OpenSsl.ensureAvailability();
    }

    public OpenSslContextBuilder certPath(String certPath) {
        if (certPath == null) {
            throw new NullPointerException("certPath");
        }
        this.certPath = certPath;
        return this;
    }

    public OpenSslContextBuilder keyPath(String keyPath) {
        if (keyPath == null) {
            throw new NullPointerException("keyPath");
        }
        this.keyPath = keyPath;
        return this;
    }

    public OpenSslContextBuilder cipherSpec(Iterable<String> cipherSpec) {
        if (cipherSpec == null) {
            this.cipherSpec = null;
            return this;
        }

        List<String> list = new ArrayList<String>();
        for (String c: cipherSpec) {
            if (c == null) {
                break;
            }
            if (c.contains(":")) {
                for (String cc : StringUtil.split(c, ':')) {
                    if (cc.length() != 0) {
                        list.add(cc);
                    }
                }
            } else {
                list.add(c);
            }
        }

        if (list.isEmpty()) {
            this.cipherSpec = null;
        } else {
            this.cipherSpec = list;
        }
        return this;
    }

    public OpenSslContextBuilder cipherSpec(String... cipherSpec) {
        if (cipherSpec == null) {
            throw new NullPointerException("cipherSpec");
        }
        return cipherSpec(Arrays.asList(cipherSpec));
    }

    public OpenSslContextBuilder keyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
        return this;
    }

    public OpenSslContextBuilder caPath(String caPath) {
        this.caPath = caPath;
        return this;
    }

    public OpenSslContextBuilder nextProtos(String nextProtos) {
        this.nextProtos = nextProtos;
        return this;
    }

    public OpenSslContextBuilder sessionCacheSize(int sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    public OpenSslContextBuilder sessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    public OpenSslContextBuilder aprPool(long aprPool) {
        this.aprPool = aprPool;
        return this;
    }

    public OpenSslContextBuilder bufPool(OpenSslBufferPool bufPool) {
        this.bufPool = bufPool;
        return this;
    }

    /**
     * Creates a new server context.
     *
     * @throws SSLException if the required fields are not assigned
     */
    public OpenSslServerContext newServerContext() throws SSLException {
        // If the cipherSpec was not specified or empty, use the default.
        if (cipherSpec == null) {
            cipherSpec = new ArrayList<String>();
        }

        if (cipherSpec.isEmpty()) {
            Collections.addAll(
                    cipherSpec,
                    "ECDHE-RSA-AES128-GCM-SHA256",
                    "ECDHE-RSA-RC4-SHA",
                    "ECDHE-RSA-AES128-SHA",
                    "ECDHE-RSA-AES256-SHA",
                    "AES128-GCM-SHA256",
                    "RC4-SHA",
                    "RC4-MD5",
                    "AES128-SHA",
                    "AES256-SHA",
                    "DES-CBC3-SHA");
        }

        return new OpenSslServerContext(
                aprPool, bufPool,
                certPath, keyPath, keyPassword, caPath, nextProtos, cipherSpec, sessionCacheSize, sessionTimeout);
    }
}
