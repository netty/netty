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

import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.PlatformDependent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Default factory giving Attribute and FileUpload according to constructor
 *
 * Attribute and FileUpload could be :<br>
 * - MemoryAttribute, DiskAttribute or MixedAttribute<br>
 * - MemoryFileUpload, DiskFileUpload or MixedFileUpload<br>
 * according to the constructor.
 */
public class DefaultHttpDataFactory implements HttpDataFactory {
    /**
     * Proposed default MINSIZE as 16 KB.
     */
    public static final long MINSIZE = 0x4000;

    private final boolean useDisk;

    private final boolean checkSize;

    private long minSize;

    /**
     * Keep all HttpDatas until cleanAllHttpDatas() is called.
     */
    private final Map<HttpRequest, List<HttpData>> requestFileDeleteMap = PlatformDependent.newConcurrentHashMap();

    /**
     * HttpData will be in memory if less than default size (16KB).
     * The type will be Mixed.
     */
    public DefaultHttpDataFactory() {
        useDisk = false;
        checkSize = true;
        minSize = MINSIZE;
    }

    /**
     * HttpData will be always on Disk if useDisk is True, else always in Memory if False
     */
    public DefaultHttpDataFactory(boolean useDisk) {
        this.useDisk = useDisk;
        checkSize = false;
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

    /**
     * @return the associated list of Files for the request
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
            Attribute attribute = new DiskAttribute(name);
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, minSize);
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.add(attribute);
            return attribute;
        }
        return new MemoryAttribute(name);
    }

    @Override
    public Attribute createAttribute(HttpRequest request, String name, String value) {
        if (useDisk) {
            Attribute attribute;
            try {
                attribute = new DiskAttribute(name, value);
            } catch (IOException e) {
                // revert to Mixed mode
                attribute = new MixedAttribute(name, value, minSize);
            }
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.add(attribute);
            return attribute;
        }
        if (checkSize) {
            Attribute attribute = new MixedAttribute(name, value, minSize);
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.add(attribute);
            return attribute;
        }
        try {
            return new MemoryAttribute(name, value);
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
                    contentTransferEncoding, charset, size);
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.add(fileUpload);
            return fileUpload;
        }
        if (checkSize) {
            FileUpload fileUpload = new MixedFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size, minSize);
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.add(fileUpload);
            return fileUpload;
        }
        return new MemoryFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size);
    }

    @Override
    public void removeHttpDataFromClean(HttpRequest request, InterfaceHttpData data) {
        if (data instanceof HttpData) {
            List<HttpData> fileToDelete = getList(request);
            fileToDelete.remove(data);
        }
    }

    @Override
    public void cleanRequestHttpDatas(HttpRequest request) {
        List<HttpData> fileToDelete = requestFileDeleteMap.remove(request);
        if (fileToDelete != null) {
            for (HttpData data: fileToDelete) {
                data.delete();
            }
            fileToDelete.clear();
        }
    }

    @Override
    public void cleanAllHttpDatas() {
        Iterator<Entry<HttpRequest, List<HttpData>>> i = requestFileDeleteMap.entrySet().iterator();
        while (i.hasNext()) {
            Entry<HttpRequest, List<HttpData>> e = i.next();
            i.remove();

            List<HttpData> fileToDelete = e.getValue();
            if (fileToDelete != null) {
                for (HttpData data : fileToDelete) {
                    data.delete();
                }
                fileToDelete.clear();
            }
        }
    }
}
