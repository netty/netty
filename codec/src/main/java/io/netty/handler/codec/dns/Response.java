package io.netty.handler.codec.dns;

/**
 * A DNS response packet. Sent to a client after server received a query.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Response extends Message {

	/**
	 * Decodes a DNS response packet from a raw data buffer.
	 * 
	 * @param data The DNS packet buffer.
	 * @return Returns a response object containing packet's information.
	 * @throws ResponseException
	 */
	public static Response decode(byte[] data) throws ResponseException {
		int pos = 0;
		int id = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		Response response = new Response(id);
		((ResponseHeader) response.getHeader()).decode(data);
		pos += 10; // Header, excluding the 2 bit id, is 10 bytes
		Question question = Question.decode(data, pos); // We assume there is always 1 question.
		response.addQuestion(question);
		pos += question.getSize();
		for (int i = 0; i < response.getAnswerCount(); i++) {
			Resource resource = getResource(data, pos);
			pos += resource.getSize(data, pos);
			response.addAnswer(resource);
		}
		for (int i = 0; i < response.getAuthorityResourceCount(); i++) {
			Resource resource = getResource(data, pos);
			pos += resource.getSize(data, pos);
			response.addAuthorityResource(resource);
		}
		for (int i = 0; i < response.getAdditionalResourceCount(); i++) {
			Resource resource = getResource(data, pos);
			pos += resource.getSize(data, pos);
			response.addAdditionalResource(resource);
		}
		return response;
	}

	/**
	 * Decodes a resource record, given a DNS packet buffer and the position at
	 * which the resource record begins.
	 * 
	 * @param data The DNS packet buffer.
	 * @param pos The position at which the resource record begins in the packet.
	 * @return Returns a resource record.
	 */
	public static Resource getResource(byte[] data, int pos) {
		String name = DNSEntry.getName(data, pos);
		pos += DNSEntry.getTrueSize(data, pos);
		int type = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		int aClass = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		long ttl = ((data[pos++] & 0xffl) << 24l) | ((data[pos++] & 0xffl) << 16l) | ((data[pos++] & 0xffl) << 8l) | data[pos++] & 0xffl;
		int len = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		byte[] resourceData = new byte[len];
		System.arraycopy(data, pos, resourceData, 0, len);
		return new Resource(name, type, aClass, ttl, resourceData);
	}

	private Response(int id) {
		header = new ResponseHeader(this, id);
	}

}
