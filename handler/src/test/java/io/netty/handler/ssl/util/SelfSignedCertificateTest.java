package io.netty.handler.ssl.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.security.cert.CertificateException;

import static org.junit.jupiter.api.Assertions.*;

class SelfSignedCertificateTest {

    @Test
    void fqdnAsteriskDoesNotThrowTest() {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                new SelfSignedCertificate("*.netty.io", "EC", 256);
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                new SelfSignedCertificate("*.netty.io", "RSA", 2048);
            }
        });
    }

    @Test
    void fqdnAsteriskFileNameTest() throws CertificateException {
        SelfSignedCertificate ssc = new SelfSignedCertificate("*.netty.io", "EC", 256);
        assertFalse(ssc.certificate().getName().contains("*"));
        assertFalse(ssc.privateKey().getName().contains("*"));
    }
}
