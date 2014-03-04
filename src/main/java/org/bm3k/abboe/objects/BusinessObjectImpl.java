package org.bm3k.abboe.objects;

import java.io.UnsupportedEncodingException;
import org.bm3k.abboe.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A business object O is a pair (M,P), where M is metadata and P is payload. Payload may be null.
 * 
 * <pre>
 * BEGIN Message Format 
 *     METADATA (UTF-8 encoded JSON) 
 *     NULL byte ('\0')
 *     PAYLOAD (raw toBytes)
 * END Message Format
 * </pre> 
 * 
 * The JSON metadata MUST contain at least the keys "size" and "type" to specify the length (in toBytes)
 * and type (or "mimetype", as preferred by some pundits) of the payload to follow.
 * 
 * Note that while this class provides a default implementation for storing the payload as toBytes,
 * subclasses are free to implement their own mechanism, in which case the payload in this class
 * can just be left blank.
 * 
 * TODO: move default implementation of storing as toBytes to a subclass "DefaultBusinessObject" and make
 * this class Abstract?
 * 
 * TODO: move payload type from businessobjectmetadata to this class.
 * 
 * TBD: are business objects to be immutable, that is can the toBytes change?
 *      At 2011-12-06, it appears that the answer should be "no". That means we 
 *      will not have equals and hashcode, either.
 *  
 */
public class BusinessObjectImpl implements BusinessObject {
	@SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BusinessObjectImpl.class);
    
   /**
    * Implementation note: this should never be set directly, but always using setMetadata.
    * This is because we always want a reverse link from the metadata to this object.
    */
    private BusinessObjectMetadata metadata;
    
    private Payload payload;
              
    /**
     * Builder constructor, the main constructor.
     *
     * @param builder
     */
    BusinessObjectImpl(BOB builder) {
        if (builder.metadata != null) {
            this.metadata = builder.metadata;
        } else {
            this.metadata = new BusinessObjectMetadata();
        }

        if (builder.type != null) {
            this.metadata.put("type", builder.type.toString());
        }

        if (builder.event != null) {
            this.metadata.setEvent(builder.event);
        }

        if (builder.payload != null) {
            this.payload = builder.payload;
            this.metadata.put("size", this.payload.getBytes().length);
            this.metadata.setType(payload.getType());            
        }
    }

    @Override
    public boolean hasPayload() {
        return metadata.hasPayload();
    }

    @Override
    public boolean isEvent() {
        return metadata.isEvent();
    }
    

	@Override
	public Payload getPayload() {
		return this.payload;
	}

    @Override
    public BusinessObjectMetadata getMetadata() {
        return metadata;
    }

    /**
	 * Represent business object as transmittable toBytes. Returns a byte array containing both the header and payload,
	 * separated by a null character, as emphasized elsewhere. Note that in order to avoid laying memory to waste,
	 * some byte iterator or other more abstract representation should be used to avoid copying the payload toBytes...
	 *
	 * Also, the content is not cached, so calling this multiple times will result in multiple memory initializations.
	 * 
	 * Alas, somewhere, in some time, there might exist a garbage collector, which should make copying the toBytes
	 * acceptable for now.
	 */  
	public final byte[] toBytes() {
	    byte[] jsonBytes = null;
	    try {
            jsonBytes = metadata.toString().getBytes("UTF-8");           
        }
        catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred
            throw new RuntimeException("phintsan has arrived to Helsinki OPEN plätkä tournament");
        }
	    
	    byte[] bytes;
	    if (metadata.hasPayload()) {
    	    // ensure that payload size matches size in metadata at this point...
    	    byte[] payload = this.payload.getBytes();
    	    
    	    // form packet
    	    bytes = new byte[jsonBytes.length+1+payload.length];
    	    System.arraycopy(jsonBytes, 0, bytes, 0, jsonBytes.length);
            bytes[jsonBytes.length] = '\0';
            System.arraycopy(payload, 0, bytes, jsonBytes.length+1, payload.length);
	    }
	    else {
	        bytes = new byte[jsonBytes.length+1];
	        System.arraycopy(jsonBytes, 0, bytes, 0, jsonBytes.length);
	        bytes[jsonBytes.length] = '\0';
	    }
	    	    	    	   	    	    	    	    
	    return bytes;
	}				
	
	public String toString() {
	    String payloadStr = metadata.hasPayload() 
	                      ? "<payload of "+ this.payload.getBytes().length+" bytes>"
	                      : (isEvent() ? "" : "<no payload>");
	    return "BusinessObjectImpl <metadata: "+metadata.toString()+"> "+payloadStr;
	}
}
