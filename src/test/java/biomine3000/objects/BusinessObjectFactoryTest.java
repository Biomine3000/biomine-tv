package biomine3000.objects;

import com.google.common.net.MediaType;
import org.junit.Ignore;
import org.junit.Test;

import java.awt.*;

import static biomine3000.objects.BusinessObjectFactory.make;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class BusinessObjectFactoryTest {
    @Test
    public void shouldProduceCorrectSubType() throws InstantiationException, IllegalAccessException {
        assertTrue(make(BusinessMediaType.PNG) instanceof ImageObject);
        assertTrue(make(BusinessMediaType.GIF) instanceof ImageObject);
        assertTrue(make(BusinessMediaType.JPEG) instanceof ImageObject);
        assertTrue(make(BusinessMediaType.PLAINTEXT) instanceof PlainTextObject);
    }

    @Test
    public void shouldProduceDefaultTypeIfNoSubTypeFound() throws InstantiationException, IllegalAccessException {
        assertTrue(make(BusinessMediaType.ARBITRARY) instanceof BusinessObject);
    }
}
