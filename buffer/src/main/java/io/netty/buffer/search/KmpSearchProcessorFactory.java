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
 * Factory that creates {@link KmpSearchProcessor}.
 * Use static {@link AbstractSearchProcessorFactory#newKmpSearchProcessorFactory}
 * to create an instance of this factory.
 * @see SearchProcessorFactory
 */
public class KmpSearchProcessorFactory extends AbstractSearchProcessorFactory {

    private final int[] jumpTable;
    private final byte[] needle;

    KmpSearchProcessorFactory(byte[] needle) {
        this.needle = needle.clone();
        this.jumpTable = new int[needle.length + 1];

        int j = 0;
        for (int i = 1; i < needle.length; i++) {
            while (j > 0 && needle[j] != needle[i]) {
                j = jumpTable[j];
            }
            if (needle[j] == needle[i]) {
                j++;
            }
            jumpTable[i + 1] = j;
        }
    }

    /**
     * Returns a new {@link KmpSearchProcessor}.
     */
    @Override
    public KmpSearchProcessor newSearchProcessor() {
        return new KmpSearchProcessor(needle, jumpTable);
    }

}
