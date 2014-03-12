package org.bm3k.abboe.common;

/** Should this be called "InvalidBusinessObjectException" */
@SuppressWarnings("serial")
public class InvalidBusinessObjectException extends RuntimeException {    
                              
    private Throwable cause;
        
    public InvalidBusinessObjectException(String message) {
        super(message);
    }

    public InvalidBusinessObjectException(Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }
    
    public InvalidBusinessObjectException(String message, Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }

    public Throwable getCause() {
        return this.cause;
    }
}

