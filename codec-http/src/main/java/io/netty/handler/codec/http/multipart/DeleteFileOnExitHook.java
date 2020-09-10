/*
 * Copyright 2012 The Netty Project
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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Extend {@link java.io.DeleteOnExitHook} to dynamically manipulate hook records.
 */
public final class DeleteFileOnExitHook {
    private static Set<String> files = new HashSet<String>();
    static final InternalLogger logger = InternalLoggerFactory.getInstance(DeleteFileOnExitHook.class);

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
                runHooks();
            }
        });
    }

    /**
     * Remove from the pool to reduce space footprint.
     *
     * @param file tmp file path
     */
    public static synchronized void remove(String file) {
        try {
            if (files != null) {
                files.remove(file);
            }
        } catch (Exception e) {
            logger.warn("The cleanup file path failed.", e);
        }
    }

    /**
     * Add to the hook and clean up when the program exits.
     *
     * @param file tmp file path
     */
    public static synchronized void add(String file) {
        if (files == null) {
            files = new HashSet<String>();
        }

        files.add(file);
    }

    static void runHooks() {
        Set<String> toBeDeleted;

        synchronized (DeleteFileOnExitHook.class) {
            toBeDeleted = files;
            files = null;
        }

        if (!toBeDeleted.isEmpty()) {
            for (String filename : toBeDeleted) {
                (new File(filename)).delete();
            }
        }
    }
}
