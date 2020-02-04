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
 * Implements <a href="https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm">Aho–Corasick</a>
 * string search algorithm as {@link io.netty.util.ByteProcessor}.
 * @see MultiSearchProcessorFactory
 */
public class AhoCorasicSearchProcessor implements MultiSearchProcessor {

    static class TrieNode {
        final TrieNode[] children = new TrieNode[256];
        int matchForNeedleId = -1;

        boolean hasChildFor(int ch) {
            return children[ch] != null;
        }
    }

    private TrieNode currentNode;

    AhoCorasicSearchProcessor(TrieNode trieRoot) {
        currentNode = trieRoot;
    }

    @Override
    public boolean process(byte value) {
        currentNode = currentNode.children[value & 0xff];
        return currentNode.matchForNeedleId == -1;
    }

    @Override
    public int getFoundNeedleId() {
        return currentNode.matchForNeedleId;
    }

}
