package biomine3000.objects;

/** Should this be called "InvalidBusinessObjectException" */
public class InvalidPacketException extends Exception {    
                              
    private Throwable cause;
        
    public InvalidPacketException(String message) {
        super(message);
    }

    public InvalidPacketException(Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }
    
    public InvalidPacketException(String message, Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }

    public Throwable getCause() {
        return this.cause;
    }
}

