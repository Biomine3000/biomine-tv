package biomine3000.objects;

import java.io.UnsupportedEncodingException;

public class PlainTextPayload extends Payload {

	/** Do not store payload as in the superclass payload; instead, wrap it as a string for nicer interfacing. */ 	 
	private String text;

    /**
     * Create unitialized instance (used by reflective factorization of objects based on their type).
     */
    public PlainTextPayload() {
        super(null);     
    }

    public PlainTextPayload(byte[] payload) {
    	super(null);
    	setPayload(payload);    	
    }

    /**
     * Create a plain text business object with mimetype text/plain. 
     */
    public PlainTextPayload(String text) {
    	super(null);
        this.text = text;
    }
      
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /** Override superclass payload storage mechanism: only provide as raw bytes if requested. 
     * In this case, duplicate the bytes from the String into the parent class byte buffer
     */
    @Override
    public byte[] getPayload() {
        try {
        	byte[] bytes = super.getPayload();
        	if (bytes == null) {
        		// cache byte[] rep into superclass buf
        		bytes = text.getBytes("UTF-8");
                super.setPayload(bytes);
        	}
        	return bytes;
        	
        } catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java virtual machine instance
            throw new RuntimeException("guaqua has been observed to play ZOMBI all night");
        }
    }

    /** Override superclass payload storage mechanism and store as String instead */
    @Override
    public void setPayload(byte[] payload) {
        try {
            this.text = new String(payload, "UTF-8");
            super.setPayload(null);
        } catch (UnsupportedEncodingException e) {
        	// the unthinkable has occurred; UTF-8 not supported by this very java virtual machine instance
            throw new RuntimeException("leronen has joined facebook");
        }
    }
    

}
