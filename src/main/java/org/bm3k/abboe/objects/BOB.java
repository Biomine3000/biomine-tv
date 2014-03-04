package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.common.Payload;
import org.bm3k.abboe.common.PlainTextPayload;

/**
 * BOB is the builder class for BusinessObjectImpl.  Short for BusinessObjectBuilder since they are
 * constructed all over the place.
 */
public class BOB {
    BusinessObjectEventType event;
    MediaType type;
    Payload payload;
    BusinessObjectMetadata metadata;

    private BOB() {
    }

    public BusinessObject build() {
        return new BusinessObjectImpl(this);
    }

    public static BOB newBuilder() {
        return new BOB();
    }

    public BOB event(BusinessObjectEventType event) {
        this.event = event;
        return this;
    }

    public BOB type(MediaType type) {
        this.type = type;
        return this;
    }

    public BOB payload(MediaType type, byte[] payload) {
        this.payload = new Payload(type, payload);
        return this;
    }

    public BOB metadata(BusinessObjectMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public BOB payload(Payload payload) {
        this.payload = payload;
        return this;
    }
    
    public BOB payload(String payload) {
        this.payload = new PlainTextPayload(payload);
        return this;
    }
}
