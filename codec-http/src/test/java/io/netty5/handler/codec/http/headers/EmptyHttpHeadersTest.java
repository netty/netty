/*
 * Copyright 2023 The Netty Project
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
package io.netty5.handler.codec.http.headers;

import org.junit.jupiter.api.Assumptions;

public class EmptyHttpHeadersTest extends AbstractHttpHeadersTest {
    // this basically tests that emptyHeaders() behaves like a normal header map, including validation

    @Override
    protected HttpHeaders newHeaders() {
        return HttpHeaders.emptyHeaders();
    }

    @Override
    protected HttpHeaders newHeaders(int initialSizeHint) {
        return Assumptions.abort("Empty headers have a fixed size hint");
    }
}
