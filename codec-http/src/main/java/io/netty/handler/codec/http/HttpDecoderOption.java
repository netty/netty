/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http;

import io.netty.handler.codec.DecoderResult;
import io.netty.util.AbstractConstant;
import io.netty.util.ConstantPool;
import io.netty.util.internal.ObjectUtil;

import java.util.List;
import java.util.regex.Pattern;

/**
 * {@link HttpDecoderOption}s may be used when configuring a {@link HttpObjectDecoder}. These options are primarily
 * intended for when the HTTP RFC offers numerous options or ambiguous interpretations for certain behavior. Where
 * possible, Netty will offer opinionated defaults for the safest and sanest behavior, but these options may still be
 * leveraged by users desiring more fine-grained control.
 * <p>
 * Some options may only be relevant to specific decoder implementations (e.g., request vs response).
 *
 * @param <T> the type of the value which is valid for the {@link HttpDecoderOption}
 */
public final class HttpDecoderOption<T> extends AbstractConstant<HttpDecoderOption<T>> {

    private static final Pattern COMMA_PATTERN = Pattern.compile(",");

    private static final ConstantPool<HttpDecoderOption<Object>> pool = new ConstantPool<HttpDecoderOption<Object>>() {
        @Override
        protected HttpDecoderOption<Object> newConstant(int id, String name) {
            return new HttpDecoderOption<Object>(id, name);
        }
    };

    // Static methods
    //---------------------------------------------------------------------------------

    /**
     * Returns the {@link HttpDecoderOption} of the specified name.
     */
    @SuppressWarnings("unchecked")
    public static <T> HttpDecoderOption<T> valueOf(String name) {
        return (HttpDecoderOption<T>) pool.valueOf(name);
    }

    /**
     * Shortcut of {@link #valueOf(String) valueOf(firstNameComponent.getName() + "#" + secondNameComponent)}.
     */
    @SuppressWarnings("unchecked")
    public static <T> HttpDecoderOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (HttpDecoderOption<T>) pool.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * Returns {@code true} if a {@link HttpDecoderOption} exists for the given {@code name}.
     */
    public static boolean exists(String name) {
        return pool.exists(name);
    }

    // Available options
    //---------------------------------------------------------------------------------

    public static final HttpDecoderOption<MultipleContentLengthHeadersBehavior>
            MULTIPLE_CONTENT_LENGTH_HEADERS_BEHAVIOR = valueOf("MULTIPLE_CONTENT_LENGTH_HEADERS_BEHAVIOR");

    // Option implementations
    //---------------------------------------------------------------------------------

    /**
     * {@link MultipleContentLengthHeadersBehavior} allows for different ways to respond to encountering multiple
     * Content-Length headers as per https://tools.ietf.org/html/rfc7230#section-3.3.2:
     * <pre>
     *     If a message is received that has multiple Content-Length header
     *     fields with field-values consisting of the same decimal value, or a
     *     single Content-Length header field with a field value containing a
     *     list of identical decimal values (e.g., "Content-Length: 42, 42"),
     *     indicating that duplicate Content-Length header fields have been
     *     generated or combined by an upstream message processor, then the
     *     recipient MUST either reject the message as invalid or replace the
     *     duplicated field-values with a single valid Content-Length field
     *     containing that decimal value prior to determining the message body
     *     length or forwarding the message.
     * </pre>
     * and https://tools.ietf.org/html/rfc7230#section-3.3.3:
     * <pre>
     *     If a message is received without Transfer-Encoding and with
     *     either multiple Content-Length header fields having differing
     *     field-values or a single Content-Length header field having an
     *     invalid value, then the message framing is invalid and the
     *     recipient MUST treat it as an unrecoverable error.
     * </pre>
     * You may use one of the pre-defined behaviors in this class or implement your own.
     */
    public interface MultipleContentLengthHeadersBehavior {

        /**
         * Invoked by {@link HttpObjectDecoder} when it encounters an instance of multiple Content-Length headers. Note
         * that the {@link List} of values represents different header lines and has not been split on commas. A single
         * line may or may not have multiple, comma-separated values.
         */
        long resolveContentLength(HttpHeaders headers, List<String> contentLengthFieldValues);

        /**
         * {@link #ALWAYS_REJECT} will always throw an {@link IllegalArgumentException} (and thus trigger a failed
         * {@link DecoderResult}) whenever it encounters multiple Content-Length header values, regardless of them being
         * the same or not.
         * <p>
         * This behavior is consistent with the first option offered in the RFC and has has been the default behavior of
         * Netty since version 4.1.44.
         */
        MultipleContentLengthHeadersBehavior ALWAYS_REJECT =
                new MultipleContentLengthHeadersBehavior() {
                    @Override
                    public long resolveContentLength(HttpHeaders headers, List<String> contentLengthFieldValues) {
                        throw new IllegalArgumentException("Multiple Content-Length headers found");
                    }
                };

        /**
         * {@link #IF_DIFFERENT_REJECT_ELSE_DEDUPE} will permit multiple Content-Length header values only if they are
         * all the same value. When this occurs, the {@link HttpHeaders} are also normalized to only contain the single,
         * unique value.
         * <p>
         * This behavior is consistent with the second option offered in the RFC.
         */
        MultipleContentLengthHeadersBehavior IF_DIFFERENT_REJECT_ELSE_DEDUPE =
                new MultipleContentLengthHeadersBehavior() {
                    @Override
                    public long resolveContentLength(HttpHeaders headers, List<String> contentLengthFieldValues) {
                        String contentLength = findAndEnforceUniqueContentLength(contentLengthFieldValues);
                        headers.set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
                        return Long.parseLong(contentLength);
                    }
                };

        /**
         * {@link #IF_DIFFERENT_REJECT_ELSE_ALLOW} will permit multiple Content-Length header values only if they are
         * all the same. When this occurs, the original {@link HttpHeaders} (and its multiple values) are left
         * untouched.
         * <p>
         * This behavior is not offered as an explicit option in the RFC but may be desired for pass-through behavior.
         */
        MultipleContentLengthHeadersBehavior IF_DIFFERENT_REJECT_ELSE_ALLOW =
                new MultipleContentLengthHeadersBehavior() {
                    @Override
                    public long resolveContentLength(HttpHeaders headers, List<String> contentLengthFieldValues) {
                        String contentLength = findAndEnforceUniqueContentLength(contentLengthFieldValues);
                        return Long.parseLong(contentLength);
                    }
                };

        MultipleContentLengthHeadersBehavior DEFAULT = ALWAYS_REJECT;
    }

    private static String findAndEnforceUniqueContentLength(List<String> fields) {
        String firstValue = null;
        for (String field : fields) {
            // limit = -1 so that empty values are not discarded, thereby enforcing stricter formatting
            String[] tokens = COMMA_PATTERN.split(field, -1);
            for (String token : tokens) {
                String trimmed = token.trim();
                if (firstValue == null) {
                    firstValue = trimmed;
                } else if (!trimmed.equals(firstValue)) {
                    throw new IllegalArgumentException("Multiple different Content-Length headers found");
                }
            }
        }
        return firstValue;
    }

    // Instance methods
    //---------------------------------------------------------------------------------

    /**
     * Creates a new {@link HttpDecoderOption} with the specified unique {@code name}.
     */
    private HttpDecoderOption(int id, String name) {
        super(id, name);
    }

    /**
     * Validate the value which is set for the {@link HttpDecoderOption}. Sub-classes may override this for special
     * checks.
     */
    public void validate(T value) {
        ObjectUtil.checkNotNull(value, "value");
    }

}
