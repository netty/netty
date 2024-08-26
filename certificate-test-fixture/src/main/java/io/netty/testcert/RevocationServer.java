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
package io.netty.testcert;

import com.sun.net.httpserver.HttpServer;
import io.netty.testcert.x509.CertificateList;
import io.netty.testcert.x509.Signed;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RevocationServer {
    private final HttpServer crlServer;
    private final String crlBaseAddress;
    private final ConcurrentHashMap<String, CrlInfo> paths;

    RevocationServer() throws Exception {
        // Use the built-in HttpServer to avoid any circular dependencies.
        crlServer = HttpServer.create(new InetSocketAddress(0), 0);
        crlBaseAddress = "http://localhost:" + crlServer.getAddress().getPort();
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
                exchange.getResponseHeaders().put("Content-Type", List.of("application/pkix-crl"));
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

    public void start() {
        if (Thread.currentThread().isDaemon()) {
            crlServer.start();
        } else {
            // It's important the CRL server creates a daemon thread,
            // Users of FakeCAs don't expect to close or stop a server.
            // By using daemon threads, we won't stop test runs from terminating.
            Thread th = new Thread(() -> crlServer.start());
            th.setDaemon(true);
            th.start();
            try {
                th.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    public void stop(int delaySeconds) {
        crlServer.stop(delaySeconds);
    }

    public void registerPath(String path, X509Bundle issuer) {
        if (!path.startsWith("/") && !path.endsWith(".crl")) {
            throw new IllegalArgumentException("Path must start with '/' and end with '.crl', but was: " + path);
        }
        URI uri = URI.create(crlBaseAddress + path);
        CrlInfo info = new CrlInfo(issuer, uri);
        CrlInfo existing = paths.putIfAbsent(path, info);
        if (existing != null) {
            throw new IllegalArgumentException("Path already mapped: " + path);
        }
    }

    public void revoke(X509Bundle cert, Instant time) {
        X509Certificate issuer = cert.getCertificatePathWithRoot()[1];
        for (CrlInfo info : paths.values()) {
            if (info.issuer.getCertificate().equals(issuer)) {
                info.revokedCerts.put(cert.getCertificate().getSerialNumber(), time);
                return;
            }
        }
    }

    public URI getCrlUri(X509Bundle issuer) {
        for (CrlInfo info : paths.values()) {
            if (info.issuer == issuer) {
                return info.uri;
            }
        }
        return null;
    }

    private static byte[] generateCrl(CrlInfo info) {
        X509Bundle issuer = info.issuer;
        Map<BigInteger, Instant> certs = info.revokedCerts;
        Instant now = Instant.now();
        CertificateList list = new CertificateList(issuer, now, now.plusMillis(100), certs.entrySet());
        try {
            Signed signed = new Signed(list::getEncoded, issuer);
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
