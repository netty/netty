/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http.multipart;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.HttpRequest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Default factory giving {@link Attribute} and {@link FileUpload} according to constructor.
 *
 * <p>According to the constructor, {@link Attribute} and {@link FileUpload} can be:</p>
 * <ul>
 * <li>MemoryAttribute, DiskAttribute or MixedAttribute</li>
 * <li>MemoryFileUpload, DiskFileUpload or MixedFileUpload</li>
 * </ul>
 */
public class DefaultHttpDataFactory implements HttpDataFactory {

    /**
     * Proposed default MINSIZE as 16 KB.
     */
    public static final long MINSIZE = 0x4000;
    /**
     * Proposed default MAXSIZE = -1 as UNLIMITED
     */
    public static final long MAXSIZE = -1;

    private final boolean useDisk;

    private final boolean checkSize;

    private long minSize;

    private long maxSize = MAXSIZE;

    private Charset charset = HttpConstants.DEFAULT_CHARSET;

    private String baseDir;

    private boolean deleteOnExit; // false is a good default cause true leaks

    /**
     * Keep all {@link HttpData}s until cleaning methods are called.
     * We need to use {@link IdentityHashMap} because different requests may be equal.
     * See {@link DefaultHttpRequest#hashCode} and {@link DefaultHttpRequest#equals}.
     * Similarly, when removing data items, we need to check their identities because
     * different data items may be equal.
     */
    private final Map<HttpRequest, List<HttpData>> requestFileDeleteMap =
            Collections.synchronizedMap(new IdentityHashMap<HttpRequest, List<HttpData>>());

    /**
     * HttpData will be in memory if less than default size (16KB).
     * The type will be Mixed.
     */
    public DefaultHttpDataFactory() {
        useDisk = false;
        checkSize = true;
        minSize = MINSIZE;
    }

    public DefaultHttpDataFactory(Charset charset) {
        this();
        this.charset = charset;
    }

    /**
     * HttpData will be always on Disk if useDisk is True, else always in Memory if False
     */
    public DefaultHttpDataFactory(boolean useDisk) {
        this.useDisk = useDisk;
        checkSize = false;
    }

    public DefaultHttpDataFactory(boolean useDisk, Charset charset) {
        this(useDisk);
        this.charset = charset;
    }
    /**
     * HttpData will be on Disk if the size of the file is greater than minSize, else it
     * will be in memory. The type will be Mixed.
     */
    public DefaultHttpDataFactory(long minSize) {
        useDisk = false;
        checkSize = true;
        this.minSize = minSize;
    }

    public DefaultHttpDataFactory(long minSize, Charset charset) {
        this(minSize);
        this.charset = charset;
    }

    /**
     * Override global {@link DiskAttribute#baseDirectory} and {@link DiskFileUpload#baseDirectory} values.
     *
     * @param baseDir directory path where to store disk attributes and file uploads.
     */
    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Override global {@link DiskAttribute#deleteOnExitTemporaryFile} and
     * {@link DiskFileUpload#deleteOnExitTemporaryFile} values.
     *
     * @param deleteOnExit true if temporary files should be deleted with the JVM, false otherwise.
     */
    public void setDeleteOnExit(boolean deleteOnExit) {
        this.deleteOnExit = deleteOnExit;
    }

    @Override
    public void setMaxLimit(long maxSize) {
        this.maxSize = maxSize;
    }

    /**
     * @return the associated list of {@link HttpData} for the request
     */
    private List<HttpData> getList(HttpRequest request) {
        List<HttpData> list = requestFileDeleteMap.get(request);
        if (list == null) {
            list = new ArrayList<HttpData>();
            requestFileDeleteMap.put(request, list);
        }
        return list;
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name) {
        if (useDisk) {
            Attribute attribute = new DiskAttribute(name, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, minSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        MemoryAttribute attribute = new MemoryAttribute(name);
        attribute.setMaxSize(maxSize);
        return attribute;
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name, long definedSize) {
        if (useDisk) {
            Attribute attribute = new DiskAttribute(name, definedSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, definedSize, minSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        MemoryAttribute attribute = new MemoryAttribute(name, definedSize);
        attribute.setMaxSize(maxSize);
        return attribute;
    }

    /**
     * Utility method
     */
    private static void checkHttpDataSize(HttpData data) {
        try {
            data.checkSize(data.length());
        } catch (IOException ignored) {
            throw new IllegalArgumentException("Attribute bigger than maxSize allowed");
        }
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name, String value) {
        if (useDisk) {
            Attribute attribute;
            try {
                attribute = new DiskAttribute(name, value, charset, baseDir, deleteOnExit);
                attribute.setMaxSize(maxSize);
            } catch (IOException e) {
                // revert to Mixed mode
                attribute = new MixedAttribute(name, value, minSize, charset, baseDir, deleteOnExit);
                attribute.setMaxSize(maxSize);
            }
            checkHttpDataSize(attribute);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, value, minSize, charset, baseDir, deleteOnExit);
            attribute.setMaxSize(maxSize);
            checkHttpDataSize(attribute);
            List<HttpData> list = getList(request);
            list.add(attribute);
            return attribute;
        }
        try {
            MemoryAttribute attribute = new MemoryAttribute(name, value, charset);
            attribute.setMaxSize(maxSize);
            checkHttpDataSize(attribute);
            return attribute;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public FileUpload createFileUpload(HttpRequest request, String name, String filename,
            String contentType, String contentTransferEncoding, Charset charset,
            long size) {
        if (useDisk) {
            FileUpload fileUpload = new DiskFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size, baseDir, deleteOnExit);
            fileUpload.setMaxSize(maxSize);
            checkHttpDataSize(fileUpload);
            List<HttpData> list = getList(request);
            list.add(fileUpload);
            return fileUpload;
        }
        if (checkSize) {
            FileUpload fileUpload = new MixedFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size, minSize, baseDir, deleteOnExit);
            fileUpload.setMaxSize(maxSize);
            checkHttpDataSize(fileUpload);
            List<HttpData> list = getList(request);
            list.add(fileUpload);
            return fileUpload;
        }
        MemoryFileUpload fileUpload = new MemoryFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size);
        fileUpload.setMaxSize(maxSize);
        checkHttpDataSize(fileUpload);
        return fileUpload;
    }

    @Override
    public void removeHttpDataFromClean(HttpRequest request, InterfaceHttpData data) {
        if (!(data instanceof HttpData)) {
            return;
        }

        // Do not use getList because it adds empty list to requestFileDeleteMap
        // if request is not found
        List<HttpData> list = requestFileDeleteMap.get(request);
        if (list == null) {
            return;
        }

        // Can't simply call list.remove(data), because different data items may be equal.
        // Need to check identity.
        Iterator<HttpData> i = list.iterator();
        while (i.hasNext()) {
            HttpData n = i.next();
            if (n == data) {
                i.remove();

                // Remove empty list to avoid memory leak
                if (list.isEmpty()) {
                    requestFileDeleteMap.remove(request);
                }

                return;
            }
        }
    }

    @Override
    public void cleanRequestHttpData(HttpRequest request) {
        List<HttpData> list = requestFileDeleteMap.remove(request);
        if (list != null) {
            for (HttpData data : list) {
                data.release();
            }
        }
    }

    @Override
    public void cleanAllHttpData() {
        Iterator<Entry<HttpRequest, List<HttpData>>> i = requestFileDeleteMap.entrySet().iterator();
        while (i.hasNext()) {
            Entry<HttpRequest, List<HttpData>> e = i.next();

            // Calling i.remove() here will cause "java.lang.IllegalStateException: Entry was removed"
            // at e.getValue() below

            List<HttpData> list = e.getValue();
            for (HttpData data : list) {
                data.release();
            }

            i.remove();
        }
    }

    @Override
    public void cleanRequestHttpDatas(HttpRequest request) {
        cleanRequestHttpData(request);
    }

    @Override
    public void cleanAllHttpDatas() {
        cleanAllHttpData();
    }
}
