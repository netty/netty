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
 * The header super-class. Includes information shared by DNS query and response packet headers
 * such as the ID, opcode, and type. The only flag shared by both classes is the flag for
 * desiring recursion.
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class Header {

	/**
	 * Message type is query.
	 */
	public static final int TYPE_QUERY = 0;
	/**
	 * Message type is response.
	 */
	public static final int TYPE_RESPONSE = 1;

	/**
	 * Message is for a standard query.
	 */
	public static final int OPCODE_QUERY = 0;
	/**
	 * Message is for an inverse query. <strong>Note: inverse queries have been obsoleted since RFC 3425,
	 * and are not necessarily supported.</strong>
	 */
	@Deprecated
	public static final int OPCODE_IQUERY = 1;

	private Message parent;
	private boolean recursionDesired;
	private int opcode;
	private int id;
	private int type;

	public Header(Message parent) {
		this.parent = parent;
	}

	/**
	 * @return The number of questions in the {@link Message}.
	 */
	public int questionCount() {
		return parent.getQuestions().length;
	}

	/**
	 * @return The number of answer resource records in the {@link Message}.
	 */
	public int answerCount() {
		return parent.getAnswers().length;
	}

	/**
	 * @return The number of authority resource records in the {@link Message}.
	 */
	public int authorityResourceCount() {
		return parent.getAuthorityResources().length;
	}

	/**
	 * @return The number of additional resource records in the {@link Message}.
	 */
	public int additionalResourceCount() {
		return parent.getAdditionalResources().length;
	}

	/**
	 * @return True if a query is to be pursued recursively.
	 */
	public boolean getRecursionDesired() {
		return recursionDesired;
	}

	/**
	 * @return The 4 bit opcode used for the {@link Message}.
	 * @see #OPCODE_QUERY
	 * @see #OPCODE_IQUERY
	 */
	public int getOpcode() {
		return opcode;
	}

	/**
	 * @return The type of {@link Message}.
	 * @see #TYPE_QUERY
	 * @see #TYPE_HEADER
	 */
	public int getType() {
		return type;
	}

	/**
	 * @return The 2 byte unsigned identifier number used for the {@link Message}.
	 */
	public int getId() {
		return id;
	}

	/**
	 * Sets the opcode for this {@link Message}.
	 * 
	 * @param opcode Opcode to set.
	 * @return Returns the header to allow method chaining.
	 */
	public Header setOpcode(int opcode) {
		this.opcode = opcode;
		return this;
	}

	/**
	 * Sets whether a name server is directed to pursue a query recursively or not.
	 * 
	 * @param recursionDesired If set to true, pursues query recursively.
	 * @return Returns the header to allow method chaining.
	 */
	public Header setRecursionDesired(boolean recursionDesired) {
		this.recursionDesired = recursionDesired;
		return this;
	}

	/**
	 * Sets the {@link Message} type.
	 * 
	 * @param type Message type.
	 * @return Returns the header to allow method chaining.
	 */
	public Header setType(int type) {
		this.type = type;
		return this;
	}

	public Header setId(int id) {
		this.id = id;
		return this;
	}

}
