package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.BusinessObjectMetadata;
import util.collections.Pair;

import com.google.common.net.MediaType;

public interface IBusinessObjectFactory {
	BusinessObject makeEvent(BusinessObjectEventType eventType);
    BusinessObject makeObject(MediaType type, byte[] payload);
    BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data);
    BusinessObject makePlainTextObject(String text);
    BusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType);
}
