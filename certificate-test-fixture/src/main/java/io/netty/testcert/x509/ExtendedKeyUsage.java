package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

import java.util.Collection;

public final class ExtendedKeyUsage {
    private ExtendedKeyUsage() {
    }

    public static byte[] extendedKeyUsage(Collection<String> oids) {
        try (DerWriter der = new DerWriter()) {
            return der.writeSequence(w -> oids.forEach(w::writeObjectIdentifier)).getBytes();
        }
    }
}
