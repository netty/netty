package io.netty.testcert.x509;

import io.netty.testcert.der.DerWriter;

public final class Extension implements DerWriter.WritableSequence {
    private final String extnId; // An OBJECT IDENTIFIER
    private final boolean critical;
    private final byte[] extnValue; // Contents of the value OCTET STRING

    public Extension(String extnId, boolean critical, byte[] extnValue) {
        this.extnId = extnId;
        this.critical = critical;
        this.extnValue = extnValue;
    }

    public void encode(DerWriter writer) {
        writer.writeSequence(this);
    }

    @Override
    public void writeSequence(DerWriter writer) {
        writer.writeObjectIdentifier(extnId);
        writer.writeBoolean(critical);
        writer.writeOctetString(extnValue);
    }
}
