package bakkar.mohamed.dnscodec;


public class Question {

	public static final int CLASS_IN = 1;

	private int type;
	private int qClass;
	private String name;

	public static Question decode(byte[] data, int off) {
		int pos = off;
		StringBuilder name = new StringBuilder();
		for (int len = data[pos++] & 0xff; pos < data.length && len != 0; len = data[pos++] & 0xff) {
			name.append(new String(data, pos, len)).append(".");
			pos += len;
		}
		int type = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		int qClass = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		return new Question(type, qClass, name.substring(0, name.length() - 1));
	}

	public Question(int type, String name) {
		this(type, CLASS_IN, name);
	}

	public Question(int type, int qClass, String name) {
		this.name = name;
		this.type = type;
		this.qClass = qClass;
	}

	public int getSize() {
		int extra = name.indexOf(".") > 0 ? 1 : 0;
		return name.length() + extra + 5;
	}

	public String getName() {
		return name;
	}

	public int getType() {
		return type;
	}

	public int getQuestionClass() {
		return qClass;
	}

	public byte[] encode() {
		byte[] data = new byte[getSize()];
		int pos = 0;
		String[] parts = name.split("\\.");
		for (int i = 0; i < parts.length; i++) {
			data[pos++] = (byte) parts[i].length();
			byte[] chars = parts[i].getBytes();
			System.arraycopy(chars, 0, data, pos, chars.length);
			pos += chars.length;
		}
		pos++;
		data[pos++] = (byte) (type >> 8);
		data[pos++] = (byte) type;
		data[pos++] = (byte) (qClass >> 8);
		data[pos++] = (byte) qClass;
		return data;
	}

}
