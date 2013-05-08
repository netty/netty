package bakkar.mohamed.dnscodec;

public class Response extends Message {

	public static final Response decode(byte[] data) {
		int id = ((data[0] & 0xff) << 8) | data[1] & 0xff;
		Response response = new Response(id);
		((ResponseHeader) response.getHeader()).decode(data);
		return response;
	}

	private Response(int id) {
		header = new ResponseHeader(this, id);
	}

}
