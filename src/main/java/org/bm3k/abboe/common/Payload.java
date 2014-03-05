package org.bm3k.abboe.common;

import com.google.common.net.MediaType;


/** Payload for a {@link org.bm3k.abboe.objects.LegacyBusinessObject}
 * 
 * TODO: 
 *  • move default implementation of storing as toBytes to a subclass "DefaultPayload" and make this class abstract?
 *  • rename to Content?
 */
public class Payload {
			
	protected MediaType type;
	
	/**
     * Always non-null (except for uninitalized instances...)
     * 
     * Previously it was possible to have this null when used its own impl for caching decoded data,
     * from where said bytes could be restored. This was dropped in favor of cleaner code, at least for the time being.
     */
    protected byte[] bytes;
    
    /**
     * Uninitialized and unusable instance. Must be completed later by setting bytes and type.
     * Needed to enable creating by type (no-op constructor needed).
     */
    public Payload() {
    	type = null;
    	bytes = null;
    }
        
    public Payload(MediaType type, byte[] data) {    
    	this.type = type;
    	if (data != null) {
    		setBytes(data);
    	}
    }
    
    public void setType(MediaType type) {
    	this.type = type;
    	bytes = null;
    }
    
    /**
	 * Get payload as transmittable toBytes. This default implementation just returns a reference to a byte array
	 * managed by this class. Subclasses desiring to implement storing of payload in some other manner than 
	 * raw toBytes should override this.
	 */	 
	public byte[] getBytes() {
	    return bytes;
	}
	
	public MediaType getType() {
		return type;
	}
	
	/**
     * Set payload supposedly received as transmitted toBytes. This default implementation just stores a reference to the
     * toBytes provided; subclasses desiring to implement storing of payload in some other manner than raw
     * toBytes should override this.
     */  
	public void setBytes(byte[] bytes) {
	    setBytes(bytes, true);
	}	
	
	/** uff. subclasses to be nuked... */
	public void setBytes(byte[] bytes, boolean notifySubclass) {
	    this.bytes = bytes;
	    if (notifySubclass) {
	    	bytesSet();
	    }
	}
	
	/** can be implemented by subclasses to create/update decoded repsentation */  
	public void bytesSet() {
		//
	}
    
}
