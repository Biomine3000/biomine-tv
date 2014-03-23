package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;

public interface BusinessObject {
	BusinessObjectMetadata getMetadata();

	byte[] toBytes();

    byte[] getPayload();
    
    boolean hasNature(String nature);
    
    boolean isEvent();

    MediaType getType();
}
