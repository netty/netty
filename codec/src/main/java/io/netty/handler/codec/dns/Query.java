package io.netty.handler.codec.dns;

/**
 * A DNS query packet. Sent to a server to receive a DNS response packet with information
 * answering a query's questions.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Query extends Message {

	/**
	 * Constructs a DNS query without a question. By default the opcode
	 * will be 0 (standard query) and recursion will be toggled on.
	 */
	public Query(int id) {
		header = new QueryHeader(this, id);
	}

	/**
	 * Constructs a DNS query with a question. By default the opcode will
	 * be 0 (standard query) and recursion will be toggled on.
	 */
	public Query(int id, Question question) {
		header = new QueryHeader(this, id);
		addQuestion(question);
	}

	/**
	 * Encodes a query to a byte array of variable length. This can be sent to
	 * a DNS server, and a response will be sent from the server.
	 * 
	 * @return Returns a byte array of this query, encoded.
	 */
	public byte[] encode() {
		byte[] header = ((QueryHeader) getHeader()).encode();
		Question[] questions = getQuestions();
		int len = header.length;
		for (int i = 0; i < questions.length; i++) {
			len += questions[i].getSize();
		}
		byte[] data = new byte[len];
		System.arraycopy(header, 0, data, 0, header.length);
		int pos = header.length;
		for (int i = 0; i < questions.length; i++) {
			byte[] encodedQuestion = questions[i].encode();
			System.arraycopy(encodedQuestion, 0, data, pos, encodedQuestion.length);
			pos += encodedQuestion.length;
		}
		return data;
	}

}
