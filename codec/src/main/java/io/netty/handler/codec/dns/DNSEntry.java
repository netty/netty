/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

/**
 * A class representing entries in a DNS packet (questions, and all resource records). Has
 * utility methods for reading domain names and sizing them. Additionally, contains data shared
 * by entries such as name, type, and class.
 */
public class DNSEntry {

	/**
	 * Address record.
	 */
	public static final int TYPE_A = 0x0001;

	/**
	 * Name server record.
	 */
	public static final int TYPE_NS = 0x0002;

	/**
	 * Canonical name record.
	 */
	public static final int TYPE_CNAME = 0x0005;

	/**
	 * Start of [a zone of] authority record.
	 */
	public static final int TYPE_SOA = 0x0006;

	/**
	 * Pointer record.
	 */
	public static final int TYPE_PTR = 0x000c;

	/**
	 * Mail exchange record.
	 */
	public static final int TYPE_MX = 0x000f;

	/**
	 * Text record.
	 */
	public static final int TYPE_TXT = 0x0010;

	/**
	 * Responsible person.
	 */
	public static final int TYPE_RP = 0x0011;

	/**
	 * AFS database record.
	 */
	public static final int TYPE_AFSDB = 0x0012;

	/**
	 * Signature.
	 */
	public static final int TYPE_SIG = 0x0018;

	/**
	 * Key record.
	 */
	public static final int TYPE_KEY = 0x0019;

	/**
	 * IPv6 address record.
	 */
	public static final int TYPE_AAAA = 0x001c;

	/**
	 * Location record.
	 */
	public static final int TYPE_LOC = 0x001d;

	/**
	 * Service locator.
	 */
	public static final int TYPE_SRV = 0x0021;

	/**
	 * Naming authority pointer.
	 */
	public static final int TYPE_NAPTR = 0x0023;

	/**
	 * Key exchanger protocol.
	 */
	public static final int TYPE_KX = 0x0024;

	/**
	 * Certificate record.
	 */
	public static final int TYPE_CERT = 0x0025;

	/**
	 * Delegation name.
	 */
	public static final int TYPE_DNAME = 0x0027;

	/**
	 * Address prefix list.
	 */
	public static final int TYPE_APL = 0x002A;

	/**
	 * Delegation signer.
	 */
	public static final int TYPE_DS = 0x002B;

	/**
	 * SSH public key fingerprint.
	 */
	public static final int TYPE_SSHFP = 0x002C;

	/**
	 * IPsec key.
	 */
	public static final int TYPE_IPSECKEY = 0x002D;

	/**
	 * DNSSEC signature.
	 */
	public static final int TYPE_RRSIG = 0x002E;

	/**
	 * Next-secure record.
	 */
	public static final int TYPE_NSEC = 0x002F;

	/**
	 * DNS key record.
	 */
	public static final int TYPE_DNSKEY = 0x0030;

	/**
	 * DHCP identifier.
	 */
	public static final int TYPE_DHCID = 0x0031;

	/**
	 * NSEC record version 3.
	 */
	public static final int TYPE_NSEC3 = 0x0032;

	/**
	 * NSEC3 parameters.
	 */
	public static final int TYPE_NSEC3PARAM = 0x0033;

	/**
	 * TLSA certificate association.
	 */
	public static final int TYPE_TLSA = 0x0034;

	/**
	 * Host identity protocol.
	 */
	public static final int TYPE_HIP = 0x0037;

	/**
	 * Sender policy framework.
	 */
	public static final int TYPE_SPF = 0x0063;

	/**
	 * Secret key record.
	 */
	public static final int TYPE_TKEY = 0x00f9;

	/**
	 * Transaction signature.
	 */
	public static final int TYPE_TSIG = 0x00fa;

	/**
	 * Certification authority authorization.
	 */
	public static final int TYPE_CAA = 0x0101;

	/**
	 * All cached records.
	 */
	public static final int TYPE_ANY = 0x00ff;

	/**
	 * Default class for DNS entries.
	 */
	public static final int CLASS_IN = 0x0001;

	public static final int CLASS_CSNET = 0x0002;
	public static final int CLASS_CHAOS = 0x0003;
	public static final int CLASS_HESIOD = 0x0004;
	public static final int CLASS_NONE = 0x00fe;
	public static final int CLASS_ALL = 0x00ff;
	public static final int CLASS_ANY = 0x00ff;

	/**
	 * Retrieves a domain name given a buffer containing a DNS packet.
	 * If the name contains a pointer, the position of the buffer will be
	 * set to directly after the pointer's index after the name has been
	 * read.
	 * 
	 * @param buf The byte buffer containing the DNS packet.
	 * @return The domain name for an entry.
	 */
	public static String readName(ByteBuf buf) {
		int position = -1;
		StringBuilder name = new StringBuilder();
		for (int len = buf.readUnsignedByte(); buf.isReadable() && len != 0; len = buf.readUnsignedByte()) {
			boolean pointer = (len & 0xc0) == 0xc0;
			if (pointer) {
				if (position == -1) {
					position = buf.readerIndex() + 1;
				}
				buf.readerIndex((len & 0x3f) << 8 | buf.readUnsignedByte());
			} else {
				name.append(buf.toString(buf.readerIndex(), len, Charset.forName("UTF-8"))).append(".");
				buf.skipBytes(len);
			}
		}
		if (position != -1) {
			buf.readerIndex(position);
		}
		return name.substring(0, name.length() - 1);
	}

	/**
	 * Retrieves a domain name given a buffer containing a DNS packet
	 * without advancing the readerIndex for the buffer.
	 * 
	 * @param buf The byte buffer containing the DNS packet.
	 * @param offset The position at which the name begins.
	 * @return The domain name for an entry.
	 */
	public static String getName(ByteBuf buf, int offset) {
		StringBuilder name = new StringBuilder();
		for (int len = buf.getUnsignedByte(offset++); buf.writerIndex() > offset && len != 0; len = buf.getUnsignedByte(offset++)) {
			boolean pointer = (len & 0xc0) == 0xc0;
			if (pointer) {
				offset = (len & 0x3f) << 8 | buf.getUnsignedByte(offset++);
			} else {
				name.append(buf.toString(offset, len, Charset.forName("UTF-8"))).append(".");
				offset += len;
			}
		}
		return name.substring(0, name.length() - 1);
	}

	protected final String name;
	protected final int type;
	protected final int dnsClass;

	public DNSEntry(String name, int type, int dnsClass) {
		this.name = name;
		this.type = type;
		this.dnsClass = dnsClass;
	}

	/**
	 * @return The name of this entry (the domain).
	 */
	public String name() {
		return name;
	}

	/**
	 * @return The type of resource record to be returned.
	 */
	public int type() {
		return type;
	}

	/**
	 * @return The class for this entry. Default is IN (Internet).
	 */
	public int dnsClass() {
		return dnsClass;
	}

}
