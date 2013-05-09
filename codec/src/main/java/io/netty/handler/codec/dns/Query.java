package bakkar.mohamed.dnscodec;

public class Query extends Message {

	public Query(int id, Question question) {
		header = new QueryHeader(this, id);
		addQuestion(question);
	}

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
