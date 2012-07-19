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
package org.jboss.netty.handler.codec.http;

import org.junit.Assert;
import org.junit.Test;

public class HttpContentCompressorTest {

    @Test
    public void testGetTargetContentEncoding() throws Exception {
        HttpContentCompressor compressor = new HttpContentCompressor();

        String[] tests = {
            // Accept-Encoding      ->     Content-Encoding
            "",                            null,
            "*",                           "gzip",
            "*;q=0.0",                     null,
            "gzip",                        "gzip",
            "compress, gzip;q=0.5",        "gzip",
            "gzip; q=0.5, identity",       "gzip",
            "gzip ; q=0.1",                "gzip",
            "gzip; q=0, deflate",          "deflate",
            " deflate ; q=0 , *;q=0.5",    "gzip",
        };
        for (int i = 0; i < tests.length; i += 2) {
            String acceptEncoding = tests[i];
            String contentEncoding = tests[i + 1];
            String targetEncoding = compressor.getTargetContentEncoding(acceptEncoding);
            Assert.assertEquals(contentEncoding, targetEncoding);
        }
    }
}
