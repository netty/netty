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

import io.netty.buffer.search.AhoCorasicSearchProcessor.TrieNode;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Factory that creates {@link AhoCorasicSearchProcessor}.
 * Use static {@link MultiSearchProcessorFactory#newAhoCorasicSearchProcessorFactory}
 * to create an instance of this factory.
 * @see MultiSearchProcessorFactory
 */
public class AhoCorasicSearchProcessorFactory extends MultiSearchProcessorFactory {

    private final TrieNode trieRoot;

    AhoCorasicSearchProcessorFactory(byte[] ...needles) {
        this.trieRoot = buildTrie(needles);
        linkSuffixes(trieRoot);
    }

    private static TrieNode buildTrie(byte[]... needles) {
        final TrieNode trieRoot = new TrieNode();

        for (int i = 0; i < needles.length; i++) {
            byte[] needle = needles[i];
            TrieNode currentNode = trieRoot;

            for (byte ch0: needle) {
                final int ch = ch0 & 0xff;
                if (!currentNode.hasChildFor(ch)) {
                    currentNode.children[ch] = new TrieNode();
                }
                currentNode = currentNode.children[ch];
            }

            currentNode.matchForNeedleId = i;
        }

        return trieRoot;
    }

    private static void linkSuffixes(TrieNode trieRoot) {

        Queue<TrieNode> queue = new ArrayDeque<TrieNode>();
        queue.add(trieRoot);
        Map<TrieNode, TrieNode> suffixLinks = new HashMap<TrieNode, TrieNode>();

        while (!queue.isEmpty()) {
            final TrieNode v = queue.remove();
            final TrieNode vLink = suffixLinks.get(v);
            final TrieNode u = vLink != null ? vLink : v;
            if (v.matchForNeedleId == -1) {
                v.matchForNeedleId = u.matchForNeedleId;
            }
            for (int ch = 0; ch < 256; ch++) {
                if (v.hasChildFor(ch)) {
                    final TrieNode link = suffixLinks.containsKey(v) && u.hasChildFor(ch) ? u.children[ch] : trieRoot;
                    suffixLinks.put(v.children[ch], link);
                    queue.add(v.children[ch]);
                } else {
                    v.children[ch] = u.hasChildFor(ch) ? u.children[ch] : trieRoot;
                }
            }
        }
    }

    /**
     * Returns a new {@link AhoCorasicSearchProcessor}.
     */
    @Override
    public AhoCorasicSearchProcessor newSearchProcessor() {
        return new AhoCorasicSearchProcessor(trieRoot);
    }

}
