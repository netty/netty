/*
 * Copyright 2013 The Netty Project
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

/**
 * A class representing entries in a DNS packet (questions, and all resource
 * records). Contains data shared by entries such as name, type, and class.
 */
public class DnsEntry {

    /**
     * Address record RFC 1035 Returns a 32-bit IPv4 address, most commonly used
     * to map hostnames to an IP address of the host, but also used for DNSBLs,
     * storing subnet masks in RFC 1101, etc.
     */
    public static final int TYPE_A = 0x0001;

    /**
     * Name server record RFC 1035 Delegates a DNS zone to use the given
     * authoritative name servers
     */
    public static final int TYPE_NS = 0x0002;

    /**
     * Canonical name record RFC 1035 Alias of one name to another: the DNS
     * lookup will continue by retrying the lookup with the new name.
     */
    public static final int TYPE_CNAME = 0x0005;

    /**
     * Start of [a zone of] authority record RFC 1035 and RFC 2308 Specifies
     * authoritative information about a DNS zone, including the primary name
     * server, the email of the domain administrator, the domain serial number,
     * and several timers relating to refreshing the zone.
     */
    public static final int TYPE_SOA = 0x0006;

    /**
     * Pointer record RFC 1035 Pointer to a canonical name. Unlike a CNAME, DNS
     * processing does NOT proceed, just the name is returned. The most common
     * use is for implementing reverse DNS lookups, but other uses include such
     * things as DNS-SD.
     */
    public static final int TYPE_PTR = 0x000c;

    /**
     * Mail exchange record RFC 1035 Maps a domain name to a list of message
     * transfer agents for that domain.
     */
    public static final int TYPE_MX = 0x000f;

    /**
     * Text record RFC 1035 Originally for arbitrary human-readable text in a
     * DNS record. Since the early 1990s, however, this record more often
     * carries machine-readable data, such as specified by RFC 1464,
     * opportunistic encryption, Sender Policy Framework, DKIM, DMARC DNS-SD,
     * etc.
     */
    public static final int TYPE_TXT = 0x0010;

    /**
     * Responsible person record RFC 1183 Information about the responsible
     * person(s) for the domain. Usually an email address with the @ replaced by
     * a .
     */
    public static final int TYPE_RP = 0x0011;

    /**
     * AFS database record RFC 1183 Location of database servers of an AFS cell.
     * This record is commonly used by AFS clients to contact AFS cells outside
     * their local domain. A subtype of this record is used by the obsolete
     * DCE/DFS file system.
     */
    public static final int TYPE_AFSDB = 0x0012;

    /**
     * Signature record RFC 2535 Signature record used in SIG(0) (RFC 2931) and
     * TKEY (RFC 2930). RFC 3755 designated RRSIG as the replacement for SIG for
     * use within DNSSEC.
     */
    public static final int TYPE_SIG = 0x0018;

    /**
     * key record RFC 2535 and RFC 2930 Used only for SIG(0) (RFC 2931) and TKEY
     * (RFC 2930). RFC 3445 eliminated their use for application keys and
     * limited their use to DNSSEC. RFC 3755 designates DNSKEY as the
     * replacement within DNSSEC. RFC 4025 designates IPSECKEY as the
     * replacement for use with IPsec.
     */
    public static final int TYPE_KEY = 0x0019;

    /**
     * IPv6 address record RFC 3596 Returns a 128-bit IPv6 address, most
     * commonly used to map hostnames to an IP address of the host.
     */
    public static final int TYPE_AAAA = 0x001c;

    /**
     * Location record RFC 1876 Specifies a geographical location associated
     * with a domain name.
     */
    public static final int TYPE_LOC = 0x001d;

    /**
     * Service locator RFC 2782 Generalized service location record, used for
     * newer protocols instead of creating protocol-specific records such as MX.
     */
    public static final int TYPE_SRV = 0x0021;

    /**
     * Naming Authority Pointer record RFC 3403 Allows regular expression based
     * rewriting of domain names which can then be used as URIs, further domain
     * names to lookups, etc.
     */
    public static final int TYPE_NAPTR = 0x0023;

    /**
     * Key eXchanger record RFC 2230 Used with some cryptographic systems (not
     * including DNSSEC) to identify a key management agent for the associated
     * domain-name. Note that this has nothing to do with DNS Security. It is
     * Informational status, rather than being on the IETF standards-track. It
     * has always had limited deployment, but is still in use.
     */
    public static final int TYPE_KX = 0x0024;

    /**
     * Certificate record RFC 4398 Stores PKIX, SPKI, PGP, etc.
     */
    public static final int TYPE_CERT = 0x0025;

    /**
     * Delegation name record RFC 2672 DNAME creates an alias for a name and all
     * its subnames, unlike CNAME, which aliases only the exact name in its
     * label. Like the CNAME record, the DNS lookup will continue by retrying
     * the lookup with the new name.
     */
    public static final int TYPE_DNAME = 0x0027;

    /**
     * Option record RFC 2671 This is a pseudo DNS record type needed to support
     * EDNS.
     */
    public static final int TYPE_OPT = 0x0029;

    /**
     * Address Prefix List record RFC 3123 Specify lists of address ranges, e.g.
     * in CIDR format, for various address families. Experimental.
     */
    public static final int TYPE_APL = 0x002a;

    /**
     * Delegation signer record RFC 4034 The record used to identify the DNSSEC
     * signing key of a delegated zone.
     */
    public static final int TYPE_DS = 0x002b;

    /**
     * SSH Public Key Fingerprint record RFC 4255 Resource record for publishing
     * SSH public host key fingerprints in the DNS System, in order to aid in
     * verifying the authenticity of the host. RFC 6594 defines ECC SSH keys and
     * SHA-256 hashes. See the IANA SSHFP RR parameters registry for details.
     */
    public static final int TYPE_SSHFP = 0x002c;

    /**
     * IPsec Key record RFC 4025 Key record that can be used with IPsec.
     */
    public static final int TYPE_IPSECKEY = 0x002d;

    /**
     * DNSSEC signature record RFC 4034 Signature for a DNSSEC-secured record
     * set. Uses the same format as the SIG record.
     */
    public static final int TYPE_RRSIG = 0x002e;

    /**
     * Next-Secure record RFC 4034 Part of DNSSEC, used to prove a name does not
     * exist. Uses the same format as the (obsolete) NXT record.
     */
    public static final int TYPE_NSEC = 0x002f;

    /**
     * DNS Key record RFC 4034 The key record used in DNSSEC. Uses the same
     * format as the KEY record.
     */
    public static final int TYPE_DNSKEY = 0x0030;

    /**
     * DHCP identifier record RFC 4701 Used in conjunction with the FQDN option
     * to DHCP.
     */
    public static final int TYPE_DHCID = 0x0031;

    /**
     * NSEC record version 3 RFC 5155 An extension to DNSSEC that allows proof
     * of nonexistence for a name without permitting zonewalking.
     */
    public static final int TYPE_NSEC3 = 0x0032;

    /**
     * NSEC3 parameters record RFC 5155 Parameter record for use with NSEC3.
     */
    public static final int TYPE_NSEC3PARAM = 0x0033;

    /**
     * TLSA certificate association record RFC 6698 A record for DNS-based
     * Authentication of Named Entities (DANE). RFC 6698 defines The TLSA DNS
     * resource record is used to associate a TLS server certificate or public
     * key with the domain name where the record is found, thus forming a 'TLSA
     * certificate association'.
     */
    public static final int TYPE_TLSA = 0x0034;

    /**
     * Host Identity Protocol record RFC 5205 Method of separating the end-point
     * identifier and locator roles of IP addresses.
     */
    public static final int TYPE_HIP = 0x0037;

    /**
     * Sender Policy Framework record RFC 4408 Specified as part of the SPF
     * protocol as an alternative to of storing SPF data in TXT records. Uses
     * the same format as the earlier TXT record.
     */
    public static final int TYPE_SPF = 0x0063;

    /**
     * Secret key record RFC 2930 A method of providing keying material to be
     * used with TSIG that is encrypted under the public key in an accompanying
     * KEY RR..
     */
    public static final int TYPE_TKEY = 0x00f9;

    /**
     * Transaction Signature record RFC 2845 Can be used to authenticate dynamic
     * updates as coming from an approved client, or to authenticate responses
     * as coming from an approved recursive name server similar to DNSSEC.
     */
    public static final int TYPE_TSIG = 0x00fa;

    /**
     * Incremental Zone Transfer record RFC 1996 Requests a zone transfer of the
     * given zone but only differences from a previous serial number. This
     * request may be ignored and a full (AXFR) sent in response if the
     * authoritative server is unable to fulfill the request due to
     * configuration or lack of required deltas.
     */
    public static final int TYPE_IXFR = 0x00fb;

    /**
     * Authoritative Zone Transfer record RFC 1035 Transfer entire zone file
     * from the master name server to secondary name servers.
     */
    public static final int TYPE_AXFR = 0x00fc;

    /**
     * All cached records RFC 1035 Returns all records of all types known to the
     * name server. If the name server does not have any information on the
     * name, the request will be forwarded on. The records returned may not be
     * complete. For example, if there is both an A and an MX for a name, but
     * the name server has only the A record cached, only the A record will be
     * returned. Sometimes referred to as ANY, for example in Windows nslookup
     * and Wireshark.
     */
    public static final int TYPE_ANY = 0x00ff;

    /**
     * Certification Authority Authorization record RFC 6844 CA pinning,
     * constraining acceptable CAs for a host/domain.
     */
    public static final int TYPE_CAA = 0x0101;

    /**
     * DNSSEC Trust Authorities record N/A Part of a deployment proposal for
     * DNSSEC without a signed DNS root. See the IANA database and Weiler Spec
     * for details. Uses the same format as the DS record.
     */
    public static final int TYPE_TA = 0x8000;

    /**
     * DNSSEC Lookaside Validation record RFC 4431 For publishing DNSSEC trust
     * anchors outside of the DNS delegation chain. Uses the same format as the
     * DS record. RFC 5074 describes a way of using these records.
     */
    public static final int TYPE_DLV = 0x8001;

    /**
     * Default class for DNS entries.
     */
    public static final int CLASS_IN = 0x0001;

    public static final int CLASS_CSNET = 0x0002;
    public static final int CLASS_CHAOS = 0x0003;
    public static final int CLASS_HESIOD = 0x0004;
    public static final int CLASS_NONE = 0x00fe;
    public static final int CLASS_ANY = 0x00ff;

    private final String name;
    private final int type;
    private final int dnsClass;

    // only allow to extend from same package
    DnsEntry(String name, int type, int dnsClass) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be left blank.");
        }
        if ((type & 0xffff) != type) {
            throw new IllegalArgumentException("type must be an unsigned short.");
        }
        if (dnsClass < 1 || dnsClass > 4 && dnsClass != CLASS_NONE && dnsClass != CLASS_ANY) {
            throw new IllegalArgumentException("an invalid class has been supplied.");
        }
        this.name = name;
        this.type = type;
        this.dnsClass = dnsClass;
    }

    /**
     * Returns the name of this entry (the domain).
     */
    public String name() {
        return name;
    }

    /**
     * Returns the type of resource record to be received.
     */
    public int type() {
        return type;
    }

    /**
     * Returns the class for this entry. Default is IN (Internet).
     */
    public int dnsClass() {
        return dnsClass;
    }

    @Override
    public int hashCode() {
        return (name.hashCode() * 31 + type) * 31 + dnsClass;
    }

    @Override
    public String toString() {
        return new StringBuilder(32).append(getClass().getSimpleName()).append("(domain name: ").append(name)
                .append(", type: ").append(type).append(", class: ").append(dnsClass).append(')').toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof DnsEntry) {
            DnsEntry other = (DnsEntry) o;
            return other.type() == type && other.dnsClass() == dnsClass && other.name().equals(name);
        }
        return false;
    }
}
