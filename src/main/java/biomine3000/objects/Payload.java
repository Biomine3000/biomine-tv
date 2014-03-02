package biomine3000.objects;

/** Payload for a {@link BusinessObject} */
public class Payload {
	/**
     * Might be null when subclass is implementing its own payload storage protocol. Should always be accessed through
     * {@link #getPayload()} and {@link #setPayload()} , never directly, even within this very class.
     * */
    private byte[] payload;
    
    /** Create unitialized instance. TODO if this needed? */ 
    public Payload() {
    	payload = null;
    }
    
    public Payload(byte[] data) {
    	setPayload(data);
    }
    
    /**
	 * Get payload as transmittable bytes. This default implementation just returns a reference to a byte array 
	 * managed by this class. Subclasses desiring to implement storing of payload in some other manner than 
	 * raw bytes should override this.
	 */	 
	public byte[] getPayload() {
	    return this.payload;
	}
	
	/**
     * Set payload supposedly received as transmitted bytes. This default implementation just stores a reference to the
     * bytes provided; subclasses desiring to implement storing of payload in some other manner than raw 
     * bytes should override this.
     */  
	public void setPayload(byte[] payload) {
	    this.payload = payload; 
	}
    
}
