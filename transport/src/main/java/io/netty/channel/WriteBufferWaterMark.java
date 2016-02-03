package io.netty.channel;

public class WriteBufferWaterMark {
	
	private int writeBufferHighWaterMark;
	private int writeBufferLowWaterMark;
 
	public WriteBufferWaterMark(int writeBufferLowWaterMark, int writeBufferHighWaterMark) {
		super();
 	    if (writeBufferLowWaterMark < 0) {
	            throw new IllegalArgumentException(
	                    "writeBufferLowWaterMark must be >= 0");
	    }
		if (writeBufferHighWaterMark < writeBufferLowWaterMark) {
		        throw new IllegalArgumentException(
		                 "writeBufferHighWaterMark cannot be less than " +
		                            "writeBufferLowWaterMark (" + writeBufferLowWaterMark + "): " +
		                            writeBufferHighWaterMark);
		}
		this.writeBufferHighWaterMark = writeBufferHighWaterMark;
		this.writeBufferLowWaterMark = writeBufferLowWaterMark;
	}

	public int getWriteBufferHighWaterMark() {
		return writeBufferHighWaterMark;
	}

	public int getWriteBufferLowWaterMark() {
		return writeBufferLowWaterMark;
	}

	public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
		this.writeBufferHighWaterMark = writeBufferHighWaterMark;
	}

	public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
		this.writeBufferLowWaterMark = writeBufferLowWaterMark;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("WriteBufferWaterMark [writeBufferHighWaterMark=");
		builder.append(writeBufferHighWaterMark);
		builder.append(", writeBufferLowWaterMark=");
		builder.append(writeBufferLowWaterMark);
		builder.append("]");
		return builder.toString();
	}
	 
}
