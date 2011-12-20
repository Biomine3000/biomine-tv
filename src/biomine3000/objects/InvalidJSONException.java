package biomine3000.objects;

/**
 * Do not use JSONException directly, as we do not want to expose clients to the gory details of the JSON package,
 * which might get replaced with some better json library at some point. 
 *
 * In practice, InvalidInvalidJSONException will probably contain a JSONException as cause, though, at least initially.
 */
public class InvalidJSONException extends Exception {               
    
    private Throwable cause;
    
    /**
     * Constructs a InvalidJSONException with an explanatory message.
     * @param message Detail about the reason for the exception.
     */
    public InvalidJSONException(String message) {
        super(message);
    }

    public InvalidJSONException(Throwable cause) {
        super(cause.getMessage());
        this.cause = cause;
    }

    public Throwable getCause() {
        return this.cause;
    }
}


