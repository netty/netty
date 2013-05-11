package io.netty.handler.codec.dns;

/**
 * The header super-class. Includes information shared by DNS query and response packet headers
 * such as the ID, opcode, and type. The only flag shared by both classes is the flag for
 * desiring recursion.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Header {

	/**
	 * Message type is query.
	 */
	public static final int TYPE_QUERY = 0;
	/**
	 * Message type is response.
	 */
	public static final int TYPE_RESPONSE = 1;

	/**
	 * Message is for a standard query.
	 */
	public static final int OPCODE_QUERY = 0;
	/**
	 * Message is for an inverse query. <strong>Note: inverse queries have been obsoleted since RFC 3425,
	 * and are not necessarily supported.</strong>
	 */
	@Deprecated
	public static final int OPCODE_IQUERY = 1;

	private boolean recursionDesired;
	private int opcode;
	private int id;
	private int type;
	protected Message parent;
	protected int questionCount;
	protected int answerCount;
	protected int authorityCount;
	protected int additionalResourceCount;

	/**
	 * Constructor for a DNS packet header. The id is the id
	 * sent to the server by the client.
	 * 
	 * @param parent The {@link Message} this header belongs to.
	 * @param id A 2 bit unsigned identification number for this header.
	 */
	public Header(Message parent, int id) {
		this.parent = parent;
		this.id = id;
	}

	/**
	 * Returns true if a query is to be pursued recursively.
	 */
	public boolean isRecursionDesired() {
		return recursionDesired;
	}

	/**
	 * Returns the number of questions in the {@link Message}.
	 */
	public int getQuestionCount() {
		return questionCount;
	}

	/**
	 * Returns the number of answer resource records in the {@link Message}.
	 */
	public int getAnswerCount() {
		return answerCount;
	}

	/**
	 * Returns the number of authority resource records in the {@link Message}.
	 */
	public int getAuthorityResourceCount() {
		return authorityCount;
	}

	/**
	 * Returns the number of additional resource records in the {@link Message}.
	 */
	public int getAdditionalResourceCount() {
		return additionalResourceCount;
	}

	/**
	 * Returns the 2 bit unsigned identifier number used for the {@link Message}.
	 */
	public int getId() {
		return id;
	}

	/**
	 * Returns the 4 bit opcode used for the {@link Message}.
	 */
	public int getOpcode() {
		return opcode;
	}

	/**
	 * Returns the type of {@link Message}.
	 * 
	 * @see #TYPE_QUERY
	 * @see #TYPE_HEADER
	 */
	public int getType() {
		return type;
	}

	/**
	 * Returns the {@link Message} this header belongs to.
	 */
	public Message getParent() {
		return parent;
	}

	/**
	 * Sets the opcode for this {@link Message}.
	 * 
	 * @param opcode Opcode to set.
	 * @see #OPCODE_QUERY
	 * @see #OPCODE_IQUERY
	 * @see #OPCODE_STATUS
	 */
	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}

	/**
	 * Sets whether a name server is directed to pursue a query recursively or not.
	 * 
	 * @param recursionDesired If set to true, pursues query recursively.
	 */
	public void setRecursionDesired(boolean recursionDesired) {
		this.recursionDesired = recursionDesired;
	}

	/**
	 * Sets the {@link Message} type.
	 * 
	 * @param type Message type.
	 * @see #TYPE_QUERY
	 * @see #TYPE_RESPONSE
	 */
	public void setType(int type) {
		this.type = type;
	}

}
