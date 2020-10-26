/*
 * Copyright 2020 The Netty Project
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

package io.netty.handler.ssl.util;

import io.netty.util.internal.ObjectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A builder for creating {@link FingerprintTrustManagerFactory}.
 */
public final class FingerprintTrustManagerFactoryBuilder {

    /**
     * A hash algorithm for fingerprints.
     */
    private final String algorithm;

    /**
     * A list of fingerprints.
     */
    private final List<String> fingerprints = new ArrayList<String>();

    /**
     * Creates a builder.
     *
     * @param algorithm a hash algorithm
     */
    FingerprintTrustManagerFactoryBuilder(String algorithm) {
        this.algorithm = ObjectUtil.checkNotNull(algorithm, "algorithm");
    }

    /**
     * Adds fingerprints.
     *
     * @param fingerprints a number of fingerprints
     * @return the same builder
     */
    public FingerprintTrustManagerFactoryBuilder fingerprints(CharSequence... fingerprints) {
        ObjectUtil.checkNotNull(fingerprints, "fingerprints");
        return fingerprints(Arrays.asList(fingerprints));
    }

    /**
     * Adds fingerprints.
     *
     * @param fingerprints a number of fingerprints
     * @return the same builder
     */
    public FingerprintTrustManagerFactoryBuilder fingerprints(Iterable<? extends CharSequence> fingerprints) {
        ObjectUtil.checkNotNull(fingerprints, "fingerprints");
        for (CharSequence fingerprint : fingerprints) {
            if (fingerprint == null) {
                throw new IllegalArgumentException("One of the fingerprints is null");
            }
            this.fingerprints.add(fingerprint.toString());
        }
        return this;
    }

    /**
     * Creates a {@link FingerprintTrustManagerFactory}.
     *
     * @return a new {@link FingerprintTrustManagerFactory}
     */
    public FingerprintTrustManagerFactory build() {
        if (fingerprints.isEmpty()) {
            throw new IllegalStateException("No fingerprints provided");
        }
        return new FingerprintTrustManagerFactory(this.algorithm,
                                                  FingerprintTrustManagerFactory.toFingerprintArray(this.fingerprints));
    }
}
