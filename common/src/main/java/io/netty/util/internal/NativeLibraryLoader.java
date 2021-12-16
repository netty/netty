/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

/**
 * Helper class to load JNI resources.
 *
 */
public final class NativeLibraryLoader {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NativeLibraryLoader.class);

    private static final String NATIVE_RESOURCE_HOME = "META-INF/native/";
    private static final File WORKDIR;
    private static final boolean DELETE_NATIVE_LIB_AFTER_LOADING;
    private static final boolean TRY_TO_PATCH_SHADED_ID;
    private static final boolean DETECT_NATIVE_LIBRARY_DUPLICATES;

    // Just use a-Z and numbers as valid ID bytes.
    private static final byte[] UNIQUE_ID_BYTES =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(CharsetUtil.US_ASCII);

    static {
        String workdir = SystemPropertyUtil.get("io.netty.native.workdir");
        if (workdir != null) {
            File f = new File(workdir);
            f.mkdirs();

            try {
                f = f.getAbsoluteFile();
            } catch (Exception ignored) {
                // Good to have an absolute path, but it's OK.
            }

            WORKDIR = f;
            logger.debug("-Dio.netty.native.workdir: " + WORKDIR);
        } else {
            WORKDIR = PlatformDependent.tmpdir();
            logger.debug("-Dio.netty.native.workdir: " + WORKDIR + " (io.netty.tmpdir)");
        }

        DELETE_NATIVE_LIB_AFTER_LOADING = SystemPropertyUtil.getBoolean(
                "io.netty.native.deleteLibAfterLoading", true);
        logger.debug("-Dio.netty.native.deleteLibAfterLoading: {}", DELETE_NATIVE_LIB_AFTER_LOADING);

        TRY_TO_PATCH_SHADED_ID = SystemPropertyUtil.getBoolean(
                "io.netty.native.tryPatchShadedId", true);
        logger.debug("-Dio.netty.native.tryPatchShadedId: {}", TRY_TO_PATCH_SHADED_ID);

        DETECT_NATIVE_LIBRARY_DUPLICATES = SystemPropertyUtil.getBoolean(
                "io.netty.native.detectNativeLibraryDuplicates", true);
        logger.debug("-Dio.netty.native.detectNativeLibraryDuplicates: {}", DETECT_NATIVE_LIBRARY_DUPLICATES);
    }

    /**
     * Loads the first available library in the collection with the specified
     * {@link ClassLoader}.
     *
     * @throws IllegalArgumentException
     *         if none of the given libraries load successfully.
     */
    public static void loadFirstAvailable(ClassLoader loader, String... names) {
        List<Throwable> suppressed = new ArrayList<Throwable>();
        for (String name : names) {
            try {
                load(name, loader);
                logger.debug("Loaded library with name '{}'", name);
                return;
            } catch (Throwable t) {
                suppressed.add(t);
            }
        }

        IllegalArgumentException iae =
                new IllegalArgumentException("Failed to load any of the given libraries: " + Arrays.toString(names));
        ThrowableUtil.addSuppressedAndClear(iae, suppressed);
        throw iae;
    }

    /**
     * The shading prefix added to this class's full name.
     *
     * @throws UnsatisfiedLinkError if the shader used something other than a prefix
     */
    private static String calculatePackagePrefix() {
        String maybeShaded = NativeLibraryLoader.class.getName();
        // Use ! instead of . to avoid shading utilities from modifying the string
        String expected = "io!netty!util!internal!NativeLibraryLoader".replace('!', '.');
        if (!maybeShaded.endsWith(expected)) {
            throw new UnsatisfiedLinkError(String.format(
                    "Could not find prefix added to %s to get %s. When shading, only adding a "
                    + "package prefix is supported", expected, maybeShaded));
        }
        return maybeShaded.substring(0, maybeShaded.length() - expected.length());
    }

    /**
     * Load the given library with the specified {@link ClassLoader}
     */
    public static void load(String originalName, ClassLoader loader) {
        // Adjust expected name to support shading of native libraries.
        String packagePrefix = calculatePackagePrefix().replace('.', '_');
        String name = packagePrefix + originalName;
        List<Throwable> suppressed = new ArrayList<Throwable>();
        try {
            // first try to load from java.library.path
            loadLibrary(loader, name, false);
            return;
        } catch (Throwable ex) {
            suppressed.add(ex);
        }

        String libname = System.mapLibraryName(name);
        String path = NATIVE_RESOURCE_HOME + libname;

        InputStream in = null;
        OutputStream out = null;
        File tmpFile = null;
        URL url = getResource(path, loader);
        try {
            if (url == null) {
                if (PlatformDependent.isOsx()) {
                    String fileName = path.endsWith(".jnilib") ? NATIVE_RESOURCE_HOME + "lib" + name + ".dynlib" :
                            NATIVE_RESOURCE_HOME + "lib" + name + ".jnilib";
                    url = getResource(fileName, loader);
                    if (url == null) {
                        FileNotFoundException fnf = new FileNotFoundException(fileName);
                        ThrowableUtil.addSuppressedAndClear(fnf, suppressed);
                        throw fnf;
                    }
                } else {
                    FileNotFoundException fnf = new FileNotFoundException(path);
                    ThrowableUtil.addSuppressedAndClear(fnf, suppressed);
                    throw fnf;
                }
            }

            int index = libname.lastIndexOf('.');
            String prefix = libname.substring(0, index);
            String suffix = libname.substring(index);

            tmpFile = PlatformDependent.createTempFile(prefix, suffix, WORKDIR);
            in = url.openStream();
            out = new FileOutputStream(tmpFile);

            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            out.flush();

            if (shouldShadedLibraryIdBePatched(packagePrefix)) {
                // Let's try to patch the id and re-sign it. This is a best-effort and might fail if a
                // SecurityManager is setup or the right executables are not installed :/
                tryPatchShadedLibraryIdAndSign(tmpFile, originalName);
            }

            // Close the output stream before loading the unpacked library,
            // because otherwise Windows will refuse to load it when it's in use by other process.
            closeQuietly(out);
            out = null;

            loadLibrary(loader, tmpFile.getPath(), true);
        } catch (UnsatisfiedLinkError e) {
            try {
                if (tmpFile != null && tmpFile.isFile() && tmpFile.canRead() &&
                    !NoexecVolumeDetector.canExecuteExecutable(tmpFile)) {
                    // Pass "io.netty.native.workdir" as an argument to allow shading tools to see
                    // the string. Since this is printed out to users to tell them what to do next,
                    // we want the value to be correct even when shading.
                    logger.info("{} exists but cannot be executed even when execute permissions set; " +
                                "check volume for \"noexec\" flag; use -D{}=[path] " +
                                "to set native working directory separately.",
                                tmpFile.getPath(), "io.netty.native.workdir");
                }
            } catch (Throwable t) {
                suppressed.add(t);
                logger.debug("Error checking if {} is on a file store mounted with noexec", tmpFile, t);
            }
            // Re-throw to fail the load
            ThrowableUtil.addSuppressedAndClear(e, suppressed);
            throw e;
        } catch (Exception e) {
            UnsatisfiedLinkError ule = new UnsatisfiedLinkError("could not load a native library: " + name);
            ule.initCause(e);
            ThrowableUtil.addSuppressedAndClear(ule, suppressed);
            throw ule;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
            // After we load the library it is safe to delete the file.
            // We delete the file immediately to free up resources as soon as possible,
            // and if this fails fallback to deleting on JVM exit.
            if (tmpFile != null && (!DELETE_NATIVE_LIB_AFTER_LOADING || !tmpFile.delete())) {
                tmpFile.deleteOnExit();
            }
        }
    }

    private static URL getResource(String path, ClassLoader loader) {
        final Enumeration<URL> urls;
        try {
            if (loader == null) {
                urls = ClassLoader.getSystemResources(path);
            } else {
                urls = loader.getResources(path);
            }
        } catch (IOException iox) {
            throw new RuntimeException("An error occurred while getting the resources for " + path, iox);
        }

        List<URL> urlsList = Collections.list(urls);
        int size = urlsList.size();
        switch (size) {
            case 0:
                return null;
            case 1:
                return urlsList.get(0);
            default:
                if (DETECT_NATIVE_LIBRARY_DUPLICATES) {
                    try {
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        // We found more than 1 resource with the same name. Let's check if the content of the file is
                        // the same as in this case it will not have any bad effect.
                        URL url = urlsList.get(0);
                        byte[] digest = digest(md, url);
                        boolean allSame = true;
                        if (digest != null) {
                            for (int i = 1; i < size; i++) {
                                byte[] digest2 = digest(md, urlsList.get(i));
                                if (digest2 == null || !Arrays.equals(digest, digest2)) {
                                    allSame = false;
                                    break;
                                }
                            }
                        } else {
                            allSame = false;
                        }
                        if (allSame) {
                            return url;
                        }
                    } catch (NoSuchAlgorithmException e) {
                        logger.debug("Don't support SHA-256, can't check if resources have same content.", e);
                    }

                    throw new IllegalStateException(
                            "Multiple resources found for '" + path + "' with different content: " + urlsList);
                } else {
                    logger.warn("Multiple resources found for '" + path + "' with different content: " +
                            urlsList + ". Please fix your dependency graph.");
                    return urlsList.get(0);
                }
        }
    }

    private static byte[] digest(MessageDigest digest, URL url) {
        InputStream in = null;
        try {
            in = url.openStream();
            byte[] bytes = new byte[8192];
            int i;
            while ((i = in.read(bytes)) != -1) {
                digest.update(bytes, 0, i);
            }
            return digest.digest();
        } catch (IOException e) {
            logger.debug("Can't read resource.", e);
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    static void tryPatchShadedLibraryIdAndSign(File libraryFile, String originalName) {
        String newId = new String(generateUniqueId(originalName.length()), CharsetUtil.UTF_8);
        if (!tryExec("install_name_tool -id " + newId + " " + libraryFile.getAbsolutePath())) {
            return;
        }

        tryExec("codesign -s - " + libraryFile.getAbsolutePath());
    }

    private static boolean tryExec(String cmd) {
        try {
            int exitValue = Runtime.getRuntime().exec(cmd).waitFor();
            if (exitValue != 0) {
                logger.debug("Execution of '{}' failed: {}", cmd, exitValue);
                return false;
            }
            logger.debug("Execution of '{}' succeed: {}", cmd, exitValue);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            logger.info("Execution of '{}' failed.", cmd, e);
        } catch (SecurityException e) {
            logger.error("Execution of '{}' failed.", cmd, e);
        }
        return false;
    }

    private static boolean shouldShadedLibraryIdBePatched(String packagePrefix) {
        return TRY_TO_PATCH_SHADED_ID && PlatformDependent.isOsx() && !packagePrefix.isEmpty();
    }

    private static byte[] generateUniqueId(int length) {
        byte[] idBytes = new byte[length];
        for (int i = 0; i < idBytes.length; i++) {
            // We should only use bytes as replacement that are in our UNIQUE_ID_BYTES array.
            idBytes[i] = UNIQUE_ID_BYTES[PlatformDependent.threadLocalRandom()
                    .nextInt(UNIQUE_ID_BYTES.length)];
        }
        return idBytes;
    }

    /**
     * Loading the native library into the specified {@link ClassLoader}.
     * @param loader - The {@link ClassLoader} where the native library will be loaded into
     * @param name - The native library path or name
     * @param absolute - Whether the native library will be loaded by path or by name
     */
    private static void loadLibrary(final ClassLoader loader, final String name, final boolean absolute) {
        Throwable suppressed = null;
        try {
            try {
                // Make sure the helper belongs to the target ClassLoader.
                final Class<?> newHelper = tryToLoadClass(loader, NativeLibraryUtil.class);
                loadLibraryByHelper(newHelper, name, absolute);
                logger.debug("Successfully loaded the library {}", name);
                return;
            } catch (UnsatisfiedLinkError e) { // Should by pass the UnsatisfiedLinkError here!
                suppressed = e;
            } catch (Exception e) {
                suppressed = e;
            }
            NativeLibraryUtil.loadLibrary(name, absolute);  // Fallback to local helper class.
            logger.debug("Successfully loaded the library {}", name);
        } catch (NoSuchMethodError nsme) {
            if (suppressed != null) {
                ThrowableUtil.addSuppressed(nsme, suppressed);
            }
            rethrowWithMoreDetailsIfPossible(name, nsme);
        } catch (UnsatisfiedLinkError ule) {
            if (suppressed != null) {
                ThrowableUtil.addSuppressed(ule, suppressed);
            }
            throw ule;
        }
    }

    @SuppressJava6Requirement(reason = "Guarded by version check")
    private static void rethrowWithMoreDetailsIfPossible(String name, NoSuchMethodError error) {
        if (PlatformDependent.javaVersion() >= 7) {
            throw new LinkageError(
                    "Possible multiple incompatible native libraries on the classpath for '" + name + "'?", error);
        }
        throw error;
    }

    private static void loadLibraryByHelper(final Class<?> helper, final String name, final boolean absolute)
            throws UnsatisfiedLinkError {
        Object ret = AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                try {
                    // Invoke the helper to load the native library, if succeed, then the native
                    // library belong to the specified ClassLoader.
                    Method method = helper.getMethod("loadLibrary", String.class, boolean.class);
                    method.setAccessible(true);
                    return method.invoke(null, name, absolute);
                } catch (Exception e) {
                    return e;
                }
            }
        });
        if (ret instanceof Throwable) {
            Throwable t = (Throwable) ret;
            assert !(t instanceof UnsatisfiedLinkError) : t + " should be a wrapper throwable";
            Throwable cause = t.getCause();
            if (cause instanceof UnsatisfiedLinkError) {
                throw (UnsatisfiedLinkError) cause;
            }
            UnsatisfiedLinkError ule = new UnsatisfiedLinkError(t.getMessage());
            ule.initCause(t);
            throw ule;
        }
    }

    /**
     * Try to load the helper {@link Class} into specified {@link ClassLoader}.
     * @param loader - The {@link ClassLoader} where to load the helper {@link Class}
     * @param helper - The helper {@link Class}
     * @return A new helper Class defined in the specified ClassLoader.
     * @throws ClassNotFoundException Helper class not found or loading failed
     */
    private static Class<?> tryToLoadClass(final ClassLoader loader, final Class<?> helper)
            throws ClassNotFoundException {
        try {
            return Class.forName(helper.getName(), false, loader);
        } catch (ClassNotFoundException e1) {
            if (loader == null) {
                // cannot defineClass inside bootstrap class loader
                throw e1;
            }
            try {
                // The helper class is NOT found in target ClassLoader, we have to define the helper class.
                final byte[] classBinary = classToByteArray(helper);
                return AccessController.doPrivileged(new PrivilegedAction<Class<?>>() {
                    @Override
                    public Class<?> run() {
                        try {
                            // Define the helper class in the target ClassLoader,
                            //  then we can call the helper to load the native library.
                            Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class,
                                    byte[].class, int.class, int.class);
                            defineClass.setAccessible(true);
                            return (Class<?>) defineClass.invoke(loader, helper.getName(), classBinary, 0,
                                    classBinary.length);
                        } catch (Exception e) {
                            throw new IllegalStateException("Define class failed!", e);
                        }
                    }
                });
            } catch (ClassNotFoundException e2) {
                ThrowableUtil.addSuppressed(e2, e1);
                throw e2;
            } catch (RuntimeException e2) {
                ThrowableUtil.addSuppressed(e2, e1);
                throw e2;
            } catch (Error e2) {
                ThrowableUtil.addSuppressed(e2, e1);
                throw e2;
            }
        }
    }

    /**
     * Load the helper {@link Class} as a byte array, to be redefined in specified {@link ClassLoader}.
     * @param clazz - The helper {@link Class} provided by this bundle
     * @return The binary content of helper {@link Class}.
     * @throws ClassNotFoundException Helper class not found or loading failed
     */
    private static byte[] classToByteArray(Class<?> clazz) throws ClassNotFoundException {
        String fileName = clazz.getName();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(lastDot + 1);
        }
        URL classUrl = clazz.getResource(fileName + ".class");
        if (classUrl == null) {
            throw new ClassNotFoundException(clazz.getName());
        }
        byte[] buf = new byte[1024];
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        InputStream in = null;
        try {
            in = classUrl.openStream();
            for (int r; (r = in.read(buf)) != -1;) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new ClassNotFoundException(clazz.getName(), ex);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    private NativeLibraryLoader() {
        // Utility
    }

    private static final class NoexecVolumeDetector {

        @SuppressJava6Requirement(reason = "Usage guarded by java version check")
        private static boolean canExecuteExecutable(File file) throws IOException {
            if (PlatformDependent.javaVersion() < 7) {
                // Pre-JDK7, the Java API did not directly support POSIX permissions; instead of implementing a custom
                // work-around, assume true, which disables the check.
                return true;
            }

            // If we can already execute, there is nothing to do.
            if (file.canExecute()) {
                return true;
            }

            // On volumes, with noexec set, even files with the executable POSIX permissions will fail to execute.
            // The File#canExecute() method honors this behavior, probaby via parsing the noexec flag when initializing
            // the UnixFileStore, though the flag is not exposed via a public API.  To find out if library is being
            // loaded off a volume with noexec, confirm or add executalbe permissions, then check File#canExecute().

            // Note: We use FQCN to not break when netty is used in java6
            Set<java.nio.file.attribute.PosixFilePermission> existingFilePermissions =
                    java.nio.file.Files.getPosixFilePermissions(file.toPath());
            Set<java.nio.file.attribute.PosixFilePermission> executePermissions =
                    EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                            java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
            if (existingFilePermissions.containsAll(executePermissions)) {
                return false;
            }

            Set<java.nio.file.attribute.PosixFilePermission> newPermissions = EnumSet.copyOf(existingFilePermissions);
            newPermissions.addAll(executePermissions);
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), newPermissions);
            return file.canExecute();
        }

        private NoexecVolumeDetector() {
            // Utility
        }
    }
}
