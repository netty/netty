/*
 * Copyright 2009 Red Hat, Inc.
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
package org.jboss.netty.handler.codec.http;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Default factory giving Attribute and FileUpload according to constructor
 *
 * Attribute and FileUpload could be :<br>
 * - MemoryAttribute, DiskAttribute or MixedAttribute<br>
 * - MemoryFileUpload, DiskFileUpload or MixedFileUpload<br>
 * according to the constructor.
 *
 * @author frederic bregier
 *
 */
public class DefaultHttpDataFactory implements HttpDataFactory {
    /**
     * Proposed default MINSIZE as 16 KB.
     */
    public static long MINSIZE = 0x4000;

    private boolean useDisk = false;

    private boolean checkSize = false;

    private long minSize = 0L;

    /**
     * Keep all HttpDatas until cleanAllHttpDatas() is called.
     */
    private List<HttpData> fileToDelete = new ArrayList<HttpData>();

    /**
     * HttpData will be in memory if less than default size (16KB).
     * The type will be Mixed.
     */
    public DefaultHttpDataFactory() {
        useDisk = false;
        checkSize = true;
        this.minSize = MINSIZE;
    }

    /**
     * HttpData will be always on Disk if useDisk is True, else always in Memory if False
     * @param useDisk
     */
    public DefaultHttpDataFactory(boolean useDisk) {
        this.useDisk = useDisk;
        checkSize = false;
    }

    /**
     * HttpData will be on Disk if the size of the file is greater than minSize, else it
     * will be in memory. The type will be Mixed.
     * @param minSize
     */
    public DefaultHttpDataFactory(long minSize) {
        useDisk = false;
        checkSize = true;
        this.minSize = minSize;
    }

    public Attribute createAttribute(String name) throws NullPointerException,
            IllegalArgumentException {
        if (useDisk) {
            Attribute attribute = new DiskAttribute(name);
            fileToDelete.add(attribute);
            return attribute;
        } else if (checkSize) {
            Attribute attribute = new MixedAttribute(name, minSize);
            fileToDelete.add(attribute);
            return attribute;
        }
        return new MemoryAttribute(name);
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpDataFactory#createAttribute(java.lang.String, java.lang.String)
     */
    public Attribute createAttribute(String name, String value)
            throws NullPointerException, IllegalArgumentException {
        if (useDisk) {
            Attribute attribute;
            try {
                attribute = new DiskAttribute(name, value);
            } catch (IOException e) {
                // revert to Mixed mode
                attribute = new MixedAttribute(name, value, minSize);
            }
            fileToDelete.add(attribute);
            return attribute;
        } else if (checkSize) {
            Attribute attribute = new MixedAttribute(name, value, minSize);
            fileToDelete.add(attribute);
            return attribute;
        }
        try {
            return new MemoryAttribute(name, value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /* (non-Javadoc)
     * @see org.jboss.netty.handler.codec.http2.HttpDataFactory#createFileUpload(java.lang.String, java.lang.String, java.lang.String)
     */
    public FileUpload createFileUpload(String name, String filename,
            String contentType, String contentTransferEncoding, String charset,
            long size) throws NullPointerException, IllegalArgumentException {
        if (useDisk) {
            FileUpload fileUpload = new DiskFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size);
            fileToDelete.add(fileUpload);
            return fileUpload;
        } else if (checkSize) {
            FileUpload fileUpload = new MixedFileUpload(name, filename, contentType,
                    contentTransferEncoding, charset, size, minSize);
            fileToDelete.add(fileUpload);
            return fileUpload;
        }
        return new MemoryFileUpload(name, filename, contentType,
                contentTransferEncoding, charset, size);
    }

    public void removeHttpDataFromClean(InterfaceHttpData data) {
        if (data instanceof HttpData) {
            fileToDelete.remove(data);
        }
    }

    public void cleanAllHttpDatas() {
        for (HttpData data: fileToDelete) {
            data.delete();
        }
        fileToDelete.clear();
    }

}
