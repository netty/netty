package io.netty.handler.codec.dns;

/**
 * The message super-class. Contains core information concerning DNS packets, both outgoing and incoming.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public abstract class Message {

	protected Header header;
	private int questionCount;
	private int answerCount;
	private int authorityCount;
	private int additionalResourceCount;

	private Question[] questions = new Question[1];
	private Resource[] answers = new Resource[1];
	private Resource[] authorityRecords = new Resource[1];
	private Resource[] additional = new Resource[1];

	protected Message() {
		
	}

	/**
	 * Returns the header belonging to this message. If the message is a {@link Query}, this is a
	 * {@link QueryHeader}. If the message is a {@link Response}, this is a {@link ResponseQuery}.
	 */
	public Header getHeader() {
		return header;
	}

	/**
	 * Returns the number of questions in this message.
	 */
	public int getQuestionCount() {
		return header.getQuestionCount();
	}

	/**
	 * Returns the number of answer resource records in the {@link Message}.
	 */
	public int getAnswerCount() {
		return header.getAnswerCount();
	}

	/**
	 * Returns the number of authority resource records in the {@link Message}.
	 */
	public int getAuthorityResourceCount() {
		return header.getAuthorityResourceCount();
	}

	/**
	 * Returns the number of additional resource records in the {@link Message}.
	 */
	public int getAdditionalResourceCount() {
		return header.getAdditionalResourceCount();
	}

	/**
	 * Returns all the questions in this message.
	 */
	public Question[] getQuestions() {
		Question[] temp = new Question[questionCount];
		System.arraycopy(questions, 0, temp, 0, temp.length);
		return temp;
	}

	/**
	 * Returns all the answer resource records in this message.
	 */
	public Resource[] getAnswers() {
		Resource[] temp = new Resource[answerCount];
		if (answerCount > 0) {
			System.arraycopy(answers, 0, temp, 0, temp.length);
		}
		return temp;
	}

	/**
	 * Returns all the authority resource records in this message.
	 */
	public Resource[] getAuthorityResources() {
		Resource[] temp = new Resource[authorityCount];
		if (authorityCount > 0) {
			System.arraycopy(authorityRecords, 0, temp, 0, temp.length);
		}
		return temp;
	}

	/**
	 * Returns all the additional resource records in this message.
	 */
	public Resource[] getAdditionalResources() {
		Resource[] temp = new Resource[additionalResourceCount];
		if (additionalResourceCount > 0) {
			System.arraycopy(additional, 0, temp, 0, temp.length);
		}
		return temp;
	}

	/**
	 * Adds an answer resource record to this message.
	 * 
	 * @param answer The answer resource record to be added.
	 */
	public void addAnswer(Resource answer) {
		if (answerCount == answers.length) {
			Resource[] temp = new Resource[answers.length * 2];
			System.arraycopy(answers, 0, temp, 0, answerCount);
			answers = temp;
		}
		answers[answerCount++] = answer;
	}

	/**
	 * Adds a question to this message.
	 * 
	 * @param question The question to be added.
	 */
	public void addQuestion(Question question) {
		if (questionCount == questions.length) {
			Question[] temp = new Question[questions.length * 2];
			System.arraycopy(questions, 0, temp, 0, questionCount);
			questions = temp;
		}
		questions[questionCount++] = question;
	}

	/**
	 * Adds an authority resource record to this message.
	 * 
	 * @param authority The authority resource record to be added.
	 */
	public void addAuthorityResource(Resource authority) {
		if (authorityCount == authorityRecords.length) {
			Resource[] temp = new Resource[authorityRecords.length * 2];
			System.arraycopy(authorityRecords, 0, temp, 0, authorityCount);
			authorityRecords = temp;
		}
		authorityRecords[authorityCount++] = authority;
	}

	/**
	 * Adds an additional resource record to this message.
	 * 
	 * @param resource The additional resource record to be added.
	 */
	public void addAdditionalResource(Resource resource) {
		if (additionalResourceCount == additional.length) {
			Resource[] temp = new Resource[additional.length * 2];
			System.arraycopy(additional, 0, temp, 0, additionalResourceCount);
			additional = temp;
		}
		additional[additionalResourceCount++] = resource;
	}

	/**
	 * Sets this message's {@link Header}.
	 * 
	 * @param header The header being attached to this message.
	 */
	public void setHeader(Header header) {
		this.header = header;
	}

}
