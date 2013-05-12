/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

/**
 * The message super-class. Contains core information concerning DNS packets, both outgoing and incoming.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public abstract class Message {

	private Header header;
	private Question[] questions = new Question[0];
	private Resource[] answers = new Resource[0];
	private Resource[] authority = new Resource[0];
	private Resource[] additional = new Resource[0];

	protected Message() {

	}

	/**
	 * @Return The header belonging to this message. If the message is a {@link Query}, this is a
	 * {@link QueryHeader}. If the message is a {@link Response}, this is a {@link ResponseQuery}.
	 */
	public Header getHeader() {
		return header;
	}

	/**
	 * @return All the questions in this message.
	 */
	public Question[] getQuestions() {
		return questions.clone();
	}

	/**
	 * @return All the answer resource records in this message.
	 */
	public Resource[] getAnswers() {
		return answers.clone();
	}

	/**
	 * @return All the authority resource records in this message.
	 */
	public Resource[] getAuthorityResources() {
		return authority.clone();
	}

	/**
	 * @return All the additional resource records in this message.
	 */
	public Resource[] getAdditionalResources() {
		return additional.clone();
	}

	// The following add methods build a new array each time. This is fine
	// because a Message will almost never have more than 1 question, and
	// in Response messages, the number of answers will usually be small
	// (1 in many cases). The get methods are likely to be called with a
	// higher frequency, and they are quicker as a result of these add methods.

	/**
	 * Adds an answer resource record to this message.
	 * 
	 * @param answer The answer resource record to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message addAnswer(Resource answer) {
		int pos = answers.length;
		Resource[] temp = new Resource[pos + 1];
		System.arraycopy(answers, 0, temp, 0, answers.length);
		answers = temp;
		answers[pos] = answer;
		return this;
	}

	/**
	 * Adds a question to this message.
	 * 
	 * @param question The question to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message addQuestion(Question question) {
		int pos = questions.length;
		Question[] temp = new Question[pos + 1];
		System.arraycopy(questions, 0, temp, 0, questions.length);
		questions = temp;
		questions[pos] = question;
		return this;
	}

	/**
	 * Adds an authority resource record to this message.
	 * 
	 * @param authority The authority resource record to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message addAuthorityResource(Resource resource) {
		int pos = authority.length;
		Resource[] temp = new Resource[pos + 1];
		System.arraycopy(authority, 0, temp, 0, authority.length);
		authority = temp;
		authority[pos] = resource;
		return this;
	}

	/**
	 * Adds an additional resource record to this message.
	 * 
	 * @param resource The additional resource record to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message addAdditionalResource(Resource resource) {
		int pos = additional.length;
		Resource[] temp = new Resource[pos + 1];
		System.arraycopy(additional, 0, temp, 0, additional.length);
		additional = temp;
		additional[pos] = resource;
		return this;
	}

	/**
	 * Sets this message's {@link Header}.
	 * 
	 * @param header The header being attached to this message.
	 * @return Returns the message to allow method chaining.
	 */
	public Message setHeader(Header header) {
		this.header = header;
		return this;
	}

}
