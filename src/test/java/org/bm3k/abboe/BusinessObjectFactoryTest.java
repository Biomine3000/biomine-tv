package org.bm3k.abboe;

import org.bm3k.abboe.objects.BusinessMediaType;
import org.bm3k.abboe.objects.ImageObject;
import org.bm3k.abboe.objects.LegacyBusinessObject;
import org.bm3k.abboe.objects.PlainTextObject;
import org.junit.Test;


import static org.bm3k.abboe.objects.LegacyBusinessObjectFactory.make;
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
        assertTrue(make(BusinessMediaType.ARBITRARY) instanceof LegacyBusinessObject);
    }
}
