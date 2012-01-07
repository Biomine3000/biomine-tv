package biomine3000.objects;

import java.io.UnsupportedEncodingException;

public class PlainTextObject extends BusinessObject {
    
    private String text;
    
    /** Create unitialized instance. */
    public PlainTextObject() {
        super();
    }
    
    /** Create a plain text business object with mimetype text/plain */ 
    public PlainTextObject(String text) {
        super(BiomineTVMimeType.PLAINTEXT.toString());
        this.text = text;               
    }
    
    /** Create a plain text business object with specified mimetype */ 
    public PlainTextObject(String text, String mimeType) {
        super(mimeType);
        this.text = text;               
    }
    
    /**
     * Create a plain text business object with specified official mimetype.
     * It is left at the responsibility of the caller that the mimetype actually be representable
     * as a plain text object.
     */  
    public PlainTextObject(String text, BiomineTVMimeType mimeType) {                
        super(mimeType.toString());
        this.text = text;               
    }
    
    public String getText() {
        return text;
    }
        
    @Override
    public byte[] getPayload() {
        try {
            return text.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java instance
            throw new RuntimeException("guaqua has been observed to play ZOMBI all night");
        }                                             
    }
    
    @Override
    public void setPayload(byte[] payload) {
        try {
            this.text = new String(payload, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred
            throw new RuntimeException("leronen has joined facebook");
        } 
    }
    
    public String toString() {
        return metadata.getType()+": "+text;
    }
            


}
