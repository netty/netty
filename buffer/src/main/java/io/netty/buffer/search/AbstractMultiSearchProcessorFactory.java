/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer.search;

/**
 * Base class for precomputed factories that create {@link MultiSearchProcessor}s.
 * <br>
 * The purpose of {@link MultiSearchProcessor} is to perform efficient simultaneous search for multiple {@code needles}
 * in the {@code haystack}, while scanning every byte of the input sequentially, only once. While it can also be used
 * to search for just a single {@code needle}, using a {@link SearchProcessorFactory} would be more efficient for
 * doing that.
 * <br>
 * See the documentation of {@link AbstractSearchProcessorFactory} for a comprehensive description of common usage.
 * In addition to the functionality provided by {@link SearchProcessor}, {@link MultiSearchProcessor} adds
 * a method to get the index of the {@code needle} found at the current position of the {@link MultiSearchProcessor} -
 * {@link MultiSearchProcessor#getFoundNeedleId()}.
 * <br>
 * <b>Note:</b> in some cases one {@code needle} can be a suffix of another {@code needle}, eg. {@code {"BC", "ABC"}},
 * and there can potentially be multiple {@code needles} found ending at the same position of the {@code haystack}.
 * In such case {@link MultiSearchProcessor#getFoundNeedleId()} returns the index of the longest matching {@code needle}
 * in the array of {@code needles}.
 * <br>
 * Usage example (given that the {@code haystack} is a {@link io.netty.buffer.ByteBuf} containing "ABCD" and the
 * {@code needles} are "AB", "BC" and "CD"):
 * <pre>
 *      MultiSearchProcessorFactory factory = MultiSearchProcessorFactory.newAhoCorasicSearchProcessorFactory(
 *          "AB".getBytes(CharsetUtil.UTF_8), "BC".getBytes(CharsetUtil.UTF_8), "CD".getBytes(CharsetUtil.UTF_8));
 *      MultiSearchProcessor processor = factory.newSearchProcessor();
 *
 *      int idx1 = haystack.forEachByte(processor);
 *      // idx1 is 1 (index of the last character of the occurrence of "AB" in the haystack)
 *      // processor.getFoundNeedleId() is 0 (index of "AB" in needles[])
 *
 *      int continueFrom1 = idx1 + 1;
 *      // continue the search starting from the next character
 *
 *      int idx2 = haystack.forEachByte(continueFrom1, haystack.readableBytes() - continueFrom1, processor);
 *      // idx2 is 2 (index of the last character of the occurrence of "BC" in the haystack)
 *      // processor.getFoundNeedleId() is 1 (index of "BC" in needles[])
 *
 *      int continueFrom2 = idx2 + 1;
 *
 *      int idx3 = haystack.forEachByte(continueFrom2, haystack.readableBytes() - continueFrom2, processor);
 *      // idx3 is 3 (index of the last character of the occurrence of "CD" in the haystack)
 *      // processor.getFoundNeedleId() is 2 (index of "CD" in needles[])
 *
 *      int continueFrom3 = idx3 + 1;
 *
 *      int idx4 = haystack.forEachByte(continueFrom3, haystack.readableBytes() - continueFrom3, processor);
 *      // idx4 is -1 (no more occurrences of any of the needles)
 *
 *      // This search session is complete, processor should be discarded.
 *      // To search for the same needles again, reuse the same {@link AbstractMultiSearchProcessorFactory}
 *      // to get a new MultiSearchProcessor.
 * </pre>
 */
public abstract class AbstractMultiSearchProcessorFactory implements MultiSearchProcessorFactory {

    /**
     * Creates a {@link MultiSearchProcessorFactory} based on
     * <a href="https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm">Aho–Corasick</a>
     * string search algorithm.
     * <br>
     * Precomputation (this method) time is linear in the size of input ({@code O(Σ|needles|)}).
     * <br>
     * The factory allocates and retains an array of 256 * X ints plus another array of X ints, where X
     * is the sum of lengths of each entry of {@code needles} minus the sum of lengths of repeated
     * prefixes of the {@code needles}.
     * <br>
     * Search (the actual application of {@link MultiSearchProcessor}) time is linear in the size of
     * {@link io.netty.buffer.ByteBuf} on which the search is performed ({@code O(|haystack|)}).
     * Every byte of {@link io.netty.buffer.ByteBuf} is processed only once, sequentually, regardles of
     * the number of {@code needles} being searched for.
     *
     * @param needles a varargs array of arrays of bytes to search for
     * @return a new instance of {@link AhoCorasicSearchProcessorFactory} precomputed for the given {@code needles}
     */
    public static AhoCorasicSearchProcessorFactory newAhoCorasicSearchProcessorFactory(byte[] ...needles) {
        return new AhoCorasicSearchProcessorFactory(needles);
    }

}
