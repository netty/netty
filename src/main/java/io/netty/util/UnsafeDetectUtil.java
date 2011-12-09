/*
 * Copyright 2011 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Utility which checks if {@value #UNSAFE} class can be found in the classpath
 * 
 * 
 *
 * @author <a href="http://netty.io/">The Netty Project</a>
 * @author <a href="http://www.murkycloud.com/">Norman Maurer</a>
 *
 */
public class UnsafeDetectUtil {

    private static final String UNSAFE = "sun.misc.Unsafe";
    private static final boolean UNSAFE_FOUND = isUnsafeFound(AtomicInteger.class.getClassLoader());

    public static boolean isUnsafeFound(ClassLoader loader) {
        try {
            Class.forName(UNSAFE, true, loader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isUnsafeFound() {
        return UNSAFE_FOUND;
    }

    private UnsafeDetectUtil() {
        // only static method supported
    }
}
