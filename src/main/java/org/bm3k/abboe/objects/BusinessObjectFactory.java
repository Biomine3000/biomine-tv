package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessMediaType;
import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.common.PlainTextPayload;
import util.collections.Pair;

public class BusinessObjectFactory implements IBusinessObjectFactory {
    public BusinessObject makeEvent(BusinessObjectEventType e) {
        return BOB.newBuilder().event(e).build();
    }

    public BusinessObject makeObject(MediaType type, byte[] payload) {
        return BOB.newBuilder().type(type).payload(payload).build();
    }

    public BusinessObject makeObject(Pair<BusinessObjectMetadata, byte[]> data) {
        return BOB.newBuilder().metadata(data.getObj1()).payload(data.getObj2()).build();
    }

    @Override
    public BusinessObject makePlainTextObject(String text) {
        return BOB.newBuilder().type(BusinessMediaType.PLAINTEXT)
                .payload(new PlainTextPayload(text)).build();
    }

    @Override
    public BusinessObject makePlainTextObject(String text, BusinessObjectEventType eventType) {
        return BOB.newBuilder()
                .type(BusinessMediaType.PLAINTEXT)
                .payload(new PlainTextPayload(text))
                .event(eventType)
                .build();
    }
}

