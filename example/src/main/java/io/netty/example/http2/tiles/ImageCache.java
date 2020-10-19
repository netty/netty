/*
 * Copyright 2015 The Netty Project
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

package io.netty.example.http2.tiles;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import static io.netty.example.http2.Http2ExampleUtil.toByteBuf;
import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches the images to avoid reading them every time from the disk.
 */
public final class ImageCache {

    public static ImageCache INSTANCE = new ImageCache();

    private final Map<String, ByteBuf> imageBank = new HashMap<String, ByteBuf>(200);

    private ImageCache() {
        init();
    }

    public static String name(int x, int y) {
        return "tile-" + y + "-" + x + ".jpeg";
    }

    public ByteBuf image(int x, int y) {
        return imageBank.get(name(x, y));
    }

    private void init() {
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 20; x++) {
                try {
                    String name = name(x, y);
                    ByteBuf fileBytes = unreleasableBuffer(toByteBuf(getClass()
                            .getResourceAsStream(name)).asReadOnly());
                    imageBank.put(name, fileBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
