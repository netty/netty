package io.netty.handler.codec.dns;

/**
 * The DNS question class. Represents a question being sent to a server via a
 * query, or the question being duplicated and sent back in a response. Usually
 * a message contains a single question, and DNS servers often don't support
 * multiple questions in a single query.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Question extends DNSEntry {

	/**
	 * Decodes a question, given a DNS packet. A proper position must be indicated.
	 * In a normal DNS packet, the question will begin at an offset of 12 bytes (a header
	 * is usually 12 bytes in size, and the question follows directly after the header).
	 * 
	 * @param data The DNS packet, encoded in a byte array, or data containing a question.
	 * @param pos The position at which the question begins.
	 * @return Returns a new Question object given an encoded DNS packet.
	 */
	public static Question decode(byte[] data, int pos) {
		String name = getName(data, pos);
		pos += getSize(name);
		int type = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		int qClass = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		return new Question(name, type, qClass);
	}

	/**
	 * Constructs a question with the default class IN (Internet).
	 * 
	 * @param name The domain name being queried i.e. "www.example.com"
	 * @param type The question type, which represents the type of {@link Resource} record that should be returned.
	 */
	public Question(String name, int type) {
		this(name, type, CLASS_IN);
	}

	/**
	 * Constructs a question with the given class.
	 * 
	 * @param name The domain name being queried i.e. "www.example.com"
	 * @param type The question type, which represents the type of {@link Resource} record that should be returned.
	 * @param qClass The class of a DNS record.
	 */
	public Question(String name, int type, int qClass) {
		super(name, type, qClass);
	}

	/**
	 * Returns the size, in bytes, of this question.
	 */
	public int getSize() {
		return getSize(name) + 4;
	}

	/**
	 * Encodes the question by writing it to a variable length
	 * byte array.
	 * 
	 * @return A variable length byte array with the question encoded.
	 */
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
		data[pos++] = (byte) (dnsClass >> 8);
		data[pos++] = (byte) dnsClass;
		return data;
	}

}
