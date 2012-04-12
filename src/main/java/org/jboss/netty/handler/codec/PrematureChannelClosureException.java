/*
 * Copyright 2012 The Netty Project
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
package org.jboss.netty.handler.codec;

/**
 * Exception which should get thrown if a Channel got closed before it is expected 
 */
public class PrematureChannelClosureException extends Exception {
    
    /**
     * 
     */
    private static final long serialVersionUID = 233460005724966593L;

    public PrematureChannelClosureException() {
        super();
    }
    
    public PrematureChannelClosureException(String msg) {
        super(msg);
    }
    
    
    public PrematureChannelClosureException(String msg, Throwable t) {
        super(msg, t);
    }
    
    
    public PrematureChannelClosureException(Throwable t) {
        super(t);
    }
    


}
