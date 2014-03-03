package org.bm3k.abboe.objects;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;
import util.IOUtils.UnexpectedEndOfStreamException;
import util.collections.Pair;


/**
 * A business object O is a pair (M,P), where M is metadata and P is payload. Payload may be null.
 * 
 * <pre>
 * BEGIN Message Format 
 *     METADATA (UTF-8 encoded JSON) 
 *     NULL byte ('\0')
 *     PAYLOAD (raw bytes)
 * END Message Format
 * </pre> 
 * 
 * The JSON metadata MUST contain at least the keys "size" and "type" to specify the length (in bytes) 
 * and type (or "mimetype", as preferred by some pundits) of the payload to follow.
 * 
 * Note that while this class provides a default implementation for storing the payload as bytes,
 * subclasses are free to implement their own mechanism, in which case the payload in this class
 * can just be left blank.
 * 
 * TODO: move default implementation of storing as bytes to a subclass "DefaultBusinessObject" and make
 * this class Abstract?
 * 
 * TODO: move payload type from businessobjectmetadata to this class.
 * 
 * TBD: are business objects to be immutable, that is can the bytes change?
 *      At 2011-12-06, it appears that the answer should be "no". That means we 
 *      will not have equals and hashcode, either.
 *  
 */
public class BusinessObjectImpl implements BusinessObject {
    private static final Logger log = LoggerFactory.getLogger(BusinessObjectImpl.class);
    
   /**
    * Implementation note: this should never be set directly, but always using setMetadata.
    * This is because we always want a reverse link from the metadata to this object.
    */
    private BusinessObjectMetadata metadata;
    
    private Payload payload;
              
    /**
     * Event with no payload.
     */
    private BusinessObjectImpl(BusinessObjectEventType eventType) {
        this(new BusinessObjectMetadata());
        metadata.setEvent(eventType);
    }
    
    /* trivial constructor with all data pre-mangled */
    protected BusinessObjectImpl(BusinessObjectMetadata meta, Payload payload) {
    	setMetadata(meta);
    	this.payload = payload;    	
    }       
                
    public static BusinessObjectImpl readObject(InputStream is) throws IOException, InvalidBusinessObjectException {
        Pair<BusinessObjectMetadata, byte[]> packet = readPacket(is);
        return makeObject(packet);
    }
    
    public boolean hasPayload() {
        return metadata.hasPayload();
    }
    
    /** Delegate to metadata (TODO(?): merge metadata class with this one once we get rid of burden of supporting different content types in 
     * this class (and subclasses, which are to be nuked in favor of Payload object hierarchy) */
    public void setSender(String sender) {
        metadata.setSender(sender);        
    }
    
    /**
     * @return null if no more business objects in stream. Note that payload may be null!
     * @throws InvalidBusinessObjectException when packet is not correctly formatted
     * @throws InvalidJSONException JSON metadata is not correctly formatted json
     * @throws IOException in case of general io error.
     */ 
    public static Pair<BusinessObjectMetadata, byte[]> readPacket(InputStream is) throws IOException, InvalidBusinessObjectException {
        byte[] metabytes;
        try {
            metabytes = IOUtils.readBytesUntilNull(is);
            if (metabytes == null) {
                // end of stream reached
                return null;
            }
        }
        catch (UnexpectedEndOfStreamException e) {
            throw new InvalidBusinessObjectException("End of stream reached before reading first null byte", e);
        }            
        
        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
        byte[] payload;
        if (metadata.hasPayload()) {
            int payloadSz = metadata.getSize();
            payload = IOUtils.readBytes(is, payloadSz);           
        }
        else {
            // no payload
            payload = null;
        }
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);
    }
        
    public void setEvent(String type) {
        metadata.setEvent(type);
    }
    
    public void setEvent(BusinessObjectEventType type) {
        metadata.setEvent(type);
    }
    
    /** Parse BusinessObjectImpl represented as raw bytes into medatata and payload */
    public static Pair<BusinessObjectMetadata, byte[]> parseBytes(byte[] data) throws InvalidBusinessObjectException {
        int i = 0;
        while (data[i] != '\0' && i < data.length) {
            i++;
        }
        
        if (i >= data.length) {
            throw new InvalidBusinessObjectException("No null byte in business object");
        }
        
        byte[] metabytes = Arrays.copyOfRange(data, 0, i);
        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
        byte[] payload;
        if (metadata.hasPayload()) {
            payload = Arrays.copyOfRange(data, i+1, data.length);          
        }
        else {
            // no päyload
            payload = null;
        }
        
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);        
    }
    
    /** Make a business object representing an event of a given type. No other metadata fields are initialized */
    public static BusinessObjectImpl makeEvent(BusinessObjectEventType eventType) {
    	return new BusinessObjectImpl(eventType);
    }
    
    public static BusinessObjectImpl makeObject(MediaType type, byte[] payload) {
    	BusinessObjectMetadata meta = new BusinessObjectMetadata();
    	meta.setType(type);
    	return makeObject(meta, payload);
    }
    
    public static BusinessObjectImpl makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
        if (data == null) {
            throw new RuntimeException("makeObject called with null data");
        }
        return makeObject(data.getObj1(), data.getObj2());
    }
    
    /**
     * Construct a BusinessObjectImpl, preferably using a dedicated implementation class.
     * 
     * To construct a raw business object using the default implementation (this very class),
     * use the constructor with similar params, instead of this factory method.
     * 
     * Payload must be null IFF metadata does not contain field "type"
     */ 
    public static BusinessObjectImpl makeObject(BusinessObjectMetadata metadata, byte[] payload) {
        MediaType officialType = metadata.getOfficialType();
        BusinessObjectImpl bo = null;
        if (officialType != null) {
            // an official type
            try {            	
                bo = new BusinessObjectImpl(metadata, PayloadFactory.make(officialType));
                bo.payload.setBytes(payload);
            }
            catch (IllegalAccessException|InstantiationException e) {
                log.error(
                        "Failed constructing payload for of an official business object type; reverting to default payload implementation", e);
            }
//            catch (InstantiationException e) {
//                Logger.error("Failed constructing an instance of an official business object type", e);                    
//            }            
        }
        
        // gravely enough, type is not official or failed constructing dedicated payload => use pesky default implementation
        if (bo == null) {
            bo = new BusinessObjectImpl(metadata, payload);
        }
                
        return bo;
    }
    
    public boolean isEvent() {
        return metadata.isEvent();
    }
    
    /** Create a business object with no payload */
    public BusinessObjectImpl(BusinessObjectMetadata metadata) {
        setMetadata(metadata);
    }    
    
    /** Create a business object supposedly being received and parsed earlier from the biomine business objects bus */
    public BusinessObjectImpl(BusinessObjectMetadata metadata, byte[] payload) {
    	this(metadata, new Payload(payload));
                
        // sanity checks
        if (metadata.hasPayload() != (payload != null)) {
            throw new RuntimeException("Cannot construct a BusinessObjectImpl with a type and no payload");
        }        
    }                 
    
    /** 
     * Metadata is represented by a json object. However, should we provide some kind of wrapper to access standard fields? 
     * Current implementation does not perform validation of payload size against one reported in metadata.
     */
    public void setMetadata(BusinessObjectMetadata metadata) {
        this.metadata = metadata;
        metadata.setObject(this);
    }           
    
	/** 
	 * Sets correct size to metadata as a side-effect. */
	public BusinessObjectMetadata getMetadata() {
//	    setSizeToMetadata();
	    return metadata;
	}
	
	/* to be renamed to getPayload after getPayload is removed*/
	public Payload getPayloadObject() {
		return this.payload;
	}
	
	/**
	 * Get payload as transmittable bytes. This default implementation just returns a reference to a byte array 
	 * managed by this class. Subclasses desiring to implement storing of payload in some other manner than 
	 * raw bytes should override this.
	 * 
	 * To be removed (use renamed getPayloadObject instead)
	 */	 	
	public byte[] getPayload() {
	    return this.payload.getBytes();
	}
	
	/**
	 * Represent business object as transmittable bytes. Returns a byte array containing both the header and payload, 
	 * separated by a null character, as emphasized elsewhere. Note that in order to avoid laying memory to waste,
	 * some byte iterator or other more abstract representation should be used to avoid copying the payload bytes...
	 *
	 * Also, the content is not cached, so calling this multiple times will result in multiple memory initializations.
	 * 
	 * Alas, somewhere, in some time, there might exist a garbage collector, which should make copying the bytes 
	 * acceptable for now.
	 */  
	public final byte[] bytes() {
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
    	    byte[] payload = getPayload();
    	    
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
	                      ? "<payload of "+getPayload().length+" bytes>" 
	                      : (isEvent() ? "" : "<no payload>");
	    return "BusinessObjectImpl <metadata: "+metadata.toString()+"> "+payloadStr;
	}
	
	public static class Factory implements IBusinessObjectFactory {
		
		public BusinessObject makeEvent(BusinessObjectEventType e) {
			return BusinessObjectImpl.makeEvent(e);
		}
	    
		public BusinessObject makeObject(MediaType type, byte[] payload) {
	    	return BusinessObjectImpl.makeObject(type, payload);
	    }
	    
	    public BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
	    	return BusinessObjectImpl.makeObject(data);
	    }
	    
		@Override
		public BusinessObject makePlainTextObject(String text) {
			BusinessObjectMetadata meta = new BusinessObjectMetadata();
			meta.setType(BusinessMediaType.PLAINTEXT);			
			PlainTextPayload payload = new PlainTextPayload(text);
	        return new BusinessObjectImpl(meta, payload);
		}

		@Override
		public BusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType) {
			BusinessObjectMetadata meta = new BusinessObjectMetadata();
			meta.setType(BusinessMediaType.PLAINTEXT);
			meta.setEvent(eventType);
			PlainTextPayload payload = new PlainTextPayload(text);
	        return new BusinessObjectImpl(meta, payload);
		}
	}
	
}
