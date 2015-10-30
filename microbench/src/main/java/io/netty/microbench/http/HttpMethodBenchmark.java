
/*
 * Copyright 2015 The Netty Project
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
package io.netty.microbench.http;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static io.netty.handler.codec.http.HttpMethod.CONNECT;
import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.HEAD;
import static io.netty.handler.codec.http.HttpMethod.OPTIONS;
import static io.netty.handler.codec.http.HttpMethod.PATCH;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpMethod.TRACE;

@Threads(1)
@State(Scope.Benchmark)
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@Fork(1)
public class HttpMethodBenchmark extends AbstractMicrobenchmark {

    /**
     * Length should be power of 2!
     */
    private static final String[] METHODS = new String[] { "OPTIONS", "GET", "HEAD", "POST", "PUT", "POST", "PATCH",
                                                           "DELETE", "TRACE", "CONNECT", "10fooo", "11goooo", "12test",
                                                           "13thisisalongerstring", "14", "15" };
    private static final int METHODS_MASK = METHODS.length - 1;

    private static final Map<String, HttpMethod> methodMapOld = new HashMap<String, HttpMethod>();
    private static final Map<String, HttpMethod> methodMapNew = new TreeMap<String, HttpMethod>();

    static {
        methodMapOld.put(OPTIONS.toString(), OPTIONS);
        methodMapOld.put(GET.toString(), GET);
        methodMapOld.put(HEAD.toString(), HEAD);
        methodMapOld.put(POST.toString(), POST);
        methodMapOld.put(PUT.toString(), PUT);
        methodMapOld.put(PATCH.toString(), PATCH);
        methodMapOld.put(DELETE.toString(), DELETE);
        methodMapOld.put(TRACE.toString(), TRACE);
        methodMapOld.put(CONNECT.toString(), CONNECT);

        methodMapNew.put(OPTIONS.toString(), OPTIONS);
        methodMapNew.put(GET.toString(), GET);
        methodMapNew.put(HEAD.toString(), HEAD);
        methodMapNew.put(POST.toString(), POST);
        methodMapNew.put(PUT.toString(), PUT);
        methodMapNew.put(PATCH.toString(), PATCH);
        methodMapNew.put(DELETE.toString(), DELETE);
        methodMapNew.put(TRACE.toString(), TRACE);
        methodMapNew.put(CONNECT.toString(), CONNECT);
    }

    private int i = -1;

    @Benchmark
    public boolean testValueOfOld() {
        i = (i + 1) & METHODS_MASK;
        return methodMapOld.get(METHODS[i]) != null;
    }

    @Benchmark
    public boolean testValueOfNew() {
        i = (i + 1) & METHODS_MASK;
        return methodMapNew.get(METHODS[i]) != null;
    }
}
