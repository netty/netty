/*
 * Copyright 2012 The Netty Project
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

import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

public class DefaultHttpHeaders extends HttpHeaders {

    private static final int BUCKET_SIZE = 17;

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    private final HeaderEntry[] entries = new HeaderEntry[BUCKET_SIZE];
    private final HeaderEntry head = new HeaderEntry();
    protected final boolean validate;

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        this.validate = validate;
        head.before = head.after = head;
    }

    void validateHeaderName0(CharSequence headerName) {
        validateHeaderName(headerName);
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        if (headers instanceof DefaultHttpHeaders) {
            if (headers == this) {
                throw new IllegalArgumentException("can't add to itself.");
            }
            DefaultHttpHeaders defaultHttpHeaders = (DefaultHttpHeaders) headers;
            HeaderEntry e = defaultHttpHeaders.head.after;
            while (e != defaultHttpHeaders.head) {
                add(e.key, e.value);
                e = e.after;
            }
            return this;
        } else {
            return super.add(headers);
        }
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        if (headers instanceof DefaultHttpHeaders) {
            if (headers != this) {
                clear();
                DefaultHttpHeaders defaultHttpHeaders = (DefaultHttpHeaders) headers;
                HeaderEntry e = defaultHttpHeaders.head.after;
                while (e != defaultHttpHeaders.head) {
                    add(e.key, e.value);
                    e = e.after;
                }
            }
            return this;
        } else {
            return super.set(headers);
        }
    }

    @Override
    public HttpHeaders add(final String name, final Object value) {
        return add((CharSequence) name, value);
    }

    @Override
    public HttpHeaders add(final CharSequence name, final Object value) {
        CharSequence strVal;
        if (validate) {
            validateHeaderName0(name);
            strVal = toCharSequence(value);
            validateHeaderValue(strVal);
        } else {
            strVal = toCharSequence(value);
        }
        int h = hash(name);
        int i = index(h);
        add0(h, i, name, strVal);
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        return add((CharSequence) name, values);
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        if (validate) {
            validateHeaderName0(name);
        }
        int h = hash(name);
        int i = index(h);
        for (Object v: values) {
            CharSequence vstr = toCharSequence(v);
            if (validate) {
                validateHeaderValue(vstr);
            }
            add0(h, i, name, vstr);
        }
        return this;
    }

    private void add0(int h, int i, final CharSequence name, final CharSequence value) {
        // Update the hash table.
        HeaderEntry e = entries[i];
        HeaderEntry newEntry;
        entries[i] = newEntry = new HeaderEntry(h, name, value);
        newEntry.next = e;

        // Update the linked list.
        newEntry.addBefore(head);
    }

    @Override
    public HttpHeaders remove(final String name) {
        return remove((CharSequence) name);
    }

    @Override
    public HttpHeaders remove(final CharSequence name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        int h = hash(name);
        int i = index(h);
        remove0(h, i, name);
        return this;
    }

    private void remove0(int h, int i, CharSequence name) {
        HeaderEntry e = entries[i];
        if (e == null) {
            return;
        }

        for (;;) {
            if (e.hash == h && equalsIgnoreCase(name, e.key)) {
                e.remove();
                HeaderEntry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    return;
                }
            } else {
                break;
            }
        }

        for (;;) {
            HeaderEntry next = e.next;
            if (next == null) {
                break;
            }
            if (next.hash == h && equalsIgnoreCase(name, next.key)) {
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }
    }

    @Override
    public HttpHeaders set(final String name, final Object value) {
        return set((CharSequence) name, value);
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Object value) {
        CharSequence strVal;
        if (validate) {
            validateHeaderName0(name);
            strVal = toCharSequence(value);
            validateHeaderValue(strVal);
        } else {
            strVal = toCharSequence(value);
        }
        int h = hash(name);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, strVal);
        return this;
    }

    @Override
    public HttpHeaders set(final String name, final Iterable<?> values) {
        return set((CharSequence) name, values);
    }

    @Override
    public HttpHeaders set(final CharSequence name, final Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }
        if (validate) {
            validateHeaderName0(name);
        }

        int h = hash(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            CharSequence strVal = toCharSequence(v);
            if (validate) {
                validateHeaderValue(strVal);
            }
            add0(h, i, name, strVal);
        }

        return this;
    }

    @Override
    public HttpHeaders clear() {
        Arrays.fill(entries, null);
        head.before = head.after = head;
        return this;
    }

    @Override
    public String get(final String name) {
        return get((CharSequence) name);
    }

    @Override
    public String get(final CharSequence name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        CharSequence value = null;
        // loop until the first header was found
        while (e != null) {
            if (e.hash == h && equalsIgnoreCase(name, e.key)) {
                value = e.value;
            }

            e = e.next;
        }
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    @Override
    public List<String> getAll(final String name) {
        return getAll((CharSequence) name);
    }

    @Override
    public List<String> getAll(final CharSequence name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && equalsIgnoreCase(name, e.key)) {
                values.addFirst(e.getValue());
            }
            e = e.next;
        }
        return values;
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        List<Map.Entry<String, String>> all =
            new LinkedList<Map.Entry<String, String>>();

        HeaderEntry e = head.after;
        while (e != head) {
            all.add(e);
            e = e.after;
        }
        return all;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return new HeaderIterator();
    }

    @Override
    public boolean contains(String name) {
        return get(name) != null;
    }

    @Override
    public boolean contains(CharSequence name) {
        return get(name) != null;
    }

    @Override
    public boolean isEmpty() {
        return head == head.after;
    }

    @Override
    public boolean contains(String name, String value, boolean ignoreCaseValue) {
        return contains((CharSequence) name, (CharSequence) value, ignoreCaseValue);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCaseValue) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && equalsIgnoreCase(name, e.key)) {
                if (ignoreCaseValue) {
                    if (equalsIgnoreCase(e.value, value)) {
                        return true;
                    }
                } else {
                    if (e.value.equals(value)) {
                        return true;
                    }
                }
            }
            e = e.next;
        }
        return false;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new LinkedHashSet<String>();
        HeaderEntry e = head.after;
        while (e != head) {
            names.add(e.getKey());
            e = e.after;
        }
        return names;
    }

    private static CharSequence toCharSequence(Object value) {
        checkNotNull(value, "value");
        if (value instanceof CharSequence) {
            return (CharSequence) value;
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Date) {
            return HttpHeaderDateFormat.get().format((Date) value);
        }
        if (value instanceof Calendar) {
            return HttpHeaderDateFormat.get().format(((Calendar) value).getTime());
        }
        return value.toString();
    }

    void encode(ByteBuf buf) {
        HeaderEntry e = head.after;
        while (e != head) {
            e.encode(buf);
            e = e.after;
        }
    }

    private final class HeaderIterator implements Iterator<Map.Entry<String, String>> {

        private HeaderEntry current = head;

        @Override
        public boolean hasNext() {
            return current.after != head;
        }

        @Override
        public Entry<String, String> next() {
            current = current.after;

            if (current == head) {
                throw new NoSuchElementException();
            }

            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class HeaderEntry implements Map.Entry<String, String> {
        final int hash;
        final CharSequence key;
        CharSequence value;
        HeaderEntry next;
        HeaderEntry before, after;

        HeaderEntry(int hash, CharSequence key, CharSequence value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        HeaderEntry() {
            hash = -1;
            key = null;
            value = null;
        }

        void remove() {
            before.after = after;
            after.before = before;
        }

        void addBefore(HeaderEntry e) {
            after  = e;
            before = e.before;
            before.after = this;
            after.before = this;
        }

        @Override
        public String getKey() {
            return key.toString();
        }

        @Override
        public String getValue() {
            return value.toString();
        }

        @Override
        public String setValue(String value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            validateHeaderValue(value);
            CharSequence oldValue = this.value;
            this.value = value;
            return oldValue.toString();
        }

        @Override
        public String toString() {
            return key.toString() + '=' + value.toString();
        }

        void encode(ByteBuf buf) {
            HttpHeaders.encode(key, value, buf);
        }
    }
}
