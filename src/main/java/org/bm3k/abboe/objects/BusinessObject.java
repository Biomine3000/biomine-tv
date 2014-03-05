package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessObjectMetadata;

public interface BusinessObject {
	BusinessObjectMetadata getMetadata();

	byte[] toBytes();

    byte[] getPayload();

    boolean isEvent();

    MediaType getType();
}
