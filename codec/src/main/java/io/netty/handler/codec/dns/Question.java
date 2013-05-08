package bakkar.mohamed.dnscodec;

public class Question {

	public static final int A = 1;
	public static final int NS = 2;
	public static final int CNAME = 5;
	public static final int PTR = 12;
	public static final int MX = 15;
	public static final int SRV = 33;
	public static final int IXFR = 251;
	public static final int AXFR = 252;
	public static final int ALL = 255;

	private int type;
	private String[] labels;

	public Question(int type, String... labels) {
		this.labels = labels;
		this.type = type;
	}

	public int getSize() {
		int nameSize = 1;
		for (int i = 0; i < labels.length; i++) {
			nameSize += labels[i].length() + 1;
		}
		int size = nameSize + 4;
		return size;
	}

	public byte[] encode() {
		byte[] data = new byte[getSize()];
		int pos = 0;
		for (int i = 0; i < labels.length; i++) {
			data[pos++] = (byte) labels[i].length();
			byte[] chars = labels[i].getBytes();
			System.arraycopy(chars, 0, data, pos, chars.length);
			pos += chars.length;
		}
		pos++;
		data[pos++] = (byte) (type >> 8);
		data[pos++] = (byte) type;
		pos++;
		data[pos++] = 1;
		return data;
	}

}
