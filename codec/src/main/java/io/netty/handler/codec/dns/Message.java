package bakkar.mohamed.dnscodec;

public abstract class Message {

	protected Header header;
	private int questionCount;
	private int answerCount;
	private int nameServerCount;
	private int additionalResourceCount;

	private Question[] questions = new Question[1];
	private Resource[] answers = new Resource[1];
	private Resource[] nameServers = new Resource[1];
	private Resource[] additional = new Resource[1];

	public Header getHeader() {
		return header;
	}

	public int getQuestionCount() {
		return header.getQuestionCount();
	}

	public int getAnswerCount() {
		return header.getAnswerCount();
	}

	public int getNameServerResourceCount() {
		return header.getNameServerResourceCount();
	}

	public int getAdditionalResourceCount() {
		return header.getAdditionalResourceCount();
	}

	public Question[] getQuestions() {
		Question[] temp = new Question[questionCount];
		System.arraycopy(questions, 0, temp, 0, temp.length);
		return temp;
	}

	public Resource[] getAnswers() {
		Resource[] temp = new Resource[answerCount];
		if (answerCount > 0) {
			System.arraycopy(answers, 0, temp, 0, temp.length);
		}
		return temp;
	}

	public Resource[] getNameServers() {
		Resource[] temp = new Resource[nameServerCount];
		if (nameServerCount > 0) {
			System.arraycopy(nameServers, 0, temp, 0, temp.length);
		}
		return temp;
	}

	public Resource[] getAdditionalResources() {
		Resource[] temp = new Resource[additionalResourceCount];
		if (additionalResourceCount > 0) {
			System.arraycopy(additional, 0, temp, 0, temp.length);
		}
		return temp;
	}

	public void addAnswer(Resource answer) {
		if (answerCount == answers.length) {
			Resource[] temp = new Resource[answers.length * 2];
			System.arraycopy(answers, 0, temp, 0, answerCount);
			answers = temp;
		}
		answers[answerCount++] = answer;
	}

	public void addQuestion(Question question) {
		if (questionCount == questions.length) {
			Question[] temp = new Question[questions.length * 2];
			System.arraycopy(questions, 0, temp, 0, questionCount);
			questions = temp;
		}
		questions[questionCount++] = question;
	}

	public void addNameServer(Resource nameServer) {
		if (nameServerCount == nameServers.length) {
			Resource[] temp = new Resource[nameServers.length * 2];
			System.arraycopy(nameServers, 0, temp, 0, nameServerCount);
			nameServers = temp;
		}
		nameServers[nameServerCount++] = nameServer;
	}

	public void addAdditionalResource(Resource resource) {
		if (additionalResourceCount == additional.length) {
			Resource[] temp = new Resource[additional.length * 2];
			System.arraycopy(additional, 0, temp, 0, additionalResourceCount);
			additional = temp;
		}
		additional[additionalResourceCount++] = resource;
	}

	public void setHeader(Header header) {
		this.header = header;
	}

}
