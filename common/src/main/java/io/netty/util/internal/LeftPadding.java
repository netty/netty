/*
 * Copyright 2014 The Netty Project
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

package io.netty.util.internal;

import java.io.Serializable;

abstract class LeftPadding implements Serializable {
    private static final long serialVersionUID = -9129166504419549394L;
    // cache line padding (must be public)
    public transient long lp1, lp2, lp3, lp4, lp5, lp6;           // 48 bytes (excluding 16-byte object header)
    public transient long lpA, lpB, lpC, lpD, lpE, lpF, lpG, lpH; // 64 bytes
}
