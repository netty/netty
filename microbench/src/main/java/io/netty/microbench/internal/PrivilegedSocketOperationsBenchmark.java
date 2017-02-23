/*
 * Copyright 2016 The Netty Project
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
package io.netty.microbench.internal;

import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.Policy;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.URIParameter;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class PrivilegedSocketOperationsBenchmark extends AbstractMicrobenchmark {

    @State(Scope.Benchmark)
    public static class SecurityManagerInstalled {

        @Setup
        public void setup() throws IOException, NoSuchAlgorithmException, URISyntaxException {
            final URI policyFile = PrivilegedSocketOperationsBenchmark.class.getResource("/jmh-security.policy")
                    .toURI();
            Policy.setPolicy(Policy.getInstance("JavaPolicy", new URIParameter(policyFile)));
            System.setSecurityManager(new SecurityManager());
        }

        @TearDown
        public void tearDown() throws IOException {
            System.setSecurityManager(null);
        }
    }

    @State(Scope.Benchmark)
    public static class SecurityManagerEmpty {

        @Setup
        public void setup() throws IOException, NoSuchAlgorithmException, URISyntaxException {
            System.setSecurityManager(null);
        }
    }

    @Benchmark
    public ServerSocketChannel testWithSMNoPrivileged(final SecurityManagerInstalled sm) throws IOException {
        final ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(null);
        ssc.configureBlocking(false);
        ssc.accept();
        ssc.close();
        return ssc;
    }

    @Benchmark
    public ServerSocketChannel testWithSM(final SecurityManagerInstalled sm) throws IOException {
        try {
            final ServerSocketChannel ssc = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<ServerSocketChannel>() {
                        @Override
                        public ServerSocketChannel run() throws Exception {
                            final ServerSocketChannel ssc = ServerSocketChannel.open();
                            ssc.socket().bind(null);
                            ssc.configureBlocking(false);
                            ssc.accept();
                            return ssc;
                        }
                    });
            ssc.close();
            return ssc;
        } catch (final PrivilegedActionException e) {
            throw (IOException) e.getCause();
        }
    }

    @Benchmark
    public ServerSocketChannel testWithSMWithNullCheck(final SecurityManagerInstalled sm) throws IOException {
        if (System.getSecurityManager() != null) {
            try {
                final ServerSocketChannel ssc = AccessController.doPrivileged(
                        new PrivilegedExceptionAction<ServerSocketChannel>() {
                            @Override
                            public ServerSocketChannel run() throws Exception {
                                final ServerSocketChannel ssc = ServerSocketChannel.open();
                                ssc.socket().bind(null);
                                ssc.configureBlocking(false);
                                ssc.accept();
                                return ssc;
                            }
                        });
                ssc.close();
                return ssc;
            } catch (final PrivilegedActionException e) {
                throw (IOException) e.getCause();
            }
        } else {
            // this should never happen during benchmarking, but we write the correct code here
            final ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.socket().bind(null);
            ssc.configureBlocking(false);
            ssc.accept();
            ssc.close();
            return ssc;
        }
    }

    @Benchmark
    public ServerSocketChannel testWithoutSMNoPrivileged(final SecurityManagerEmpty sm) throws IOException {
        final ServerSocketChannel ssc = ServerSocketChannel.open();
        ssc.socket().bind(null);
        ssc.configureBlocking(false);
        ssc.accept();
        ssc.close();
        return ssc;
    }

    @Benchmark
    public ServerSocketChannel testWithoutSM(final SecurityManagerEmpty sm) throws IOException {
        try {
            final ServerSocketChannel ssc = AccessController.doPrivileged(
                    new PrivilegedExceptionAction<ServerSocketChannel>() {
                        @Override
                        public ServerSocketChannel run() throws Exception {
                            final ServerSocketChannel ssc = ServerSocketChannel.open();
                            ssc.socket().bind(null);
                            ssc.configureBlocking(false);
                            ssc.accept();
                            return ssc;
                        }
                    });
            ssc.close();
            return ssc;
        } catch (final PrivilegedActionException e) {
            throw (IOException) e.getCause();
        }
    }

    @Benchmark
    public ServerSocketChannel testWithoutSMWithNullCheck(final SecurityManagerEmpty sm) throws IOException {
        if (System.getSecurityManager() != null) {
            // this should never happen during benchmarking, but we write the correct code here
            try {
                final ServerSocketChannel ssc = AccessController.doPrivileged(
                        new PrivilegedExceptionAction<ServerSocketChannel>() {
                            @Override
                            public ServerSocketChannel run() throws Exception {
                                final ServerSocketChannel ssc = ServerSocketChannel.open();
                                ssc.socket().bind(null);
                                ssc.configureBlocking(false);
                                ssc.accept();
                                return ssc;
                            }
                        });
                ssc.close();
                return ssc;
            } catch (final PrivilegedActionException e) {
                throw (IOException) e.getCause();
            }
        } else {
            final ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.socket().bind(null);
            ssc.configureBlocking(false);
            ssc.accept();
            ssc.close();
            return ssc;
        }
    }
}
