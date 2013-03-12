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
package io.netty.handler.codec.httpx;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public abstract class HashedHttpHeaders extends AbstractHttpHeaders {

    private static final int BUCKET_SIZE = 17;

    private static int hash(String name) {
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i --) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
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
        int nameLen = name1.length();
        if (nameLen != name2.length()) {
            return false;
        }

        for (int i = nameLen - 1; i >= 0; i --) {
            char c1 = name1.charAt(i);
            char c2 = name2.charAt(i);
            if (c1 != c2) {
                if (c1 >= 'A' && c1 <= 'Z') {
                    c1 += 32;
                }
                if (c2 >= 'A' && c2 <= 'Z') {
                    c2 += 32;
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    private final HeaderEntry[] entries = new HeaderEntry[BUCKET_SIZE];
    private final HeaderEntry head = new HeaderEntry(-1, null, null);

    protected HashedHttpHeaders(HttpMessageType type) {
        super(type);
        head.before = head.after = head;
    }

    @Override
    protected void add0(String name, String value) {
        int h = hash(name);
        int i = index(h);
        add0(h, i, name, value);
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        validateName(name);
        int h = hash(name);
        int i = index(h);
        for (Object v: values) {
            String vstr = toString(v);
            validateValue(vstr);
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
        int h = hash(name);
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
    protected void remove0(String name, String value) {
        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        if (e == null) {
            return;
        }

        if (e.hash == h && eq(name, e.key) && e.value.equals(value)) {
            e.remove();
            HeaderEntry next = e.next;
            if (next != null) {
                entries[i] = next;
            } else {
                entries[i] = null;
            }
        } else {
            for (;;) {
                HeaderEntry next = e.next;
                if (next == null) {
                    break;
                }
                if (next.hash == h && eq(name, next.key) && next.value.equals(value)) {
                    e.next = next.next;
                    next.remove();
                    break;
                } else {
                    e = next;
                }
            }
        }
    }

    @Override
    protected void set0(String name, String value) {
        int h = hash(name);
        int i = index(h);
        remove0(h, i, name);
        add0(h, i, name, value);
    }

    @Override
    public HttpHeaders set(final String name, final Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        validateName(name);

        int h = hash(name);
        int i = index(h);

        remove0(h, i, name);
        for (Object v: values) {
            if (v == null) {
                break;
            }
            String strVal = toString(v);
            validateValue(strVal);
            add0(h, i, name, strVal);
        }

        return this;
    }

    @Override
    public HttpHeaders clear() {
        for (int i = 0; i < entries.length; i ++) {
            entries[i] = null;
        }
        head.before = head.after = head;
        return this;
    }

    @Override
    public String get(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name);
        int i = index(h);
        HeaderEntry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                return e.value;
            }

            e = e.next;
        }
        return null;
    }

    @Override
    public List<String> getAll(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h = hash(name);
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
            validateValue(value);
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
