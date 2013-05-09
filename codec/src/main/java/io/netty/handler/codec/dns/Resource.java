package bakkar.mohamed.dnscodec;

public class Resource {

	public static final int A = 0x0001;
	public static final int NS = 0x0002;
	public static final int MD = 0x0003;
	public static final int MF = 0x0004;
	public static final int CNAME = 0x0005;
	public static final int SOA = 0x0006;
	public static final int MB = 0x0007;
	public static final int MG = 0x0008;
	public static final int MR = 0x0009;
	public static final int NULL = 0x000a;
	public static final int WKS = 0x000b;
	public static final int PTR = 0x000c;
	public static final int HINFO = 0x000d;
	public static final int MINFO = 0x000e;
	public static final int MX = 0x000f;
	public static final int TXT = 0x0010;
	public static final int RP = 0x0011;
	public static final int AFSDB = 0x0012;
	public static final int X25 = 0x0013;
	public static final int ISDN = 0x0014;
	public static final int RT = 0x0015;
	public static final int NSAP = 0x0016;
	public static final int NSAPPTR = 0x0017;
	public static final int SIG = 0x0018;
	public static final int KEY = 0x0019;
	public static final int PX = 0x001a;
	public static final int GPOS = 0x001b;
	public static final int AAAA = 0x001c;
	public static final int LOC = 0x001d;
	public static final int NXT = 0x001e;
	public static final int EID = 0x001f;
	public static final int NIMLOC = 0x0020;
	public static final int SRV = 0x0021;
	public static final int ATMA = 0x0022;
	public static final int NAPTR = 0x0023;
	public static final int KX = 0x0024;
	public static final int CERT = 0x0025;
	public static final int A6 = 0x0026;
	public static final int DNAME = 0x0027;
	public static final int SINK = 0x0028;
	public static final int OPT = 0x0029;
	public static final int DS = 0x002B;
	public static final int RRSIG = 0x002E;
	public static final int NSEC = 0x002F;
	public static final int DNSKEY = 0x0030;
	public static final int DHCID = 0x0031;
	public static final int UINFO = 0x0064;
	public static final int UID = 0x0065;
	public static final int GID = 0x0066;
	public static final int UNSPEC = 0x0067;
	public static final int ADDRS = 0x00f8;
	public static final int TKEY = 0x00f9;
	public static final int TSIG = 0x00fa;
	public static final int IXFR = 0x00fb;
	public static final int AXFR = 0x00fc;
	public static final int MAILB = 0x00fd;
	public static final int MAILA = 0x00fe;
	public static final int ANY = 0x00ff;
	public static final int WINS = 0xff01;
	public static final int WINSR = 0xff02;

	private int name;
	private int type;
	private int aClass;
	private long ttl; // The time to live is actually a 4 byte integer, but since it's unsigned
					  // we should store it as long to be properly expressed in Java
	private byte[] data;
	
	public Resource(int name, int type, int aClass, long ttl, byte[] data) {
		this.name = name;
		this.type = type;
		this.aClass = aClass;
		this.ttl = ttl;
		this.data = data;
	}

	public int getName() {
		return name;
	}

	public int getType() {
		return type;
	}

	public int getAnswerClass() {
		return aClass;
	}

	public long getTimeToLive() {
		return ttl;
	}

	public int getDataLength() {
		return data.length;
	}

	public byte[] getData() {
		return data.clone();
	}

}
