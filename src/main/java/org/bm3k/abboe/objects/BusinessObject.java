package org.bm3k.abboe.objects;

public interface BusinessObject {
	BusinessObjectMetadata getMetadata();

	byte[] bytes();
	boolean hasPayload();
    byte[] getPayload();

    boolean isEvent();
	void setEvent(String type);
	void setEvent(BusinessObjectEventType type);
}
