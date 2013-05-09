package bakkar.mohamed.dnscodec;

public class Header {

	public static final int TYPE_QUERY = 0;
	public static final int TYPE_RESPONSE = 1;

	public static final int OPCODE_QUERY = 0;
	@Deprecated
	public static final int OPCODE_IQUERY = 1;
	public static final int OPCODE_STATUS = 2;

	private boolean recursionDesired;
	private int opcode;
	private int id;
	private int type;
	protected Message parent;
	protected int questionCount;
	protected int answerCount;
	protected int nameServerCount;
	protected int additionalResourceCount;

	public Header(Message parent, int id) {
		this.parent = parent;
		this.id = id;
	}

	public boolean isRecursionDesired() {
		return recursionDesired;
	}

	public int getQuestionCount() {
		return questionCount;
	}

	public int getAnswerCount() {
		return answerCount;
	}

	public int getNameServerResourceCount() {
		return nameServerCount;
	}

	public int getAdditionalResourceCount() {
		return additionalResourceCount;
	}

	public int getId() {
		return id;
	}

	public int getOpcode() {
		return opcode;
	}

	public int getType() {
		return type;
	}

	public Message getParent() {
		return parent;
	}

	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}

	public void setRecursionDesired(boolean recursionDesired) {
		this.recursionDesired = recursionDesired;
	}

	public void setType(int type) {
		this.type = type;
	}

}
