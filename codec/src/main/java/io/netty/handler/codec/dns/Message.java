package bakkar.mohamed.dnscodec;

public abstract class Message {

	protected Header header;
	private int questionCount = 0;
	private Question[] questions = new Question[1];

	public Header getHeader() {
		return header;
	}

	public int getQuestionCount() {
		return questionCount;
	}

	public Question[] getQuestions() {
		Question[] temp = new Question[questionCount];
		System.arraycopy(questions, 0, temp, 0, temp.length);
		return temp;
	}

	public void addQuestion(Question question) {
		if (questionCount == questions.length) {
			Question[] temp = new Question[questionCount * 2];
			System.arraycopy(questions, 0, temp, 0, questionCount);
			questions = temp;
		}
		questions[questionCount++] = question;
	}

	public void setHeader(Header header) {
		this.header = header;
	}

}
