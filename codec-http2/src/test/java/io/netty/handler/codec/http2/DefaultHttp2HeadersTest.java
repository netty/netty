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

package io.netty.handler.codec.http2;

import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;

public class DefaultHttp2HeadersTest {

    @Test
    public void pseudoHeadersMustComeFirstWhenIterating() {
        DefaultHttp2Headers headers = new DefaultHttp2Headers();
        headers.add(bs("name1"), bs("value1"), bs("value2"));
        headers.method(bs("POST"));
        headers.add(bs("2name"), bs("value3"));
        headers.path(bs("/index.html"));
        headers.status(bs("200"));
        headers.authority(bs("netty.io"));
        headers.add(bs("name3"), bs("value4"));
        headers.scheme(bs("https"));

        Iterator<Entry<ByteString, ByteString>> iter = headers.iterator();
        List<ByteString> names = new ArrayList<ByteString>();
        for (int i = 0; i < 5; i++) {
            names.add(iter.next().getKey());
        }
        assertTrue(names.containsAll(asList(bs(":method"), bs(":status"), bs(":path"), bs(":scheme"),
                                            bs(":authority"))));
    }

    private static ByteString bs(String str) {
        return new ByteString(str, CharsetUtil.US_ASCII);
    }
}
