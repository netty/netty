/*
* Copyright 2020 The Netty Project
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
package io.netty.microbench.search;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;

public class IndexOfBenchmark extends AbstractSearchMicrobenchmark {

    private ByteBuf http1Needle;
    private ByteBuf worstCaseNeedle;

    @Setup
    public void setup() {
        super.setup();
        http1Needle = Unpooled.wrappedBuffer(http1Bytes);
        worstCaseNeedle = Unpooled.wrappedBuffer(worstCaseBytes);
    }

    @TearDown
    public void tearDown() {
        http1Needle.release();
        worstCaseNeedle.release();
    }

    @Benchmark
    public void http1IndexOf() {
        ByteBufUtil.indexOf(http1Needle, haystack);
    }

    @Benchmark
    public void worstCaseIndexOf() {
        ByteBufUtil.indexOf(worstCaseNeedle, haystack);
    }

}
