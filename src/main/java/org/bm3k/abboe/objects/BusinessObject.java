package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.BusinessObjectEventType;
import org.bm3k.abboe.common.BusinessObjectMetadata;

public interface BusinessObject {
	BusinessObjectMetadata getMetadata();

	byte[] bytes();
	boolean hasPayload();
    byte[] getPayload();

    boolean isEvent();
	void setEvent(String type);
	void setEvent(BusinessObjectEventType type);
}
