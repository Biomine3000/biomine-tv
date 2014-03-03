package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.common.Payload;

public interface BusinessObject {
	BusinessObjectMetadata getMetadata();

	byte[] toBytes();

	boolean hasPayload();
    Payload getPayload();

    boolean isEvent();
}
