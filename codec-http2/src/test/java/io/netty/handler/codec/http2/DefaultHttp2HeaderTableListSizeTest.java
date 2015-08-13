/*
 * Copyright 2015 The Netty Project
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

package io.netty.handler.codec.http2;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link DefaultHttp2HeaderTableListSize}.
 */
public class DefaultHttp2HeaderTableListSizeTest {

    private DefaultHttp2HeaderTableListSize headerTable;

    @Before
    public void setup() {
        headerTable = new DefaultHttp2HeaderTableListSize();
    }

    @Test
    public void defaultMaxHeaderListSizeShouldSucceed() {
        assertEquals(Integer.MAX_VALUE, (long) headerTable.maxHeaderListSize());
    }

    @Test
    public void standardMaxHeaderListSizeShouldSucceed() throws Http2Exception {
        headerTable.maxHeaderListSize(123);
        assertEquals(123L, (long) headerTable.maxHeaderListSize());
    }

    @Test
    public void boundaryMaxHeaderListSizeShouldSucceed() throws Http2Exception {
        headerTable.maxHeaderListSize(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, (long) headerTable.maxHeaderListSize());

        final long settingsValueUpperBound = (1L << 32) - 1L;
        headerTable.maxHeaderListSize((int) settingsValueUpperBound);
        assertEquals(Integer.MAX_VALUE, (long) headerTable.maxHeaderListSize());
    }
}
