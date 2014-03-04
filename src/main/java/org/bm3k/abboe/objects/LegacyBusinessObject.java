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
 * <pre>
 * BEGIN Message Format 
 *     JSON METADATA in UTF-8 encoding
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
 * TODO: move payload implementations to different class?
 * 
 * TBD: are business objects to be immutable, that is can the toBytes change?
 *      At 2011-12-06, it appears that the answer should be "no".
 *  
 */
@Deprecated
public class LegacyBusinessObject implements BusinessObject {
    private static final Logger log = LoggerFactory.getLogger(LegacyBusinessObject.class);

   /**
    * Implementation note: this should never be set directly, but always using setMetadata.
    * This is because we always want a reverse link from the metadata to this object.
    */
    private BusinessObjectMetadata metadata;
    
    /**
     * Might be null when subclass is implementing its own payload storage protocol. Should always be accessed through
     * {@link #getBytePayload()}, never directly, even within this very class.
     * */
    private byte[] payload;
    
    /**
     * Metadata shall be empty, and there will be no payload.
     */
    protected LegacyBusinessObject() {
        this(new BusinessObjectMetadata());
    }       

    /**
     * Create an event object with no payload.
     */
    protected LegacyBusinessObject(BusinessObjectEventType eventType) {
        this(new BusinessObjectMetadata());
        metadata.setEvent(eventType);
    }
    
     
    public static LegacyBusinessObject readObject(InputStream is) throws IOException, InvalidBusinessObjectException {
        Pair<BusinessObjectMetadata, byte[]> packet = readPacket(is);
        return makeObject(packet);
    }
    
    public boolean hasPayload() {
        return metadata.hasPayload();
    }

    @Override
    public Payload getPayload() {
    	MediaType type = getMetadata().getOfficialType();
    	if (type == null) {
    		throw new RuntimeException("No type");
    	}
        return new Payload(type, getBytePayload());
    }

    
    /**
     * @return null if no more business objects in stream. Note that payload may be null!
     * @throws InvalidBusinessObjectException when packet is not correctly formatted
     * @throws org.bm3k.abboe.common.InvalidJSONException JSON metadata is not correctly formatted json
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
//        System.err.println("Got metadata toBytes: "+new String(metabytes));
        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
//        System.err.println("Got metadata: "+metadata);
        byte[] payload;
        if (metadata.hasPayload()) {
            // log.info("Metadata has payload");
            int payloadSz = metadata.getSize();
            payload = IOUtils.readBytes(is, payloadSz);           
        }
        else {
            // no payload
            payload = null;
        }
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);
    }
    
    /**
     * TODO: actually, the metadada should be a more integral part of the buziness object, and 
     * not implemented as a separate class; instead, the payload should be implemented as a separate class... 
     */
    public void setEvent(String type) {
        metadata.setEvent(type);
    }
    
    public void setEvent(BusinessObjectEventType type) {
        metadata.setEvent(type);
    }
    
    /** Parse businessobject represented as raw toBytes into medatata and payload */
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
//            int payloadSz = metadata.getSize();
//            payload = IOUtils.readBytes(is, payloadSz);           
        }
        else {
            // no payload
            payload = null;
        }
        
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);        
    }
    
    private static LegacyBusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
        if (data == null) {
            throw new RuntimeException("makeObject called with null data");
        }
        return makeObject(data.getObj1(), data.getObj2());
    }
    
    /**
     * Construct a BusinessObject using a dedicated implementation class.
     * 
     * To construct a raw business object using the default implementation (this very class),
     * use the constructor with similar params, instead of this factory method.
     * 
     * Payload must be null IFF metadata does not contain field "type"
     */ 
    public static LegacyBusinessObject makeObject(BusinessObjectMetadata metadata, byte[] payload) {
        MediaType officialType = metadata.getOfficialType();
        LegacyBusinessObject bo = null;
        if (officialType != null) {
            // an official type
            try {
                bo = LegacyBusinessObjectFactory.make(officialType);
                bo.setMetadata(metadata);
                bo.setPayload(payload);
            }
            catch (IllegalAccessException e) {
                log.error("Failed constructing an instance of an official business object type", e);
            }
            catch (InstantiationException e) {
                log.error("Failed constructing an instance of an official business object type", e);
            }
            
        }
        
        // gravely enough, type is not official, or even more gravely, failed to construct an 
        // instance => use pesky default implementation
        if (bo == null) {
            bo = new LegacyBusinessObject(metadata, payload);
        }
                
        return bo;
    }
    
    public boolean isEvent() {
        return metadata.isEvent();
    }
    
    /** Create a business object with no payload */
    public LegacyBusinessObject(BusinessObjectMetadata metadata) {
        setMetadata(metadata);
    }    
    
    /** Create a business object supposedly being received and parsed earlier from the biomine business objects bus */
    public LegacyBusinessObject(BusinessObjectMetadata metadata, byte[] payload) {
        setMetadata(metadata);
        setPayload(payload);
        
        // sanity checks
        if (metadata.hasPayload() != (payload != null)) {
            throw new RuntimeException("Cannot construct a BusinessObject with a type and no payload");
        }        
    }
    
    /** Create metadata and set type as the only field. */
    private void initMetadata(String type) {
        BusinessObjectMetadata meta = new BusinessObjectMetadata();
        meta.setType(type);
        setMetadata(meta);
    }
    
    /**
     * Create a new business object to be sent; payload length will be set to metadata automatically.
     * Type and payload are required to be non-null here (use constructor with no parameters to create
     * an object with (at least initially) no payload (and thus no type)
     */
    public LegacyBusinessObject(String type, byte[] payload) {
        initMetadata(type);
        setPayload(payload); 
    }               
    
    /**
     * Create a new business object to be sent; payload length will be set to metadata automatically.
     * Naturally, both type and payload are required to be non-null.
     */
    public LegacyBusinessObject(MediaType type, byte[] payload) {
        initMetadata(type.toString());
        setPayload(payload); 
    }
    
    /**
     * To be called by subclass constructor that while a novel BusinessObject is being created from scratch.
     * This method will create a metadata which initially only contains the type field.
     * Subclass is assumed to set the payload size to the superclass metadata by 
     * calling getMetadata().setPayloadSize() after its own construction process has been finished.
     */
    public LegacyBusinessObject(String type) {
        BusinessObjectMetadata meta = new BusinessObjectMetadata();
        meta.setType(type);
        setMetadata(meta);
        
        setPayload(null);
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
	
	/**
	 * Get payload as transmittable toBytes. This default implementation just returns a reference to a byte array
	 * managed by this class. Subclasses desiring to implement storing of payload in some other manner than 
	 * raw toBytes should override this.
	 */	 
	public byte[] getBytePayload() {
	    return this.payload;
	}

	/**
     * Set payload supposedly received as transmitted toBytes. This default implementation just stores a reference to the
     * toBytes provided; subclasses desiring to implement storing of payload in some other manner than raw
     * toBytes should override this.
     * 
     * Sets size to payload as a side-effect.
     */  
	public void setPayload(byte[] payload) {
	    this.payload = payload; 
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
    	    byte[] payload = getBytePayload();
    	    
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
	
	public boolean equals(Object o) {
	    if (o instanceof LegacyBusinessObject) {
	        LegacyBusinessObject bo = (LegacyBusinessObject)o;
	        return metadata.equals(bo.metadata) 
	                && Arrays.equals(payload, bo.payload);
	    }
	    else {
	        return false;
	    }
	}			
	
	public String toString() {
	    String payloadStr = metadata.hasPayload() 
	                      ? "<payload of "+ getBytePayload().length+" bytes>"
	                      : (isEvent() ? "" : "<no payload>");
	    return "BusinessObject <metadata: "+metadata.toString()+"> "+payloadStr;
	}
	
	public static class Factory implements IBusinessObjectFactory {
		
		public BusinessObject makeEvent(BusinessObjectEventType eventType) {
			return new LegacyBusinessObject(eventType);
		}
	    
		public BusinessObject makeObject(MediaType type, byte[] payload) {
	    	return new LegacyBusinessObject(type, payload);
	    }
	    
	    public BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
	    	return LegacyBusinessObject.makeObject(data);
	    }

		@Override
		public BusinessObject makePlainTextObject(String text) {
			return new PlainTextObject(text);
		}

		@Override
		public BusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType) {
			return new PlainTextObject(text, eventType); 
		}
	}
}
