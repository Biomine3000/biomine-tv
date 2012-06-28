package biomine3000.objects;

/**
 * Should this be called "InvalidBusinessObjectException"
 */
public class InvalidBusinessObjectException extends Exception {

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

