package io.netty.testcert.x509;

import io.netty.buffer.ByteBufInputStream;
import io.netty.testcert.der.DerWriter;

import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Objects;
import java.util.function.Supplier;

public class Signed {
    private final byte[] toBeSigned;
    private final String algorithmIdentifier;
    private final PrivateKey privateKey;

    public Signed(Supplier<byte[]> toBeSigned, String algorithmIdentifier, PrivateKey privateKey) {
        this.toBeSigned = toBeSigned.get();
        this.algorithmIdentifier = Objects.requireNonNull(algorithmIdentifier, "algorithmIdentifier");
        this.privateKey = privateKey;
    }

    public InputStream toInputStream() throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance(algorithmIdentifier);
        signature.initSign(privateKey);
        signature.update(toBeSigned);
        byte[] signatureBytes = signature.sign();
        try (DerWriter der = new DerWriter()) {
            der.writeSequence(writer -> {
                writer.writeRawDER(toBeSigned);
                AlgorithmIdentifier.writeAlgorithmId(algorithmIdentifier, writer);
                writer.writeBitString(signatureBytes, 0);
            });
            return new ByteBufInputStream(der.retain().content(), true);
        }
    }
}
