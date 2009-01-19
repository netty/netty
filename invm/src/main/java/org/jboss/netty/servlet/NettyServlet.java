/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.servlet;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class NettyServlet extends HttpServlet {
    protected void doRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession();
        System.out.println("session.getId() = " + session.getId());
        Channel channel = (Channel) session.getAttribute("channel");
        int length = request.getContentLength();
        if (length > 0) {
            byte[] bytes = new byte[length];
            int read = request.getInputStream().read(bytes);
            ChannelBuffer cb = ChannelBuffers.copiedBuffer(bytes);
            channel.write(cb);
        }
        ServletChannelHandler handler = (ServletChannelHandler) session.getAttribute("handler");
        if (handler.isStreaming()) {
            streamResponse(response, session, handler, channel);
        }
        else {
            pollResponse(response, session, handler);
        }
    }

    private void streamResponse(HttpServletResponse response, HttpSession session, ServletChannelHandler handler, Channel channel) throws IOException {
        if (handler.getOutputStream() == null) {
            handler.setOutputStream(response.getOutputStream());
            response.setHeader("jsessionid", session.getId());
            response.setStatus(HttpServletResponse.SC_OK);
        }


    }

    private void pollResponse(HttpServletResponse response, HttpSession session, ServletChannelHandler handler) throws IOException {
        int length;
        handler.setOutputStream(response.getOutputStream());
        List<ChannelBuffer> buffers = handler.getBuffers();
        length = 0;
        if (buffers.size() > 0) {
            for (ChannelBuffer buffer : buffers) {
                length += buffer.readableBytes();
            }
        }
        response.setHeader("jsessionid", session.getId());
        response.setContentLength(length);
        response.setStatus(HttpServletResponse.SC_OK);
        System.out.println("response = " + response.getOutputStream());
        for (ChannelBuffer buffer : buffers) {
            byte[] b = new byte[buffer.readableBytes()];
            buffer.readBytes(b);
            response.getOutputStream().write(b);
        }
    }

    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doRequest(httpServletRequest, httpServletResponse);
    }

    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doRequest(httpServletRequest, httpServletResponse);
    }
}

