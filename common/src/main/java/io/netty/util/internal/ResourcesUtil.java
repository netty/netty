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
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * A utility class that provides various common operations and constants
 * related to loading resources
 */
public final class ResourcesUtil {

    /**
     * Returns a {@link File} for the given resource {@link URL}.
     * <p>
     * The {@link URL} should be obtained by the caller, e.g. via {@code getClass().getResource("name")}.
     * Resource lookups are caller-sensitive when running on the module path, so looking the resource up here,
     * on behalf of a class in another module, would not find resources in packages that are not open to this module.
     *
     * @param resourceUrl The resource {@link URL}, as returned by {@link Class#getResource(String)}.
     * @return The {@link File} that the {@code resourceUrl} points to.
     * @throws NullPointerException if {@code resourceUrl} is {@code null}, which is what
     * {@link Class#getResource(String)} returns if the resource was not found.
     */
    public static File getFile(URL resourceUrl) {
        if (resourceUrl == null) {
            throw new NullPointerException("resourceUrl (resource not found, or not visible to the caller)");
        }
        return new File(URLDecoder.decode(resourceUrl.getFile(), StandardCharsets.UTF_8));
    }

    private ResourcesUtil() { }
}
