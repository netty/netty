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

import io.netty.util.internal.EmptyArrays;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.bouncycastle.jcajce.spec.EdDSAParameterSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import javax.security.auth.x500.X500Principal;

import static java.util.Objects.requireNonNull;

/**
 * The {@link CertificateBuilder} produce {@link X509Bundle} instances, where the keys use the specified
 * algorithm, and the certificate have the specified data.
 * <p>
 * The builder can make self-signed bundles, or can make bundles that are signed by other bundles to build a verified
 * certificate path.
 * <p>
 * The builder can also make certificate that are invalid in various ways, for testing purpose.
 * The most typical application is to make a certificate that has already expired or is not yet valid.
 * <p>
 * See RFC 5280 for the details of X.509 certificate contents.
 * <p>
 * Here is an example where a leaf certificate is created and signed by a self-signed issuer certificate:
 * <pre>{@code
 * Instant now = Instant.now();
 * CertificateBuilder template = new CertificateBuilder()
 *             .notBefore(now.minus(1, DAYS))
 *             .notAfter(now.plus(1, DAYS));
 * X509Bundle issuer = template.copy()
 *             .subject("CN=testca, OU=dept, O=your-org")
 *             .setKeyUsage(true, KeyUsage.digitalSignature, KeyUsage.keyCertSign)
 *             .setIsCertificateAuthority(true)
 *             .buildSelfSigned();
 * X509Bundle leaf = template.copy()
 *             .subject("CN=leaf, OU=dept, O=your-org")
 *             .setKeyUsage(true, KeyUsage.digitalSignature)
 *             .addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_SERVER_AUTH)
 *             .addSanDnsName("san-1.leaf.dept.your-org.com")
 *             .buildIssuedBy(issuer);
 * }</pre>
 */
public final class CertificateBuilder {

    static final String OID_X509_NAME_CONSTRAINTS = "2.5.29.30";
    static final String OID_PKIX_KP = "1.3.6.1.5.5.7.3";
    static final String OID_PKIX_KP_SERVER_AUTH = OID_PKIX_KP + ".1";
    static final String OID_PKIX_KP_CLIENT_AUTH = OID_PKIX_KP + ".2";
    static final String OID_PKIX_KP_CODE_SIGNING = OID_PKIX_KP + ".3";
    static final String OID_PKIX_KP_EMAIL_PROTECTION = OID_PKIX_KP + ".4";
    static final String OID_PKIX_KP_TIME_STAMPING = OID_PKIX_KP + ".8";
    static final String OID_PKIX_KP_OCSP_SIGNING = OID_PKIX_KP + ".9";
    static final String OID_KERBEROS_KEY_PURPOSE_CLIENT_AUTH = "1.3.6.1.5.2.3.4";
    static final String OID_MICROSOFT_SMARTCARD_LOGIN = "1.3.6.1.4.1.311.20.2.2";
    private static final GeneralName[] EMPTY_GENERAL_NAMES = new GeneralName[0];
    private static final DistributionPoint[] EMPTY_DIST_POINTS = new DistributionPoint[0];
    private static final AlgorithmParameterSpec UNSUPPORTED_SPEC = new AlgorithmParameterSpec() {
    };
    private static final String UNSUPPORTED_SIGN = "UNSUPPORTED_SIGN";

    SecureRandom random;
    Algorithm algorithm = Algorithm.ecp256;
    Instant notBefore = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant notAfter = Instant.now().plus(1, ChronoUnit.DAYS);
    List<BuilderCallback> modifierCallbacks = new ArrayList<>();
    List<GeneralName> subjectAlternativeNames = new ArrayList<>();
    List<DistributionPoint> crlDistributionPoints = new ArrayList<>();
    BigInteger serial;
    X500Principal subject;
    boolean isCertificateAuthority;
    OptionalInt pathLengthConstraint = OptionalInt.empty();
    PublicKey publicKey;
    Set<String> extendedKeyUsage = new TreeSet<>();
    Extension keyUsage;

    /**
     * Create a new certificate builder with a default configuration.
     * Unless specified otherwise, the builder will produce bundles that use the
     * {@linkplain Algorithm#ecp256 NIST EC-P 256} key algorithm,
     * and the certificates will be valid as of yesterday and expire tomorrow.
     */
    public CertificateBuilder() {
    }

    /**
     * Produce a copy of the current state in this certificate builder.
     * @return A copy of this certificate builder.
     */
    public CertificateBuilder copy() {
        CertificateBuilder copy = new CertificateBuilder();
        copy.random = random;
        copy.algorithm = algorithm;
        copy.notBefore = notBefore;
        copy.notAfter = notAfter;
        copy.modifierCallbacks = new ArrayList<>(modifierCallbacks);
        copy.subjectAlternativeNames = new ArrayList<>(subjectAlternativeNames);
        copy.crlDistributionPoints = new ArrayList<>(crlDistributionPoints);
        copy.serial = serial;
        copy.subject = subject;
        copy.isCertificateAuthority = isCertificateAuthority;
        copy.pathLengthConstraint = pathLengthConstraint;
        copy.publicKey = publicKey;
        copy.keyUsage = keyUsage;
        copy.extendedKeyUsage = new TreeSet<>(extendedKeyUsage);
        return copy;
    }

    /**
     * Set the {@link SecureRandom} instance to use when generating keys.
     * @param secureRandom The secure random instance to use.
     * @return This certificate builder.
     */
    public CertificateBuilder secureRandom(SecureRandom secureRandom) {
        random = requireNonNull(secureRandom);
        return this;
    }

    /**
     * Set the not-before field of the certificate. The certificate will not be valid before this time.
     * @param instant The not-before time.
     * @return This certificate builder.
     */
    public CertificateBuilder notBefore(Instant instant) {
        notBefore = requireNonNull(instant);
        return this;
    }

    /**
     * Set the not-after field of the certificate. The certificate will not be valid after this time.
     * @param instant The not-after time.
     * @return This certificate builder.
     */
    public CertificateBuilder notAfter(Instant instant) {
        notAfter = requireNonNull(instant);
        return this;
    }

    /**
     * Set the specific serial number to use in the certificate.
     * One will be generated randomly, if none is specified.
     * @param serial The serial number to use, or {@code null}.
     * @return This certificate builder.
     */
    public CertificateBuilder serial(BigInteger serial) {
        this.serial = serial;
        return this;
    }

    /**
     * Set the fully-qualified domain name (an X.500 name) as the subject of the certificate.
     * @param fqdn The subject name to use.
     * @return This certificate builder.
     */
    public CertificateBuilder subject(String fqdn) {
        subject = new X500Principal(requireNonNull(fqdn));
        return this;
    }

    /**
     * Set the subject name of the certificate to the given {@link X500Principal}.
     * @param name The subject name to use.
     * @return This certificate builder.
     */
    public CertificateBuilder subject(X500Principal name) {
        subject = requireNonNull(name);
        return this;
    }

    /**
     * Add an Other Name to the Subject Alternative Names, of the given OID type, and with the given encoded value.
     * The type and value will be wrapped in a SEQUENCE.
     * @param typeOid The OID type of the Other Name value.
     * @param encodedValue The encoded Other Name value.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanOtherName(String typeOid, byte[] encodedValue) {
        subjectAlternativeNames.add(GeneralNameUtils.otherName(typeOid, encodedValue));
        return this;
    }

    /**
     * Add an RFC 822 name to the Subject Alternative Names.
     * The RFC 822 standard is the obsolete specification for email, so these SANs are email addresses.
     * @param name The email address to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanRfc822Name(String name) {
        subjectAlternativeNames.add(GeneralNameUtils.rfc822Name(name));
        return this;
    }

    /**
     * Add a DNS name to the Subject Alternate Names.
     * @param dns The DNS name to add.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanDnsName(String dns) {
        if (dns.trim().isEmpty()) {
            throw new IllegalArgumentException("Blank DNS SANs are forbidden by RFC 5280, Section 4.2.1.6.");
        }
        subjectAlternativeNames.add(GeneralNameUtils.dnsName(dns));
        return this;
    }

    // x400Address support intentionally omitted; not in common use.

    /**
     * Add a Directory Name to the Subject Alternative Names.
     * These are LDAP directory paths.
     * @param dirName The directory name to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanDirectoryName(String dirName) {
        subjectAlternativeNames.add(GeneralNameUtils.directoryName(dirName));
        return this;
    }

    // ediPartyName support intentionally omitted; not in common use.

    /**
     * Add a URI name to the Subject Alternative Names.
     * @param uri The URI to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanUriName(String uri) throws URISyntaxException {
        subjectAlternativeNames.add(GeneralNameUtils.uriName(uri));
        return this;
    }

    /**
     * Add a URI name to the Subject Alternative Names.
     * @param uri The URI to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanUriName(URI uri) {
        subjectAlternativeNames.add(GeneralNameUtils.uriName(uri));
        return this;
    }

    /**
     * Add an IP address to the Subject Alternative Names.
     * IPv4 and IPv6 addresses are both supported and converted to their correct encoding.
     * @param ipAddress The IP address to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanIpAddress(String ipAddress) {
        subjectAlternativeNames.add(GeneralNameUtils.ipAddress(ipAddress));
        return this;
    }

    /**
     * Add an IP address to the Subject Alternative Names.
     * IPv4 and IPv6 addresses are both supported and converted to their correct encoding.
     * @param ipAddress The IP address to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanIpAddress(InetAddress ipAddress) {
        subjectAlternativeNames.add(GeneralNameUtils.ipAddress(ipAddress.getHostAddress()));
        return this;
    }

    /**
     * Add a registeredID to the Subject Alternative Names.
     * A registeredID is an OBJECT IDENTIFIER, or OID, in ASN.1 speak.
     * @param oid The OID to add to the SANs.
     * @return This certificate builder.
     */
    public CertificateBuilder addSanRegisteredId(String oid) {
        subjectAlternativeNames.add(GeneralNameUtils.registeredId(oid));
        return this;
    }

    /**
     * Add a URI distribution point for a certificate revocation list.
     * <p>
     * If you are testing certificate revocation using the {@link RevocationServer},
     * you would obtain this URI from {@link RevocationServer#getCrlUri(X509Bundle)} with your intended issuer
     * certificate bundle.
     *
     * @param uri The URI for the CRL file.
     * @return This certificate builder.
     */
    public CertificateBuilder addCrlDistributionPoint(URI uri) {
        GeneralName fullName = GeneralNameUtils.uriName(uri);
        crlDistributionPoints.add(new DistributionPoint(
                new DistributionPointName(new GeneralNames(fullName)),
                null,
                null));
        return this;
    }

    /**
     * Add a URI distribution point for a certificate revocation list.
     * <p>
     * If you are testing certificate revocation using the {@link RevocationServer},
     * you would obtain this URI from {@link RevocationServer#getCrlUri(X509Bundle)} with your intended issuer
     * certificate bundle.
     *
     * @param uri The URI for the CRL file.
     * @param issuer The issuer that signs the CRL file.
     * This MUST be {@code null} if the CRL issuer is also the issuer of the certificate being built.
     * Otherwise, if this certificate and the CRL will be signed by different issuers, then this MUST be the subject
     * name of the CRL signing certificate.
     * @return This certificate builder.
     */
    public CertificateBuilder addCrlDistributionPoint(URI uri, X500Principal issuer) {
        GeneralName fullName = GeneralNameUtils.uriName(uri);
        GeneralName issuerName = GeneralNameUtils.directoryName(issuer);
        crlDistributionPoints.add(new DistributionPoint(
                new DistributionPointName(new GeneralNames(fullName)),
                null,
                new GeneralNames(issuerName)));
        return this;
    }

    /**
     * Set the certificate authority field.
     * If this is set to {@code true}, then this builder can build self-signed certificates, and those certifiactes
     * can be used to sign other certificates.
     * @param isCA {@code true} if this builder should make CA certificates.
     * @return This certificate builder.
     */
    public CertificateBuilder setIsCertificateAuthority(boolean isCA) {
        isCertificateAuthority = isCA;
        return this;
    }

    /**
     * Certificate Authority certificates may impose a limit to the length of the verified certificate path they permit.
     * @param pathLengthConstraint The maximum verified path length, if any.
     * @return This certificate builder.
     */
    public CertificateBuilder setPathLengthConstraint(OptionalInt pathLengthConstraint) {
        this.pathLengthConstraint = requireNonNull(pathLengthConstraint, "pathLengthConstraint");
        return this;
    }

    /**
     * Set the key algorithm to use. This also determines how certificates are signed.
     * @param algorithm The algorithm to use when generating the private key.
     * @return This certificate builder.
     */
    public CertificateBuilder algorithm(Algorithm algorithm) {
        requireNonNull(algorithm, "algorithm");
        if (algorithm.parameterSpec == UNSUPPORTED_SPEC) {
            throw new UnsupportedOperationException("This algorithm is not supported: " + algorithm);
        }
        this.algorithm = algorithm;
        return this;
    }

    /**
     * Make this certificate builder use the {@linkplain Algorithm#ecp256 NIST EC-P 256} elliptic curve key algorithm.
     * This algorithm provides a good balance between security, compatibility, performance, and key & signature sizes.
     * @return This certificate builder.
     * @see Algorithm#ecp256
     */
    public CertificateBuilder ecp256() {
        return algorithm(Algorithm.ecp256);
    }

    /**
     * Make this certificate builder use the {@linkplain Algorithm#rsa2048 2048-bit RSA} encryption and signing
     * algorithm. This algorithm provides maximum compatibility, but keys are large and slow to generate.
     * @return This certificate builder.
     * @see Algorithm#rsa2048
     */
    public CertificateBuilder rsa2048() {
        return algorithm(Algorithm.rsa2048);
    }

    /**
     * Instruct the certificate builder to not generate its own key pair, but to instead create a certificate that
     * uses the given public key.
     * <p>
     * This method is useful if you want to use an existing key-pair, e.g. to emulate a certificate authority
     * responding to a Certificate Signing Request (CSR).
     * <p>
     * If the given public key is {@code null} (the default) then a new key-pair will be generated instead.
     *
     * @param key The public key to wrap in a certificate.
     * @return This certificate builder.
     */
    public CertificateBuilder publicKey(PublicKey key) {
        publicKey = key;
        return this;
    }

    private CertificateBuilder addExtension(String identifierOid, boolean critical, byte[] value) {
        requireNonNull(identifierOid, "identifierOid");
        requireNonNull(value, "value");
        modifierCallbacks.add(builder -> {
            builder.addExtension(new Extension(
                    new ASN1ObjectIdentifier(identifierOid),
                    critical,
                    value));
        });
        return this;
    }

    /**
     * Add a custom extension to the certificate, with the given OID, criticality flag, and DER-encoded contents.
     * @param identifierOID The OID identifying the extension.
     * @param critical {@code true} if the extension is critical, otherwise {@code false}.
     * Certificate systems MUST reject certificates with critical extensions they don't recognize.
     * @param contents The DER-encoded extension contents.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtensionOctetString(String identifierOID, boolean critical, byte[] contents) {
        requireNonNull(identifierOID, "identifierOID");
        requireNonNull(contents, "contents");
        modifierCallbacks.add(builder -> {
            builder.addExtension(new Extension(
                    new ASN1ObjectIdentifier(identifierOID),
                    critical,
                    contents));
        });
        return this;
    }

    /**
     * Add a custom DER-encoded ASN.1 UTF-8 string extension to the certificate, with the given OID, criticality,
     * and string value.
     * The string will be converted to its proper binary encoding by this method.
     * @param identifierOID The OID identifying the extension.
     * @param critical {@code true} if the extension is critical, otherwise {@code false}.
     * Certificate systems MUST reject certificates with critical extensions they don't recognize.
     * @param value The string value.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtensionUtf8String(String identifierOID, boolean critical, String value) {
        try {
            return addExtension(identifierOID, critical, new DERUTF8String(value).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Add a custom DER-encoded ASN.1 IA5String (an ASCII string) extension to the certificate, with the given OID,
     * criticality, and string value.
     * The string will be converted to its proper binary encoding by this method.
     * @param identifierOID The OID identifying the extension.
     * @param critical {@code true} if the extension is critical, otherwise {@code false}.
     * Certificate systems MUST reject certificates with critical extensions they don't recognize.
     * @param value The string value.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtensionAsciiString(String identifierOID, boolean critical, String value) {
        try {
            return addExtension(identifierOID, critical, new DERIA5String(value).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The key usage specify the intended usages for which the certificate has been issued.
     * Some are overlapping, some are deprecated, and some are implied by usage.
     * <p>
     * For Certificate Authority usage, the important ones are {@link KeyUsage#keyCertSign}
     * and {@link KeyUsage#cRLSign}.
     * <p>
     * Any certificate that has {@link KeyUsage#keyCertSign} must also have {@link #setIsCertificateAuthority(boolean)}
     * set to {@code true}.
     *
     * @param critical {@code true} if certificate recipients are required to understand all the set bits,
     * otherwise {@code false}.
     * @param keyUsages The key usages to set.
     * @return This certificate builder.
     */
    public CertificateBuilder setKeyUsage(boolean critical, KeyUsage... keyUsages) {
        int maxBit = 0;
        for (KeyUsage usage : keyUsages) {
            maxBit = Math.max(usage.bitId, maxBit);
        }
        boolean[] bits = new boolean[maxBit + 1];
        for (KeyUsage usage : keyUsages) {
            bits[usage.bitId] = true;
        }
        int padding = 8 - bits.length % 8;
        int lenBytes = bits.length / 8 + 1;
        if (padding == 8) {
            padding = 0;
            lenBytes--;
        }
        byte[] bytes = new byte[lenBytes];
        for (int i = 0; i < bits.length; i++) {
            if (bits[i]) {
                int byteIndex = i / 8;
                int bitIndex = i % 8;
                bytes[byteIndex] |= (byte) (0x80 >>> bitIndex);
            }
        }

        try {
            keyUsage = new Extension(
                    Extension.keyUsage,
                    critical,
                    new DERBitString(bytes, padding).getEncoded("DER"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /**
     * Add the given OID to the list of extended key usages.
     * @param oid The OID to add.
     * @return This certificate builder.
     * @see ExtendedKeyUsage
     * @see #addExtendedKeyUsage(ExtendedKeyUsage)
     */
    public CertificateBuilder addExtendedKeyUsage(String oid) {
        extendedKeyUsage.add(oid);
        return this;
    }

    /**
     * Add the given {@link ExtendedKeyUsage} to the list of extended key usages.
     * @param keyUsage The extended key usage to add.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsage(ExtendedKeyUsage keyUsage) {
        extendedKeyUsage.add(keyUsage.getOid());
        return this;
    }

    /**
     * Add server-authentication to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageServerAuth() {
        return addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_SERVER_AUTH);
    }

    /**
     * Add client-authentication to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageClientAuth() {
        return addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_CLIENT_AUTH);
    }

    /**
     * Add code signing to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageCodeSigning() {
        return addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_CODE_SIGNING);
    }

    /**
     * Add email protection to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageEmailProtection() {
        return addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_EMAIL_PROTECTION);
    }

    /**
     * Add time-stamping to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageTimeStamping() {
        return addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_TIME_STAMPING);
    }

    /**
     * Add OCSP signing to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageOcspSigning() {
        return addExtendedKeyUsage(ExtendedKeyUsage.PKIX_KP_OCSP_SIGNING);
    }

    /**
     * Add Kerberos client authentication to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageKerberosClientAuth() {
        return addExtendedKeyUsage(ExtendedKeyUsage.KERBEROS_KEY_PURPOSE_CLIENT_AUTH);
    }

    /**
     * Add Microsoft smartcard login to the list of extended key usages.
     * @return This certificate builder.
     */
    public CertificateBuilder addExtendedKeyUsageMicrosoftSmartcardLogin() {
        return addExtendedKeyUsage(ExtendedKeyUsage.MICROSOFT_SMARTCARD_LOGIN);
    }

    /**
     * Build a {@link X509Bundle} with a self-signed certificate.
     * @return The newly created bundle.
     * @throws Exception If something went wrong in the process.
     */
    public X509Bundle buildSelfSigned() throws Exception {
        if (publicKey != null) {
            throw new IllegalStateException("Cannot create a self-signed certificate with a public key from a CSR.");
        }
        if (!algorithm.supportSigning()) {
            throw new IllegalStateException("Cannot create a self-signed certificate with a " +
                    "key algorithm that does not support signing: " + algorithm);
        }
        KeyPair keyPair = generateKeyPair();

        V3TBSCertificateGenerator generator = createCertBuilder(subject, subject, keyPair, algorithm.signatureType);

        addExtensions(generator);

        Signed signed = new Signed(tbsCertToBytes(generator), algorithm.signatureType, keyPair.getPrivate());
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) factory.generateCertificate(signed.toInputStream());
        return X509Bundle.fromRootCertificateAuthority(cert, keyPair);
    }

    /**
     * Build a {@link X509Bundle} with a certificate signed by the given issuer bundle.
     * The signing algorithm used will be derived from the issuers public key.
     * @return The newly created bundle.
     * @throws Exception If something went wrong in the process.
     */
    public X509Bundle buildIssuedBy(X509Bundle issuerBundle) throws Exception {
        String issuerSignAlgorithm = preferredSignatureAlgorithm(issuerBundle.getCertificate().getPublicKey());
        return buildIssuedBy(issuerBundle, issuerSignAlgorithm);
    }

    /**
     * Build a {@link X509Bundle} with a certificate signed by the given issuer bundle, using the specified
     * signing algorithm.
     * @return The newly created bundle.
     * @throws Exception If something went wrong in the process.
     */
    public X509Bundle buildIssuedBy(X509Bundle issuerBundle, String signAlg) throws Exception {
        final KeyPair keyPair;
        if (publicKey == null) {
            keyPair = generateKeyPair();
        } else {
            keyPair = new KeyPair(publicKey, null);
        }

        X500Principal issuerPrincipal = issuerBundle.getCertificate().getSubjectX500Principal();
        V3TBSCertificateGenerator generator = createCertBuilder(issuerPrincipal, subject, keyPair, signAlg);

        addExtensions(generator);

        PrivateKey issuerPrivateKey = issuerBundle.getKeyPair().getPrivate();
        if (issuerPrivateKey == null) {
            throw new IllegalArgumentException(
                    "Cannot sign certificate with issuer bundle that does not have a private key.");
        }
        Signed signed = new Signed(tbsCertToBytes(generator), signAlg, issuerPrivateKey);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) factory.generateCertificate(signed.toInputStream());
        X509Certificate[] issuerPath = issuerBundle.getCertificatePath();
        X509Certificate[] path = new X509Certificate[issuerPath.length + 1];
        path[0] = cert;
        System.arraycopy(issuerPath, 0, path, 1, issuerPath.length);
        return X509Bundle.fromCertificatePath(path, issuerBundle.getRootCertificate(), keyPair);
    }

    private static String preferredSignatureAlgorithm(PublicKey key) {
        if (key instanceof RSAPublicKey) {
            RSAPublicKey rsa = (RSAPublicKey) key;
            if (rsa.getModulus().bitLength() < 4096) {
                return "SHA256withRSA";
            }
            return "SHA384withRSA";
        }
        if (key instanceof ECPublicKey) {
            ECPublicKey ec = (ECPublicKey) key;
            int size = ec.getW().getAffineX().bitLength();
            // Note: the coords are not guaranteed to use up all available bits, hence less-than-or-equal checks.
            if (size <= 256) {
                return "SHA256withECDSA";
            }
            if (size <= 384) {
                return "SHA384withECDSA";
            }
            return "SHA512withECDSA";
        }
        if (key instanceof DSAPublicKey) {
            throw new IllegalArgumentException("DSA keys are not supported because they are obsolete");
        }
        String keyAlgorithm = key.getAlgorithm();
        if ("Ed25519".equals(keyAlgorithm) || "1.3.101.112".equals(keyAlgorithm)) {
            return "Ed25519";
        }
        if ("Ed448".equals(keyAlgorithm) || "1.3.101.113".equals(keyAlgorithm)) {
            return "Ed448";
        }
        if ("EdDSA".equals(keyAlgorithm)) {
            byte[] encoded = key.getEncoded();
            if (encoded.length <= 44) {
                return "Ed25519";
            }
            if (encoded.length <= 69) {
                return "Ed448";
            }
        }
        if ("ML-DSA".equals(keyAlgorithm)) {
            try {
                Method getParams = key.getClass().getMethod("getParams");
                Object params = getParams.invoke(key);
                Method getName = params.getClass().getMethod("getName");
                return (String) getName.invoke(params);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot get algorithm name for ML-DSA key", e);
            }
        }
        if ("ML-KEM".equals(keyAlgorithm)) {
            throw new IllegalArgumentException("ML-KEM keys cannot be used for signing");
        }
        throw new IllegalArgumentException("Don't know what signature algorithm is best for " + key);
    }

    private KeyPair generateKeyPair() throws GeneralSecurityException {
        return algorithm.generateKeyPair(getSecureRandom());
    }

    private V3TBSCertificateGenerator createCertBuilder(
            X500Principal issuer, X500Principal subject, KeyPair keyPair, String signAlg) {
        BigInteger serial = this.serial != null ? this.serial : new BigInteger(159, getSecureRandom());
        PublicKey pubKey = keyPair.getPublic();

        V3TBSCertificateGenerator generator = new V3TBSCertificateGenerator();
        generator.setIssuer(X500Name.getInstance(issuer.getEncoded()));
        if (subject != null) {
            generator.setSubject(X500Name.getInstance(subject.getEncoded()));
        }
        generator.setSerialNumber(new ASN1Integer(serial));
        generator.setSignature(new AlgorithmIdentifier(new ASN1ObjectIdentifier(
                Algorithms.oidForAlgorithmName(signAlg))));
        generator.setStartDate(new Time(Date.from(notBefore)));
        generator.setEndDate(new Time(Date.from(notAfter)));
        generator.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(pubKey.getEncoded()));
        return generator;
    }

    private static byte[] tbsCertToBytes(V3TBSCertificateGenerator generator) {
        try {
            return generator.generateTBSCertificate().getEncoded("DER");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SecureRandom getSecureRandom() {
        SecureRandom rng = random;
        if (rng == null) {
            rng = SecureRandomHolder.RANDOM;
        }
        return rng;
    }

    private void addExtensions(V3TBSCertificateGenerator tbsCert) throws Exception {
        ExtensionsGenerator generator = new ExtensionsGenerator();
        if (isCertificateAuthority) {
            final BasicConstraints basicConstraints;
            if (pathLengthConstraint.isPresent()) {
                basicConstraints = new BasicConstraints(pathLengthConstraint.getAsInt());
            } else {
                basicConstraints = new BasicConstraints(true);
            }
            final byte[] basicConstraintsBytes = basicConstraints.getEncoded("DER");
            generator.addExtension(new Extension(Extension.basicConstraints, true, basicConstraintsBytes));
        }
        if (keyUsage != null) {
            generator.addExtension(keyUsage);
        }

        if (!extendedKeyUsage.isEmpty()) {
            KeyPurposeId[] usages = new KeyPurposeId[extendedKeyUsage.size()];
            String[] usagesStrings = extendedKeyUsage.toArray(EmptyArrays.EMPTY_STRINGS);
            for (int i = 0; i < usagesStrings.length; i++) {
                usages[i] = KeyPurposeId.getInstance(new ASN1ObjectIdentifier(usagesStrings[i]));
            }
            byte[] der = new org.bouncycastle.asn1.x509.ExtendedKeyUsage(usages).getEncoded("DER");
            generator.addExtension(new Extension(Extension.extendedKeyUsage, false, der));
        }

        if (!subjectAlternativeNames.isEmpty()) {
            // SAN is critical extension if subject is empty sequence:
            boolean critical = subject.getName().isEmpty();
            byte[] result;
            GeneralNames generalNames = new GeneralNames(subjectAlternativeNames.toArray(EMPTY_GENERAL_NAMES));
            try {
                result = generalNames.getEncoded("DER");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            generator.addExtension(new Extension(Extension.subjectAlternativeName, critical, result));
        }

        if (!crlDistributionPoints.isEmpty()) {
            generator.addExtension(Extension.create(
                    Extension.cRLDistributionPoints,
                    false,
                    new CRLDistPoint(crlDistributionPoints.toArray(EMPTY_DIST_POINTS))));
        }

        for (BuilderCallback callback : modifierCallbacks) {
            callback.modify(generator);
        }

        if (!generator.isEmpty()) {
            tbsCert.setExtensions(generator.generate());
        }
    }

    /**
     * The {@link Algorithm} enum encapsulates both the key type, key generation parameters, and the signature
     * algorithm to use.
     */
    public enum Algorithm {
        /**
         * The NIST P-256 elliptic curve algorithm, offer fast key generation, signing, and verification,
         * with small keys and signatures, at 128-bits of security strength.
         * <p>
         * This algorithm is older than the Edwards curves, and are more widely supported.
         */
        ecp256("EC", new ECGenParameterSpec("secp256r1"), "SHA256withECDSA"),
        /**
         * The NIST P-384 elliptic curve algorithm, offer fast key generation, signing, and verification,
         * with small keys and signatures, at 192-bits of security strength.
         * <p>
         * This algorithm is older than the Edwards curves, and are more widely supported.
         */
        ecp384("EC", new ECGenParameterSpec("secp384r1"), "SHA384withECDSA"),
        /**
         * The 2048-bit RSA algorithm offer roughly 112-bits of security strength, at the cost of large keys
         * and slightly expensive key generation.
         * <p>
         * This algorithm enjoy the widest support and compatibility, though.
         */
        rsa2048("RSA", new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), "SHA256withRSA"),
        /**
         * The 3072-bit RSA algorithm offer roughly 128-bits of security strength, at the cost of large keys
         * and fairly expensive key generation.
         * <p>
         * RSA enjoy pretty wide compatibility, though not all systems support keys this large.
         */
        rsa3072("RSA", new RSAKeyGenParameterSpec(3072, RSAKeyGenParameterSpec.F4), "SHA256withRSA"),
        /**
         * The 4096-bit RSA algorithm offer roughly greater than 128-bits of security strength,
         * at the cost of large keys and very expensive key generation.
         * <p>
         * RSA enjoy pretty wide compatibility, though not all systems support keys this large.
         */
        rsa4096("RSA", new RSAKeyGenParameterSpec(4096, RSAKeyGenParameterSpec.F4), "SHA384withRSA"),
        /**
         * The 8192-bit RSA algorithm offer roughly greater than 192-bits of security strength,
         * at the cost of very large keys and extremely expensive key generation.
         * <p>
         * RSA enjoy pretty wide compatibility, though not all systems support keys this large.
         */
        rsa8192("RSA", new RSAKeyGenParameterSpec(8192, RSAKeyGenParameterSpec.F4), "SHA384withRSA"),
        /**
         * The Ed25519 algorithm offer fast key generation, signing, and verification,
         * with very small keys and signatures, at 128-bits of security strength.
         * <p>
         * This algorithm was added in Java 15, and may not be supported everywhere.
         */
        ed25519("Ed25519", namedParameterSpec("Ed25519"), "Ed25519"),
        /**
         * The Ed448 algorithm offer fast key generation, signing, and verification,
         * with small keys and signatures, at 224-bits of security strength.
         * <p>
         * This algorithm was added in Java 15, and may not be supported everywhere.
         */
        ed448("Ed448", namedParameterSpec("Ed448"), "Ed448"),
        /**
         * The ML-DSA-44 algorithm is the NIST FIPS 204 version of the post-quantum Dilithium algorithm.
         * It has 128-bits of classical security strength, and is claimed to meet NIST Level 2
         * quantum security strength (equivalent to finding a SHA-256 collision).
         * <p>
         * This algorithm was added in Java 24, and may not be supported everywhere.
         */
        mlDsa44("ML-DSA", namedParameterSpec("ML-DSA-44"), "ML-DSA-44"),
        /**
         * The ML-DSA-65 algorithm is the NIST FIPS 204 version of the post-quantum Dilithium algorithm.
         * It has 192-bits of classical security strength, and is claimed to meet NIST Level 3
         * quantum security strength (equivalent to finding the key for an AES-192 block).
         * <p>
         * This algorithm was added in Java 24, and may not be supported everywhere.
         */
        mlDsa65("ML-DSA", namedParameterSpec("ML-DSA-65"), "ML-DSA-65"),
        /**
         * The ML-DSA-87 algorithm is the NIST FIPS 204 version of the post-quantum Dilithium algorithm.
         * It has 256-bits of classical security strength, and is claimed to meet NIST Level 5
         * quantum security strength (equivalent to finding the key for an AES-256 block).
         * <p>
         * This algorithm was added in Java 24, and may not be supported everywhere.
         */
        mlDsa87("ML-DSA", namedParameterSpec("ML-DSA-87"), "ML-DSA-87"),
        /**
         * The ML-KEM-512 algorithm is the NIST FIPS 203 version of the post-quantum Kyber algorithm.
         * It has 128-bits of classical security strength, and is claimed to meet NIST Level 1
         * quantum security strength (equivalent to finding the key for an AES-1128 block).
         * <p>
         * This algorithm was added in Java 24, and may not be supported everywhere.
         */
        mlKem512("ML-KEM", namedParameterSpec("ML-KEM-512"), UNSUPPORTED_SIGN),
        /**
         * The ML-KEM-768 algorithm is the NIST FIPS 203 version of the post-quantum Kyber algorithm.
         * It has 192-bits of classical security strength, and is claimed to meet NIST Level 3
         * quantum security strength (equivalent to finding the key for an AES-192 block).
         * <p>
         * This algorithm was added in Java 24, and may not be supported everywhere.
         */
        mlKem768("ML-KEM", namedParameterSpec("ML-KEM-768"), UNSUPPORTED_SIGN),
        /**
         * The ML-KEM-1024 algorithm is the NIST FIPS 203 version of the post-quantum Kyber algorithm.
         * It has 256-bits of classical security strength, and is claimed to meet NIST Level 5
         * quantum security strength (equivalent to finding the key for an AES-256 block).
         * <p>
         * This algorithm was added in Java 24, and may not be supported everywhere.
         */
        mlKem1024("ML-KEM", namedParameterSpec("ML-KEM-1024"), UNSUPPORTED_SIGN);

        final String keyType;
        final AlgorithmParameterSpec parameterSpec;
        final String signatureType;

        Algorithm(String keyType, AlgorithmParameterSpec parameterSpec, String signatureType) {
            this.keyType = keyType;
            this.parameterSpec = parameterSpec;
            this.signatureType = signatureType;
        }

        private static AlgorithmParameterSpec namedParameterSpec(String name) {
            try {
                Class<?> cls = Class.forName("java.security.spec.NamedParameterSpec");
                return (AlgorithmParameterSpec) cls.getConstructor(String.class).newInstance(name);
            } catch (Exception e) {
                if ("Ed25519".equals(name)) {
                    return new EdDSAParameterSpec(EdDSAParameterSpec.Ed25519);
                }
                if ("Ed448".equals(name)) {
                    return new EdDSAParameterSpec(EdDSAParameterSpec.Ed448);
                }
                return UNSUPPORTED_SPEC;
            }
        }

        /**
         * Generate a new {@link KeyPair} using this algorithm, and the given {@link SecureRandom} generator.
         * @param secureRandom The {@link SecureRandom} generator to use, not {@code null}.
         * @return The generated {@link KeyPair}.
         * @throws GeneralSecurityException if the key pair cannot be generated using this algorithm for some reason.
         * @throws UnsupportedOperationException if this algorithm is not support in the current JVM.
         */
        public KeyPair generateKeyPair(SecureRandom secureRandom)
                throws GeneralSecurityException {
            requireNonNull(secureRandom, "secureRandom");

            if (parameterSpec == UNSUPPORTED_SPEC) {
                throw new UnsupportedOperationException("This algorithm is not supported: " + this);
            }

            KeyPairGenerator keyGen = Algorithms.keyPairGenerator(keyType, parameterSpec, secureRandom);
            return keyGen.generateKeyPair();
        }

        /**
         * Tell whether this algorithm is supported in the current JVM.
         * @return {@code true} if this algorithm is supported.
         */
        public boolean isSupported() {
            return parameterSpec != UNSUPPORTED_SPEC;
        }

        /**
         * Discern if this algorithm can be used for signing.
         * Algorithms need to support signing in order to create self-signed certificates,
         * or to be used as signing issuers of other certificates.
         * <p>
         * Note that this method only inspects a property of the algorithm, and does not check if the algorithm
         * {@linkplain #isSupported() is supported} in your environment.
         *
         * @return {@code true} if this algorithm can be used for signing, otherwise {@code false}.
         */
        public boolean supportSigning() {
            return !Objects.equals(signatureType, UNSUPPORTED_SIGN);
        }
    }

    /**
     * The key usage field specify what the certificate and key is allowed to be used for.
     * <p>
     * These key usages are specified by the X.509 standard, and some of them are deprecated.
     * <p>
     * See the {@link ExtendedKeyUsage} for other commonly used key usage extensions.
     * <p>
     * See ITU-T X.509 (10/2019) section 9.2.2.3 for the precise meaning of these usages.
     */
    public enum KeyUsage {
        /**
         * For verifying digital signatures, for entity authentication,
         * for entity authentication, or for integrity verification.
         */
        digitalSignature(0),
        /**
         * This key usage is deprecated by X.509, and commitment may instead be derived from the actual use of the keys.
         * <p>
         * For verifying digital signatures that imply the signer has "committed" to the
         * content being signed. This does not imply any specific policy or review on part of the signer, however.
         */
        contentCommitment(1),
        /**
         * For enciphering keys or other security information.
         */
        keyEncipherment(2),
        /**
         * For enciphering user data, but not keys or security information.
         */
        dataEncipherment(3),
        /**
         * For use in public key agreement.
         */
        keyAgreement(4),
        /**
         * For verifying the Certificate Authority's signature on a public-key certificate.
         * <p>
         * This implies {@link #digitalSignature} and {@link #contentCommitment}, so they do not need to be specified
         * separately.
         */
        keyCertSign(5),
        /**
         * For verifying the Certificate Authority's signature on a Certificate Revocation List.
         * <p>
         * This implies {@link #digitalSignature} and {@link #contentCommitment}, so they do not need to be specified
         * separately.
         */
        cRLSign(6),
        /**
         * For use with {@link #keyAgreement} to limit the key to enciphering only.
         * <p>
         * The meaning of this without the {@link #keyAgreement} bit set is unspecified.
         */
        encipherOnly(7),
        /**
         * For use with {@link #keyAgreement} to limit the key to deciphering only.
         * <p>
         * The meaning of this without the {@link #keyAgreement} bit set is unspecified.
         */
        decipherOnly(8);

        private final int bitId;

        KeyUsage(int bitId) {
            this.bitId = bitId;
        }
    }

    /**
     * The extended key usage field specify what the certificate and key is allowed to be used for.
     * <p>
     * A certificate can have many key usages. For instance, some certificates support both client and server usage
     * for TLS connections.
     * <p>
     * The key usage must be checked by the opposing peer receiving the certificate, and reject certificates that do
     * not permit the given usage.
     * <p>
     * For instance, if a TLS client connects to a server that presents a certificate without the
     * {@linkplain #PKIX_KP_SERVER_AUTH server-authentication} usage, then the client must reject the server
     * certificate as invalid.
     */
    public enum ExtendedKeyUsage {
        /**
         * The certificate can be used on the server-side of a TLS connection.
         */
        PKIX_KP_SERVER_AUTH(OID_PKIX_KP_SERVER_AUTH),
        /**
         * The certificate can be used on the client-side of a TLS connection.
         */
        PKIX_KP_CLIENT_AUTH(OID_PKIX_KP_CLIENT_AUTH),
        /**
         * The certificate can be used for code signing.
         */
        PKIX_KP_CODE_SIGNING(OID_PKIX_KP_CODE_SIGNING),
        /**
         * The certificate can be used for protecting email.
         */
        PKIX_KP_EMAIL_PROTECTION(OID_PKIX_KP_EMAIL_PROTECTION),
        /**
         * The certificate can be used for time-stamping.
         */
        PKIX_KP_TIME_STAMPING(OID_PKIX_KP_TIME_STAMPING),
        /**
         * The certificate can be used to sign OCSP replies.
         */
        PKIX_KP_OCSP_SIGNING(OID_PKIX_KP_OCSP_SIGNING),
        /**
         * The certificate can be used for Kerberos client authentication.
         */
        KERBEROS_KEY_PURPOSE_CLIENT_AUTH(OID_KERBEROS_KEY_PURPOSE_CLIENT_AUTH),
        /**
         * The certificate can be used for Microsoft smartcard logins.
         */
        MICROSOFT_SMARTCARD_LOGIN(OID_MICROSOFT_SMARTCARD_LOGIN);

        private final String oid;

        ExtendedKeyUsage(String oid) {
            this.oid = oid;
        }

        public String getOid() {
            return oid;
        }
    }

    @FunctionalInterface
    private interface BuilderCallback {
        void modify(ExtensionsGenerator builder) throws Exception;
    }

    private static final class SecureRandomHolder {
        private static final SecureRandom RANDOM = new SecureRandom();
    }
}
