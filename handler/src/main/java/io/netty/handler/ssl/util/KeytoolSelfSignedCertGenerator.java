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

package io.netty.handler.ssl.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Self-signed certificate generator based on the keytool CLI.
 */
final class KeytoolSelfSignedCertGenerator {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.ROOT);
    private static final String ALIAS = "alias";
    private static final String PASSWORD = "insecurepassword";
    private static final Path KEYTOOL;
    private static final String KEY_STORE_TYPE;

    static {
        String home = System.getProperty("java.home");
        if (home == null) {
            KEYTOOL = null;
        } else {
            Path likely = Paths.get(home).resolve("bin").resolve("keytool");
            if (Files.exists(likely)) {
                KEYTOOL = likely;
            } else {
                KEYTOOL = null;
            }
        }
        // Java < 11 does not support encryption for PKCS#12: JDK-8220734
        // For 11+, we prefer PKCS#12 for FIPS compliance
        KEY_STORE_TYPE = PlatformDependent.javaVersion() >= 11 ? "PKCS12" : "JKS";
    }

    private KeytoolSelfSignedCertGenerator() {
    }

    static boolean isAvailable() {
        return KEYTOOL != null;
    }

    static void generate(SelfSignedCertificate.Builder builder) throws IOException, GeneralSecurityException {
        // Change all asterisk to 'x' for file name safety.
        String dirFqdn = builder.fqdn.replaceAll("[^\\w.-]", "x");

        Path directory = Files.createTempDirectory("keytool_" + dirFqdn);
        Path keyStore = directory.resolve("keystore.jks");
        try {
            Process process = new ProcessBuilder()
                    .command(
                            KEYTOOL.toAbsolutePath().toString(),
                            "-genkeypair",
                            "-keyalg", builder.algorithm,
                            "-keysize", String.valueOf(builder.bits),
                            "-startdate", DATE_FORMAT.format(
                                    builder.notBefore.toInstant().atZone(ZoneId.systemDefault())),
                            "-validity", String.valueOf(builder.notBefore.toInstant().until(
                                    builder.notAfter.toInstant(), ChronoUnit.DAYS)),
                            "-keystore", keyStore.toString(),
                            "-alias", ALIAS,
                            "-keypass", PASSWORD,
                            "-storepass", PASSWORD,
                            "-dname", "CN=" + builder.fqdn,
                            "-storetype", KEY_STORE_TYPE
                    )
                    .redirectErrorStream(true)
                    .start();
            try {
                if (!process.waitFor(60, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("keytool timeout");
                }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }

            if (process.exitValue() != 0) {
                ByteBuf buffer = Unpooled.buffer();
                try {
                    try (InputStream stream = process.getInputStream()) {
                        while (true) {
                            if (buffer.writeBytes(stream, 4096) == -1) {
                                break;
                            }
                        }
                    }
                    String log = buffer.toString(StandardCharsets.UTF_8);
                    throw new IOException("Keytool exited with status " + process.exitValue() + ": " + log);
                } finally {
                    buffer.release();
                }
            }

            KeyStore ks = KeyStore.getInstance(KEY_STORE_TYPE);
            try (InputStream is = Files.newInputStream(keyStore)) {
                ks.load(is, PASSWORD.toCharArray());
            }
            KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) ks.getEntry(
                    ALIAS, new KeyStore.PasswordProtection(PASSWORD.toCharArray()));
            builder.paths = SelfSignedCertificate.newSelfSignedCertificate(
                    builder.fqdn, entry.getPrivateKey(), (X509Certificate) entry.getCertificate());
            builder.privateKey = entry.getPrivateKey();
        } finally {
            Files.deleteIfExists(keyStore);
            Files.delete(directory);
        }
    }
}
