/*
 * Copyright 2021 The Netty Project
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
package io.netty.handler.codec.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BrotliContentCompressorTest {

    @Test
    public void testGetTargetContentEncoding() {
        BrotliContentCompressor compressor = new BrotliContentCompressor();

        String[] tests = {
                // Accept-Encoding -> Content-Encoding
                "", null,
                "*", "br",
                "*;q=0.0", null,
                "br", "br",
                "compress, br;q=0.5", "br",
                "br; q=0.5, identity", "br",
                "br ; q=0.1", "br",
                "br; q=0, deflate", "br",
                "br ; q=0 , *;q=0.5", "br",
        };
        for (int i = 0; i < tests.length; i += 2) {
            String acceptEncoding = tests[i];
            String contentEncoding = tests[i + 1];
            String targetEncoding = compressor.determineEncoding(acceptEncoding);
            assertEquals(contentEncoding, targetEncoding);
        }
    }
}
