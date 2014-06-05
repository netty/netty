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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

public class EmptyTextHeaders implements TextHeaders {

    protected EmptyTextHeaders() { }

    @Override
    public String get(CharSequence name) {
        return null;
    }

    @Override
    public String get(CharSequence name, String defaultValue) {
        return defaultValue;
    }

    @Override
    public int getInt(CharSequence name) {
        throw new NoSuchElementException(String.valueOf(name));
    }

    @Override
    public int getInt(CharSequence name, int defaultValue) {
        return defaultValue;
    }

    @Override
    public long getLong(CharSequence name) {
        throw new NoSuchElementException(String.valueOf(name));
    }

    @Override
    public long getLong(CharSequence name, long defaultValue) {
        return defaultValue;
    }

    @Override
    public long getTimeMillis(CharSequence name) {
        throw new NoSuchElementException(String.valueOf(name));
    }

    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        return defaultValue;
    }

    @Override
    public CharSequence getUnconverted(CharSequence name) {
        return null;
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return Collections.emptyList();
    }

    @Override
    public List<CharSequence> getAllUnconverted(CharSequence name) {
        return Collections.emptyList();
    }

    @Override
    public List<Entry<String, String>> entries() {
        return Collections.emptyList();
    }

    @Override
    public List<Entry<CharSequence, CharSequence>> unconvertedEntries() {
        return Collections.emptyList();
    }

    @Override
    public boolean contains(CharSequence name) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Set<String> names() {
        return Collections.emptySet();
    }

    @Override
    public Set<CharSequence> unconvertedNames() {
        return Collections.emptySet();
    }

    @Override
    public TextHeaders add(CharSequence name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders add(CharSequence name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders add(CharSequence name, Object... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders add(TextHeaders headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders set(CharSequence name, Object value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders set(CharSequence name, Iterable<?> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders set(CharSequence name, Object... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public TextHeaders set(TextHeaders headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean remove(CharSequence name) {
        return false;
    }

    @Override
    public TextHeaders clear() {
        return this;
    }

    @Override
    public boolean contains(CharSequence name, Object value) {
        return false;
    }

    @Override
    public boolean contains(CharSequence name, Object value, boolean ignoreCase) {
        return false;
    }

    @Override
    public Iterator<Entry<String, String>> iterator() {
        return entries().iterator();
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> unconvertedIterator() {
        return unconvertedEntries().iterator();
    }

    @Override
    public TextHeaders forEachEntry(TextHeaderProcessor processor) {
        return this;
    }
}
