package org.bm3k.abboe.common;

import com.google.common.net.MediaType;

import java.util.HashMap;
import java.util.Map;

public class PayloadFactory  {
    private static final Map<MediaType, Class<? extends Payload>> implementations = new HashMap<>();

    static {
        implementations.put(BusinessMediaType.PLAINTEXT, PlainTextPayload.class);
        implementations.put(BusinessMediaType.MP3, MP3Payload.class);
    }

    public static Payload make(MediaType type) throws IllegalAccessException, InstantiationException {
        if (type.is(MediaType.ANY_IMAGE_TYPE)) {
            return new ImagePayload(type);            
        }

        if (implementations.containsKey(type)) {
            return implementations.get(type).newInstance();
        } else {
            return Payload.class.newInstance();
        }
    }
}

