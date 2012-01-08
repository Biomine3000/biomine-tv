    package biomine3000.objects;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import biomine3000.objects.BusinessObjectException.ExType;

import util.IOUtils;
import util.IOUtils.UnexpectedEndOfStreamException;
import util.collections.Pair;
import util.dbg.ILogger;
import util.dbg.Logger;
import util.dbg.StdErrLogger;


/**
 * BEGIN Message Format 
 *     JSON METADATA in UTF-8 encoding
 *     NULL byte ('\0')
 *     PAYLOAD (raw bytes)
 * END Message Format
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
 * TBD: are business objects to be immutable, that is can the bytes change?
 *      At 2011-12-06, it appears that the answer should be "no".
 *  
 */
public class BusinessObject {    
    
    protected BusinessObjectMetadata metadata;
    
    /**
     * Might be null when subclass is implementing its own payload storage protocol. Should always be accessed through
     * {@link #getPayload()}, newer directly, even within this class.
     * */
    private byte[] payload;
    
    /**
     * No-op constructor targeted to enable creating subclasses by reflection.
     * 
     * Metadata and payload must be set ASAP by {@link #setPayload()} and 
     * {@link #setMetadata()}.  
     * 
     * that are not able to provide superclass constructor
     * params on the first line of the constructor due to some mandatory try-catching...
     * 
     * Callers need to call {@link #init()} ASAP after this!
     */
    protected BusinessObject() {
        // no action
    }
     
    public static BusinessObject readObject(InputStream is) throws IOException, InvalidPacketException, BusinessObjectException {        
        Pair<BusinessObjectMetadata, byte[]> packet = readPacket(is);
        return makeObject(packet);
    }
    
    /**
     * @return null if no more business objects in stream.
     * @throws InvalidPacketException when packet is not correctly formatted
     * @throws InvalidJSONException JSON metadata is not correctly formatted json
     * @throws BusinessObjectException when some other errors occurs in constructing buziness object 
     * @throws IOException in case of general io error.
     */ 
    public static Pair<BusinessObjectMetadata, byte[]> readPacket(InputStream is) throws IOException, InvalidPacketException, BusinessObjectException {
        byte[] metabytes;
        try {
            metabytes = IOUtils.readBytesUntilNull(is);
            if (metabytes == null) {
                // end of stream reached
                return null;
            }
        }
        catch (UnexpectedEndOfStreamException e) {
            throw new InvalidPacketException("End of stream reached before reading first null byte", e);
        }
//        System.err.println("Got metadata bytes: "+new String(metabytes));                                                          
        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
//        System.err.println("Got metadata: "+metadata);
        int payloadSz = metadata.getSize();
        byte[] payload = IOUtils.readBytes(is, payloadSz);
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);
    }
    
    /** Parse businessobject represented as raw bytes into medatata and payload */ 
    public static Pair<BusinessObjectMetadata, byte[]> parseBytes(byte[] data) throws InvalidJSONException, BusinessObjectException {
        int i = 0;
        while (data[i] != '\0' && i < data.length) {
            i++;
        }
        
        if (i >= data.length) {
            throw new BusinessObjectException(ExType.ILLEGAL_FORMAT);
        }
        
        byte[] metabytes = Arrays.copyOfRange(data, 0, i);
        BusinessObjectMetadata metadata = new BusinessObjectMetadata(metabytes);
        byte[] payload = Arrays.copyOfRange(data, i+1, data.length);
        return new Pair<BusinessObjectMetadata, byte[]>(metadata, payload);        
    }
    
    public static BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
        return makeObject(data.getObj1(), data.getObj2());
    }
    
    /**
     * Construct a BusinessObject using a dedicated implementation class, if one exists
     * (managed by class {@link BiomineTVMimeType} 
     * 
     * To construct a raw business object using the default implementation (this very class),
     * use the constructor with similar params
     */ 
    public static BusinessObject makeObject(BusinessObjectMetadata metadata, byte[] payload) {
        BiomineTVMimeType officialType = metadata.getOfficialType();
        BusinessObject bo = null;
        if (officialType != null) {
            // an official type
            try {
                bo = officialType.makeBusinessObject();
                bo.setMetaData(metadata);
                bo.setPayload(payload);
            }
            catch (IllegalAccessException e) {
                Logger.error("Failed constructing an instance of an official business object type", e);                     
            }
            catch (InstantiationException e) {
                Logger.error("Failed constructing an instance of an official business object type", e);                    
            }
            
        }
        
        // gravely enough, type is not official, or even more gravely, failed to construct an 
        // instance => use pesky default implementation
        if (bo == null) {
            bo = new BusinessObject(metadata, payload);
        }
                
        return bo;
    }
    
    public boolean isEvent() {
        return metadata.isEvent();
    }
    
    /** Create a business object supposedly being received and parsed earlier from the biomine business objects bus */
    public BusinessObject(BusinessObjectMetadata metadata, byte[] payload) {
        this.metadata = metadata;
        this.payload = payload;
        
        // sanity check
        if (metadata.getSize() != payload.length) {
            throw new BusinessObjectException(BusinessObjectException.ExType.ILLEGAL_SIZE);
        }
    }
    
    /** Create a new business object to be sent; payload length will be set to metadata automatically */
    protected BusinessObject(String type, byte[] payload) {
        this.metadata = new BusinessObjectMetadata(type, payload.length);
        this.payload = payload; 
    }               
    
    /** Create a new business object to be sent; payload length will be set to metadata automatically */
    protected BusinessObject(BiomineTVMimeType type, byte[] payload) {
        this.metadata = new BusinessObjectMetadata(type.toString(), payload.length);
        this.payload = payload; 
    }
    
    /**
     * To be called by subclass constructor that while a novel BusinessObject is being created from scratch.
     * This method will create a metadata which initially only contains the type field.
     * Subclass is assumed to set the payload size to the superclass metadata by 
     * calling getMetadata().setPayloadSize() after its own construction process has been finished.
     */
    protected BusinessObject(String type) {
        this.metadata = new BusinessObjectMetadata(type, null);        
        this.payload = null;
    }    
    
    /** 
     * Metadata is represented by a json object. However, should we provide some kind of wrapper to access standard fields? 
     * Current implementation does not perform validation of payload size against one reported in metadata.
     */
    public void setMetaData(BusinessObjectMetadata metadata) {
        this.metadata = metadata;
    }
    
	/** Metadata is represented by a json object. However, should we provide some kind of wrapper to access standard fields? */
	public BusinessObjectMetadata getMetaData() {
	    Integer payloadSz = metadata.getInteger("size");
	    if (payloadSz == null) {
	        payloadSz = getPayload().length;
	        metadata.setPayloadSize(payloadSz);
	    }
	    
	    return metadata;
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
	
	/**
	 * Represent business object as transmittable bytes. Returns a byte array containing both the header and payload, 
	 * separated by a null character, as emphasized elsewhere. Note that in order to avoid memory waste,
	 * some byte iterator or other more abstract representation should be used to avoid copying the payload bytes... 
	 * 
	 * Alas, somewhere, in some time, there might exist a garbage collector, which should make copying the bytes 
	 * acceptable for now.
	 */  
	public final byte[] bytes() {
	    // ensure that payload size matches size in metadata at this point...
	    byte[] payload = getPayload();
	    metadata.setPayloadSize(payload.length);
	    byte[] jsonBytes = null;
	    
	    try {
	        jsonBytes = metadata.toString().getBytes("UTF-8");
	    }
	    catch (UnsupportedEncodingException e) {
	        // the unthinkable has occurred
	        throw new RuntimeException("phintsan has arrived to Helsinki OPEN pl�tk� tournament");
	    }
	    byte[] bytes = new byte[jsonBytes.length+1+payload.length];
	    
	    System.arraycopy(jsonBytes, 0, bytes, 0, jsonBytes.length);
	    bytes[jsonBytes.length] = '\0';
	    System.arraycopy(payload, 0, bytes, jsonBytes.length+1, payload.length);
	    
	    return bytes;
	}
	
	public boolean equals(Object o) {
	    if (o instanceof BusinessObject) {
	        BusinessObject bo = (BusinessObject)o;
	        return metadata.equals(bo.metadata) 
	                && Arrays.equals(payload, bo.payload);
	    }
	    else {
	        return false;
	    }
	}			
	

	
	public static void main(String[] args) {
	    String msgStr = "It has been implemented";
	    PlainTextObject sentBO = new PlainTextObject(msgStr, BiomineTVMimeType.BIOMINE_ANNOUNCEMENT);
	    ILogger log = new StdErrLogger();
	    System.out.println("Sent bo: "+sentBO);
	    byte[] msgBytes = sentBO.bytes();
	    try {
	        Pair<BusinessObjectMetadata, byte[]> tmp = parseBytes(msgBytes);
	        BusinessObjectMetadata receivedMetadata = tmp.getObj1();
	        byte[] receivedPayload = tmp.getObj2();	        
	        BiomineTVMimeType officialType = receivedMetadata.getOfficialType();
	        BusinessObject receivedBO = null;
	        if (officialType != null) {
	            // an official type
	            try {
	                receivedBO = officialType.makeBusinessObject();
	                receivedBO.setMetaData(receivedMetadata);
	                receivedBO.setPayload(receivedPayload);
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
	        if (receivedBO == null) {
	            receivedBO = new BusinessObject(receivedMetadata, receivedPayload);
	        }
	        log.info("Received business object: "+receivedBO);
	    }
	    catch (InvalidJSONException e) {
	        log.error("Received business object with invalid JSON: "+e);	        
	    }
	}
	
	public String toString() {
	    return "BusinessObject <metadata: "+metadata.toString()+"> <payload of "+payload.length+" bytes>";
	}
	
}
