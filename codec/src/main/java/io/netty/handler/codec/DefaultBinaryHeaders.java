/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.util.internal.PlatformDependent;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

public class DefaultBinaryHeaders implements BinaryHeaders {
    private final HeaderMap.ValueUnmarshaller<AsciiString> VALUE_UNMARSHALLER =
            new HeaderMap.ValueUnmarshaller<AsciiString>() {
                @Override
                public AsciiString unmarshal(CharSequence value) {
                    return (AsciiString) value;
                }
            };

    private final BinaryHeaderVisitor addAll = new BinaryHeaderVisitor() {
        @Override
        public boolean visit(AsciiString name, AsciiString value) throws Exception {
            add(name, value);
            return true;
        }
    };
    private final BinaryHeaderVisitor setAll = new BinaryHeaderVisitor() {
        @Override
        public boolean visit(AsciiString name, AsciiString value) throws Exception {
            set(name, value);
            return true;
        }
    };

    private final HeaderMap headers;

    public DefaultBinaryHeaders() {
        // Binary headers are case-sensitive. It's up the HTTP/1 translation layer to convert headers to
        // lowercase.
        headers = new HeaderMap(false);
    }

    @Override
    public BinaryHeaders add(AsciiString name, AsciiString value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public BinaryHeaders add(AsciiString name, Iterable<AsciiString> values) {
        headers.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders add(AsciiString name, AsciiString... values) {
        headers.add(name, values);
        return this;
    }

    @Override
    public BinaryHeaders add(BinaryHeaders headers) {
        checkNotNull(headers, "headers");

        add0(headers);
        return this;
    }

    private void add0(BinaryHeaders headers) {
        if (headers.isEmpty()) {
            return;
        }

        if (headers instanceof DefaultBinaryHeaders) {
            this.headers.add(((DefaultBinaryHeaders) headers).headers);
        } else {
            forEachEntry(addAll);
        }
    }

    @Override
    public boolean remove(AsciiString name) {
        return headers.remove(name);
    }

    @Override
    public BinaryHeaders set(AsciiString name, AsciiString value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public BinaryHeaders set(AsciiString name, Iterable<AsciiString> values) {
        headers.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders set(AsciiString name, AsciiString... values) {
        headers.set(name, values);
        return this;
    }

    @Override
    public BinaryHeaders set(BinaryHeaders headers) {
        checkNotNull(headers, "headers");
        clear();
        add0(headers);
        return this;
    }

    @Override
    public BinaryHeaders setAll(BinaryHeaders headers) {
        checkNotNull(headers, "headers");

        if (headers instanceof DefaultBinaryHeaders) {
            this.headers.setAll(((DefaultBinaryHeaders) headers).headers);
        } else {
            forEachEntry(setAll);
        }

        return this;
    }

    @Override
    public BinaryHeaders clear() {
        headers.clear();
        return this;
    }

    @Override
    public AsciiString get(AsciiString name) {
        return (AsciiString) headers.get(name);
    }

    @Override
    public AsciiString get(AsciiString name, AsciiString defaultValue) {
        AsciiString v = get(name);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    @Override
    public AsciiString getAndRemove(AsciiString name) {
        return (AsciiString) headers.getAndRemove(name);
    }

    @Override
    public AsciiString getAndRemove(AsciiString name, AsciiString defaultValue) {
        AsciiString v = getAndRemove(name);
        if (v == null) {
            return defaultValue;
        }
        return v;
    }

    @Override
    public List<AsciiString> getAll(AsciiString name) {
        return headers.getAll(name, VALUE_UNMARSHALLER);
    }

    @Override
    public List<AsciiString> getAllAndRemove(AsciiString name) {
        return headers.getAllAndRemove(name, VALUE_UNMARSHALLER);
    }

    @Override
    public List<Map.Entry<AsciiString, AsciiString>> entries() {
        int size = size();
        @SuppressWarnings("unchecked")
        final Map.Entry<AsciiString, AsciiString>[] all = new Map.Entry[size];

        headers.forEachEntry(new HeaderMap.EntryVisitor() {
            int cnt;
            @Override
            public boolean visit(Entry<CharSequence, CharSequence> entry) {
                all[cnt++] = new AsciiStringHeaderEntry(entry);
                return true;
            }
        });

        return Arrays.asList(all);
    }

    @Override
    public Iterator<Entry<AsciiString, AsciiString>> iterator() {
        return new AsciiStringHeaderIterator();
    }

    @Override
    public boolean contains(AsciiString name) {
        return get(name) != null;
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
    public boolean contains(AsciiString name, AsciiString value) {
        return contains(name, value, false);
    }

    public boolean contains(AsciiString name, AsciiString value, boolean ignoreCase) {
        return headers.contains(name, value);
    }

    @Override
    public Set<AsciiString> names() {
        return names(headers.isIgnoreCase());
    }

    /**
     * Get the set of names for all text headers
     * @param caseInsensitive {@code true} if names should be added in a case insensitive
     * @return The set of names for all text headers
     */
    public Set<AsciiString> names(boolean caseInsensitive) {
        final Set<AsciiString> names = caseInsensitive ? new TreeSet<AsciiString>(AsciiString.CASE_INSENSITIVE_ORDER)
                                            : new LinkedHashSet<AsciiString>(size());
        headers.forEachName(new HeaderMap.NameVisitor() {
            @Override
            public boolean visit(CharSequence name) {
                names.add((AsciiString) name);
                return true;
            }
        });
        return names;
    }

    @Override
    public BinaryHeaders forEachEntry(final BinaryHeaders.BinaryHeaderVisitor visitor) {
        headers.forEachEntry(new HeaderMap.EntryVisitor() {
            @Override
            public boolean visit(Entry<CharSequence, CharSequence> entry) {
                try {
                    return visitor.visit((AsciiString) entry.getKey(),
                            (AsciiString) entry.getValue());
                } catch (Exception e) {
                    PlatformDependent.throwException(e);
                    return false;
                }
            }
        });
        return this;
    }

    @Override
    public int hashCode() {
        return Utils.hashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BinaryHeaders)) {
            return false;
        }

        return Utils.equals(this, (BinaryHeaders) o);
    }

    @Override
    public String toString() {
        return Utils.toStringUtf8(this);
    }

    static <T> void checkNotNull(T value, String name) {
        if (value == null) {
            throw new NullPointerException(name);
        }
    }

    private static final class AsciiStringHeaderEntry implements Map.Entry<AsciiString, AsciiString> {
        private final Entry<CharSequence, CharSequence> entry;

        AsciiStringHeaderEntry(Entry<CharSequence, CharSequence> entry) {
            this.entry = entry;
        }

        @Override
        public AsciiString getKey() {
            return (AsciiString) entry.getKey();
        }

        @Override
        public AsciiString getValue() {
            return (AsciiString) entry.getValue();
        }

        @Override
        public AsciiString setValue(AsciiString value) {
            checkNotNull(value, "value");
            return (AsciiString) entry.setValue(value);
        }

        @Override
        public String toString() {
            return entry.toString();
        }
    }

    private final class AsciiStringHeaderIterator implements Iterator<Map.Entry<AsciiString, AsciiString>> {

        private Iterator<Entry<CharSequence, CharSequence>> iter = headers.iterator();

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Entry<AsciiString, AsciiString> next() {
            Entry<CharSequence, CharSequence> entry = iter.next();
            return new AsciiStringHeaderEntry(entry);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
