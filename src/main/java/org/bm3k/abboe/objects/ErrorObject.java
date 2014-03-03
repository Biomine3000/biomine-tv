package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessMediaType;

@Deprecated
public class ErrorObject extends LegacyBusinessObject {
        
    /** Create unitialized instance. */
    public ErrorObject() {
        super();
        getMetadata().setEvent(BusinessObjectEventType.ERROR);
    }
        
    public ErrorObject(String text) {
        super(BusinessMediaType.PLAINTEXT.toString());
        getMetadata().setEvent(BusinessObjectEventType.ERROR);
        this.setPayload(text.getBytes());               
    }
        
    public ErrorObject(String text, String mimeType) {
        super(mimeType);
        getMetadata().setEvent(BusinessObjectEventType.ERROR);
        this.setPayload(text.getBytes());
    }
    
    /**
     * Create and error business object with specified official mimetype.
     * It is left at the responsibility of the caller that the mimetype actually be representable
     * as a plain text object.
     */  
    public ErrorObject(String text, MediaType mimeType) {
        super(mimeType.toString());
        getMetadata().setEvent(BusinessObjectEventType.ERROR);
        this.setPayload(text.getBytes());
    }
    
    public String getText() {
        return new String(this.getPayload());
    }
                      
    public String toString() {
        return "ERROR ("+getMetadata().getType()+": "+getText()+")";
    }            

}

