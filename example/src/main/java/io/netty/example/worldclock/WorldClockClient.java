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
package io.netty.example.worldclock;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sends a list of continent/city pairs to a {@link WorldClockServer} to
 * get the local times of the specified cities.
 */
public class WorldClockClient {

    private final String host;
    private final int port;
    private final Collection<String> cities;

    public WorldClockClient(String host, int port, Collection<String> cities) {
        this.host = host;
        this.port = port;
        this.cities = new ArrayList<String>();
        this.cities.addAll(cities);
    }

    public void run() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
             .channel(NioSocketChannel.class)
             .handler(new WorldClockClientInitializer());

            // Make a new connection.
            Channel ch = b.connect(host, port).sync().channel();

            // Get the handler instance to initiate the request.
            WorldClockClientHandler handler =
                ch.pipeline().get(WorldClockClientHandler.class);

            // Request and get the response.
            List<String> response = handler.getLocalTimes(cities);

            // Close the connection.
            ch.close();

            // Print the response at last but not least.
            Iterator<String> i1 = cities.iterator();
            Iterator<String> i2 = response.iterator();
            while (i1.hasNext()) {
                System.out.format("%28s: %s%n", i1.next(), i2.next());
            }
        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        // Print usage if necessary.
        if (args.length < 3) {
            printUsage();
            return;
        }

        // Parse options.
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        Collection<String> cities = parseCities(args, 2);
        if (cities == null) {
            return;
        }

        new WorldClockClient(host, port, cities).run();
    }

    private static void printUsage() {
        System.err.println(
                "Usage: " + WorldClockClient.class.getSimpleName() +
                " <host> <port> <continent/city_name> ...");
        System.err.println(
                "Example: " + WorldClockClient.class.getSimpleName() +
                " localhost 8080 America/New_York Asia/Seoul");
    }

    private static final Pattern CITY_PATTERN = Pattern.compile("^[_A-Za-z]+/[_A-Za-z]+$");

    private static List<String> parseCities(String[] args, int offset) {
        List<String> cities = new ArrayList<String>();
        for (int i = offset; i < args.length; i ++) {
            if (!CITY_PATTERN.matcher(args[i]).matches()) {
                System.err.println("Syntax error: '" + args[i] + '\'');
                printUsage();
                return null;
            }
            cities.add(args[i].trim());
        }
        return cities;
    }
}
