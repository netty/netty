/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;
import static io.netty.handler.codec.dns.DnsResponseDecoder.readName;
import io.netty.util.collection.IntObjectHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a DNS record type.
 */
public final class DnsType<RecordType extends DnsEntry> implements Comparable<DnsType<?>> {

    /**
     * Address record RFC 1035 Returns a 32-bit IPv4 address, most commonly used
     * to map hostnames to an IP address of the host, but also used for DNSBLs,
     * storing subnet masks in RFC 1101, etc.
     */
    public static final DnsType<Ipv4AddressRecord> A = new DnsType<Ipv4AddressRecord>(0x0001, "A", new Ipv4Decoder());

    /**
     * Name server record RFC 1035 Delegates a DNS zone to use the given
     * authoritative name servers
     */
    public static final DnsType<NameServerDnsRecord> NS = new DnsType<NameServerDnsRecord>(0x0002, "NS",
            new NsResourceDecoder());

    /**
     * Canonical name record RFC 1035 Alias of one name to another: the DNS
     * lookup will continue by retrying the lookup with the new name.
     */
    public static final DnsType<CNameDnsRecord> CNAME = new DnsType<CNameDnsRecord>(0x0005, "CNAME",
            new CnameDecoder());

    /**
     * Start of [a zone of] authority record RFC 1035 and RFC 2308 Specifies
     * authoritative information about a DNS zone, including the primary name
     * server, the email of the domain administrator, the domain serial number,
     * and several timers relating to refreshing the zone.
     */
    public static final DnsType<StartOfAuthorityDnsRecord> SOA = new DnsType<StartOfAuthorityDnsRecord>(0x0006,
            "SOA", new SoaDecoder());

    /**
     * Pointer record RFC 1035 Pointer to a canonical name. Unlike a CNAME, DNS
     * processing does NOT proceed, just the name is returned. The most common
     * use is for implementing reverse DNS lookups, but other uses include such
     * things as DNS-SD.
     */
    public static final DnsType<PointerDnsRecord> PTR = new DnsType<PointerDnsRecord>(0x000c, "PTR", new PtrDecoder());

    /**
     * Mail exchange record RFC 1035 Maps a domain name to a list of message
     * transfer agents for that domain.
     */
    public static final DnsType<MailExchangerDnsRecord> MX = new DnsType<MailExchangerDnsRecord>(0x000f, "MX",
            new MxDecoder());

    /**
     * Text record RFC 1035 Originally for arbitrary human-readable text in a
     * DNS record. Since the early 1990s, however, this record more often
     * carries machine-readable data, such as specified by RFC 1464,
     * opportunistic encryption, Sender Policy Framework, DKIM, DMARC DNS-SD,
     * etc.
     */
    public static final DnsType<DnsResource> TXT = new DnsType<DnsResource>(0x0010, "TXT");

    /**
     * Responsible person record RFC 1183 Information about the responsible
     * person(s) for the domain. Usually an email address with the @ replaced by
     * a .
     */
    public static final DnsType<DnsResource> RP = new DnsType<DnsResource>(0x0011, "RP");

    /**
     * AFS database record RFC 1183 Location of database servers of an AFS cell.
     * This record is commonly used by AFS clients to contact AFS cells outside
     * their local domain. A subtype of this record is used by the obsolete
     * DCE/DFS file system.
     */
    public static final DnsType<DnsResource> AFSDB = new DnsType<DnsResource>(0x0012, "AFSDB");

    /**
     * Signature record RFC 2535 Signature record used in SIG(0) (RFC 2931) and
     * TKEY (RFC 2930). RFC 3755 designated RRSIG as the replacement for SIG for
     * use within DNSSEC.
     */
    public static final DnsType<DnsResource> SIG = new DnsType<DnsResource>(0x0018, "SIG");

    /**
     * key record RFC 2535 and RFC 2930 Used only for SIG(0) (RFC 2931) and TKEY
     * (RFC 2930). RFC 3445 eliminated their use for application keys and
     * limited their use to DNSSEC. RFC 3755 designates DNSKEY as the
     * replacement within DNSSEC. RFC 4025 designates IPSECKEY as the
     * replacement for use with IPsec.
     */
    public static final DnsType<DnsResource> KEY = new DnsType<DnsResource>(0x0019, "KEY");

    /**
     * IPv6 address record RFC 3596 Returns a 128-bit IPv6 address, most
     * commonly used to map hostnames to an IP address of the host.
     */
    public static final DnsType<DnsResource> AAAA = new DnsType<DnsResource>(0x001c, "AAAA");

    /**
     * Location record RFC 1876 Specifies a geographical location associated
     * with a domain name.
     */
    public static final DnsType<DnsResource> LOC = new DnsType<DnsResource>(0x001d, "LOC");

    /**
     * Service locator RFC 2782 Generalized service location record, used for
     * newer protocols instead of creating protocol-specific records such as MX.
     */
    public static final DnsType<DnsResource> SRV = new DnsType<DnsResource>(0x0021, "SRV");

    /**
     * Naming Authority Pointer record RFC 3403 Allows regular expression based
     * rewriting of domain names which can then be used as URIs, further domain
     * names to lookups, etc.
     */
    public static final DnsType<DnsResource> NAPTR = new DnsType<DnsResource>(0x0023, "NAPTR");

    /**
     * Key eXchanger record RFC 2230 Used with some cryptographic systems (not
     * including DNSSEC) to identify a key management agent for the associated
     * domain-name. Note that this has nothing to do with DNS Security. It is
     * Informational status, rather than being on the IETF standards-track. It
     * has always had limited deployment, but is still in use.
     */
    public static final DnsType<DnsResource> KX = new DnsType<DnsResource>(0x0024, "KX");

    /**
     * Certificate record RFC 4398 Stores PKIX, SPKI, PGP, etc.
     */
    public static final DnsType<DnsResource> CERT = new DnsType<DnsResource>(0x0025, "CERT");

    /**
     * Delegation name record RFC 2672 DNAME creates an alias for a name and all
     * its subnames, unlike CNAME, which aliases only the exact name in its
     * label. Like the CNAME record, the DNS lookup will continue by retrying
     * the lookup with the new name.
     */
    public static final DnsType<DnsResource> DNAME = new DnsType<DnsResource>(0x0027, "DNAME");

    /**
     * Option record RFC 2671 This is a pseudo DNS record type needed to support
     * EDNS.
     */
    public static final DnsType<DnsResource> OPT = new DnsType<DnsResource>(0x0029, "OPT");

    /**
     * Address Prefix List record RFC 3123 Specify lists of address ranges, e.g.
     * in CIDR format, for various address families. Experimental.
     */
    public static final DnsType<DnsResource> APL = new DnsType<DnsResource>(0x002a, "APL");

    /**
     * Delegation signer record RFC 4034 The record used to identify the DNSSEC
     * signing key of a delegated zone.
     */
    public static final DnsType<DnsResource> DS = new DnsType<DnsResource>(0x002b, "DS");

    /**
     * SSH Public Key Fingerprint record RFC 4255 Resource record for publishing
     * SSH public host key fingerprints in the DNS System, in order to aid in
     * verifying the authenticity of the host. RFC 6594 defines ECC SSH keys and
     * SHA-256 hashes. See the IANA SSHFP RR parameters registry for details.
     */
    public static final DnsType<DnsResource> SSHFP = new DnsType<DnsResource>(0x002c, "SSHFP");

    /**
     * IPsec Key record RFC 4025 Key record that can be used with IPsec.
     */
    public static final DnsType<DnsResource> IPSECKEY = new DnsType<DnsResource>(0x002d, "IPSECKEY");

    /**
     * DNSSEC signature record RFC 4034 Signature for a DNSSEC-secured record
     * set. Uses the same format as the SIG record.
     */
    public static final DnsType<DnsResource> RRSIG = new DnsType<DnsResource>(0x002e, "RRSIG");

    /**
     * Next-Secure record RFC 4034 Part of DNSSEC, used to prove a name does not
     * exist. Uses the same format as the (obsolete) NXT record.
     */
    public static final DnsType<DnsResource> NSEC = new DnsType<DnsResource>(0x002f, "NSEC");

    /**
     * DNS Key record RFC 4034 The key record used in DNSSEC. Uses the same
     * format as the KEY record.
     */
    public static final DnsType<DnsResource> DNSKEY = new DnsType<DnsResource>(0x0030, "DNSKEY");

    /**
     * DHCP identifier record RFC 4701 Used in conjunction with the FQDN option
     * to DHCP.
     */
    public static final DnsType<DnsResource> DHCID = new DnsType<DnsResource>(0x0031, "DHCID");

    /**
     * NSEC record version 3 RFC 5155 An extension to DNSSEC that allows proof
     * of nonexistence for a name without permitting zonewalking.
     */
    public static final DnsType<DnsResource> NSEC3 = new DnsType<DnsResource>(0x0032, "NSEC3");

    /**
     * NSEC3 parameters record RFC 5155 Parameter record for use with NSEC3.
     */
    public static final DnsType<DnsResource> NSEC3PARAM = new DnsType<DnsResource>(0x0033, "NSEC3PARAM");

    /**
     * TLSA certificate association record RFC 6698 A record for DNS-based
     * Authentication of Named Entities (DANE). RFC 6698 defines The TLSA DNS
     * resource record is used to associate a TLS server certificate or public
     * key with the domain name where the record is found, thus forming a 'TLSA
     * certificate association'.
     */
    public static final DnsType<DnsResource> TLSA = new DnsType<DnsResource>(0x0034, "TLSA");

    /**
     * Host Identity Protocol record RFC 5205 Method of separating the end-point
     * identifier and locator roles of IP addresses.
     */
    public static final DnsType<DnsResource> HIP = new DnsType<DnsResource>(0x0037, "HIP");

    /**
     * Sender Policy Framework record RFC 4408 Specified as part of the SPF
     * protocol as an alternative to of storing SPF data in TXT records. Uses
     * the same format as the earlier TXT record.
     */
    public static final DnsType<DnsResource> SPF = new DnsType<DnsResource>(0x0063, "SPF");

    /**
     * Secret key record RFC 2930 A method of providing keying material to be
     * used with TSIG that is encrypted under the public key in an accompanying
     * KEY RR..
     */
    public static final DnsType<DnsResource> TKEY = new DnsType<DnsResource>(0x00f9, "TKEY");

    /**
     * Transaction Signature record RFC 2845 Can be used to authenticate dynamic
     * updates as coming from an approved client, or to authenticate responses
     * as coming from an approved recursive name server similar to DNSSEC.
     */
    public static final DnsType<DnsResource> TSIG = new DnsType<DnsResource>(0x00fa, "TSIG");

    /**
     * Incremental Zone Transfer record RFC 1996 Requests a zone transfer of the
     * given zone but only differences from a previous serial number. This
     * request may be ignored and a full (AXFR) sent in response if the
     * authoritative server is unable to fulfill the request due to
     * configuration or lack of required deltas.
     */
    public static final DnsType<DnsResource> IXFR = new DnsType<DnsResource>(0x00fb, "IXFR");

    /**
     * Authoritative Zone Transfer record RFC 1035 Transfer entire zone file
     * from the master name server to secondary name servers.
     */
    public static final DnsType<DnsResource> AXFR = new DnsType<DnsResource>(0x00fc, "AXFR");

    /**
     * All cached records RFC 1035 Returns all records of all types known to the
     * name server. If the name server does not have any information on the
     * name, the request will be forwarded on. The records returned may not be
     * complete. For example, if there is both an A and an MX for a name, but
     * the name server has only the A record cached, only the A record will be
     * returned. Sometimes referred to as ANY, for example in Windows nslookup
     * and Wireshark.
     */
    public static final DnsType<DnsResource> ANY = new DnsType<DnsResource>(0x00ff, "ANY");

    /**
     * Certification Authority Authorization record RFC 6844 CA pinning,
     * constraining acceptable CAs for a host/domain.
     */
    public static final DnsType<DnsResource> CAA = new DnsType<DnsResource>(0x0101, "CAA");

    /**
     * DNSSEC Trust Authorities record N/A Part of a deployment proposal for
     * DNSSEC without a signed DNS root. See the IANA database and Weiler Spec
     * for details. Uses the same format as the DS record.
     */
    public static final DnsType<DnsResource> TA = new DnsType<DnsResource>(0x8000, "TA");

    /**
     * DNSSEC Lookaside Validation record RFC 4431 For publishing DNSSEC trust
     * anchors outside of the DNS delegation chain. Uses the same format as the
     * DS record. RFC 5074 describes a way of using these records.
     */
    public static final DnsType<DnsResource> DLV = new DnsType<DnsResource>(0x8001, "DLV");

    private static final Map<String, DnsType<?>> BY_NAME = new HashMap<String, DnsType<?>>();
    private static final IntObjectHashMap<DnsType<?>> BY_TYPE = new IntObjectHashMap<DnsType<?>>();
    private static final String EXPECTED;

    static {
        DnsType[] all = {
            A, NS, CNAME, SOA, PTR, MX, TXT, RP, AFSDB, SIG, KEY, AAAA, LOC, SRV, NAPTR, KX, CERT, DNAME, OPT, APL,
            DS, SSHFP, IPSECKEY, RRSIG, NSEC, DNSKEY, DHCID, NSEC3, NSEC3PARAM, TLSA, HIP, SPF, TKEY, TSIG, IXFR,
            AXFR, ANY, CAA, TA, DLV
        };

        StringBuilder expected = new StringBuilder(410);
        expected.append(" (expected: ");

        for (DnsType<?> type : all) {
            BY_NAME.put(type.name(), type);
            BY_TYPE.put(type.intValue(), type);
            expected.append(type.name());
            expected.append('(');
            expected.append(type.intValue());
            expected.append("), ");
        }

        expected.setLength(expected.length() - 2);
        expected.append(')');
        EXPECTED = expected.toString();
    }

    /**
     * Decode the <i>payload</i> of a record of this type into a record of type
     * &lt;RecordType&gt;. The resulting object will be of {@link DnsClass.IN}.
     *
     * @param name The name for the resulting record
     * @param timeToLive The time to live of the resulting record
     * @param buf A buffer to read from
     * @param length The length of the payload in the buffer
     * @return An object of type &lt;RecordType&gt; such as an {@link Ipv4AddressRecord}
     */
    public RecordType decode(String name, long timeToLive, ByteBuf buf, int length)
            throws DnsDecoderException {
        return decode(name, DnsClass.IN, timeToLive, buf, length);
    }

    /**
     * Decode the <i>payload</i> of a record of this type into a record of type
     * &lt;RecordType&gt;.
     *
     * @param name The name for the resulting record
     * @param clazz The DNS class
     * @param timeToLive The time to live of the resulting record
     * @param buf A buffer to read from
     * @param length The length of the payload in the buffer
     * @return An object of type &lt;RecordType&gt; such as an {@link Ipv4AddressRecord}
     */
    public RecordType decode(String name, DnsClass clazz, long timeToLive, ByteBuf buf,
            int length) throws DnsDecoderException {
        try {
            return decoder.decode(name, this, clazz, timeToLive, buf, length);
        } catch (IndexOutOfBoundsException ex) {
            throw new DnsDecoderException(DnsResponseCode.FORMERROR, ex);
        }
    }

    /**
     * Get the constant value of the code returned by a DNS response.
     * If an unknown code is passed, will return a DnsType with the passed
     * code and a name of "UNKNOWN".
     *
     * @param intValue The code
     * @return The constant
     */
    public static DnsType<?> valueOf(int intValue) {
        DnsType result = BY_TYPE.get(intValue);
        if (result == null) {
            return new DnsType(intValue, "UNKNOWN");
        }
        return result;
    }

    /**
     * Look up a DnsType by its name according to the DNS spec
     * @param name The name of a DNS record type as defined in one of the
     * relevant RFCs
     * @return A DnsType
     * @throws IllegalArgumentException if the name is unknown
     */
    public static DnsType<?> valueOf(String name) {
        DnsType result = BY_NAME.get(name);
        if (result == null) {
            throw new IllegalArgumentException("name: " + name + EXPECTED);
        }
        return result;
    }

    /**
     * Returns a new instance.
     */
    public static DnsType<?> valueOf(int intValue, String name) {
        return new DnsType(intValue, name);
    }

    private final int intValue;
    private final String name;
    private final Decoder<RecordType> decoder;

    @SuppressWarnings("unchecked")
    private DnsType(int intValue, String name) {
        this(intValue, name, (Decoder<RecordType>) DnsResourceDecoder.INSTANCE);
    }

    private DnsType(int intValue, String name, Decoder<RecordType> decoder) {
        if ((intValue & 0xffff) != intValue) {
            throw new IllegalArgumentException("intValue: " + intValue + " (expected: 0 ~ 65535)");
        }
        if (decoder == null) {
            throw new NullPointerException("decoder");
        }
        this.intValue = intValue;
        this.name = name;
        this.decoder = decoder;
    }

    /**
     * Returns the name of this type, as seen in bind config files
     */
    public String name() {
        return name;
    }

    /**
     * Returns the value of this DnsType as it appears in DNS protocol
     */
    public int intValue() {
        return intValue;
    }

    @Override
    public int hashCode() {
        return intValue;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DnsType && ((DnsType) o).intValue == intValue;
    }

    @Override
    public int compareTo(DnsType o) {
        return intValue() - o.intValue();
    }

    @Override
    public String toString() {
        return name;
    }

    static long toUnsignedLong(int val) {
        return 0x00000000FFFFFFFFL & (long) val;
    }

    /**
     * Non-public interface for objects which can decode the payload
     * of a received packet into the appropriate &lt;RecordType&gt;.
     * @param <T> The record type
     */
    interface Decoder<T extends DnsEntry> {

        /**
         * Decode the contents of a {@link ByteBuf}, using the passed
         * name, type, time-to-live and class, along with the decoded
         * payload to construct an {@link DnsEntry} of the appropriate
         * type
         * @param name The name this record asks or answers for
         * @param type The type of record to be held by the resulting object
         * @param clazz The DNS class
         * @param timeToLive The length of time in seconds that the result
         * should be considered current for
         * @param buf The bytes to decode
         * @param length The number of bytes from the current reader index
         * of the buffer, which should be used when decodeing
         * @return An object of the appropriate type
         * @throws DnsDecoderException If the data is invalid in some way
         */
        T decode(String name, DnsType<T> type, DnsClass clazz, long timeToLive, ByteBuf buf, int length)
                throws DnsDecoderException;
    }

    private static final class DnsResourceDecoder implements Decoder<DnsResource> {

        static final Decoder<DnsResource> INSTANCE = new DnsResourceDecoder();

        @Override
        public DnsResource decode(String name, DnsType<DnsResource> type, DnsClass clazz, long timeToLive,
                ByteBuf buf, int length) {
            int readerIndex = buf.readerIndex();
            ByteBuf payload = buf.duplicate().setIndex(readerIndex, readerIndex + length).retain();
            buf.readerIndex(readerIndex + length);
            return new DnsResource(name, type, clazz, timeToLive, payload);
        }
    }

    private static final class NsResourceDecoder implements Decoder<NameServerDnsRecord> {

        @Override
        public NameServerDnsRecord decode(String name, DnsType<NameServerDnsRecord> type, DnsClass clazz,
                long timeToLive, ByteBuf buf, int length) {
            String ns = readName(buf);
            return new NameServerDnsRecord(name, type, clazz, ns, timeToLive);
        }
    }

    private static final class CnameDecoder implements Decoder<CNameDnsRecord> {

        @Override
        public CNameDnsRecord decode(String name, DnsType<CNameDnsRecord> type, DnsClass clazz,
                long timeToLive, ByteBuf buf, int length) {
            String cname = DnsResponseDecoder.readName(buf);
            return new CNameDnsRecord(name, type, clazz, timeToLive, cname);
        }
    }

    private static final class Ipv4Decoder implements Decoder<Ipv4AddressRecord> {

        @Override
        public Ipv4AddressRecord decode(String name, DnsType<Ipv4AddressRecord> type, DnsClass clazz,
                long timeToLive, ByteBuf buf, int length) {
            int addr = buf.readInt();
            return new Ipv4AddressRecord(name, type, clazz, timeToLive, addr);
        }
    }

    private static final class SoaDecoder implements Decoder<StartOfAuthorityDnsRecord> {

        @Override
        public StartOfAuthorityDnsRecord decode(String name, DnsType<StartOfAuthorityDnsRecord> type,
                DnsClass clazz, long timeToLive, ByteBuf buf, int length) {
            String primaryNs = readName(buf);
            String adminMailbox = readName(buf);
            long serialNumber = toUnsignedLong(buf.readInt());
            long refreshInterval = toUnsignedLong(buf.readInt());
            long retryInterval = toUnsignedLong(buf.readInt());
            long expirationLimit = toUnsignedLong(buf.readInt());
            long minimumTtl = toUnsignedLong(buf.readInt());
            return new StartOfAuthorityDnsRecord(name, type, clazz,
                    primaryNs, adminMailbox, serialNumber, refreshInterval,
                    retryInterval, expirationLimit, minimumTtl, timeToLive);
        }
    }

    private static final class PtrDecoder implements Decoder<PointerDnsRecord> {

        @Override
        public PointerDnsRecord decode(String name, DnsType<PointerDnsRecord> type, DnsClass clazz,
                long timeToLive, ByteBuf buf, int length) {
            String ptr = readName(buf);
            return new PointerDnsRecord(name, type, clazz, ptr, timeToLive);
        }
    }

    private static final class MxDecoder implements Decoder<MailExchangerDnsRecord> {

        @Override
        public MailExchangerDnsRecord decode(String name, DnsType<MailExchangerDnsRecord> type,
                DnsClass clazz, long timeToLive, ByteBuf buf, int length) {
            int pref = buf.readShort();
            String mx = readName(buf);
            return new MailExchangerDnsRecord(name, type, clazz, timeToLive, pref, mx);
        }
    }
}
