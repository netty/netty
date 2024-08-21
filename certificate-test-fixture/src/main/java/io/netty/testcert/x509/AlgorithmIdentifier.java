package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

public final class AlgorithmIdentifier {
    private AlgorithmIdentifier() {
    }

    public static void writeAlgorithmId(String algorithmIdentifier, DerWriter writer) {
        switch (algorithmIdentifier) {
            case "SHA256WITHECDSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.10045.4.3.2"));
                break;
            case "SHA384WITHECDSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.10045.4.3.3"));
                break;
            case "SHA256WITHRSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.113549.1.1.11"));
                break;
            case "SHA384WITHRSA":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.2.840.113549.1.1.12"));
                break;
            case "Ed25519":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.3.101.112"));
                break;
            case "Ed448":
                writer.writeSequence(w -> w.writeObjectIdentifier("1.3.101.113"));
                break;
            default:
                throw new UnsupportedOperationException("Algorithm not supported: " + algorithmIdentifier);
        }
    }
}
