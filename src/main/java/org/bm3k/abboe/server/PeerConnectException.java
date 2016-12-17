package org.bm3k.abboe.server;

/** Exception while connecting to a peer server */
@SuppressWarnings("serial") class PeerConnectException extends Exception {    
                              
	private boolean retryable;
    private Throwable cause;
                           
    public PeerConnectException(String message, Throwable cause, boolean retryable) {
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