package org.jboss.netty.handler.codec.protobuf;

import static org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

import com.google.protobuf.MessageLite;

/** 
 * A protobuf encoder implementation that can encode any number of message types by appending an adding a header to the stream.
 * Assumes that the remote decoder has the same message to index mapping as this encoder.
 *
 * @author Ant Len
 *
 */
@Sharable
public class MultiProtobufEncoder extends OneToOneEncoder {
	private static final Logger LOGGER = Logger.getLogger(MultiProtobufEncoder.class);
	protected static final int INVALID = -1;
	
	private final int headerFieldLength;
	
	private final Map<Integer, MessageLite> messages;
	
	
    /**
     * @param messageTypes - A list of the message types.  The header value will be the index of the element in the list.
     * 
     * @param headerFieldLength - the size of the header field:  
     * <BR> 1 = Byte
     * <BR> 2 = Short
     * <BR> 3 = Medium
     * <BR> 4 = Integer
     * 
     */
	public MultiProtobufEncoder(List<MessageLite>  messages, int headerFieldLength) {
		this(toMap(messages), headerFieldLength);
	}
	
	
    /**
     * @param messageTypes - A map of the message types.  The key of the MessageLite in the map will be used for the header.
     * 
     * @param headerFieldLength - the size of the header field:  
     * <BR> 1 = Byte
     * <BR> 2 = Short
     * <BR> 3 = Medium
     * <BR> 4 = Integer
     * 
     */
	public MultiProtobufEncoder(Map<Integer, MessageLite> messages, int headerFieldLength) {
		super();
		this.messages = messages;
		this.headerFieldLength = headerFieldLength;
	}
	
	private static Map<Integer, MessageLite> toMap(final List<MessageLite> list){
		final Map<Integer, MessageLite> map = new HashMap<Integer, MessageLite>();
		
		int headerValue = 0;
		for(MessageLite msg : list){
			map.put(headerValue++, msg);
		}
		return map;
	}
	
	
    @Override
    protected final Object encode(final ChannelHandlerContext ctx, final Channel channel, final Object msg) throws Exception {
    	
         if (!(msg instanceof MessageLite)) {
            return msg;
         }
         
    	 final ChannelBuffer header = channel.getConfig().getBufferFactory().getBuffer(headerFieldLength);
    	 final MessageLite thisMessage = (MessageLite) msg;
         final ChannelBuffer body = wrappedBuffer(thisMessage.toByteArray());
    	 
         final int headerValue = getHeaderValue(thisMessage);
         
         if(headerValue == INVALID){
        	 RuntimeException error = new RuntimeException("Cannot find Header type for message " + thisMessage);
        	 LOGGER.error(error.getMessage());
        	 throw error;
         }
		
 	    switch (headerFieldLength) {
        case 1:
            header.writeByte((byte) headerValue);
            break;
        case 2:
            header.writeShort((short) headerValue);
            break;
        case 3:
            header.writeMedium(headerValue);
            break;
        case 4:
            header.writeInt(headerValue);
            break;
        default:
            throw new Error("Invalid encoding type " + headerFieldLength);
        }
 	    
         final ChannelBuffer wrapped=  wrappedBuffer(header, body);
		
         if(LOGGER.isDebugEnabled()){
        	 LOGGER.debug("ENCODING [message=" + thisMessage + ", body size=" + body.readableBytes()+ ", header value=" + 
					headerValue + ", header size=" +header.readableBytes() + ", message size=" + wrapped.readableBytes() + "]");
         }
         
         return wrapped;
    }
    
    /**
     * Returns a header value that represents this message type. 
     * 
     * @param thisMessage
     * @return
     */
    private int getHeaderValue(final MessageLite msg){
    	
		for(Entry<Integer, MessageLite> e : messages.entrySet()){
			if(e.getValue().getClass().equals(msg.getClass())){
				return e.getKey();
			}
		}
		return INVALID;
    }
    
}
