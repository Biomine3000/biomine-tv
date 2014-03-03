package org.bm3k.abboe.common;


/** Payload for a {@link org.bm3k.abboe.objects.LegacyBusinessObject}
 * 
 * TODO: 
 *  • move default implementation of storing as toBytes to a subclass "DefaultPayload" and make this class abstract?
 *  • rename to Content?
 */
public class Payload {
					
	/**
     * Might be null when subclass is implementing its own payload storage protocol. Should always be accessed through
     * {@link #getBytes()} and {@link #setPayload()} , never directly, even within this very class.
     * */
    private byte[] bytes;
            
    /** Create unitialized instance. TODO if this needed? */ 
    public Payload() {    	
    	this.bytes = null;
    }
    
    public Payload(byte[] data) {
    	setBytes(data);
    }
    
    /**
	 * Get payload as transmittable toBytes. This default implementation just returns a reference to a byte array
	 * managed by this class. Subclasses desiring to implement storing of payload in some other manner than 
	 * raw toBytes should override this.
	 */	 
	public byte[] getBytes() {
	    return this.bytes;
	}
	
	/**
     * Set payload supposedly received as transmitted toBytes. This default implementation just stores a reference to the
     * toBytes provided; subclasses desiring to implement storing of payload in some other manner than raw
     * toBytes should override this.
     */  
	public void setBytes(byte[] payload) {
	    this.bytes = payload; 
	}	
    
}
