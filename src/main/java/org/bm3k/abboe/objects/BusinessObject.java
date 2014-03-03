package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.BusinessObjectMetadata;

public interface BusinessObject {
	BusinessObjectMetadata getMetadata();

	byte[] toBytes();
	boolean hasPayload();
    byte[] getPayload();

    boolean isEvent();
}
