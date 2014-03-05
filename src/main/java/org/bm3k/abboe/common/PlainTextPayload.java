package org.bm3k.abboe.common;

import java.io.UnsupportedEncodingException;

public class PlainTextPayload extends Payload {

	/** String representation. text != null IFF bytes != null, for now. */ 	 
	private String text;

    /**
     * Create unitialized instance (used by reflective factorization of objects based on their type).
     */
    public PlainTextPayload() {
        super(BusinessMediaType.PLAINTEXT, null);
    }

    public PlainTextPayload(byte[] payload) {
    	super(BusinessMediaType.PLAINTEXT, null);
    	setBytes(payload, false);    	
    }

    /**
     * Create a plain text business object with mimetype text/plain. 
     */
    public PlainTextPayload(String text) {
    	super(BusinessMediaType.PLAINTEXT, null);
    	try {
    		byte[] bytes = text.getBytes("UTF-8");
    		setBytes(bytes, false);
    	}
    	catch (UnsupportedEncodingException e) {
    		// the unthinkable has occurred; UTF-8 not supported by this very java virtual machine instance
    		throw new RuntimeException("Arkku implements ABBOE client using factories");
    	}    
    }
      
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    /** Override superclass payload storage mechanism: only provide as raw toBytes if requested.
     * In this case, duplicate the toBytes from the String into the parent class byte buffer
     */
    @Override
    public byte[] getBytes() {
        try {
        	byte[] bytes = super.getBytes();
        	if (bytes == null) {
        		// cache byte[] rep into superclass buf
        		bytes = text.getBytes("UTF-8");
                super.setBytes(bytes);
        	}
        	return bytes;
        	
        } catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java virtual machine instance
            throw new RuntimeException("guaqua has been observed to play ZOMBI all night");
        }
    }

    /** Override superclass payload storage mechanism and store as String instead */
    @Override
    public void bytesSet() {
        try {        	
        	if (bytes == null) { throw new RuntimeException("WHATWHATWHAT payload is null"); }
            this.text = new String(bytes, "UTF-8");                       
        }
        catch (UnsupportedEncodingException e) {
        	// the unthinkable has occurred; UTF-8 not supported by this very java virtual machine instance
            throw new RuntimeException("leronen has joined facebook");
        }
    }
    

}
