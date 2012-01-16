package biomine3000.objects;


public class BusinessObjectException extends RuntimeException {
    ExType type;

    public BusinessObjectException(String message, ExType type) {
        super(type+message);
        this.type = type;        
    }
    
    public BusinessObjectException(ExType type) {
        super(""+type);
        this.type = type;        
    }            

    public enum ExType {
        CEASE_CONJURING,
        JSON_IMPLEMENTATION_MELTDOWN,
        UNRECOGNIZED_IMAGE_TYPE,
        INVALID_JSON,      
        ILLEGAL_FORMAT,
        MISSING_TYPE,
        MISSING_SIZE,
        ILLEGAL_SIZE,
        ILLEGAL_PARAMS;
    }
}
