/*
 * Copyright 2023 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.handler.codec.DefaultHeaders.NameValidator;
import io.netty.handler.codec.DefaultHeaders.ValueValidator;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * A builder of {@link HttpHeadersFactory} instances, that itself implements {@link HttpHeadersFactory}.
 * The builder is immutable, and every {@code with-} method produce a new, modified instance.
 * <p>
 * The default builder you most likely want to start with is {@link HttpHeaders#DEFAULT_HEADER_FACTORY}.
 */
public class HttpHeadersBuilder implements HttpHeadersFactory {

    private static final NameValidator<CharSequence> DEFAULT_NAME_VALIDATOR =
            DefaultHttpHeaders.nameValidator(true);
    private static final ValueValidator<CharSequence> DEFAULT_VALUE_VALIDATOR =
            DefaultHttpHeaders.valueValidator(true);
    private static final NameValidator<CharSequence> DEFAULT_TRAILER_NAME_VALIDATOR =
            new NameValidator<CharSequence>() {
                @Override
                public void validateName(CharSequence name) {
                    DefaultHttpHeaders.HttpNameValidator.validateName(name);
                    if (HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(name)
                            || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(name)
                            || HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(name)) {
                        throw new IllegalArgumentException("prohibited trailing header: " + name);
                    }
                }
            };

    private static final NameValidator<CharSequence> NO_NAME_VALIDATOR =
            DefaultHttpHeaders.nameValidator(false);
    private static final ValueValidator<CharSequence> NO_VALUE_VALIDATOR =
            DefaultHttpHeaders.valueValidator(false);

    static final HttpHeadersBuilder DEFAULT = new HttpHeadersBuilder();
    static final HttpHeadersBuilder DEFAULT_TRAILER =
            new HttpHeadersBuilder(DEFAULT_TRAILER_NAME_VALIDATOR, DEFAULT_VALUE_VALIDATOR, false);
    static final HttpHeadersBuilder DEFAULT_COMBINING =
            new HttpHeadersBuilder(DEFAULT.nameValidator, DEFAULT.valueValidator, true);
    static final HttpHeadersBuilder DEFAULT_NO_VALIDATION =
            new HttpHeadersBuilder(NO_NAME_VALIDATOR, NO_VALUE_VALIDATOR, false);

    private final NameValidator<CharSequence> nameValidator;
    private final ValueValidator<CharSequence> valueValidator;
    private final boolean combiningHeaders;

    /**
     * Create a header builder with the default settings.
     */
    protected HttpHeadersBuilder() {
        this(DEFAULT_NAME_VALIDATOR, DEFAULT_VALUE_VALIDATOR, false);
    }

    /**
     * Create a header builder with the given settings.
     *
     * @param nameValidator The name validator to use, not null.
     * @param valueValidator The value validator to use, not null.
     * @param combiningHeaders {@code true} if multi-valued headers should be combined into single lines.
     */
    protected HttpHeadersBuilder(
            NameValidator<CharSequence> nameValidator,
            ValueValidator<CharSequence> valueValidator,
            boolean combiningHeaders) {
        this.nameValidator = checkNotNull(nameValidator, "nameValidator");
        this.valueValidator = checkNotNull(valueValidator, "valueValidator");
        this.combiningHeaders = combiningHeaders;
    }

    private boolean notSubclassed() {
        return getClass() == HttpHeadersBuilder.class;
    }

    /**
     * Create a new header builder instance with the given overrides.
     * This method is used by all other {@code with-} methods, for creating new builder instances.
     *
     * @param nameValidator The name validator to use, not null.
     * @param valueValidator The value validator to use, not null.
     * @param combiningHeaders {@code true} if multi-valued headers should be combined into single lines.
     * @return The new header builder instance.
     */
    protected HttpHeadersBuilder with(
            NameValidator<CharSequence> nameValidator,
            ValueValidator<CharSequence> valueValidator,
            boolean combiningHeaders) {
        return new HttpHeadersBuilder(nameValidator, valueValidator, combiningHeaders);
    }

    @Override
    public HttpHeaders createHeaders() {
        if (isCombiningHeaders()) {
            return new CombinedHttpHeaders(getNameValidator(), getValueValidator());
        }
        return new DefaultHttpHeaders(getNameValidator(), getValueValidator());
    }

    /**
     * Create a new builder that has HTTP header name validation enabled or disabled.
     * <p>
     * <b>Warning!</b> Setting {@code validation} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validation If validation should be enabled or disabled.
     * @return The new builder.
     */
    public HttpHeadersBuilder withNameValidation(boolean validation) {
        return withNameValidator(validation ? DEFAULT_NAME_VALIDATOR : NO_NAME_VALIDATOR);
    }

    /**
     * Create a new builder that with the given {@link NameValidator}.
     * <p>
     * <b>Warning!</b> If the given validator does not check that the header names are standards compliant, Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validator The HTTP header name validator to use.
     * @return The new builder.
     */
    public HttpHeadersBuilder withNameValidator(NameValidator<CharSequence> validator) {
        if (nameValidator == checkNotNull(validator, "validator")) {
            return this;
        }
        if (notSubclassed() && validator == DEFAULT_NAME_VALIDATOR && valueValidator == DEFAULT_VALUE_VALIDATOR) {
            return combiningHeaders ? DEFAULT_COMBINING : DEFAULT;
        }
        return with(validator, valueValidator, combiningHeaders);
    }

    /**
     * Create a new builder that has HTTP header value validation enabled or disabled.
     * <p>
     * <b>Warning!</b> Setting {@code validation} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validation If validation should be enabled or disabled.
     * @return The new builder.
     */
    public HttpHeadersBuilder withValueValidation(boolean validation) {
        return withValueValidator(validation ? DEFAULT_VALUE_VALIDATOR : NO_VALUE_VALIDATOR);
    }

    /**
     * Create a new builder that with the given {@link ValueValidator}.
     * <p>
     * <b>Warning!</b> If the given validator does not check that the header values are standards compliant, Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validator The HTTP header name validator to use.
     * @return The new builder.
     */
    public HttpHeadersBuilder withValueValidator(ValueValidator<CharSequence> validator) {
        if (valueValidator == checkNotNull(validator, "validator")) {
            return this;
        }
        if (notSubclassed() && nameValidator == DEFAULT_NAME_VALIDATOR && validator == DEFAULT_VALUE_VALIDATOR) {
            return combiningHeaders ? DEFAULT_COMBINING : DEFAULT;
        }
        return with(nameValidator, validator, combiningHeaders);
    }

    /**
     * Create a new builder that has HTTP header validation enabled or disabled.
     * <p>
     * <b>Warning!</b> Setting {@code validation} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied headers that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validation If validation should be enabled or disabled.
     * @return The new builder.
     */
    public HttpHeadersBuilder withValidation(boolean validation) {
        if (notSubclassed()) {
            return validation ? DEFAULT : DEFAULT_NO_VALIDATION;
        }
        return withNameValidation(false).withValueValidation(false);
    }

    /**
     * Create a new builder that will build {@link HttpHeaders} objects that either combine
     * multi-valued headers, or not.
     *
     * @param combiningHeaders {@code true} if multi-valued headers should be combined, otherwise {@code false}.
     * @return The new builder.
     */
    public HttpHeadersBuilder withCombiningHeaders(boolean combiningHeaders) {
        if (this.combiningHeaders == combiningHeaders) {
            return this;
        }
        return with(nameValidator, valueValidator, combiningHeaders);
    }

    /**
     * Get the currently configured {@link NameValidator}.
     * <p>
     * This method will be used by the {@link #createHeaders()} method.
     *
     * @return The configured name validator.
     */
    public NameValidator<CharSequence> getNameValidator() {
        return nameValidator;
    }

    /**
     * Get the currently configured {@link ValueValidator}.
     * <p>
     * This method will be used by the {@link #createHeaders()} method.
     *
     * @return The configured value validator.
     */
    public ValueValidator<CharSequence> getValueValidator() {
        return valueValidator;
    }

    /**
     * Check whether header combining is enabled or not.
     *
     * @return {@code true} if header value combining is enabled, otherwise {@code false}.
     */
    public boolean isCombiningHeaders() {
        return combiningHeaders;
    }

    /**
     * Check whether header name validation is enabled.
     *
     * @return {@code true} if header name validation is enabled, otherwise {@code false}.
     */
    public boolean isValidatingHeaderNames() {
        return nameValidator != NO_NAME_VALIDATOR;
    }

    /**
     * Check whether header value validation is enabled.
     *
     * @return {@code true} if header value validation is enabled, otherwise {@code false}.
     */
    public boolean isValidatingHeaderValues() {
        return valueValidator != NO_VALUE_VALIDATOR;
    }
}
