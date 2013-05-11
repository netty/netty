package io.netty.handler.codec.dns;

import java.io.IOException;

/**
 * This exception is thrown when the server sends an error response
 * code (any response code that is not 0).
 * 
 * @author Mohamed Bakkar
 * @version 1.0
 */
public class ResponseException extends IOException {

	private static final long serialVersionUID = 1L;

	public ResponseException(int id) {
		super(ResponseCode.get(id));
	}

}
