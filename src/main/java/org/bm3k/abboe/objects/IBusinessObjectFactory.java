package org.bm3k.abboe.objects;

import org.bm3k.abboe.common.BusinessObjectEventType;
import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.objects.BusinessObject;
import util.collections.Pair;

import com.google.common.net.MediaType;

public interface IBusinessObjectFactory {
	public BusinessObject makeEvent(BusinessObjectEventType eventType);
    public BusinessObject makeObject(MediaType type, byte[] payload);
    public BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data);
    public BusinessObject makePlainTextObject(String text);
    public BusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType);
}
