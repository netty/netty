/*
 * Copyright 2014 The Netty Project
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

package io.netty.handler.codec;

import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.internal.PlatformDependent;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

public class DefaultTextHeaders implements TextHeaders {
    private final HeaderMap.NameConverter NAME_CONVERTER = new HeaderMap.NameConverter() {
        @Override
        public CharSequence convertName(CharSequence name) {
            return DefaultTextHeaders.this.convertName(name);
        }
    };
    private final HeaderMap.ValueMarshaller VALUE_MARSHALLER = new HeaderMap.ValueMarshaller() {
        @Override
        public CharSequence marshal(Object value) {
            return convertValue(value);
        }
    };
    private final HeaderMap.ValueUnmarshaller<String> VALUE_UNMARSHALLER =
            new HeaderMap.ValueUnmarshaller<String>() {
                @Override
                public String unmarshal(CharSequence value) {
                    return value.toString();
                }
            };

    private final TextHeaderProcessor addAll = new TextHeaderProcessor() {
        @Override
        public boolean process(CharSequence name, CharSequence value) throws Exception {
            headers.add(name, value);
            return true;
        }
    };

    private final TextHeaderProcessor setAll = new TextHeaderProcessor() {
        @Override
        public boolean process(CharSequence name, CharSequence value) throws Exception {
            headers.set(name, value);
            return true;
        }
    };

    private final HeaderMap headers;

    public DefaultTextHeaders() {
        this(true);
    }

    public DefaultTextHeaders(boolean ignoreCase) {
        headers = new HeaderMap(ignoreCase, NAME_CONVERTER);
    }

    protected CharSequence convertName(CharSequence name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return name;
    }

    protected CharSequence convertValue(Object value) {
        if (value == null) {
            throw new NullPointerException("value");
        }
        if (value instanceof CharSequence) {
            return (CharSequence) value;
        }
        return value.toString();
    }

    @Override
    public TextHeaders add(CharSequence name, Object value) {
        CharSequence convertedVal = convertValue(value);
        headers.add(name, convertedVal);
        return this;
    }

    @Override
    public TextHeaders add(CharSequence name, Iterable<?> values) {
        headers.addConvertedValues(name, VALUE_MARSHALLER, values);
        return this;
    }

    @Override
    public TextHeaders add(CharSequence name, Object... values) {
        headers.addConvertedValues(name, VALUE_MARSHALLER, values);
        return this;
    }

    @Override
    public TextHeaders add(TextHeaders headers) {
        checkNotNull(headers, "headers");

        add0(headers);
        return this;
    }

    private void add0(TextHeaders headers) {
        if (headers.isEmpty()) {
            return;
        }

        if (headers instanceof DefaultTextHeaders) {
            this.headers.add(((DefaultTextHeaders) headers).headers);
        } else {
            headers.forEachEntry(addAll);
        }
    }

    @Override
    public boolean remove(CharSequence name) {
        return headers.remove(name);
    }

    @Override
    public TextHeaders set(CharSequence name, Object value) {
        CharSequence convertedVal = convertValue(value);
        headers.set(name, convertedVal);
        return this;
    }

    @Override
    public TextHeaders set(CharSequence name, Iterable<?> values) {
        headers.set(name, VALUE_MARSHALLER, values);
        return this;
    }

    @Override
    public TextHeaders set(CharSequence name, Object... values) {
        headers.set(name, VALUE_MARSHALLER, values);
        return this;
    }

    @Override
    public TextHeaders set(TextHeaders headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }

        clear();
        add0(headers);
        return this;
    }

    @Override
    public TextHeaders setAll(TextHeaders headers) {
        if (headers == null) {
            throw new NullPointerException("headers");
        }

        if (headers instanceof DefaultTextHeaders) {
            this.headers.setAll(((DefaultTextHeaders) headers).headers);
        } else {
            headers.forEachEntry(setAll);
        }

        return this;
    }

    @Override
    public TextHeaders clear() {
        headers.clear();
        return this;
    }

    @Override
    public CharSequence getUnconverted(CharSequence name) {
        return headers.get(name);
    }

    @Override
    public String get(CharSequence name) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return null;
        }
        return v.toString();
    }

    @Override
    public String get(CharSequence name, String defaultValue) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return defaultValue;
        }
        return v.toString();
    }

    @Override
    public Integer getInt(CharSequence name) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return null;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseInt();
            } else {
                return Integer.parseInt(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return defaultValue;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseInt();
            } else {
                return Integer.parseInt(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public Long getLong(CharSequence name) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return null;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseLong();
            } else {
                return Long.parseLong(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public long getLong(CharSequence name, long defaultValue) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return defaultValue;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseLong();
            } else {
                return Long.parseLong(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public Long getTimeMillis(CharSequence name) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return null;
        }

        try {
            return HttpHeaderDateFormat.get().parse(v.toString());
        } catch (ParseException ignored) {
            return null;
        }
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        CharSequence v = getUnconverted(name);
        if (v == null) {
            return defaultValue;
        }

        return HttpHeaderDateFormat.get().parse(v.toString(), defaultValue);
    }

    @Override
    public CharSequence getUnconvertedAndRemove(CharSequence name) {
        return headers.getAndRemove(name);
    }

    @Override
    public String getAndRemove(CharSequence name) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return null;
        }
        return v.toString();
    }

    @Override
    public String getAndRemove(CharSequence name, String defaultValue) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return defaultValue;
        }
        return v.toString();
    }

    @Override
    public Integer getIntAndRemove(CharSequence name) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return null;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseInt();
            } else {
                return Integer.parseInt(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public int getIntAndRemove(CharSequence name, int defaultValue) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return defaultValue;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseInt();
            } else {
                return Integer.parseInt(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public Long getLongAndRemove(CharSequence name) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return null;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseLong();
            } else {
                return Long.parseLong(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public long getLongAndRemove(CharSequence name, long defaultValue) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return defaultValue;
        }

        try {
            if (v instanceof AsciiString) {
                return ((AsciiString) v).parseLong();
            } else {
                return Long.parseLong(v.toString());
            }
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    @Override
    public Long getTimeMillisAndRemove(CharSequence name) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return null;
        }

        try {
            return HttpHeaderDateFormat.get().parse(v.toString());
        } catch (ParseException ignored) {
            return null;
        }
    }

    @Override
    public long getTimeMillisAndRemove(CharSequence name, long defaultValue) {
        CharSequence v = getUnconvertedAndRemove(name);
        if (v == null) {
            return defaultValue;
        }

        return HttpHeaderDateFormat.get().parse(v.toString(), defaultValue);
    }

    @Override
    public List<CharSequence> getAllUnconverted(CharSequence name) {
        return headers.getAll(name);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return headers.getAll(name, VALUE_UNMARSHALLER);
    }

    @Override
    public List<String> getAllAndRemove(CharSequence name) {
        return headers.getAll(name, VALUE_UNMARSHALLER);
    }

    @Override
    public List<CharSequence> getAllUnconvertedAndRemove(CharSequence name) {
        return headers.getAllAndRemove(name);
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        int size = size();
        @SuppressWarnings("unchecked")
        final Map.Entry<String, String>[] all = new Map.Entry[size];

        headers.forEachEntry(new HeaderMap.EntryVisitor() {
            int cnt;
            @Override
            public boolean visit(Entry<CharSequence, CharSequence> entry) {
                all[cnt++] = new StringHeaderEntry(entry);
                return true;
            }
        });

        return Arrays.asList(all);
    }

    @Override
    public List<Map.Entry<CharSequence, CharSequence>> unconvertedEntries() {
        return headers.entries();
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return new StringHeaderIterator();
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> unconvertedIterator() {
        return headers.iterator();
    }

    @Override
    public boolean contains(CharSequence name) {
        return getUnconverted(name) != null;
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public boolean isEmpty() {
        return headers.isEmpty();
    }

    @Override
    public boolean contains(CharSequence name, Object value) {
        return contains(name, value, false);
    }

    @Override
    public boolean contains(CharSequence name, Object value, boolean ignoreCase) {
        CharSequence convertedVal = convertValue(value);
        return headers.contains(name, convertedVal, ignoreCase);
    }

    @Override
    public Set<CharSequence> unconvertedNames() {
        return headers.names();
    }

    @Override
    public Set<String> names() {
        return names(false);
    }

    /**
     * Get the set of names for all text headers
     * @param caseInsensitive {@code true} if names should be added in a case insensitive
     * @return The set of names for all text headers
     */
    public Set<String> names(boolean caseInsensitive) {
        final Set<String> names =
                caseInsensitive ? new TreeSet<String>(String.CASE_INSENSITIVE_ORDER)
                        : new LinkedHashSet<String>(size());
        headers.forEachName(new HeaderMap.NameVisitor() {
            @Override
            public boolean visit(CharSequence name) {
                names.add(name.toString());
                return true;
            }
        });
        return names;
    }

    @Override
    public TextHeaders forEachEntry(final TextHeaderProcessor processor) {
        headers.forEachEntry(new HeaderMap.EntryVisitor() {
            @Override
            public boolean visit(Entry<CharSequence, CharSequence> entry) {
                try {
                    return processor.process(entry.getKey(), entry.getValue());
                } catch (Exception ex) {
                    PlatformDependent.throwException(ex);
                    return false;
                }
            }
        });
        return this;
    }

    @Override
    public int hashCode() {
        return headers.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultTextHeaders)) {
            return false;
        }

        DefaultTextHeaders other = (DefaultTextHeaders) o;

        // First, check that the set of names match.
        Set<String> names = names(true);
        if (!names.equals(other.names(true))) {
            return false;
        }

        // Compare the values for each name.
        for (String name : names) {
            List<String> values = getAll(name);
            List<String> otherValues = other.getAll(name);
            if (values.size() != otherValues.size()) {
                return false;
            }

            // Convert the values to a set and remove values from the other object to see if
            // they match.
            Set<String> valueSet = new HashSet<String>(values);
            valueSet.removeAll(otherValues);
            if (!valueSet.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    private static <T> void checkNotNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
    }

    private static final class StringHeaderEntry implements Entry<String, String> {
        private final Entry<CharSequence, CharSequence> entry;
        private String name;
        private String value;

        StringHeaderEntry(Entry<CharSequence, CharSequence> entry) {
            this.entry = entry;
        }

        @Override
        public String getKey() {
            if (name == null) {
                name = entry.getKey().toString();
            }
            return name;
        }

        @Override
        public String getValue() {
            if (value == null) {
                value = entry.getValue().toString();
            }
            return value;
        }

        @Override
        public String setValue(String value) {
            return entry.setValue(value).toString();
        }

        @Override
        public String toString() {
            return entry.toString();
        }
    }

    private final class StringHeaderIterator implements Iterator<Entry<String, String>> {

        private Iterator<Entry<CharSequence, CharSequence>> iter = headers.iterator();

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Entry<String, String> next() {
            Entry<CharSequence, CharSequence> next = iter.next();

            return new StringHeaderEntry(next);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * This DateFormat decodes 3 formats of {@link java.util.Date}, but only encodes the one,
     * the first:
     * <ul>
     * <li>Sun, 06 Nov 1994 08:49:37 GMT: standard specification, the only one with
     * valid generation</li>
     * <li>Sun, 06 Nov 1994 08:49:37 GMT: obsolete specification</li>
     * <li>Sun Nov 6 08:49:37 1994: obsolete specification</li>
     * </ul>
     */
    static final class HttpHeaderDateFormat {

        private static final ParsePosition parsePos = new ParsePosition(0);
        private static final FastThreadLocal<HttpHeaderDateFormat> dateFormatThreadLocal =
                new FastThreadLocal<HttpHeaderDateFormat>() {
                    @Override
                    protected HttpHeaderDateFormat initialValue() {
                        return new HttpHeaderDateFormat();
                    }
                };

        static HttpHeaderDateFormat get() {
            return dateFormatThreadLocal.get();
        }

        /**
         * Standard date format:
         * <pre>Sun, 06 Nov 1994 08:49:37 GMT -> E, d MMM yyyy HH:mm:ss z</pre>
         */
        private final DateFormat dateFormat1 = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
        /**
         * First obsolete format:
         * <pre>Sunday, 06-Nov-94 08:49:37 GMT -> E, d-MMM-y HH:mm:ss z</pre>
         */
        private final DateFormat dateFormat2 = new SimpleDateFormat("E, dd-MMM-yy HH:mm:ss z", Locale.ENGLISH);
        /**
         * Second obsolete format
         * <pre>Sun Nov 6 08:49:37 1994 -> EEE, MMM d HH:mm:ss yyyy</pre>
         */
        private final DateFormat dateFormat3 = new SimpleDateFormat("E MMM d HH:mm:ss yyyy", Locale.ENGLISH);

        private HttpHeaderDateFormat() {
            TimeZone tz = TimeZone.getTimeZone("GMT");
            dateFormat1.setTimeZone(tz);
            dateFormat2.setTimeZone(tz);
            dateFormat3.setTimeZone(tz);
        }

        long parse(String text) throws ParseException {
            Date date = dateFormat1.parse(text, parsePos);
            if (date == null) {
                date = dateFormat2.parse(text, parsePos);
            }
            if (date == null) {
                date = dateFormat3.parse(text, parsePos);
            }
            if (date == null) {
                throw new ParseException(text, 0);
            }
            return date.getTime();
        }

        long parse(String text, long defaultValue) {
            Date date = dateFormat1.parse(text, parsePos);
            if (date == null) {
                date = dateFormat2.parse(text, parsePos);
            }
            if (date == null) {
                date = dateFormat3.parse(text, parsePos);
            }
            if (date == null) {
                return defaultValue;
            }
            return date.getTime();
        }
    }
}
