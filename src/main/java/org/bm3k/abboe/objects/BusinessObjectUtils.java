package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.InvalidBusinessObjectException;
import org.bm3k.abboe.common.InvalidBusinessObjectMetadataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.MediaType;

import util.IOUtils;
import util.collections.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class BusinessObjectUtils {
	
	private static final Logger log = LoggerFactory.getLogger(BusinessObjectUtils.class);
	
    private static final int MAX_METADATA_BYTES = 1_000_000; // Stetson-K_leronen: Let it be quite large
    
    /**
     * @return null if no more business objects in stream.
     * @throws InvalidBusinessObjectException when packet is not correctly formatted
     * @throws org.bm3k.abboe.common.InvalidBusinessObjectMetadataException JSON metadata is not correctly formatted json
     * @throws IOException in case of general io error.
     */
    public static BusinessObject readObject(InputStream is) throws IOException, InvalidBusinessObjectException {
    	Pair<BusinessObjectMetadata, byte[]> packet = BusinessObjectUtils.readPacket(is);
    	
    	if (packet == null) {
    		return null;
    	}
                                               
        BusinessObjectMetadata meta = packet.getObj1();
        byte[] payload;
        
        if (meta.hasPayload()) {
        	MediaType type = meta.getOfficialType();
        	byte[] data = packet.getObj2();
            if (type != null) {
                payload = data;
            }
            else { 
            	log.warn("Cannot process payload. Metadata: "+meta);
            	payload = null;
            }                                       
        }
        else {
        	// no payload
        	payload = null;
        }
                                       
        return BOB.newBuilder()
            .metadata(meta)
            .payload(payload)
            .build();                                
    }
    
    /**
     * @return null if no more business objects in stream. Note that payload may be null!
     * @throws InvalidBusinessObjectException when packet is not correctly formatted
     * @throws org.bm3k.abboe.common.InvalidBusinessObjectMetadataException JSON metadata is not correctly formatted json
     * @throws IOException in case of general io error.
     */
    public static Pair<BusinessObjectMetadata, byte[]> readPacket(InputStream is) throws IOException, InvalidBusinessObjectException {
        byte[] metabytes;
        try {
            metabytes = IOUtils.readBytesUntilNull(is, MAX_METADATA_BYTES);
            if (metabytes == null) {
                // end of stream reached
                return null;
            }
        }
        catch (IOUtils.UnexpectedEndOfStreamException e) {
            throw new InvalidBusinessObjectException("End of stream reached before reading first null byte", e);
        }
        catch (IOUtils.TooManyNonNullBytesException e) {
            throw new InvalidBusinessObjectMetadataException("Too long metadata in business object (> " + MAX_METADATA_BYTES + " bytes)", e);
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

    /** Parse BusinessObjectImpl represented as raw toBytes into medatata and payload. TODO: remove this unused method? */
    static Pair<BusinessObjectMetadata, byte[]> parseBytes(byte[] data) throws InvalidBusinessObjectException {
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
}
