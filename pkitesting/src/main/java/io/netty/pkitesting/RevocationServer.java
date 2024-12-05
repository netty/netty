/*
 * Copyright 2024 The Netty Project
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
package io.netty.pkitesting;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple HTTP server that serves Certificate Revocation Lists.
 * <p>
 * Issuer certificates can be registered with the server, and revocations of their certificates and be published
 * and added to the revocation lists.
 * <p>
 * The server is only intended for testing usage, and runs entirely in a single thread.
 *
 * @implNote The CRLs will have the same very short life times, to minimize caching effects in tests.
 * This currently means the time in the "this update" and "next update" fields are set to the same value.
 */
public final class RevocationServer {
    private static volatile RevocationServer instance;

    private final HttpServer crlServer;
    private final String crlBaseAddress;
    private final AtomicInteger issuerCounter;
    private final ConcurrentMap<X509Certificate, CrlInfo> issuers;
    private final ConcurrentMap<String, CrlInfo> paths;

    /**
     * Get the shared revocation server instance.
     * This will start the server, if it isn't already running, and bind it to a random port on the loopback address.
     * @return The revocation server instance.
     * @throws Exception If the server failed to start.
     */
    public static RevocationServer getInstance() throws Exception {
        if (instance != null) {
            return instance;
        }
        synchronized (RevocationServer.class) {
            RevocationServer server = instance;
            if (server == null) {
                server = new RevocationServer();
                server.start();
                instance = server;
            }
            return server;
        }
    }

    private RevocationServer() throws Exception {
        // Use the JDK built-in HttpServer to avoid any circular dependencies with Netty itself.
        crlServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        crlBaseAddress = "http://localhost:" + crlServer.getAddress().getPort();
        issuerCounter = new AtomicInteger();
        issuers = new ConcurrentHashMap<>();
        paths = new ConcurrentHashMap<>();
        crlServer.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String path = exchange.getRequestURI().getPath();
                CrlInfo info = paths.get(path);
                if (info == null) {
                    exchange.sendResponseHeaders(404, 0);
                    exchange.close();
                    return;
                }
                byte[] crl = generateCrl(info);
                exchange.getResponseHeaders().put("Content-Type", Collections.singletonList("application/pkix-crl"));
                exchange.sendResponseHeaders(200, crl.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(crl);
                    out.flush();
                }
            } else {
                exchange.sendResponseHeaders(405, 0);
            }
            exchange.close();
        });
    }

    private void start() {
        if (Thread.currentThread().isDaemon()) {
            crlServer.start();
        } else {
            // It's important the CRL server creates a daemon thread,
            // because it's a singleton and won't be stopped except by terminating the JVM.
            // Threads in the ForkJoin common pool are always daemon, and JUnit 5 initializes
            // it anyway, so we can let it call start() for us.
            ForkJoinPool.commonPool().execute(crlServer::start);
        }
    }

    /**
     * Register an issuer with the revocation server.
     * This must be done before CRLs can be served for that issuer, and before any of its certificates can be revoked.
     * @param issuer The issuer to register.
     */
    public void register(X509Bundle issuer) {
        issuers.computeIfAbsent(issuer.getCertificate(), bundle -> {
            String path = "/crl/" + issuerCounter.incrementAndGet() + ".crl";
            URI uri = URI.create(crlBaseAddress + path);
            CrlInfo info = new CrlInfo(issuer, uri);
            paths.put(path, info);
            return info;
        });
    }

    /**
     * Revoke the given certificate with the given revocation time.
     * <p>
     * The issuer of the given certificate must be {@linkplain #register(X509Bundle) registered} before its certifiactes
     * can be revoked.
     * @param cert The certificate to revoke.
     * @param time The time of revocation.
     */
    public void revoke(X509Bundle cert, Instant time) {
        X509Certificate[] certPath = cert.getCertificatePathWithRoot();
        X509Certificate issuer = certPath.length == 1 ? certPath[0] : certPath[1];
        CrlInfo info = issuers.get(issuer);
        if (info != null) {
            info.revokedCerts.put(cert.getCertificate().getSerialNumber(), time);
        } else {
            throw new IllegalArgumentException("Not a registered issuer: " + issuer.getSubjectX500Principal());
        }
    }

    /**
     * Get the URI of the Certificate Revocation List for the given issuer.
     * @param issuer The issuer to get the CRL for.
     * @return The URI to the CRL for the given issuer,
     * or {@code null} if the issuer is not {@linkplain #register(X509Bundle) registered}.
     */
    public URI getCrlUri(X509Bundle issuer) {
        CrlInfo info = issuers.get(issuer.getCertificate());
        if (info != null) {
            return info.uri;
        }
        return null;
    }

    private static byte[] generateCrl(CrlInfo info) {
        X509Bundle issuer = info.issuer;
        Map<BigInteger, Instant> certs = info.revokedCerts;
        Instant now = Instant.now();
        CertificateList list = new CertificateList(issuer, now, now, certs.entrySet());
        try {
            Signed signed = new Signed(list.getEncoded(), issuer);
            return signed.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign CRL", e);
        }
    }

    private static final class CrlInfo {
        private final X509Bundle issuer;
        private final URI uri;
        private final Map<BigInteger, Instant> revokedCerts;

        CrlInfo(X509Bundle issuer, URI uri) {
            this.issuer = issuer;
            this.uri = uri;
            revokedCerts = new ConcurrentHashMap<>();
        }
    }
}
