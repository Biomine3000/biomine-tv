package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessMediaType;
import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.common.InvalidBusinessObjectException;

import java.io.UnsupportedEncodingException;

/**
 * BOB is the builder class for BusinessObjectImpl.  Short for BusinessObjectBuilder since they are
 * constructed all over the place.
 */
public class BOB {
    BusinessObjectEventType event;
    MediaType type;
    byte[] payload;
    BusinessObjectMetadata metadata;

    private BOB() {
    }

    public BusinessObject build() {
        if (this.metadata == null) {
            this.metadata = new BusinessObjectMetadata();
        }

        if (payload != null && metadata.getType() == null) {
            throw new RuntimeException("Payload without a type");
        }

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

    public BOB payload(byte[] payload) {
        this.payload = payload;
        return this;
    }

    public BOB metadata(BusinessObjectMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public BOB payload(String payload) {
        try {
            this.payload = payload.getBytes("UTF-8");
            this.type(BusinessMediaType.PLAINTEXT);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Unexpected encoding exception", e);
        }
        return this;
    }
}
