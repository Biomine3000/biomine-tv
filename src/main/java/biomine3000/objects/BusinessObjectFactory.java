package biomine3000.objects;

import com.google.common.net.MediaType;

import java.util.HashMap;
import java.util.Map;

public class BusinessObjectFactory {
    private static final Map<MediaType, Class<? extends BusinessObject>> implementations = new HashMap<>();

    static {
        implementations.put(BusinessMediaType.PLAINTEXT, PlainTextObject.class);
    }

    public static BusinessObject make(MediaType type) throws IllegalAccessException, InstantiationException {
        if (type.is(MediaType.ANY_IMAGE_TYPE)) {
            return new ImageObject();
        }

        if (implementations.containsKey(type)) {
            return implementations.get(type).newInstance();
        } else {
            return BusinessObject.class.newInstance();
        }
    }
}
