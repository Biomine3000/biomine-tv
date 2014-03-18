package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessMediaType;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    Map<String,String> attributes;
    List<String> route;

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
        
        if (attributes != null) {
            for (String attribute: attributes.keySet()) {
                String val = attributes.get(attribute);
                metadata.put(attribute, val);
            }
        }
        
        if (route != null) {
            metadata.putStringArray("route", route);
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
    
    /** add some natures */
    public BOB natures(String... natures) {
        if (this.natures == null) {
            this.natures = new ArrayList<String>();           
        }
        
        for (String nature: natures) {
            this.natures.add(nature);
        }
        
        return this;
    }

    public BOB attribute(String attribute, String value) {
        if (attributes == null) {
            attributes = new LinkedHashMap<>();
        }
        attributes.put(attribute, value);
        
        return this;
    }
    
    public BOB route(String... routingIds) {        
        route = Arrays.asList(routingIds);
        
        return this;
    }
    
    public BOB type(MediaType type) {
        this.type = type;
        return this;
    }

    public BOB metadata(BusinessObjectMetadata metadata) {
        this.metadata = metadata;
        return this;
    }
    
    public BOB payload(byte[] payload) {
        this.payload = payload;
        return this;
    }   

    public BOB payload(CharSequence payload) {
        if (payload != null) {
            payload(payload.toString());
        }
        
        return this;        
    }
    
    public BOB payload(String payload) {
        if (payload != null) {
            try {
                this.payload = payload.getBytes("UTF-8");
                this.type(BusinessMediaType.PLAINTEXT);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("Unexpected encoding exception", e);
            }
        }
        
        return this;
    }
}
