package bakkar.mohamed.dnscodec;

public class QueryHeader extends Header {

	protected QueryHeader(Message parent, int id) {
		super(parent, id);

		// defaults
		setType(Header.TYPE_QUERY);
		setOpcode(Header.OPCODE_QUERY);
		setRecursionDesired(true);
	}

	public byte[] encode() {
		byte[] data = new byte[12];
		data[0] = (byte) (getId() >> 8);
		data[1] = (byte) getId();
		int flags = 0;
		flags |= getType() << 15;
		flags |= getOpcode() << 14;
		boolean truncated = false;
		Question[] questions = getParent().getQuestions();
		int size = 0;
		for (int i = 0; i < questions.length; i++) {
			size += questions[i].getSize();
		}
		if (size + 96 > 512)
			truncated = true;
		flags |= truncated ? (1 << 9) : 0;
		flags |= isRecursionDesired() ? (1 << 8) : 0;
		data[2] = (byte) (flags >> 8);
		data[3] = (byte) (flags & 0xff);
		data[4] = (byte) (questions.length >> 8);
		data[5] = (byte) questions.length;
		// data[6] - data[11] are used in response headers
		return data;
	}

}
