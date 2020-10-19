/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.compression;

/**
 * A 256 entry Move To Front transform.
 */
final class Bzip2MoveToFrontTable {
    /**
     * The Move To Front list.
     */
    private final byte[] mtf = {
         0,    1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13,  14,  15,
         16,  17,  18,  19,  20,  21,  22,  23,  24,  25,  26,  27,  28,  29,  30,  31,
         32,  33,  34,  35,  36,  37,  38,  39,  40,  41,  42,  43,  44,  45,  46,  47,
         48,  49,  50,  51,  52,  53,  54,  55,  56,  57,  58,  59,  60,  61,  62,  63,
         64,  65,  66,  67,  68,  69,  70,  71,  72,  73,  74,  75,  76,  77,  78,  79,
         80,  81,  82,  83,  84,  85,  86,  87,  88,  89,  90,  91,  92,  93,  94,  95,
         96,  97,  98,  99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111,
        112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127,
        (byte) 128, (byte) 129, (byte) 130, (byte) 131, (byte) 132, (byte) 133, (byte) 134, (byte) 135,
        (byte) 136, (byte) 137, (byte) 138, (byte) 139, (byte) 140, (byte) 141, (byte) 142, (byte) 143,
        (byte) 144, (byte) 145, (byte) 146, (byte) 147, (byte) 148, (byte) 149, (byte) 150, (byte) 151,
        (byte) 152, (byte) 153, (byte) 154, (byte) 155, (byte) 156, (byte) 157, (byte) 158, (byte) 159,
        (byte) 160, (byte) 161, (byte) 162, (byte) 163, (byte) 164, (byte) 165, (byte) 166, (byte) 167,
        (byte) 168, (byte) 169, (byte) 170, (byte) 171, (byte) 172, (byte) 173, (byte) 174, (byte) 175,
        (byte) 176, (byte) 177, (byte) 178, (byte) 179, (byte) 180, (byte) 181, (byte) 182, (byte) 183,
        (byte) 184, (byte) 185, (byte) 186, (byte) 187, (byte) 188, (byte) 189, (byte) 190, (byte) 191,
        (byte) 192, (byte) 193, (byte) 194, (byte) 195, (byte) 196, (byte) 197, (byte) 198, (byte) 199,
        (byte) 200, (byte) 201, (byte) 202, (byte) 203, (byte) 204, (byte) 205, (byte) 206, (byte) 207,
        (byte) 208, (byte) 209, (byte) 210, (byte) 211, (byte) 212, (byte) 213, (byte) 214, (byte) 215,
        (byte) 216, (byte) 217, (byte) 218, (byte) 219, (byte) 220, (byte) 221, (byte) 222, (byte) 223,
        (byte) 224, (byte) 225, (byte) 226, (byte) 227, (byte) 228, (byte) 229, (byte) 230, (byte) 231,
        (byte) 232, (byte) 233, (byte) 234, (byte) 235, (byte) 236, (byte) 237, (byte) 238, (byte) 239,
        (byte) 240, (byte) 241, (byte) 242, (byte) 243, (byte) 244, (byte) 245, (byte) 246, (byte) 247,
        (byte) 248, (byte) 249, (byte) 250, (byte) 251, (byte) 252, (byte) 253, (byte) 254, (byte) 255
    };

    /**
     * Moves a value to the head of the MTF list (forward Move To Front transform).
     * @param value The value to move
     * @return The position the value moved from
     */
    int valueToFront(final byte value) {
        int index = 0;
        byte temp = mtf[0];
        if (value != temp) {
            mtf[0] = value;
            while (value != temp) {
                index++;
                final byte temp2 = temp;
                temp = mtf[index];
                mtf[index] = temp2;
            }
        }
        return index;
    }

    /**
     * Gets the value from a given index and moves it to the front of the MTF list (inverse Move To Front transform).
     * @param index The index to move
     * @return The value at the given index
     */
    byte indexToFront(final int index) {
        final byte value = mtf[index];
        System.arraycopy(mtf, 0, mtf, 1, index);
        mtf[0] = value;

        return value;
    }
}
