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

import io.netty.util.AbstractReferenceCounted;
import io.netty.util.ReferenceCountUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The message super-class which contains core information concerning DNS
 * packets, both outgoing and incoming.
 */
public abstract class DnsMessage<H extends DnsHeader> extends AbstractReferenceCounted {

    private List<DnsQuestion> questions;
    private List<DnsResource> answers;
    private List<DnsResource> authority;
    private List<DnsResource> additional;

    private final H header;

    // Only allow to extend from same package
    DnsMessage(int id) {
        header = newHeader(id);
    }

    /**
     * Returns the header belonging to this message.
     */
    public H header() {
        return header;
    }

    /**
     * Returns a list of all the questions in this message.
     */
    public List<DnsQuestion> questions() {
        if (questions == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(questions);
    }

    /**
     * Returns a list of all the answer resource records in this message.
     */
    public List<DnsResource> answers() {
        if (answers == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(answers);
    }

    /**
     * Returns a list of all the authority resource records in this message.
     */
    public List<DnsResource> authorityResources() {
        if (authority == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(authority);
    }

    /**
     * Returns a list of all the additional resource records in this message.
     */
    public List<DnsResource> additionalResources() {
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

    @Override
    protected void deallocate() {
        // NOOP
    }

    @Override
    public boolean release() {
        release(questions());
        release(answers());
        release(additionalResources());
        release(authorityResources());
        return super.release();
    }

    private static void release(List<?> resources) {
        for (Object resource: resources) {
            ReferenceCountUtil.release(resource);
        }
    }

    @Override
    public boolean release(int decrement) {
        release(questions(), decrement);
        release(answers(), decrement);
        release(additionalResources(), decrement);
        release(authorityResources(), decrement);
        return super.release(decrement);
    }

    private static void release(List<?> resources, int decrement) {
        for (Object resource: resources) {
            ReferenceCountUtil.release(resource, decrement);
        }
    }

    @Override
    public DnsMessage<H> touch(Object hint) {
        touch(questions(), hint);
        touch(answers(), hint);
        touch(additionalResources(), hint);
        touch(authorityResources(), hint);
        return this;
    }

    private static void touch(List<?> resources, Object hint) {
        for (Object resource: resources) {
            ReferenceCountUtil.touch(resource, hint);
        }
    }

    @Override
    public DnsMessage<H> retain() {
        retain(questions());
        retain(answers());
        retain(additionalResources());
        retain(authorityResources());
        super.retain();
        return this;
    }

    private static void retain(List<?> resources) {
        for (Object resource: resources) {
            ReferenceCountUtil.retain(resource);
        }
    }

    @Override
    public DnsMessage<H> retain(int increment) {
        retain(questions(), increment);
        retain(answers(), increment);
        retain(additionalResources(), increment);
        retain(authorityResources(), increment);
        super.retain(increment);
        return this;
    }

    private static void retain(List<?> resources, int increment) {
        for (Object resource: resources) {
            ReferenceCountUtil.retain(resource, increment);
        }
    }

    @Override
    public DnsMessage<H> touch() {
        super.touch();
        return this;
    }

    protected abstract H newHeader(int id);
}
