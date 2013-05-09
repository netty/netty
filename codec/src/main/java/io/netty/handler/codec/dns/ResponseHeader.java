package bakkar.mohamed.dnscodec;

public class ResponseHeader extends Header {

	private boolean authoritativeAnswer;
	private boolean truncated;
	private boolean recursionAvailable;

	private int z;
	private int responseCode;

	public ResponseHeader(Message parent, int id) {
		super(parent, id);
	}

	public boolean isAuthoritativeAnswer() {
		return authoritativeAnswer;
	}

	public boolean isTruncated() {
		return truncated;
	}

	public boolean isRecursionAvailable() {
		return recursionAvailable;
	}

	public int getZ() {
		return z;
	}

	public int getResponseCode() {
		return responseCode;
	}

	public void decode(byte[] data) throws ResponseException {
		int flags = ((data[2] & 0xff) << 8) | data[3] & 0xff;
		int type = flags >> 15;
		int opcode = (flags >> 11) & 0xf;

		setOpcode(opcode);
		setType(type);
		setRecursionDesired(((flags >> 8) & 1) == 1);

		authoritativeAnswer = ((flags >> 10) & 1) == 1;
		truncated = ((flags >> 9) & 1) == 1;
		recursionAvailable = ((flags >> 7) & 1) == 1;
		z = (flags >> 4) & 0x7;
		responseCode = flags & 0xf;
		if (responseCode != 0)
			throw new ResponseException(responseCode);
		questionCount = ((data[4] & 0xff) << 8) | data[5] & 0xff;
		answerCount = ((data[6] & 0xff) << 8) | data[7] & 0xff;
		nameServerCount = ((data[8] & 0xff) << 8) | data[9] & 0xff;
		additionalResourceCount = ((data[10] & 0xff) << 8) | data[11] & 0xff;
	}

}
