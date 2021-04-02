/*
 * Copyright 2018 The Netty Project
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
package io.netty.util.internal;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * A utility class that provides various common operations and constants
 * related to loading resources
 */
public final class ResourcesUtil {

    /**
     * Returns a {@link File} named {@code fileName} associated with {@link Class} {@code resourceClass} .
     *
     * @param resourceClass The associated class
     * @param fileName The file name
     * @return The file named {@code fileName} associated with {@link Class} {@code resourceClass} .
     */
    public static File getFile(Class resourceClass, String fileName) {
        try {
            return new File(URLDecoder.decode(resourceClass.getResource(fileName).getFile(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            return new File(resourceClass.getResource(fileName).getFile());
        }
    }

    private ResourcesUtil() { }
}
