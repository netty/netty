/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.iostream;

import io.netty.bootstrap.ClientBootstrap;
import io.netty.channel.*;
import io.netty.channel.iostream.IOStreamAddress;
import io.netty.channel.iostream.IOStreamChannelFactory;
import io.netty.handler.codec.frame.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.frame.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An example demonstrating the use of the {@link io.netty.channel.iostream.IOStreamChannel}.
 */
public class IOStream {

	private static volatile boolean running = true;

	public static void main(String[] args) {

		final ExecutorService executorService = Executors.newCachedThreadPool();
		final ClientBootstrap bootstrap = new ClientBootstrap(new IOStreamChannelFactory(executorService));

		// Configure the event pipeline factory.
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				DefaultChannelPipeline pipeline = new DefaultChannelPipeline();
				pipeline.addLast("framer", new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
				pipeline.addLast("decoder", new StringDecoder());
				pipeline.addLast("encoder", new StringEncoder());
				pipeline.addLast("loggingHandler", new SimpleChannelHandler() {
					@Override
					public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e)
							throws Exception {

						final String message = (String) e.getMessage();
						synchronized (System.out) {
							e.getChannel().write("Message received: " + message);
						}
						if ("exit".equals(message)) {
							IOStream.running = false;
						}
						super.messageReceived(ctx, e);
					}
				}
				);
				return pipeline;
			}
		});

		// Make a new connection.
		ChannelFuture connectFuture = bootstrap.connect(new IOStreamAddress(System.in, System.out));

		// Wait until the connection is made successfully.
		Channel channel = connectFuture.awaitUninterruptibly().getChannel();

		while (running) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Close the connection.
		channel.close().awaitUninterruptibly();

		// Shut down all thread pools to exit.
		bootstrap.releaseExternalResources();

	}

}
