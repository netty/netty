/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.smtp;

import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.UnstableApi;

import java.util.Collections;
import java.util.List;

/**
 * Default {@link SmtpRequest} implementation.
 */
@UnstableApi
public final class DefaultSmtpRequest implements SmtpRequest {

    private final SmtpCommand command;
    private final List<CharSequence> parameters;

    /**
     * Creates a new instance with the given command and no parameters.
     */
    public DefaultSmtpRequest(SmtpCommand command) {
        this.command = ObjectUtil.checkNotNull(command, "command");
        parameters = Collections.emptyList();
    }

    /**
     * Creates a new instance with the given command and parameters.
     */
    public DefaultSmtpRequest(SmtpCommand command, CharSequence... parameters) {
        this.command = ObjectUtil.checkNotNull(command, "command");
        this.parameters = SmtpUtils.toUnmodifiableList(parameters);
    }

    /**
     * Creates a new instance with the given command and parameters.
     */
    public DefaultSmtpRequest(CharSequence command, CharSequence... parameters) {
        this(SmtpCommand.valueOf(command), parameters);
    }

    DefaultSmtpRequest(SmtpCommand command, List<CharSequence> parameters) {
        this.command = ObjectUtil.checkNotNull(command, "command");
        this.parameters = parameters != null ?
                Collections.unmodifiableList(parameters) : Collections.<CharSequence>emptyList();
    }

    @Override
    public SmtpCommand command() {
        return command;
    }

    @Override
    public List<CharSequence> parameters() {
        return parameters;
    }

    @Override
    public int hashCode() {
        return command.hashCode() * 31 + parameters.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultSmtpRequest)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        DefaultSmtpRequest other = (DefaultSmtpRequest) o;

        return command().equals(other.command()) &&
                parameters().equals(other.parameters());
    }

    @Override
    public String toString() {
        return "DefaultSmtpRequest{" +
                "command=" + command +
                ", parameters=" + parameters +
                '}';
    }
}
