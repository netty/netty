package org.jboss.netty.handler.codec.protobuf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.MessageLite;

/** 
 *
 * A protobuf decoder implementation that can decode any number 
 * of message types.  Assumes that the remote encoder has the same 
 * message to index mapping as this decoder.  If the header cannot be
 * read because its is in an incorrect format, or the value of the header
 * does not map to a MessageLite type then a {@link IllegalStateException} is thrown.
 *
 * @author Ant Len
 *
 */
@Sharable
public class MultiProtobufDecoder extends OneToOneDecoder  {
	private static final Logger LOGGER = Logger.getLogger(MultiProtobufDecoder.class);
	protected static final int INVALID = -1;
	
	private final ExtensionRegistry extensionRegistry;
    private final int headerFieldLength;
	private final Map<Integer, MessageLite> messageTypes;
	
	
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
    public MultiProtobufDecoder(final List<MessageLite> messageTypes, final int headerFieldLength) {
        this(messageTypes, headerFieldLength, null);
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
    public MultiProtobufDecoder(final Map<Integer, MessageLite> messageTypes, final int headerFieldLength) {
        this(messageTypes, headerFieldLength, null);
    }

    public MultiProtobufDecoder(final List<MessageLite> messageTypes, final int headerFieldLength, final ExtensionRegistry extensionRegistry) {
    	this(toMap(messageTypes), headerFieldLength, extensionRegistry);
    }
    
    public MultiProtobufDecoder(final Map<Integer, MessageLite> messageTypes, final int headerFieldLength, final ExtensionRegistry extensionRegistry) {
    	super();
    	
        if(headerFieldLength <1 || headerFieldLength >4)
        	throw new IllegalArgumentException("Incorrect field length " + headerFieldLength + ". Only values 1-4 are supported.");
    	
        this.extensionRegistry = extensionRegistry;
    	this.messageTypes = messageTypes;
    	this.headerFieldLength = headerFieldLength;
    }
    
    private static Map<Integer, MessageLite> toMap(final List<MessageLite> list){
    	final Map<Integer, MessageLite> map = new HashMap<Integer, MessageLite>(list.size());
    	int headerValue = 0;
    	
    	for(MessageLite msg : list){
			map.put(headerValue++, msg);
    	}
    	return map;
	}

    @Override
    protected final Object decode(final ChannelHandlerContext ctx, final Channel channel, final Object msg) throws Exception {
 
        if (!(msg instanceof ChannelBuffer)) {
            return msg;
        }
        
        final ChannelBuffer buffer = (ChannelBuffer)msg;
   
        final int headerValue = readHeader(buffer);
        
        final MessageLite prototype = headerValue==INVALID ? null : messageTypes.get(headerValue);
        
        if(headerValue == INVALID || prototype == null){
        	IllegalStateException error = new IllegalStateException("Cannot find Message type for header " + headerValue);
        	LOGGER.error(error.getMessage());
       	 	throw error;
        }
        
        //skip over the header bytes for when its passed upstream
        buffer.skipBytes(headerFieldLength);
        
        if(LOGGER.isDebugEnabled()){
        	LOGGER.debug("DECODING [body =" + prototype + ", body size=" + (buffer.readableBytes() - headerFieldLength)+ ", header value=" + 
        			headerValue + ", header size=" + headerFieldLength+ "]");
        }
        
        if (extensionRegistry == null) {
            return prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(buffer)).build();
        } else {
            return prototype.newBuilderForType().mergeFrom(new ChannelBufferInputStream(buffer),
                    extensionRegistry).build();
        }
    }
   
    /**
     * Reads the header value from the buffer as the same type represented by headerFieldLength.  
     * <BR> 1 = Byte
     * <BR> 2 = Short
     * <BR> 3 = Medium
     * <BR> 4 = Integer
     * 
     * @param buffer
     * @return
     */
    protected int readHeader(final ChannelBuffer buffer){
        switch (headerFieldLength) {
        case 1:
        	return buffer.getUnsignedByte(buffer.readerIndex());
        case 2:
        	return buffer.getUnsignedShort(buffer.readerIndex());
        case 3:
        	return buffer.getUnsignedMedium(buffer.readerIndex());
        case 4:
        	return (int)buffer.getUnsignedInt(buffer.readerIndex());

        default:
            return INVALID;
        }
    }
    

}
