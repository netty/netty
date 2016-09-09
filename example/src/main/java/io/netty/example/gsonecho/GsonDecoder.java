/*
 * Copyright 2016 The Netty Project
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

package io.netty.example.gsonecho;

import com.google.gson.Gson;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import java.util.List;

/**
 * Decode received Json {@link String} into Java objects using Gson.
 * Please note that this decoder must be used with a proper {@link String} decoder.
 * <p>
 * <pre>
 * // Decoders
 * pipeline.addLast("frameDecoder", new {@link LengthFieldBasedFrameDecoder}(1048576, 0, 4, 0, 4));
 * pipeline.addLast("stringDecoder", new {@link StringDecoder}(CharsetUtil.UTF_8));
 * pipeline.addLast("gsonDecoder", new {@link GsonDecoder}&lt;MyType&gt;());
 * </pre>
 * <p>
 * <pre>
 * // Encoders
 * pipeline.addLast("frameEncoder", new {@link LengthFieldPrepender}(4));
 * pipeline.addLast("stringEncoder", new {@link StringEncoder}(CharsetUtil.UTF_8));
 * pipeline.addLast("gsonEncoder", new {@link GsonEncoder}&lt;MyType&gt;());
 * </pre>
 */
@Sharable
public class GsonDecoder<T> extends MessageToMessageDecoder<String> {

    private final Class clazz;
    private final Gson gson;

    public GsonDecoder(Class<T> clazz) {
        this(new Gson(), clazz);
    }

    public GsonDecoder(Gson gson, Class<T> clazz) {
        this.gson = gson;
        this.clazz = clazz;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, String msg, List<Object> out) throws Exception {
        out.add(gson.fromJson(msg, clazz));
    }
}
