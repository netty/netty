/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.dns;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The message super-class which contains core information concerning DNS
 * packets, both outgoing and incoming.
 */
public abstract class DnsMessage<H extends DnsHeader> {

    private List<DnsQuestion> questions;
    private List<DnsResource> answers;
    private List<DnsResource> authority;
    private List<DnsResource> additional;

    private H header;

    // Only allow to extend from same package
    DnsMessage() { }

    /**
     * Returns the header belonging to this message.
     */
    public H getHeader() {
        return header;
    }

    /**
     * Returns a list of all the questions in this message.
     */
    public List<DnsQuestion> getQuestions() {
        if (questions == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(questions);
    }

    /**
     * Returns a list of all the answer resource records in this message.
     */
    public List<DnsResource> getAnswers() {
        if (answers == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(answers);
    }

    /**
     * Returns a list of all the authority resource records in this message.
     */
    public List<DnsResource> getAuthorityResources() {
        if (authority == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(authority);
    }

    /**
     * Returns a list of all the additional resource records in this message.
     */
    public List<DnsResource> getAdditionalResources() {
        if (additional == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(additional);
    }

    /**
     * Adds an answer resource record to this message.
     *
     * @param answer
     *            the answer resource record to be added
     * @return the message to allow method chaining
     */
    public DnsMessage<H> addAnswer(DnsResource answer) {
        if (answers == null) {
            answers = new LinkedList<DnsResource>();
        }
        answers.add(answer);
        return this;
    }

    /**
     * Adds a question to this message.
     *
     * @param question
     *            the question to be added
     * @return the message to allow method chaining
     */
    public DnsMessage<H> addQuestion(DnsQuestion question) {
        if (questions == null) {
            questions = new LinkedList<DnsQuestion>();
        }
        questions.add(question);
        return this;
    }

    /**
     * Adds an authority resource record to this message.
     *
     * @param resource
     *            the authority resource record to be added
     * @return the message to allow method chaining
     */
    public DnsMessage<H> addAuthorityResource(DnsResource resource) {
        if (authority == null) {
            authority = new LinkedList<DnsResource>();
        }
        authority.add(resource);
        return this;
    }

    /**
     * Adds an additional resource record to this message.
     *
     * @param resource
     *            the additional resource record to be added
     * @return the message to allow method chaining
     */
    public DnsMessage<H> addAdditionalResource(DnsResource resource) {
        if (additional == null) {
            additional = new LinkedList<DnsResource>();
        }
        additional.add(resource);
        return this;
    }

    /**
     * Sets this message's {@link DnsHeader}.
     *
     * @param header
     *            the header being attached to this message
     * @return the message to allow method chaining
     */
    public DnsMessage<H> setHeader(H header) {
        this.header = header;
        return this;
    }
}
