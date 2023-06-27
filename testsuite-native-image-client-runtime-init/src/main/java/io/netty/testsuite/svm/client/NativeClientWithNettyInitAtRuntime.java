/*
 * Copyright 2020 The Netty Project
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
package io.netty.testsuite.svm.client;

import io.netty.util.NetUtil;

/**
 * A client that triggers runtime initialization of NetUtil when
 * built to a native image.
 */
public final class NativeClientWithNettyInitAtRuntime {
    /**
     * Main entry point (not instantiable)
     */
    private NativeClientWithNettyInitAtRuntime() {
    }

    public static void main(String[] args) {
        System.out.println(NetUtil.LOCALHOST4);
        System.out.println(NetUtil.LOCALHOST6);
        System.out.println(NetUtil.LOCALHOST);
    }
}
