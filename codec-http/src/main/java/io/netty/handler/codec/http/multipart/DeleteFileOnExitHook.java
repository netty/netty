/*
 * Copyright 2020 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DeleteFileOnExitHook.
 */
final class DeleteFileOnExitHook {
    private static final Set<String> FILES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private DeleteFileOnExitHook() {
    }

    static {
        // DeleteOnExitHook must be the last shutdown hook to be invoked.
        // Application shutdown hooks may add the first file to the
        // delete on exit list and cause the DeleteOnExitHook to be
        // registered during shutdown in progress.
        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                runHook();
            }
        });
    }

    /**
     * Remove from the pool to reduce space footprint.
     *
     * @param file tmp file path
     */
    public static void remove(String file) {
        FILES.remove(file);
    }

    /**
     * Add to the hook and clean up when the program exits.
     *
     * @param file tmp file path
     */
    public static void add(String file) {
        FILES.add(file);
    }

    /**
     * Check in the hook files.
     *
     * @param file target file
     * @return true or false
     */
    public static boolean checkFileExist(String file) {
        return FILES.contains(file);
    }

    /**
     * Clean up all the files.
     */
    static void runHook() {
        for (String filename : FILES) {
            new File(filename).delete();
        }
    }
}
