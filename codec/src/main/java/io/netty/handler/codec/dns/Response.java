package bakkar.mohamed.dnscodec;

public class Response extends Message {

	public static final Response decode(byte[] data) throws ResponseException {
		int pos = 0;
		int id = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
		Response response = new Response(id);
		((ResponseHeader) response.getHeader()).decode(data);
		pos += 10;
		Question question = Question.decode(data, pos); // We assume there is always 1 question.
		response.addQuestion(question);
		pos += question.getSize();
		for (int i = 0; i < response.getAnswerCount(); i++) {
			int name = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			int type = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			int aClass = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			long ttl = ((data[pos++] & 0xffl) << 24l) | ((data[pos++] & 0xffl) << 16l) | ((data[pos++] & 0xffl) << 8l) | data[pos++] & 0xffl;
			int len = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			byte[] resourceData = new byte[len];
			System.arraycopy(data, pos, resourceData, 0, len);
			pos+= len;
			response.addAnswer(new Resource(name, type, aClass, ttl, resourceData));
		}
		for (int i = 0; i < response.getNameServerResourceCount(); i++) {
			int name = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			int type = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			int aClass = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			long ttl = ((data[pos++] & 0xffl) << 24l) | ((data[pos++] & 0xffl) << 16l) | ((data[pos++] & 0xffl) << 8l) | data[pos++] & 0xffl;
			int len = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			byte[] resourceData = new byte[len];
			System.arraycopy(data, pos, resourceData, 0, len);
			pos+= len;
			response.addNameServer(new Resource(name, type, aClass, ttl, resourceData));
		}
		for (int i = 0; i < response.getAdditionalResourceCount(); i++) {
			int name = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			int type = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			int aClass = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			long ttl = ((data[pos++] & 0xffl) << 24l) | ((data[pos++] & 0xffl) << 16l) | ((data[pos++] & 0xffl) << 8l) | data[pos++] & 0xffl;
			int len = ((data[pos++] & 0xff) << 8) | data[pos++] & 0xff;
			byte[] resourceData = new byte[len];
			System.arraycopy(data, pos, resourceData, 0, len);
			pos+= len;
			response.addAdditionalResource(new Resource(name, type, aClass, ttl, resourceData));
		}
		return response;
	}

	private Response(int id) {
		header = new ResponseHeader(this, id);
	}

}
