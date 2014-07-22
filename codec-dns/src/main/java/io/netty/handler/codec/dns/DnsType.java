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

import java.lang.reflect.Field;

/**
 * Represents a DNS record type.
 */
public class DnsType {

    /**
     * Address record RFC 1035 Returns a 32-bit IPv4 address, most commonly used
     * to map hostnames to an IP address of the host, but also used for DNSBLs,
     * storing subnet masks in RFC 1101, etc.
     */
    public static final DnsType A = new DnsType(0x0001, "A");

    /**
     * Name server record RFC 1035 Delegates a DNS zone to use the given
     * authoritative name servers
     */
    public static final DnsType NS = new DnsType(0x0002, "NS");

    /**
     * Canonical name record RFC 1035 Alias of one name to another: the DNS
     * lookup will continue by retrying the lookup with the new name.
     */
    public static final DnsType CNAME = new DnsType(0x0005, "CNAME");

    /**
     * Start of [a zone of] authority record RFC 1035 and RFC 2308 Specifies
     * authoritative information about a DNS zone, including the primary name
     * server, the email of the domain administrator, the domain serial number,
     * and several timers relating to refreshing the zone.
     */
    public static final DnsType SOA = new DnsType(0x0006, "SOA");

    /**
     * Pointer record RFC 1035 Pointer to a canonical name. Unlike a CNAME, DNS
     * processing does NOT proceed, just the name is returned. The most common
     * use is for implementing reverse DNS lookups, but other uses include such
     * things as DNS-SD.
     */
    public static final DnsType PTR = new DnsType(0x000c, "PTR");

    /**
     * Mail exchange record RFC 1035 Maps a domain name to a list of message
     * transfer agents for that domain.
     */
    public static final DnsType MX = new DnsType(0x000f, "MX");

    /**
     * Text record RFC 1035 Originally for arbitrary human-readable text in a
     * DNS record. Since the early 1990s, however, this record more often
     * carries machine-readable data, such as specified by RFC 1464,
     * opportunistic encryption, Sender Policy Framework, DKIM, DMARC DNS-SD,
     * etc.
     */
    public static final DnsType TXT = new DnsType(0x0010, "TXT");

    /**
     * Responsible person record RFC 1183 Information about the responsible
     * person(s) for the domain. Usually an email address with the @ replaced by
     * a .
     */
    public static final DnsType RP = new DnsType(0x0011, "RP");

    /**
     * AFS database record RFC 1183 Location of database servers of an AFS cell.
     * This record is commonly used by AFS clients to contact AFS cells outside
     * their local domain. A subtype of this record is used by the obsolete
     * DCE/DFS file system.
     */
    public static final DnsType AFSDB = new DnsType(0x0012, "AFSDB");

    /**
     * Signature record RFC 2535 Signature record used in SIG(0) (RFC 2931) and
     * TKEY (RFC 2930). RFC 3755 designated RRSIG as the replacement for SIG for
     * use within DNSSEC.
     */
    public static final DnsType SIG = new DnsType(0x0018, "SIG");

    /**
     * key record RFC 2535 and RFC 2930 Used only for SIG(0) (RFC 2931) and TKEY
     * (RFC 2930). RFC 3445 eliminated their use for application keys and
     * limited their use to DNSSEC. RFC 3755 designates DNSKEY as the
     * replacement within DNSSEC. RFC 4025 designates IPSECKEY as the
     * replacement for use with IPsec.
     */
    public static final DnsType KEY = new DnsType(0x0019, "KEY");

    /**
     * IPv6 address record RFC 3596 Returns a 128-bit IPv6 address, most
     * commonly used to map hostnames to an IP address of the host.
     */
    public static final DnsType AAAA = new DnsType(0x001c, "AAAA");

    /**
     * Location record RFC 1876 Specifies a geographical location associated
     * with a domain name.
     */
    public static final DnsType LOC = new DnsType(0x001d, "LOC");

    /**
     * Service locator RFC 2782 Generalized service location record, used for
     * newer protocols instead of creating protocol-specific records such as MX.
     */
    public static final DnsType SRV = new DnsType(0x0021, "SRV");

    /**
     * Naming Authority Pointer record RFC 3403 Allows regular expression based
     * rewriting of domain names which can then be used as URIs, further domain
     * names to lookups, etc.
     */
    public static final DnsType NAPTR = new DnsType(0x0023, "NAPTR");

    /**
     * Key eXchanger record RFC 2230 Used with some cryptographic systems (not
     * including DNSSEC) to identify a key management agent for the associated
     * domain-name. Note that this has nothing to do with DNS Security. It is
     * Informational status, rather than being on the IETF standards-track. It
     * has always had limited deployment, but is still in use.
     */
    public static final DnsType KX = new DnsType(0x0024, "KX");

    /**
     * Certificate record RFC 4398 Stores PKIX, SPKI, PGP, etc.
     */
    public static final DnsType CERT = new DnsType(0x0025, "CERT");

    /**
     * Delegation name record RFC 2672 DNAME creates an alias for a name and all
     * its subnames, unlike CNAME, which aliases only the exact name in its
     * label. Like the CNAME record, the DNS lookup will continue by retrying
     * the lookup with the new name.
     */
    public static final DnsType DNAME = new DnsType(0x0027, "DNAME");

    /**
     * Option record RFC 2671 This is a pseudo DNS record type needed to support
     * EDNS.
     */
    public static final DnsType OPT = new DnsType(0x0029, "OPT");

    /**
     * Address Prefix List record RFC 3123 Specify lists of address ranges, e.g.
     * in CIDR format, for various address families. Experimental.
     */
    public static final DnsType APL = new DnsType(0x002a, "APL");

    /**
     * Delegation signer record RFC 4034 The record used to identify the DNSSEC
     * signing key of a delegated zone.
     */
    public static final DnsType DS = new DnsType(0x002b, "DS");

    /**
     * SSH Public Key Fingerprint record RFC 4255 Resource record for publishing
     * SSH public host key fingerprints in the DNS System, in order to aid in
     * verifying the authenticity of the host. RFC 6594 defines ECC SSH keys and
     * SHA-256 hashes. See the IANA SSHFP RR parameters registry for details.
     */
    public static final DnsType SSHFP = new DnsType(0x002c, "SSHFP");

    /**
     * IPsec Key record RFC 4025 Key record that can be used with IPsec.
     */
    public static final DnsType IPSECKEY = new DnsType(0x002d, "IPSECKEY");

    /**
     * DNSSEC signature record RFC 4034 Signature for a DNSSEC-secured record
     * set. Uses the same format as the SIG record.
     */
    public static final DnsType RRSIG = new DnsType(0x002e, "RRSIG");

    /**
     * Next-Secure record RFC 4034 Part of DNSSEC, used to prove a name does not
     * exist. Uses the same format as the (obsolete) NXT record.
     */
    public static final DnsType NSEC = new DnsType(0x002f, "NSEC");

    /**
     * DNS Key record RFC 4034 The key record used in DNSSEC. Uses the same
     * format as the KEY record.
     */
    public static final DnsType DNSKEY = new DnsType(0x0030, "DNSKEY");

    /**
     * DHCP identifier record RFC 4701 Used in conjunction with the FQDN option
     * to DHCP.
     */
    public static final DnsType DHCID = new DnsType(0x0031, "DHCID");

    /**
     * NSEC record version 3 RFC 5155 An extension to DNSSEC that allows proof
     * of nonexistence for a name without permitting zonewalking.
     */
    public static final DnsType NSEC3 = new DnsType(0x0032, "NSEC3");

    /**
     * NSEC3 parameters record RFC 5155 Parameter record for use with NSEC3.
     */
    public static final DnsType NSEC3PARAM = new DnsType(0x0033, "NSEC3PARAM");

    /**
     * TLSA certificate association record RFC 6698 A record for DNS-based
     * Authentication of Named Entities (DANE). RFC 6698 defines The TLSA DNS
     * resource record is used to associate a TLS server certificate or public
     * key with the domain name where the record is found, thus forming a 'TLSA
     * certificate association'.
     */
    public static final DnsType TLSA = new DnsType(0x0034, "TLSA");

    /**
     * Host Identity Protocol record RFC 5205 Method of separating the end-point
     * identifier and locator roles of IP addresses.
     */
    public static final DnsType HIP = new DnsType(0x0037, "HIP");

    /**
     * Sender Policy Framework record RFC 4408 Specified as part of the SPF
     * protocol as an alternative to of storing SPF data in TXT records. Uses
     * the same format as the earlier TXT record.
     */
    public static final DnsType SPF = new DnsType(0x0063, "SPF");

    /**
     * Secret key record RFC 2930 A method of providing keying material to be
     * used with TSIG that is encrypted under the public key in an accompanying
     * KEY RR..
     */
    public static final DnsType TKEY = new DnsType(0x00f9, "TKEY");

    /**
     * Transaction Signature record RFC 2845 Can be used to authenticate dynamic
     * updates as coming from an approved client, or to authenticate responses
     * as coming from an approved recursive name server similar to DNSSEC.
     */
    public static final DnsType TSIG = new DnsType(0x00fa, "TSIG");

    /**
     * Incremental Zone Transfer record RFC 1996 Requests a zone transfer of the
     * given zone but only differences from a previous serial number. This
     * request may be ignored and a full (AXFR) sent in response if the
     * authoritative server is unable to fulfill the request due to
     * configuration or lack of required deltas.
     */
    public static final DnsType IXFR = new DnsType(0x00fb, "IXFR");

    /**
     * Authoritative Zone Transfer record RFC 1035 Transfer entire zone file
     * from the master name server to secondary name servers.
     */
    public static final DnsType AXFR = new DnsType(0x00fc, "AXFR");

    /**
     * All cached records RFC 1035 Returns all records of all types known to the
     * name server. If the name server does not have any information on the
     * name, the request will be forwarded on. The records returned may not be
     * complete. For example, if there is both an A and an MX for a name, but
     * the name server has only the A record cached, only the A record will be
     * returned. Sometimes referred to as ANY, for example in Windows nslookup
     * and Wireshark.
     */
    public static final DnsType ANY = new DnsType(0x00ff, "ANY");

    /**
     * Certification Authority Authorization record RFC 6844 CA pinning,
     * constraining acceptable CAs for a host/domain.
     */
    public static final DnsType CAA = new DnsType(0x0101, "CAA");

    /**
     * DNSSEC Trust Authorities record N/A Part of a deployment proposal for
     * DNSSEC without a signed DNS root. See the IANA database and Weiler Spec
     * for details. Uses the same format as the DS record.
     */
    public static final DnsType TA = new DnsType(0x8000, "TA");

    /**
     * DNSSEC Lookaside Validation record RFC 4431 For publishing DNSSEC trust
     * anchors outside of the DNS delegation chain. Uses the same format as the
     * DS record. RFC 5074 describes a way of using these records.
     */
    public static final DnsType DLV = new DnsType(0x8001, "DLV");

    /**
     * The value of this type in DNS protocol
     */
    private final int type;
    /**
     * The name of this type
     */
    private final String name;

    DnsType(int type, String name) {
        if ((type & 0xffff) != type) {
            throw new IllegalArgumentException("type must be an unsigned short.");
        }
        this.type = type;
        this.name = name;
    }

    /**
     * The name of this type, as seen in bind config files
     * @return the name
     */
    public final String name() {
        return name;
    }

    /**
     * The value of this DnsType as it appears in DNS protocol
     * @return The valueF
     */
    public final int type() {
        return type;
    }

    public static DnsType create(int type, String name) {
        return new DnsType(type, name);
    }

    @Override
    public final int hashCode() {
        return type;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof DnsType && ((DnsType) o).type == type;
    }

    @Override
    public final String toString() {
        return name;
    }

    public static DnsType find(int type) {
        switch (type) {
            case 1:
                return A;
            case 28:
                return AAAA;
            case 16:
                return TXT;
            case 2:
                return NS;
            case 5:
                return CNAME;
            case 6:
                return SOA;
            case 12:
                return PTR;
            case 15:
                return MX;
            case 255:
                return ANY;
            case 29:
                return LOC;
            case 17:
                return RP;
            case 18:
                return AFSDB;
            case 24:
                return SIG;
            case 25:
                return KEY;
            case 33:
                return SRV;
            case 35:
                return NAPTR;
            case 36:
                return KX;
            case 37:
                return CERT;
            case 39:
                return DNAME;
            case 41:
                return OPT;
            case 42:
                return APL;
            case 43:
                return DS;
            case 44:
                return SSHFP;
            case 45:
                return IPSECKEY;
            case 46:
                return RRSIG;
            case 47:
                return NSEC;
            case 48:
                return DNSKEY;
            case 49:
                return DHCID;
            case 50:
                return NSEC3;
            case 51:
                return NSEC3PARAM;
            case 52:
                return TLSA;
            case 55:
                return HIP;
            case 99:
                return SPF;
            case 249:
                return TKEY;
            case 250:
                return TSIG;
            case 251:
                return IXFR;
            case 252:
                return AXFR;
            case 257:
                return CAA;
            case 32768:
                return TA;
            case 32769:
                return DLV;
            default:
                return null;
        }
    }

    public static DnsType forName(String name) {
        if ("A".equals(name)) {
            return A;
        } else if ("AAAA".equals(name)) {
            return AAAA;
        } else if ("TXT".equals(name)) {
            return TXT;
        } else if ("NS".equals(name)) {
            return NS;
        } else if ("CNAME".equals(name)) {
            return CNAME;
        } else if ("SOA".equals(name)) {
            return SOA;
        } else if ("PTR".equals(name)) {
            return PTR;
        } else if ("MX".equals(name)) {
            return MX;
        } else if ("ANY".equals(name)) {
            return ANY;
        } else if ("LOC".equals(name)) {
            return LOC;
        } else if ("RP".equals(name)) {
            return RP;
        } else if ("AFSDB".equals(name)) {
            return AFSDB;
        } else if ("SIG".equals(name)) {
            return SIG;
        } else if ("KEY".equals(name)) {
            return KEY;
        } else if ("SRV".equals(name)) {
            return SRV;
        } else if ("NAPTR".equals(name)) {
            return NAPTR;
        } else if ("KX".equals(name)) {
            return KX;
        } else if ("CERT".equals(name)) {
            return CERT;
        } else if ("DNAME".equals(name)) {
            return DNAME;
        } else if ("OPT".equals(name)) {
            return OPT;
        } else if ("APL".equals(name)) {
            return APL;
        } else if ("DS".equals(name)) {
            return DS;
        } else if ("SSHFP".equals(name)) {
            return SSHFP;
        } else if ("IPSECKEY".equals(name)) {
            return IPSECKEY;
        } else if ("RRSIG".equals(name)) {
            return RRSIG;
        } else if ("NSEC".equals(name)) {
            return NSEC;
        } else if ("DNSKEY".equals(name)) {
            return DNSKEY;
        } else if ("DHCID".equals(name)) {
            return DHCID;
        } else if ("NSEC3".equals(name)) {
            return NSEC3;
        } else if ("NSEC3PARAM".equals(name)) {
            return NSEC3PARAM;
        } else if ("TLSA".equals(name)) {
            return TLSA;
        } else if ("HIP".equals(name)) {
            return HIP;
        } else if ("SPF".equals(name)) {
            return SPF;
        } else if ("TKEY".equals(name)) {
            return TKEY;
        } else if ("TSIG".equals(name)) {
            return TSIG;
        } else if ("IXFR".equals(name)) {
            return IXFR;
        } else if ("AXFR".equals(name)) {
            return AXFR;
        } else if ("CAA".equals(name)) {
            return CAA;
        } else if ("TA".equals(name)) {
            return TA;
        } else if ("DLV".equals(name)) {
            return DLV;
        } else {
            return null;
        }
    }
}
