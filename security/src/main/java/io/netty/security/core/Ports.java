/*
 * Copyright 2022 The Netty Project
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
package io.netty.security.core;

public interface Ports extends PortLookup, Comparable<Ports> {

    /**
     * Accept any port number
     */
    Ports ANYPORT = new Ports() {
        @Override
        public boolean equals(Object o) {
            return getClass() == o.getClass();
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public int start() {
            return 1;
        }

        @Override
        public int end() {
            return 65_535;
        }

        @Override
        public int compareTo(Ports ports) {
            return 0;
        }

        @Override
        public int lookup(int port) {
            return 0;
        }

        @Override
        public boolean lookupPort(int port) {
            return true;
        }
    };

    /**
     * Start of Port range
     */
    int start();

    /**
     * End of Port range
     */
    int end();

    @Override
    int compareTo(Ports ports);
}
