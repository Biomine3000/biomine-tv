package org.bm3k.abboe.objects;

@SuppressWarnings("serial")
public abstract class ServerException extends Exception {
    public ServerException(String pMsg, Throwable pCause) {
        super(pMsg, pCause);        
    }

    public ServerException(String pMsg) {
        super(pMsg);       
    }
    
    public ServerException(Throwable pCause) {
        super(pCause);          
    }
}
