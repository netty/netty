/*
 * Copyright 2026 The Netty Project
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
package java.lang;

/**
 * Compile-time only stub for the Java 9 ProcessHandle API.
 * This class must never be loaded on Java 8. Use a guarded holder class
 * when calling this API from sources compiled for Java 8.
 */
public interface ProcessHandle {

    static ProcessHandle current() {
        throw new UnsupportedOperationException("Compile-time only stub");
    }

    long pid();
}
