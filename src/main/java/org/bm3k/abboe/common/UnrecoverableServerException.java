package org.bm3k.abboe.common;

/**
 * An exception which requires shutting down the server.
 */
@SuppressWarnings("serial")
public class UnrecoverableServerException extends ServerException {

    public UnrecoverableServerException(String pMsg, Throwable pCause) {
        super(pMsg, pCause);        
    }

    public UnrecoverableServerException(String pMsg) {
        super(pMsg);       
    }
    
    public UnrecoverableServerException(Throwable pCause) {
        super(pCause);          
    }
}
