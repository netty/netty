/*
 * Copyright 2018 The Netty Project
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

import io.netty.handler.codec.http2.Http2Exception.StreamException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.TE;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.METHOD;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.PATH;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.SCHEME;
import static io.netty.handler.codec.http2.Http2Headers.PseudoHeaderName.STATUS;
import static io.netty.handler.codec.http2.Http2HeadersValidator.validateConnectionSpecificHeaders;
import static io.netty.handler.codec.http2.Http2HeadersValidator.validateRequestPseudoHeaders;
import static io.netty.handler.codec.http2.Http2TestUtil.newHttp2HeadersWithRequestPseudoHeaders;

public class Http2HeadersValidatorTest {

    private static final int STREAM_ID = 3;

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @Test
    public void validateConnectionSpecificHeadersShouldThrowIfConnectionHeaderPresent() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Connection-specific headers like [connection] must not be used with HTTP");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.add(CONNECTION, "keep-alive");
        validateConnectionSpecificHeaders(headers, STREAM_ID);
    }

    @Test
    public void validateConnectionSpecificHeadersShouldThrowIfTeHeaderValueIsNotTrailers() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("TE header must not contain any value other than \"trailers\"");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.add(TE, "trailers, deflate");
        validateConnectionSpecificHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowWhenMethodHeaderIsMissing() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Mandatory header [:method] is missing.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.remove(METHOD.value());
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowWhenPathHeaderIsMissing() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Mandatory header [:path] is missing.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.remove(PATH.value());
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowWhenPathHeaderIsEmpty() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("[:path] header cannot be empty.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.set(PATH.value(), "");
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowWhenSchemeHeaderIsMissing() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Mandatory header [:scheme] is missing.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.remove(SCHEME.value());
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfMethodHeaderIsNotUnique() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Header [:method] should have a unique value.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.add(METHOD.value(), "GET");
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfPathHeaderIsNotUnique() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Header [:path] should have a unique value.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.add(PATH.value(), "/");
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfSchemeHeaderIsNotUnique() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Header [:scheme] should have a unique value.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.add(SCHEME.value(), "/");
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfMethodHeaderIsNotUniqueWhenMethodIsConnect() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Header [:method] should have a unique value.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.remove(SCHEME.value());
        headers.remove(PATH.value());
        headers.set(METHOD.value(), "CONNECT");
        headers.add(METHOD.value(), "CONNECT");
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfPathHeaderIsPresentWhenMethodIsConnect() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Header [:path] must be omitted when using CONNECT method.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.set(METHOD.value(), "CONNECT");
        headers.remove(SCHEME.value());
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfSchemeHeaderIsPresentWhenMethodIsConnect() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Header [:scheme] must be omitted when using CONNECT method.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.set(METHOD.value(), "CONNECT");
        headers.remove(PATH.value());
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

    @Test
    public void validatePseudoHeadersShouldThrowIfResponseHeaderInRequest() throws Http2Exception {
        expectedException.expect(StreamException.class);
        expectedException.expectMessage("Response pseudo-header [:status] is not allowed in a request.");

        final Http2Headers headers = newHttp2HeadersWithRequestPseudoHeaders();
        headers.add(STATUS.value(), "200");
        validateRequestPseudoHeaders(headers, STREAM_ID);
    }

}
