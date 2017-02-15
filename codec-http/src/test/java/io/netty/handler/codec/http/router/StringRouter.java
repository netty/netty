/*
 * Copyright 2015 The Netty Project
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

final class StringRouter {
    // Utility classes should not have a public or default constructor.
    private StringRouter() { }

    public static final Router<String> router = new Router<String>()
              .GET("/articles",             "index")
              .GET("/articles/:id",         "show")
              .GET("/articles/:id/:format", "show")
        .GET_FIRST("/articles/new",         "new")
             .POST("/articles",             "post")
            .PATCH("/articles/:id",         "patch")
           .DELETE("/articles/:id",         "delete")
              .ANY("/anyMethod",            "anyMethod")
              .GET("/download/:*",          "download")
         .notFound("404");

    // Visualize the routes only once
    static {
        System.out.println(router.toString());
    }
}

interface Action { }
class Index implements Action { }
class Show  implements Action { }
