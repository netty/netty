/*
 * Copyright 2013 The Netty Project
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

package io.netty.handler.codec.xml;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

/**
 * A frame decoder for single separate XML based message streams.
 * <p/>
 * A couple examples will better help illustrate
 * what this decoder actually does.
 * <p/>
 * Given an input array of bytes split over 3 frames like this:
 * <pre>
 * +-----+-----+-----------+
 * | &lt;an | Xml | Element/&gt; |
 * +-----+-----+-----------+
 * </pre>
 * <p/>
 * this decoder would output a single frame:
 * <p/>
 * <pre>
 * +-----------------+
 * | &lt;anXmlElement/&gt; |
 * +-----------------+
 * </pre>
 *
 * Given an input array of bytes split over 5 frames like this:
 * <pre>
 * +-----+-----+-----------+-----+----------------------------------+
 * | &lt;an | Xml | Element/&gt; | &lt;ro | ot&gt;&lt;child&gt;content&lt;/child&gt;&lt;/root&gt; |
 * +-----+-----+-----------+-----+----------------------------------+
 * </pre>
 * <p/>
 * this decoder would output two frames:
 * <p/>
 * <pre>
 * +-----------------+-------------------------------------+
 * | &lt;anXmlElement/&gt; | &lt;root&gt;&lt;child&gt;content&lt;/child&gt;&lt;/root&gt; |
 * +-----------------+-------------------------------------+
 * </pre>
 *
 * Please note that this decoder is not suitable for
 * xml streaming protocols such as
 * <a href="http://xmpp.org/rfcs/rfc6120.html">XMPP</a>,
 * where an initial xml element opens the stream and only
 * gets closed at the end of the session, although this class
 * could probably allow for such type of message flow with
 * minor modifications.
 */
public class XmlFrameDecoder extends ByteToMessageDecoder {

	private enum XmlStat {
		HEADER,
		COMMENT,
		CDATA,
		HEAD,
		ATTR,
		BODY,
		END,
		INIT
	}
	
	private int maxObjectLength;
	private int idx;
	private int openCount=0;
	private boolean closeing = false;
	private XmlStat stat = XmlStat.INIT;
	private XmlStat lastStat = XmlStat.INIT;
	
	public XmlFrameDecoder() {
		this(1024*1024);
	}
	public XmlFrameDecoder(int maxObjectLength) {
		this.maxObjectLength = maxObjectLength;
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in,
			List<Object> out) throws Exception {
        // index of next byte to process.
        int wrtIdx = in.writerIndex();
        if (wrtIdx > maxObjectLength) {
            // buffer size exceeded maxObjectLength; discarding the complete buffer.
            in.skipBytes(in.readableBytes());
            reset();
            throw new TooLongFrameException(
                            "object length exceeds " + maxObjectLength + ": " + wrtIdx + " bytes discarded");
        }
        int idx = this.idx;
        if(idx>wrtIdx){
//        	System.out.println("$$ "+idx+"_"+in.readerIndex()+"_"+wrtIdx);
        	if(in.readerIndex()==0)idx=0;
        }
        for (/* use current idx */; idx < wrtIdx; idx++) {
            char c = (char)in.getByte(idx);
//            System.out.println(c);
//            if(Character.isWhitespace(c))continue;
            if(stat==XmlStat.INIT){
            	if(c=='<'){
            		if((idx+1)>=wrtIdx)return;
            		byte c_next = in.getByte(idx+1);
            		if(c_next=='?'){
            			lastStat = stat;
            			stat = XmlStat.HEADER;
            			idx++;
            		}
            		else if(c_next=='!'){
            			lastStat = stat;
            			stat = XmlStat.HEADER;
            			idx++;
            		}
            		else{
            			stat = XmlStat.HEAD;
            			openCount++;
            		}
            	}
            	else if(Character.isWhitespace(c)==false){
//            	else{
            		reset();
            		in.skipBytes(in.readableBytes());
            		throw new DecoderException("XML_ERR");
            	}
            }else if(stat==XmlStat.HEADER){
            	if(c=='>' && idx>0 &&in.getByte(idx-1)=='?'){
            		stat = lastStat;
            	}
            }else if(stat==XmlStat.COMMENT){
            	if(c=='-'){
            		if((idx+2)>=wrtIdx)return;
            		if(in.getByte(idx+1)=='-'&&in.getByte(idx+2)=='>'){//结束注释
            			idx+=2;
            			stat = lastStat;
            		}
            	}
            }else if(stat==XmlStat.HEAD){
            	if(c=='='){//开始注释
            		if((idx+1)>=wrtIdx)return;
            		if(in.getByte(idx+1)=='"'){
            			idx++;
            			lastStat = stat;
            			stat = XmlStat.ATTR;
            		}
            	}else if(c=='>'){//结束 头部
            		if(in.getByte(idx-1)=='/'){//结束节点
            			openCount--;
            		}
            		stat = XmlStat.BODY;
            	}
            }else if(stat==XmlStat.ATTR){
            	if(c=='"'&&in.getByte(idx-1)!='\\'){
            		stat = lastStat;
            	}
            }else if(stat==XmlStat.BODY){
            	if(c=='<'){
            		if((idx+1)>=wrtIdx)return;
            		byte c_next = in.getByte(idx+1);
            		if(c_next=='/'){
            			closeing = true;
            			stat = XmlStat.END;
            			idx++;
            		}else if(c_next=='!'){
            			if((idx+3)>=wrtIdx)return;
            			if(in.getByte(idx+2)=='-'&&in.getByte(idx+3)=='-'){
            				lastStat = stat;
            				stat = XmlStat.COMMENT;
            				idx+=3;
            				continue;
            			}
            			if((idx+8)>=wrtIdx)return;
            			//<![CDATA[
            			if(in.getByte(idx+2)=='['&&in.getByte(idx+3)=='C'&&in.getByte(idx+4)=='D'&&in.getByte(idx+5)=='A'&&in.getByte(idx+6)=='T'&&in.getByte(idx+7)=='A'&&in.getByte(idx+8)=='['){
            				lastStat = stat;
            				stat = XmlStat.CDATA;
            				idx+=8;
            			}
            		}else{
            			openCount ++;
            			stat = XmlStat.HEAD;
            		}
            	}
            }else if(stat==XmlStat.CDATA){
            	if((idx+2)>=wrtIdx)return;
            	if(in.getByte(idx)==']'&&in.getByte(idx+1)==']'&&in.getByte(idx+2)=='>'){
            		idx +=2;
            		stat = lastStat;
            	}
            }else if(stat==XmlStat.END){
            	if(c=='>'){
            		if(closeing){
            			closeing = false;
            			stat = XmlStat.BODY;
            			if(idx>1&&in.getByte(idx-1)=='/'&&in.getByte(idx-2)=='<'){
            				continue;
            			}
            			openCount --;
            		}else{
            			reset();
            			in.skipBytes(in.readableBytes());
            			throw new DecoderException("XML_ERR");
            		}
            	}
            }
            if(openCount==0 && stat!=XmlStat.HEADER && stat!=XmlStat.INIT){
            	int length = idx+1-in.readerIndex();
            	stat = XmlStat.INIT;
            	lastStat = stat;
            	if(length>0){
            		out.add(in.copy(in.readerIndex(), length));
            		in.skipBytes(length);
            		idx = in.readerIndex();
            		break;
            	}
            }
        }
        if (in.readableBytes() == 0) {
            this.idx = 0;
        } else {
            this.idx = idx;
        }
	}
    private void reset() {
    	stat = XmlStat.INIT;
    	lastStat = stat;
        idx = 0;
        openCount = 0;
        closeing = false;
    }
	
}