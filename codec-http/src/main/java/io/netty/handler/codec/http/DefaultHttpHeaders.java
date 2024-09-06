/*
 * Copyright 2012 The Netty Project
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

import io.netty.handler.codec.CharSequenceValueConverter;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.DefaultHeaders.NameValidator;
import io.netty.handler.codec.DefaultHeaders.ValueValidator;
import io.netty.handler.codec.DefaultHeadersImpl;
import io.netty.handler.codec.Headers;
import io.netty.handler.codec.HeadersUtils;
import io.netty.handler.codec.ValueConverter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static io.netty.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty.util.AsciiString.CASE_SENSITIVE_HASHER;

/**
 * Default implementation of {@link HttpHeaders}.
 */
public class DefaultHttpHeaders extends HttpHeaders {
    private final DefaultHeaders<CharSequence, CharSequence, ?> headers;

    /**
     * Create a new, empty HTTP headers object.
     * <p>
     * Header names and values are validated as they are added, to ensure they are compliant with the HTTP protocol.
     */
    public DefaultHttpHeaders() {
        this(nameValidator(true), valueValidator(true));
    }

    /**
     * <b>Warning!</b> Setting {@code validate} to {@code false} will mean that Netty won't
     * validate & protect against user-supplied header values that are malicious.
     * This can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * When disabling this validation, it is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validate Should Netty validate header values to ensure they aren't malicious.
     * @deprecated Prefer using the {@link #DefaultHttpHeaders()} constructor instead,
     * to always have validation enabled.
     */
    @Deprecated
    public DefaultHttpHeaders(boolean validate) {
        this(nameValidator(validate), valueValidator(validate));
    }

    /**
     * Create an HTTP headers object with the given name validator.
     * <p>
     * <b>Warning!</b> It is strongly recommended that the name validator implement validation that is at least as
     * strict as {@link HttpHeaderValidationUtil#validateToken(CharSequence)}.
     * It is also strongly recommended that {@code validateValues} is enabled.
     * <p>
     * Without these validations in place, your code can be susceptible to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * It is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param validateValues Should Netty validate header values to ensure they aren't malicious.
     * @param nameValidator The {@link NameValidator} to use, never {@code null.
     */
    protected DefaultHttpHeaders(boolean validateValues, NameValidator<CharSequence> nameValidator) {
        this(nameValidator, valueValidator(validateValues));
    }

    /**
     * Create an HTTP headers object with the given name and value validators.
     * <p>
     * <b>Warning!</b> It is strongly recommended that the name validator implement validation that is at least as
     * strict as {@link HttpHeaderValidationUtil#validateToken(CharSequence)}.
     * And that the value validator is at least as strict as
     * {@link HttpHeaderValidationUtil#validateValidHeaderValue(CharSequence)}.
     * <p>
     * Without these validations in place, your code can be susceptible to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * It is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param nameValidator The {@link NameValidator} to use, never {@code null}.
     * @param valueValidator The {@link ValueValidator} to use, never {@code null}.
     */
    protected DefaultHttpHeaders(
            NameValidator<CharSequence> nameValidator,
            ValueValidator<CharSequence> valueValidator) {
        this(nameValidator, valueValidator, 16);
    }

    /**
     * Create an HTTP headers object with the given name and value validators.
     * <p>
     * <b>Warning!</b> It is strongly recommended that the name validator implement validation that is at least as
     * strict as {@link HttpHeaderValidationUtil#validateToken(CharSequence)}.
     * And that the value validator is at least as strict as
     * {@link HttpHeaderValidationUtil#validateValidHeaderValue(CharSequence)}.
     * <p>
     * Without these validations in place, your code can be susceptible to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">
     *     CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')
     * </a>.
     * It is the responsibility of the caller to ensure that the values supplied
     * do not contain a non-url-escaped carriage return (CR) and/or line feed (LF) characters.
     *
     * @param nameValidator The {@link NameValidator} to use, never {@code null}.
     * @param valueValidator The {@link ValueValidator} to use, never {@code null}.
     * @param sizeHint A hint about the anticipated number of entries.
     */
    protected DefaultHttpHeaders(
            NameValidator<CharSequence> nameValidator,
            ValueValidator<CharSequence> valueValidator,
            int sizeHint) {
        this(new DefaultHeadersImpl<CharSequence, CharSequence>(
                CASE_INSENSITIVE_HASHER,
                HeaderValueConverter.INSTANCE,
                nameValidator,
                sizeHint,
                valueValidator));
    }

    protected DefaultHttpHeaders(DefaultHeaders<CharSequence, CharSequence, ?> headers) {
        this.headers = headers;
    }

    public Headers<CharSequence, CharSequence, ?> unwrap() {
        return headers;
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        if (headers instanceof DefaultHttpHeaders) {
            this.headers.add(((DefaultHttpHeaders) headers).headers);
            return this;
        } else {
            return super.add(headers);
        }
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        if (headers instanceof DefaultHttpHeaders) {
            this.headers.set(((DefaultHttpHeaders) headers).headers);
            return this;
        } else {
            return super.set(headers);
        }
    }

    @Override
    public HttpHeaders add(String name, Object value) {
        headers.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        headers.addObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        headers.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        headers.addObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        headers.addInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        headers.addShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders remove(String name) {
        headers.remove(name);
        return this;
    }

    @Override
    public HttpHeaders remove(CharSequence name) {
        headers.remove(name);
        return this;
    }

    @Override
    public HttpHeaders set(String name, Object value) {
        headers.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        headers.setObject(name, value);
        return this;
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        headers.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        headers.setObject(name, values);
        return this;
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        headers.setInt(name, value);
        return this;
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        headers.setShort(name, value);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        headers.clear();
        return this;
    }

    @Override
    public String get(String name) {
        return get((CharSequence) name);
    }

    @Override
    public String get(CharSequence name) {
        return HeadersUtils.getAsString(headers, name);
    }

    @Override
    public Integer getInt(CharSequence name) {
        return headers.getInt(name);
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        return headers.getInt(name, defaultValue);
    }

    @Override
    public Short getShort(CharSequence name) {
        return headers.getShort(name);
    }

    @Override
    public short getShort(CharSequence name, short defaultValue) {
        return headers.getShort(name, defaultValue);
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        return headers.getTimeMillis(name);
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        return headers.getTimeMillis(name, defaultValue);
    }

    @Override
    public List<String> getAll(String name) {
        return getAll((CharSequence) name);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return HeadersUtils.getAllAsString(headers, name);
    }

    @Override
    public List<Entry<String, String>> entries() {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        List<Entry<String, String>> entriesConverted = new ArrayList<Entry<String, String>>(
                headers.size());
        for (Entry<String, String> entry : this) {
            entriesConverted.add(entry);
        }
        return entriesConverted;
    }

    @Deprecated
    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return HeadersUtils.iteratorAsString(headers);
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> iteratorCharSequence() {
        return headers.iterator();
    }

    @Override
    public Iterator<String> valueStringIterator(CharSequence name) {
        final Iterator<CharSequence> itr = valueCharSequenceIterator(name);
        return new Iterator<String>() {
            @Override
            public boolean hasNext() {
                return itr.hasNext();
            }

            @Override
            public String next() {
                return itr.next().toString();
            }

            @Override
            public void remove() {
                itr.remove();
            }
        };
    }

    @Override
    public Iterator<CharSequence> valueCharSequenceIterator(CharSequence name) {
        return headers.valueIterator(name);
    }

    @Override
    public boolean contains(String name) {
        return contains((CharSequence) name);
    }

    @Override
    public boolean contains(CharSequence name) {
        return headers.contains(name);
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean contains(String name, String value, boolean ignoreCase) {
        return contains((CharSequence) name, (CharSequence) value, ignoreCase);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return headers.contains(name, value, ignoreCase ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER);
    }

    @Override
    public Set<String> names() {
        return HeadersUtils.namesAsString(headers);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DefaultHttpHeaders
                && headers.equals(((DefaultHttpHeaders) o).headers, CASE_SENSITIVE_HASHER);
    }

    @Override
    public int hashCode() {
        return headers.hashCode(CASE_SENSITIVE_HASHER);
    }

    @Override
    public HttpHeaders copy() {
        return new DefaultHttpHeaders(headers.copy());
    }

    static ValueConverter<CharSequence> valueConverter() {
        return HeaderValueConverter.INSTANCE;
    }

    static ValueValidator<CharSequence> valueValidator(boolean validate) {
        return validate ? DefaultHttpHeadersFactory.headersFactory().getValueValidator() :
                DefaultHttpHeadersFactory.headersFactory().withValidation(false).getValueValidator();
    }

    static NameValidator<CharSequence> nameValidator(boolean validate) {
        return validate ? DefaultHttpHeadersFactory.headersFactory().getNameValidator() :
                DefaultHttpHeadersFactory.headersFactory().withNameValidation(false).getNameValidator();
    }

    private static class HeaderValueConverter extends CharSequenceValueConverter {
        static final HeaderValueConverter INSTANCE = new HeaderValueConverter();

        @Override
        public CharSequence convertObject(Object value) {
            if (value instanceof CharSequence) {
                return (CharSequence) value;
            }
            if (value instanceof Date) {
                return DateFormatter.format((Date) value);
            }
            if (value instanceof Calendar) {
                return DateFormatter.format(((Calendar) value).getTime());
            }
            return value.toString();
        }
    }
}
