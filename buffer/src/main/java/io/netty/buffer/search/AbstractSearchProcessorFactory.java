/*
 * Copyright 2020 The Netty Project
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
package io.netty.buffer.search;

/**
 * Base class for precomputed factories that create {@link SearchProcessor}s.
 * <br>
 * Different factories implement different search algorithms with performance characteristics that
 * depend on a use case, so it is advisable to benchmark a concrete use case with different algorithms
 * before choosing one of them.
 * <br>
 * A concrete instance of {@link AbstractSearchProcessorFactory} is built for searching for a concrete sequence of bytes
 * (the {@code needle}), it contains precomputed data needed to perform the search, and is meant to be reused
 * whenever searching for the same {@code needle}.
 * <br>
 * <b>Note:</b> implementations of {@link SearchProcessor} scan the {@link io.netty.buffer.ByteBuf} sequentially,
 * one byte after another, without doing any random access. As a result, when using {@link SearchProcessor}
 * with such methods as {@link io.netty.buffer.ByteBuf#forEachByte}, these methods return the index of the last byte
 * of the found byte sequence within the {@link io.netty.buffer.ByteBuf} (which might feel counterintuitive,
 * and different from {@link io.netty.buffer.ByteBufUtil#indexOf} which returns the index of the first byte
 * of found sequence).
 * <br>
 * A {@link SearchProcessor} is implemented as a
 * <a href="https://en.wikipedia.org/wiki/Finite-state_machine">Finite State Automaton</a> that contains a
 * small internal state which is updated with every byte processed. As a result, an instance of {@link SearchProcessor}
 * should not be reused across independent search sessions (eg. for searching in different
 * {@link io.netty.buffer.ByteBuf}s). A new instance should be created with {@link AbstractSearchProcessorFactory} for
 * every search session. However, a {@link SearchProcessor} can (and should) be reused within the search session,
 * eg. when searching for all occurrences of the {@code needle} within the same {@code haystack}. That way, it can
 * also detect overlapping occurrences of the {@code needle} (eg. a string "ABABAB" contains two occurences of "BAB"
 * that overlap by one character "B"). For this to work correctly, after an occurrence of the {@code needle} is
 * found ending at index {@code idx}, the search should continue starting from the index {@code idx + 1}.
 * <br>
 * Example (given that the {@code haystack} is a {@link io.netty.buffer.ByteBuf} containing "ABABAB" and
 * the {@code needle} is "BAB"):
 * <pre>
 *     SearchProcessorFactory factory =
 *         SearchProcessorFactory.newKmpSearchProcessorFactory(needle.getBytes(CharsetUtil.UTF_8));
 *     SearchProcessor processor = factory.newSearchProcessor();
 *
 *     int idx1 = haystack.forEachByte(processor);
 *     // idx1 is 3 (index of the last character of the first occurrence of the needle in the haystack)
 *
 *     int continueFrom1 = idx1 + 1;
 *     // continue the search starting from the next character
 *
 *     int idx2 = haystack.forEachByte(continueFrom1, haystack.readableBytes() - continueFrom1, processor);
 *     // idx2 is 5 (index of the last character of the second occurrence of the needle in the haystack)
 *
 *     int continueFrom2 = idx2 + 1;
 *     // continue the search starting from the next character
 *
 *     int idx3 = haystack.forEachByte(continueFrom2, haystack.readableBytes() - continueFrom2, processor);
 *     // idx3 is -1 (no more occurrences of the needle)
 *
 *     // After this search session is complete, processor should be discarded.
 *     // To search for the same needle again, reuse the same factory to get a new SearchProcessor.
 * </pre>
 */
public abstract class AbstractSearchProcessorFactory implements SearchProcessorFactory {

    /**
     * Creates a {@link SearchProcessorFactory} based on
     * <a href="https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm">Knuth-Morris-Pratt</a>
     * string search algorithm. It is a reasonable default choice among the provided algorithms.
     * <br>
     * Precomputation (this method) time is linear in the size of input ({@code O(|needle|)}).
     * <br>
     * The factory allocates and retains an int array of size {@code needle.length + 1}, and retains a reference
     * to the {@code needle} itself.
     * <br>
     * Search (the actual application of {@link SearchProcessor}) time is linear in the size of
     * {@link io.netty.buffer.ByteBuf} on which the search is peformed ({@code O(|haystack|)}).
     * Every byte of {@link io.netty.buffer.ByteBuf} is processed only once, sequentually.
     *
     * @param needle an array of bytes to search for
     * @return a new instance of {@link KmpSearchProcessorFactory} precomputed for the given {@code needle}
     */
    public static KmpSearchProcessorFactory newKmpSearchProcessorFactory(byte[] needle) {
        return new KmpSearchProcessorFactory(needle);
    }

    /**
     * Creates a {@link SearchProcessorFactory} based on Bitap string search algorithm.
     * It is a jump free algorithm that has very stable performance (the contents of the inputs have a minimal
     * effect on it). The limitation is that the {@code needle} can be no more than 64 bytes long.
     * <br>
     * Precomputation (this method) time is linear in the size of the input ({@code O(|needle|)}).
     * <br>
     * The factory allocates and retains a long[256] array.
     * <br>
     * Search (the actual application of {@link SearchProcessor}) time is linear in the size of
     * {@link io.netty.buffer.ByteBuf} on which the search is peformed ({@code O(|haystack|)}).
     * Every byte of {@link io.netty.buffer.ByteBuf} is processed only once, sequentually.
     *
     * @param needle an array <b>of no more than 64 bytes</b> to search for
     * @return a new instance of {@link BitapSearchProcessorFactory} precomputed for the given {@code needle}
     */
    public static BitapSearchProcessorFactory newBitapSearchProcessorFactory(byte[] needle) {
        return new BitapSearchProcessorFactory(needle);
    }

}
