package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessMediaType;
import org.bm3k.abboe.common.BusinessObjectMetadata;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * BOB is the builder class for BusinessObjectImpl.  Short for BusinessObjectBuilder since they are
 * constructed all over the place.
 */
public class BOB {
    BusinessObjectEventType event;
    MediaType type;
    byte[] payload;
    BusinessObjectMetadata metadata;
    List<String> natures;

    private BOB() {
    }

    public BusinessObject build() {
        if (this.metadata == null) {
            this.metadata = new BusinessObjectMetadata();
        }

        if (type != null) {
            metadata.setType(type);
        }

        if (event != null) {
            metadata.setEvent(event);
        }

        if (natures != null) {
            metadata.setNatures(natures);
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
    
    /** add single nature */
    public BOB nature(String nature) {
        if (natures == null) {
            natures = new ArrayList<String>();           
        }
        
        natures.add(nature);
        
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
