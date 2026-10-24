/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.handler.codec.compression.StandardCompressionOptions;
import org.junit.jupiter.api.Test;

import static io.netty.handler.codec.http.HttpHeaderValues.BR;
import static io.netty.handler.codec.http.HttpHeaderValues.GZIP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class Http2ContentEncodingNegotiatorTest {

    @Test
    public void nullAcceptEncodingYieldsNoNegotiation() {
        Http2ContentEncodingNegotiator negotiator =
                new Http2ContentEncodingNegotiator(StandardCompressionOptions.gzip());

        assertNull(negotiator.negotiate(null));
    }

    @Test
    public void identityAcceptEncodingYieldsNoNegotiation() {
        Http2ContentEncodingNegotiator negotiator =
                new Http2ContentEncodingNegotiator(StandardCompressionOptions.gzip());

        assertNull(negotiator.negotiate("identity"));
    }

    @Test
    public void gzipAcceptEncodingNegotiatesGzip() {
        Http2ContentEncodingNegotiator negotiator =
                new Http2ContentEncodingNegotiator(StandardCompressionOptions.gzip());

        NegotiatedEncoding negotiated = negotiator.negotiate("gzip");

        assertEquals(GZIP.toString(), negotiated.contentEncoding.toString());
    }

    /**
     * Mirrors {@code HttpContentCompressor.determineEncoding}: encodings are chosen by a fixed
     * priority cascade ({@code br > zstd > snappy > gzip > deflate}), with each tier only compared
     * against the next higher tier's q-value -- not by a single global q-value ranking across all
     * tiers. Verified directly against {@code HttpContentCompressor.determineEncoding(
     * "br;q=0.5, gzip;q=0.8")}, which likewise returns {@code "br"} when both encodings are
     * configured.
     */
    @Test
    public void brotliOutranksGzipRegardlessOfQValue() {
        Http2ContentEncodingNegotiator negotiator = new Http2ContentEncodingNegotiator(
                StandardCompressionOptions.gzip(), StandardCompressionOptions.brotli());

        NegotiatedEncoding negotiated = negotiator.negotiate("br;q=0.5, gzip;q=0.8");

        assertEquals(BR.toString(), negotiated.contentEncoding.toString());
    }

    @Test
    public void unsupportedEncodingYieldsNoNegotiation() {
        Http2ContentEncodingNegotiator negotiator =
                new Http2ContentEncodingNegotiator(StandardCompressionOptions.gzip());

        assertNull(negotiator.negotiate("br"));
    }

    @Test
    public void wildcardAcceptEncodingNegotiatesOnlyConfiguredOption() {
        Http2ContentEncodingNegotiator negotiator =
                new Http2ContentEncodingNegotiator(StandardCompressionOptions.gzip());

        NegotiatedEncoding negotiated = negotiator.negotiate("*");

        assertEquals(GZIP.toString(), negotiated.contentEncoding.toString());
    }
}
