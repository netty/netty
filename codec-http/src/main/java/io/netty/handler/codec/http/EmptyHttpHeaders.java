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

package io.netty.handler.codec.http;

import io.netty.handler.codec.EmptyHeaders;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

public class EmptyHttpHeaders extends EmptyHeaders<CharSequence, CharSequence, HttpHeaders> implements HttpHeaders {
    public static final EmptyHttpHeaders INSTANCE = new EmptyHttpHeaders();
    private static final Iterator<Entry<String, String>> EMPTY_STRING_ITERATOR =
            new Iterator<Entry<String, String>>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public Entry<String, String> next() {
                    throw new NoSuchElementException();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove");
                }
    };

    protected EmptyHttpHeaders() {
    }

    @Override
    public String getAsString(CharSequence name) {
        return null;
    }

    @Override
    public List<String> getAllAsString(CharSequence name) {
        return Collections.emptyList();
    }

    @Override
    public Iterator<Entry<String, String>> iteratorAsString() {
        return EMPTY_STRING_ITERATOR;
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return false;
    }
}
