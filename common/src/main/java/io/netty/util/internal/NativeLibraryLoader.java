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
package io.netty.util.internal;

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helper class to load JNI resources.
 *
 */
public final class NativeLibraryLoader {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NativeLibraryLoader.class);

    private static final Pattern REPLACE = Pattern.compile("\\W+");
    private static final File WORKDIR;

    static {
        String workdir = SystemPropertyUtil.get("io.netty.native.workdir");
        if (workdir != null) {
            File f = new File(workdir);
            if (!f.exists()) {
                // ok to ignore as createTempFile will take care
                //noinspection ResultOfMethodCallIgnored
                f.mkdirs();
            }

            try {
                f = f.getAbsoluteFile();
            } catch (Exception ignored) {
                // Good to have an absolute path, but it's OK.
            }

            WORKDIR = f;
            logger.debug("-Dio.netty.netty.workdir: {}", WORKDIR);
        } else {
            WORKDIR = PlatformDependent.tmpdir();
            logger.debug("-Dio.netty.netty.workdir: {} (io.netty.tmpdir)", WORKDIR);
        }
    }

    /**
     * Load the given library with the specified {@link java.lang.ClassLoader}
     */
    public static void load(String name, ClassLoader loader) {
        String libname = System.mapLibraryName(name);
        String path = "META-INF/native/" + osIdentifier() + PlatformDependent.bitMode() + '/' + libname;

        URL url = loader.getResource(path);
        if (url == null) {
            // Fall back to normal loading of JNI stuff
            System.loadLibrary(name);
        } else {
            int index = libname.lastIndexOf('.');
            String prefix = libname.substring(0, index);
            String suffix = libname.substring(index, libname.length());
            InputStream in = null;
            OutputStream out = null;
            File tmpFile = null;
            boolean loaded = false;
            try {
                tmpFile = File.createTempFile(prefix, suffix, WORKDIR);
                in = url.openStream();
                out = new FileOutputStream(tmpFile);

                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                out.flush();
                out.close();
                out = null;

                System.load(tmpFile.getPath());
                loaded = true;
            } catch (Exception e) {
                throw (UnsatisfiedLinkError) new UnsatisfiedLinkError(
                        "could not load a native library: " + name).initCause(e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
                if (tmpFile != null) {
                    if (loaded) {
                        tmpFile.deleteOnExit();
                    } else {
                        if (!tmpFile.delete()) {
                            tmpFile.deleteOnExit();
                        }
                    }
                }
            }
        }
    }

    private static String osIdentifier() {
        String name = SystemPropertyUtil.get("os.name", "unknown").toLowerCase(Locale.US).trim();
        if (name.startsWith("win")) {
            return "windows";
        }
        if (name.startsWith("mac os x")) {
            return "osx";
        }
        if (name.startsWith("linux")) {
            return "linux";
        }

        return REPLACE.matcher(name).replaceAll("_");
    }

    private NativeLibraryLoader() {
        // Utility
    }
}
