/*
 * Copyright 2017 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static io.netty.handler.codec.http.HttpConstants.CR;
import static io.netty.handler.codec.http.HttpConstants.LF;
import static io.netty.handler.codec.http.HttpConstants.SP;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
public class HttpRequestEncoderInsertBenchmark extends AbstractMicrobenchmark {

    private static final String[] PARAMS = {
            "eventType=CRITICAL",
            "from=0",
            "to=1497437160327",
            "limit=10",
            "offset=0"
    };
    @Param({"1024", "128000"})
    private int samples;

    private String[] uris;
    private int index;
    private final OldHttpRequestEncoder encoderOld = new OldHttpRequestEncoder();
    private final HttpRequestEncoder encoderNew = new HttpRequestEncoder();

    @Setup
    public void setup() {
        List<String[]> permutations = new ArrayList<String[]>();
        permute(PARAMS.clone(), 0, permutations);

        String[] allCombinations = new String[permutations.size()];
        String base = "http://localhost?";
        for (int i = 0; i < permutations.size(); i++) {
            StringBuilder sb = new StringBuilder(base);
            String[] p = permutations.get(i);
            for (int j = 0; j < p.length; j++) {
                if (j != 0) {
                    sb.append('&');
                }
                sb.append(p[j]);
            }
            allCombinations[i] = sb.toString();
        }
        uris = new String[samples];
        Random rand = new Random(42);
        for (int i = 0; i < uris.length; i++) {
            uris[i] = allCombinations[rand.nextInt(allCombinations.length)];
        }
        index = 0;
    }

    private static void permute(String[] arr, int start, List<String[]> out) {
        if (start == arr.length - 1) {
            out.add(Arrays.copyOf(arr, arr.length));
            return;
        }
        for (int i = start; i < arr.length; i++) {
            swap(arr, start, i);
            permute(arr, start + 1, out);
            swap(arr, start, i);
        }
    }

    private static void swap(String[] a, int i, int j) {
        String t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private String nextUri() {
        if (index >= uris.length) {
            index = 0;
        }
        return uris[index++];
    }

    @Benchmark
    public ByteBuf oldEncoder() throws Exception {
        ByteBuf buffer = Unpooled.buffer(100);
        try {
            encoderOld.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, nextUri()));
            return buffer;
        } finally {
            buffer.release();
        }
    }

    @Benchmark
    public ByteBuf newEncoder() throws Exception {
        ByteBuf buffer = Unpooled.buffer(100);
        try {
            encoderNew.encodeInitialLine(buffer, new DefaultHttpRequest(HttpVersion.HTTP_1_1,
                    HttpMethod.GET, nextUri()));
            return buffer;
        } finally {
            buffer.release();
        }
    }

    private static class OldHttpRequestEncoder extends HttpObjectEncoder<HttpRequest> {
        private static final byte[] CRLF = {CR, LF};
        private static final char SLASH = '/';
        private static final char QUESTION_MARK = '?';

        @Override
        public boolean acceptOutboundMessage(Object msg) throws Exception {
            return super.acceptOutboundMessage(msg) && !(msg instanceof HttpResponse);
        }

        @Override
        protected void encodeInitialLine(ByteBuf buf, HttpRequest request) throws Exception {
            AsciiString method = request.method().asciiName();
            ByteBufUtil.copy(method, method.arrayOffset(), buf, method.length());
            buf.writeByte(SP);

            // Add / as absolute path if no is present.
            // See https://tools.ietf.org/html/rfc2616#section-5.1.2
            String uri = request.uri();

            if (uri.isEmpty()) {
                uri += SLASH;
            } else {
                int start = uri.indexOf("://");
                if (start != -1 && uri.charAt(0) != SLASH) {
                    int startIndex = start + 3;
                    // Correctly handle query params.
                    // See https://github.com/netty/netty/issues/2732
                    int index = uri.indexOf(QUESTION_MARK, startIndex);
                    if (index == -1) {
                        if (uri.lastIndexOf(SLASH) <= startIndex) {
                            uri += SLASH;
                        }
                    } else {
                        if (uri.lastIndexOf(SLASH, index) <= startIndex) {
                            int len = uri.length();
                            StringBuilder sb = new StringBuilder(len + 1);
                            sb.append(uri, 0, index)
                                    .append(SLASH)
                                    .append(uri, index, len);
                            uri = sb.toString();
                        }
                    }
                }
            }

            buf.writeBytes(uri.getBytes(CharsetUtil.UTF_8));

            buf.writeByte(SP);
            request.protocolVersion().encode(buf);
            buf.writeBytes(CRLF);
        }
    }
}
