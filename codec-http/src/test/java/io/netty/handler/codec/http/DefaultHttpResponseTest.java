/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class DefaultHttpResponseTest {

   @Test
    public void testNotEquals() {
        HttpResponse ok = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpResponse notFound = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        assertNotEquals(ok, notFound);
        assertNotEquals(ok.hashCode(), notFound.hashCode());
   }

    @Test
    public void testEquals() {
        HttpResponse ok = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpResponse ok2 = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        assertEquals(ok, ok2);
        assertEquals(ok.hashCode(), ok2.hashCode());
    }
}
