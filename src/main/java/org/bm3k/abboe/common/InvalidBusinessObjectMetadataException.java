package org.bm3k.abboe.common;

/**
 * Do not use JSONException directly, as we do not want to expose clients to the gory details of the JSON package,
 * which might get replaced with some better json library at some point. 
 *
 * In practice, InvalidInvalidJSONException will probably contain a JSONException as cause, though, at least initially.
 */
@SuppressWarnings("serial")
public class InvalidBusinessObjectMetadataException extends InvalidBusinessObjectException {               
    
    private Throwable cause;
    
    public InvalidBusinessObjectMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public InvalidBusinessObjectMetadataException(String message) {
        super(message);
    }

    public InvalidBusinessObjectMetadataException(Throwable cause) {
        super(cause);
        this.cause = cause;
    }

    public Throwable getCause() {
        return this.cause;
    }
}


