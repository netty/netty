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
package io.netty.microbench.handler.ssl;

import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.internal.EmptyArrays;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Compares collecting the converted signature algorithm names of a peer in a {@link LinkedHashSet} with
 * {@code SignatureAlgorithmConverter.toJavaNames(String[])}, for the algorithms typical clients offer.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SignatureAlgorithmNamesBenchmark extends AbstractMicrobenchmark {

    // SignatureAlgorithmConverter is package-private. Constant method handles are called like the methods
    // themselves once the JIT compiled the benchmark.
    private static final MethodHandle TO_JAVA_NAME = converterMethod("toJavaName", String.class);
    private static final MethodHandle TO_JAVA_NAMES = converterMethod("toJavaNames", String[].class);

    // What a browser offers in a TLS 1.3 ClientHello, as BoringSSL names them.
    private static final String[] TLS13_CLIENT = {
            "ecdsa_sha256",
            "rsa_pss_rsae_sha256",
            "rsa_pkcs1_sha256",
            "ecdsa_sha384",
            "rsa_pss_rsae_sha384",
            "rsa_pkcs1_sha384",
            "rsa_pss_rsae_sha512",
            "rsa_pkcs1_sha512"
    };

    // What an OpenSSL based TLS 1.2 client offers, as BoringSSL names them. It is longer as it still has the
    // SHA-1 algorithms.
    private static final String[] TLS12_CLIENT = {
            "ecdsa_sha256",
            "ecdsa_sha384",
            "ecdsa_sha512",
            "ed25519",
            "rsa_pss_rsae_sha256",
            "rsa_pss_rsae_sha384",
            "rsa_pss_rsae_sha512",
            "rsa_pkcs1_sha256",
            "rsa_pkcs1_sha384",
            "rsa_pkcs1_sha512",
            "ecdsa_sha1",
            "rsa_pkcs1_sha1"
    };

    @Param({ "TLSv1.3", "TLSv1.2" })
    public String client;

    private String[] peerSignatureAlgorithms;

    private static MethodHandle converterMethod(String name, Class<?> parameterType) {
        try {
            Method method = Class.forName("io.netty.handler.ssl.SignatureAlgorithmConverter")
                    .getDeclaredMethod(name, parameterType);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Setup
    public void setup() {
        peerSignatureAlgorithms = "TLSv1.3".equals(client) ? TLS13_CLIENT : TLS12_CLIENT;
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }

    /**
     * How the names were collected before {@code SignatureAlgorithmConverter.toJavaNames(String[])} was added.
     */
    @Benchmark
    public String[] linkedHashSet() throws Throwable {
        String[] algs = peerSignatureAlgorithms;
        Set<String> algorithmList = new LinkedHashSet<String>(algs.length);
        for (String alg: algs) {
            String converted = (String) TO_JAVA_NAME.invokeExact(alg);

            if (converted != null) {
                algorithmList.add(converted);
            }
        }
        return algorithmList.toArray(EmptyArrays.EMPTY_STRINGS);
    }

    @Benchmark
    public String[] toJavaNames() throws Throwable {
        return (String[]) TO_JAVA_NAMES.invokeExact(peerSignatureAlgorithms);
    }
}
