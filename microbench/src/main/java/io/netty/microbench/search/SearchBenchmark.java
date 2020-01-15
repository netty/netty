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

import io.netty.buffer.search.MultiSearchProcessorFactory;
import io.netty.buffer.search.SearchProcessorFactory;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

public class SearchBenchmark extends AbstractSearchMicrobenchmark {

    public enum Algorithm {
        KNUTH_MORRIS_PRATT {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return SearchProcessorFactory.newKmpSearchProcessorFactory(needle);
            }
        },
        SHIFTING_BIT_MASK {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return SearchProcessorFactory.newShiftingBitMaskSearchProcessorFactory(needle);
            }
        },
        AHO_CORASIC {
            @Override
            SearchProcessorFactory newFactory(byte[] needle) {
                return MultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(needle);
            }
        };
        abstract SearchProcessorFactory newFactory(byte[] needle);
    }

    @Param
    public Algorithm algorithm;

    private SearchProcessorFactory http1SearchProcessorFactory;
    private SearchProcessorFactory worstCaseSearchProcessorFactory;

    @Setup
    public void setup() {
        super.setup();
        http1SearchProcessorFactory = algorithm.newFactory(http1Bytes);
        worstCaseSearchProcessorFactory = algorithm.newFactory(worstCaseBytes);
    }

    @Benchmark
    public void http1Search() {
        haystack.forEachByte(http1SearchProcessorFactory.newSearchProcessor());
    }

    @Benchmark
    public void worstCaseSearch() {
        haystack.forEachByte(worstCaseSearchProcessorFactory.newSearchProcessor());
    }

}
