package biomine3000.objects;

public class UnknownImageTypeException extends Exception {

    public UnknownImageTypeException(String fileName) {
        super("Unknown image type for file: "+fileName);
    }
    
}
