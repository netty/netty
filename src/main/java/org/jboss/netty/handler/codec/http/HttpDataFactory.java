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

/**
 * Interface to enable creation of InterfaceHttpData objects
 *
 * @author frederic bregier
 *
 */
public interface HttpDataFactory {
    /**
    *
    * @param name
    * @return a new Attribute with no value
    * @throws NullPointerException
    * @throws IllegalArgumentException
    */
   public Attribute createAttribute(String name)
           throws NullPointerException, IllegalArgumentException;

    /**
     *
     * @param name
     * @param value
     * @return a new Attribute
     * @throws NullPointerException
     * @throws IllegalArgumentException
     */
    public Attribute createAttribute(String name, String value)
            throws NullPointerException, IllegalArgumentException;

    /**
     *
     * @param name
     * @param filename
     * @param contentType
     * @param charset
     * @param size the size of the Uploaded file
     * @return a new FileUpload
     */
    public FileUpload createFileUpload(String name, String filename,
            String contentType, String contentTransferEncoding, String charset,
            long size) throws NullPointerException, IllegalArgumentException;

    /**
     * Remove the given InterfaceHttpData from clean list (will not delete the file, except if the file
     * is still a temporary one as setup at construction)
     * @param data
     */
    public void removeHttpDataFromClean(InterfaceHttpData data);

    /**
     * Remove all InterfaceHttpData from virtual File storage from clean list
     */
    public void cleanAllHttpDatas();
}
