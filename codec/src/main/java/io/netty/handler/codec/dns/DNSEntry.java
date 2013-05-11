package io.netty.handler.codec.dns;

/**
 * A class representing entries in a DNS packet (questions, and all resource records). Has
 * utility methods for reading domain names and sizing them. Additionally, contains data shared
 * by entries such as name, type, and class.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
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
	 * Retrieves a domain name given a buffer and an offset.
	 * 
	 * @param data The byte array containing a DNS packet.
	 * @param pos The position at which the domain name is located.
	 * @return  Returns the domain name for an entry.
	 */
	public static String getName(byte[] data, int pos) {
		StringBuilder name = new StringBuilder();
		for (int len = data[pos++] & 0xff; pos < data.length && len != 0; len = data[pos++] & 0xff) {
			boolean pointer = (len & 0xc0) == 0xc0;
			if (pointer) {
				pos = ((data[pos - 1] << 8) | data[pos]) & 0x3fff;
			} else {
				name.append(new String(data, pos, len)).append(".");
				pos += len;
			}
		}
		return name.substring(0, name.length() - 1);
	}

	/**
	 * Gives the size in bytes of a domain name, when sent in a
	 * DNS packet. Does not work with pointers.
	 * 
	 * @param name The domain name.
	 * @return The size in bytes of the name.
	 */
	public static int getSize(String name) {
		int extra = name.indexOf(".") > 0 ? 1 : 0;
		return name.length() + extra + 1;
	}

	/**
	 * Gets the size of a domain name in bytes, excluding data read from
	 * pointers. A pointer is indicated by the length of the next label having
	 * the flag 0xC0 (the two most significant bits are 1 and 1). This
	 * will get the size in bytes of the data preceding the pointer + 2 (2 is the
	 * size of a pointer).
	 * 
	 * Example:
	 * 
	 * 07 101 120 97 109 112 108 101 -48 12
	 * 
	 * In this byte sequence, the beginning byte indicates a label of length 7.
	 * The following 7 characters read "example", a sub-domain of the main domain.
	 * The next label length is -64 which has the flag C0 (1100 0000 binary). The
	 * 14 bits directly after this flag (01 0000 0000 1100) indicate the position
	 * in the packet where you should continue reading to obtain the full domain
	 * name. In this case, 4108 bytes into the packet (which is unrealistic, since
	 * usually DNS packets are limited to 512 bytes, but this is just an example).
	 * This method will only include the bytes preceding the pointer data in
	 * calculating size, so this example will return a size of 10 bytes.
	 * 
	 * @param data The byte array containing a DNS packet.
	 * @param pos The position at which the domain name is located.
	 * @return  Returns the size in bytes of a domain name, excluding data read from pointers.
	 */
	public static int getTrueSize(byte[] data, int pos) {
		int start = pos;
		for (int len = data[pos++] & 0xff; pos < data.length && len != 0; len = data[pos++] & 0xff) {
			if ((len & 0xc0) == 0xc0) {
				pos++;
				break;
			}
			pos += len;
		}
		return pos - start;
	}

	protected String name;
	protected int type;
	protected int dnsClass;

	public DNSEntry(String name, int type, int dnsClass) {
		this.name = name;
		this.type = type;
		this.dnsClass = dnsClass;
	}

	/**
	 * Returns the name of this entry (the domain).
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the type of resource record to be returned.
	 */
	public int getType() {
		return type;
	}

	/**
	 * Returns the class for this entry. Default is IN (Internet).
	 */
	public int getDNSClass() {
		return dnsClass;
	}

}
