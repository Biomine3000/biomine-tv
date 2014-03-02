package biomine3000.objects;

@SuppressWarnings("serial")
public class UnknownImageTypeException extends Exception {

	public UnknownImageTypeException(String fileName) {
        super("Unknown image type for file: "+fileName);
    }
    
}
