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
import java.util.Set;

public class EmptyBinaryHeaders implements BinaryHeaders {

    @Override
    public AsciiString get(AsciiString name) {
        return null;
    }

    @Override
    public AsciiString get(AsciiString name, AsciiString defaultValue) {
        return defaultValue;
    }

    @Override
    public AsciiString getAndRemove(AsciiString name) {
        return null;
    }

    @Override
    public AsciiString getAndRemove(AsciiString name, AsciiString defaultValue) {
        return defaultValue;
    }

    @Override
    public List<AsciiString> getAll(AsciiString name) {
        return Collections.emptyList();
    }

    @Override
    public List<AsciiString> getAllAndRemove(AsciiString name) {
        return Collections.emptyList();
    }

    @Override
    public List<Entry<AsciiString, AsciiString>> entries() {
        return Collections.emptyList();
    }

    @Override
    public boolean contains(AsciiString name) {
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
    public Set<AsciiString> names() {
        return Collections.emptySet();
    }

    @Override
    public BinaryHeaders add(AsciiString name, AsciiString value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders add(AsciiString name, Iterable<AsciiString> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders add(AsciiString name, AsciiString... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders add(BinaryHeaders headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders set(AsciiString name, AsciiString value) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders set(AsciiString name, Iterable<AsciiString> values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders set(AsciiString name, AsciiString... values) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders set(BinaryHeaders headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public BinaryHeaders setAll(BinaryHeaders headers) {
        throw new UnsupportedOperationException("read only");
    }

    @Override
    public boolean remove(AsciiString name) {
        return false;
    }

    @Override
    public BinaryHeaders clear() {
        return this;
    }

    @Override
    public boolean contains(AsciiString name, AsciiString value) {
        return false;
    }

    @Override
    public Iterator<Entry<AsciiString, AsciiString>> iterator() {
        return entries().iterator();
    }

    @Override
    public BinaryHeaders forEachEntry(BinaryHeaderVisitor processor) {
        return this;
    }

    @Override
    public int hashCode() {
        return BinaryHeaders.Utils.hashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BinaryHeaders)) {
            return false;
        }
        return ((BinaryHeaders) obj).isEmpty();
    }

    @Override
    public String toString() {
        return BinaryHeaders.Utils.toStringUtf8(this);
    }
}
