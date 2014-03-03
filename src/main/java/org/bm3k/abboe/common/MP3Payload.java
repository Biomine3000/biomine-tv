package org.bm3k.abboe.common;


/**
 * At this present stage, a MP3Payload offers no special handling of the payload on top of that offered
 * by the superclass Payload. Instead, the interpretation  of mp3 toBytes is left to the discretion of the
 * client. However, for the purposes of demonstrating content type hierarchies, an in anticipation of 
 * streaming content, let's have a dedicated class for this.  
 */ 
public class MP3Payload extends Payload {
                      
    /** Create unitialized instance. */
    public MP3Payload() {
        super(null);
    }
                       
    /** Create a new business object to be sent; payload length will be set to metadata automatically */
    public MP3Payload(byte[] payload) {
        super(payload);
    }            

}
