/*
 * Copyright 2013 The Netty Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The message super-class. Contains core information concerning DNS packets, both outgoing and incoming.
 */
public abstract class Message<H extends Header> {

	private H header;
	private List<Question> questions = new ArrayList<Question>();
	private List<Resource> answers = new ArrayList<Resource>();
	private List<Resource> authority = new ArrayList<Resource>();
	private List<Resource> additional = new ArrayList<Resource>();

	protected Message() {

	}

	/**
	 * @Return The header belonging to this message. If the message is a {@link Query}, this should be
	 * {@link QueryHeader}. If the message is a {@link Response}, this should be {@link ResponseQuery}.
	 */
	public H getHeader() {
		return header;
	}

	/**
	 * @return A list of all the questions in this message.
	 */
	public List<Question> getQuestions() {
		return Collections.unmodifiableList(questions);
	}

	/**
	 * @return A list of all the answer resource records in this message.
	 */
	public List<Resource> getAnswers() {
		return Collections.unmodifiableList(answers);
	}

	/**
	 * @return A list of all the authority resource records in this message.
	 */
	public List<Resource> getAuthorityResources() {
		return Collections.unmodifiableList(authority);
	}

	/**
	 * @return A list of all the additional resource records in this message.
	 */
	public List<Resource> getAdditionalResources() {
		return Collections.unmodifiableList(additional);
	}

	/**
	 * Adds an answer resource record to this message.
	 * 
	 * @param answer The answer resource record to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message<H> addAnswer(Resource answer) {
		answers.add(answer);
		return this;
	}

	/**
	 * Adds a question to this message.
	 * 
	 * @param question The question to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message<H> addQuestion(Question question) {
		questions.add(question);
		return this;
	}

	/**
	 * Adds an authority resource record to this message.
	 * 
	 * @param authority The authority resource record to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message<H> addAuthorityResource(Resource resource) {
		authority.add(resource);
		return this;
	}

	/**
	 * Adds an additional resource record to this message.
	 * 
	 * @param resource The additional resource record to be added.
	 * @return Returns the message to allow method chaining.
	 */
	public Message<H> addAdditionalResource(Resource resource) {
		additional.add(resource);
		return this;
	}

	/**
	 * Sets this message's {@link Header}.
	 * 
	 * @param header The header being attached to this message.
	 * @return Returns the message to allow method chaining.
	 */
	public Message<H> setHeader(H header) {
		this.header = header;
		return this;
	}

}
