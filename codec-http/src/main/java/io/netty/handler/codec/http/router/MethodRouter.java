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
package io.netty.handler.codec.http.router;

import io.netty.handler.codec.http.HttpMethod;

public abstract class MethodRouter<T, RouteLike extends MethodRouter<T, RouteLike>>
extends io.netty.handler.codec.http.routing.Router<HttpMethod, T, RouteLike> {
    @Override protected HttpMethod CONNECT() { return HttpMethod.CONNECT; }
    @Override protected HttpMethod DELETE()  { return HttpMethod.DELETE ; }
    @Override protected HttpMethod GET()     { return HttpMethod.GET    ; }
    @Override protected HttpMethod HEAD()    { return HttpMethod.HEAD   ; }
    @Override protected HttpMethod OPTIONS() { return HttpMethod.OPTIONS; }
    @Override protected HttpMethod PATCH()   { return HttpMethod.PATCH  ; }
    @Override protected HttpMethod POST()    { return HttpMethod.POST   ; }
    @Override protected HttpMethod PUT()     { return HttpMethod.PUT    ; }
    @Override protected HttpMethod TRACE()   { return HttpMethod.TRACE  ; }
}
