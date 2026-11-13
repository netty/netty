/*
 * Copyright 2026 The Netty Project
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

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.CharsetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Measures converting the signature algorithms of one handshake, which the server does for every connection.
 * The names are the ones the native libraries reported for a JDK 25 client.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SignatureAlgorithmConverterBenchmark extends AbstractMicrobenchmark {

    private final String[] boringSslNames = {
            "ecdsa_sha256",
            "ecdsa_sha384",
            "ecdsa_sha512",
            "ed25519",
            "rsa_pss_rsae_sha256",
            "rsa_pss_rsae_sha384",
            "rsa_pss_rsae_sha512",
            "rsa_pkcs1_sha256",
            "rsa_pkcs1_sha384",
            "rsa_pkcs1_sha512"
    };

    private final String[] openSslNames = {
            "ecdsa-with-SHA256",
            "ecdsa-with-SHA384",
            "ecdsa-with-SHA512",
            "ED25519",
            "ED448",
            "rsassaPss",
            "rsassaPss",
            "rsassaPss",
            "rsassaPss",
            "rsassaPss",
            "rsassaPss",
            "sha256WithRSAEncryption",
            "sha384WithRSAEncryption",
            "sha512WithRSAEncryption",
            "dsa_with_SHA256",
            "ecdsa-with-SHA224",
            "sha224WithRSAEncryption",
            "dsaEncryption"
    };

    @Param({ "BORINGSSL", "OPENSSL" })
    public String library;

    private String[] names;
    private byte[][] encodedNames;

    @Setup
    public void setup() {
        names = "BORINGSSL".equals(library) ? boringSslNames : openSslNames;
        encodedNames = new byte[names.length][];
        for (int i = 0; i < names.length; i++) {
            encodedNames[i] = names[i].getBytes(CharsetUtil.US_ASCII);
        }
    }

    /**
     * Converts {@link String}s that have their hash code cached already.
     */
    @Benchmark
    public void convert(Blackhole blackhole) {
        for (String name : names) {
            blackhole.consume(SignatureAlgorithmConverter.toJavaName(name));
        }
    }

    /**
     * The same as {@link #convert(Blackhole)}, but with the regular expression that was used for every name before.
     */
    @Benchmark
    public void convertWithPattern(Blackhole blackhole) {
        for (String name : names) {
            blackhole.consume(SignatureAlgorithmConverter.toJavaNameWithPattern(name));
        }
    }

    /**
     * Converts {@link String}s that were just created, which is what the native code hands over for every handshake.
     */
    @Benchmark
    public void createAndConvert(Blackhole blackhole) {
        for (byte[] encodedName : encodedNames) {
            blackhole.consume(SignatureAlgorithmConverter.toJavaName(new String(encodedName, CharsetUtil.US_ASCII)));
        }
    }

    /**
     * The same as {@link #createAndConvert(Blackhole)}, but with the regular expression that was used for every name
     * before.
     */
    @Benchmark
    public void createAndConvertWithPattern(Blackhole blackhole) {
        for (byte[] encodedName : encodedNames) {
            blackhole.consume(SignatureAlgorithmConverter.toJavaNameWithPattern(
                    new String(encodedName, CharsetUtil.US_ASCII)));
        }
    }

    /**
     * The share of the two benchmarks above that is spent on creating the {@link String}s.
     */
    @Benchmark
    public void createOnly(Blackhole blackhole) {
        for (byte[] encodedName : encodedNames) {
            blackhole.consume(new String(encodedName, CharsetUtil.US_ASCII));
        }
    }
}
