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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.example.worldclock.WorldClockProtocol.Continent;
import io.netty.example.worldclock.WorldClockProtocol.DayOfWeek;
import io.netty.example.worldclock.WorldClockProtocol.LocalTime;
import io.netty.example.worldclock.WorldClockProtocol.LocalTimes;
import io.netty.example.worldclock.WorldClockProtocol.Location;
import io.netty.example.worldclock.WorldClockProtocol.Locations;

import java.util.Calendar;
import java.util.TimeZone;

import static java.util.Calendar.*;

public class WorldClockServerHandler extends SimpleChannelInboundHandler<Locations> {

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Locations locations) throws Exception {
        long currentTime = System.currentTimeMillis();

        LocalTimes.Builder builder = LocalTimes.newBuilder();
        for (Location l: locations.getLocationList()) {
            TimeZone tz = TimeZone.getTimeZone(
                    toString(l.getContinent()) + '/' + l.getCity());
            Calendar calendar = getInstance(tz);
            calendar.setTimeInMillis(currentTime);

            builder.addLocalTime(LocalTime.newBuilder().
                    setYear(calendar.get(YEAR)).
                    setMonth(calendar.get(MONTH) + 1).
                    setDayOfMonth(calendar.get(DAY_OF_MONTH)).
                    setDayOfWeek(DayOfWeek.valueOf(calendar.get(DAY_OF_WEEK))).
                    setHour(calendar.get(HOUR_OF_DAY)).
                    setMinute(calendar.get(MINUTE)).
                    setSecond(calendar.get(SECOND)).build());
        }

        ctx.write(builder.build());
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private static String toString(Continent c) {
        return c.name().charAt(0) + c.name().toLowerCase().substring(1);
    }
}
