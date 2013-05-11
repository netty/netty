package io.netty.handler.codec.dns;

/**
 * The DNS response header class. Used when receiving data from a DNS server.
 * Contains information contained in a DNS response header, such as recursion
 * availability, and response codes.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class ResponseHeader extends Header {

	private boolean authoritativeAnswer;
	private boolean truncated;
	private boolean recursionAvailable;

	private int z;
	private int responseCode;

	/**
	 * Constructor for a DNS packet response header. The id is the id
	 * sent to the server by the client.
	 * 
	 * @param parent The {@link Message} this header belongs to.
	 * @param id A 2 bit unsigned identification number for this response.
	 */
	public ResponseHeader(Message parent, int id) {
		super(parent, id);
	}

	/**
	 * Returns true when responding server is authoritative for the domain name in the query message.
	 */
	public boolean isAuthoritativeAnswer() {
		return authoritativeAnswer;
	}

	/**
	 * Returns true if response has been truncated, usually if it is over 512 bytes.
	 */
	public boolean isTruncated() {
		return truncated;
	}

	/**
	 * Returns true if DNS can handle recursive queries.
	 */
	public boolean isRecursionAvailable() {
		return recursionAvailable;
	}

	/**
	 * 3 bit reserved field.
	 */
	public int getZ() {
		return z;
	}

	/**
	 * 4 bit return code for query message. Response codes outlined in {@link ReturnCode}.
	 */
	public int getResponseCode() {
		return responseCode;
	}

	/**
	 * Decodes data from a response packet received from a DNS. A DNS packet
	 * begins with the ID, and the response header begins at the 3rd byte.
	 * This method begins reading from the 3rd byte, so the data should not
	 * have any offset.
	 * 
	 * @param data DNS response packet read from server, no offset.
	 */
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
		authorityCount = ((data[8] & 0xff) << 8) | data[9] & 0xff;
		additionalResourceCount = ((data[10] & 0xff) << 8) | data[11] & 0xff;
	}

}
