/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import org.junit.Assert;
import org.junit.Test;

public class Http3HeadersSinkTest {

    @Test
    public void testHeaderSizeExceeded() {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 32, false);
        sink.accept("name", "value");
        sink.accept("someothername", "someothervalue");

        try {
            sink.finish();
            Assert.fail();
        } catch (Http3Exception e) {
            Http3TestUtils.assertException(Http3ErrorCode.H3_EXCESSIVE_LOAD, e);
        }
    }

    @Test
    public void testHeaderSizeNotExceed() throws Exception {
        Http3Headers headers = new DefaultHttp3Headers();
        Http3HeadersSink sink = new Http3HeadersSink(headers, 64, false);
        sink.accept("name", "value");
        Assert.assertEquals("value", headers.get("name"));
        sink.finish();
    }

    @Test(expected = Http3HeadersValidationException.class)
    public void testPseudoHeaderFollowsNormalHeader() throws Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true);
        sink.accept("name", "value");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        sink.finish();
    }

    @Test(expected = Http3HeadersValidationException.class)
    public void testInvalidatePseudoHeader() throws Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true);
        sink.accept(":invalid", "value");
        sink.finish();
    }

    @Test(expected = Http3HeadersValidationException.class)
    public void testMixRequestResponsePseudoHeaders() throws Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true);
        sink.accept(Http3Headers.PseudoHeaderName.METHOD.value(), "value");
        sink.accept(Http3Headers.PseudoHeaderName.STATUS.value(), "value");
        sink.finish();
    }

    @Test
    public void testValidPseudoHeader() throws Exception {
        Http3Headers headers = new DefaultHttp3Headers();
        Http3HeadersSink sink = new Http3HeadersSink(headers, 512, true);
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        sink.finish();
        Assert.assertEquals("value", headers.get(Http3Headers.PseudoHeaderName.AUTHORITY.value()));
    }

    @Test(expected = Http3HeadersValidationException.class)
    public void testDuplicatePseudoHeader() throws Http3Exception {
        Http3HeadersSink sink = new Http3HeadersSink(new DefaultHttp3Headers(), 512, true);
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        sink.accept(Http3Headers.PseudoHeaderName.AUTHORITY.value(), "value");
        sink.finish();
    }

}
