/*
 *
 *  * Copyright 2020 The Netty Project
 *  *
 *  * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 *  * "License"); you may not use this file except in compliance with the License. You may obtain a
 *  * copy of the License at:
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License
 *  * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 *  * or implied. See the License for the specific language governing permissions and limitations under
 *  * the License.
 *
 */

package io.netty.example.nettymsg;

import com.google.gson.Gson;

public class GsonUtil {
    private static final Gson GSON = new Gson();

    public static <T> T parse(String str, Class<T> classOfT) {
        return GSON.fromJson(str, classOfT);
    }

    public static String toJson(Object src) {
        return GSON.toJson(src);
    }
}
