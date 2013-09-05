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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class DefaultHttpHeaders extends HttpHeaders {

    private static final int BUCKET_SIZE = 17;

    private static final Set<String> KNOWN_NAMES = createSet(Names.class);
    private static final Set<String> KNOWN_VALUES = createSet(Values.class);

    private static Set<String> createSet(Class<?> clazz) {
        Set<String> set = new HashSet<String>();
        Field[] fields = clazz.getDeclaredFields();

        for (Field f: fields) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && Modifier.isFinal(m)
                    && f.getType().isAssignableFrom(String.class)) {
                try {
                    set.add((String) f.get(null));
                } catch (Throwable cause) {
                    // ignore
                }
            }
        }
        return set;
    }

    private static int hash(String name, boolean validate) {
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i --) {
            char c = name.charAt(i);
            if (validate) {
                valideHeaderNameChar(c);
            }
            c = toLowerCase(c);
            h = 31 * h + c;
        }

        if (h > 0) {
            return h;
        } else if (h == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return -h;
        }
    }

    private static boolean eq(String name1, String name2) {
        if (name1 == name2) {
            // check for object equality as the user may reuse our static fields in HttpHeaders.Names
            return true;
        }
        int nameLen = name1.length();
        if (nameLen != name2.length()) {
            return false;
        }

        for (int i = nameLen - 1; i >= 0; i --) {
            char c1 = name1.charAt(i);
            char c2 = name2.charAt(i);
            if (c1 != c2) {
                if (toLowerCase(c1) != toLowerCase(c2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static char toLowerCase(char c) {
        if (c >= 'A' && c <= 'Z') {
            c += 32;
        }
        return c;
    }

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    private final HeaderEntry[] entries = new HeaderEntry[BUCKET_SIZE];
    private final HeaderEntry head = new HeaderEntry(-1, null, null);
    private final boolean validate;

    public DefaultHttpHeaders() {
        this(true);
    }

    public DefaultHttpHeaders(boolean validate) {
        head.before = head.after = head;
        this.validate = validate;
    }

    void validateHeaderValue0(String headerValue) {
        if (KNOWN_VALUES.contains(headerValue)) {
            return;
        }
        validateHeaderValue(headerValue);
    }

    @Override
    public HttpHeaders add(final String name, final Object value) {
        String strVal = toString(value);
        boolean validateName = false;
        if (validate) {
            validateHeaderValue0(strVal);
            validateName = !KNOWN_NAMES.contains(name);
        }

        int h = hash(name, validateName);
        int i = index(h);
        add0(h, i, name, strVal);
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        boolean validateName = false;
        if (validate) {
            validateName = !KNOWN_NAMES.contains(name);
        }

        int h = hash(name, validateName);
        int i = index(h);
        for (Object v: values) {
            String vstr = toString(v);
            if (validate) {
                validateHeaderValue0(vstr);
            }
            add0(h, i, name, vstr);
        }
        return this;
    }

    private void add0(int h, int i, final String name, final String value) {
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
        if (name == null) {
            throw new NullPointerException("name");
        }
        int h = hash(name, false);
        int i = index(h);
        remove0(h, i, name);
        return this;
    }

    private void remove0(int h, int i, String name) {
        HeaderEntry e = entries[i];
        if (e == null) {
            return;
        }

        for (;;) {
            if (e.hash == h && eq(name, e.key)) {
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
            if (next.hash == h && eq(name, next.key)) {
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }
    }

    @Override
    public HttpHeaders set(final String name, final Object value) {
        String strVal = toString(value);
        boolean validateName = false;
        if (validate) {
            validateHeaderValue0(strVal);
            validateName = !KNOWN_NAMES.contains(name);
        }

        int h = hash(name, validateName);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, strVal);
        return this;
    }

    @Override
    public HttpHeaders set(final String name, final Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        boolean validateName = false;
        if (validate) {
            validateName = !KNOWN_NAMES.contains(name);
        }

        int h = hash(name, validateName);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            String strVal = toString(v);
            if (validate) {
                validateHeaderValue0(strVal);
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
        return get(name, false);
    }

    private String get(final String name, boolean last) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name, false);
        int i = index(h);
        HeaderEntry e = entries[i];
        String value = null;
        // loop until the first header was found
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                value = e.value;
                if (last) {
                    break;
                }
            }

            e = e.next;
        }
        return value;
    }

    @Override
    public List<String> getAll(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h = hash(name, false);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                values.addFirst(e.value);
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
        return entries().iterator();
    }

    @Override
    public boolean contains(String name) {
        return get(name, true) != null;
    }

    @Override
    public boolean isEmpty() {
        return head == head.after;
    }

    @Override
    public Set<String> names() {

        Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);

        HeaderEntry e = head.after;
        while (e != head) {
            names.add(e.key);
            e = e.after;
        }
        return names;
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
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

    private final class HeaderEntry implements Map.Entry<String, String> {
        final int hash;
        final String key;
        String value;
        HeaderEntry next;
        HeaderEntry before, after;

        HeaderEntry(int hash, String key, String value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
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
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            if (validate) {
                validateHeaderValue0(value);
            }
            String oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return key + '=' + value;
        }
    }
}
