package org.bm3k.abboe.server;

/** Exception while subscribing to a peer server */
@SuppressWarnings("serial") class SubscribeException extends Exception {    
                              
	private boolean retryable;
    private Throwable cause;
        
    public SubscribeException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }
        
    public SubscribeException(String message, Throwable cause, boolean retryable) {
        super(cause.getMessage());
        this.cause = cause;
        this.retryable = retryable;
    }

    public Throwable getCause() {
        return this.cause;
    }
    
    public boolean isRetryable() {
        return this.retryable;
    }
}