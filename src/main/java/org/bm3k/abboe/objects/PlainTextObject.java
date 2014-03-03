package org.bm3k.abboe.objects;

import com.google.common.net.MediaType;
import org.bm3k.abboe.common.BusinessMediaType;
import org.bm3k.abboe.common.BusinessObjectEventType;
import org.bm3k.abboe.common.BusinessObjectMetadata;

import java.io.UnsupportedEncodingException;

@Deprecated
public class PlainTextObject extends LegacyBusinessObject {

    private String text;

    /**
     * Create unitialized instance (used by reflective factorization of
     * objects based on their type).
     */
    public PlainTextObject() {
        super();
        getMetadata().setType(BusinessMediaType.PLAINTEXT);
    }

    public PlainTextObject(BusinessObjectMetadata meta, byte[] payload) {
        super(meta);
        setPayload(payload);
    }

    /**
     * Create a plain text business object with mimetype text/plain
     */
    public PlainTextObject(String text) {
        super();
        getMetadata().setType(BusinessMediaType.PLAINTEXT);
        this.text = text;
    }

    /**
     * Create an event with plain text content.
     */
    public PlainTextObject(String text, BusinessObjectEventType et) {
        super(et);
        getMetadata().setType(BusinessMediaType.PLAINTEXT);
        this.text = text;
    }

    /**
     * Create a plain text business object with specified mimetype
     */
    public PlainTextObject(String text, String mimeType) {
        super(mimeType);
        this.text = text;
    }

    /**
     * Create a plain text business object with specified official mimetype.
     * It is left at the responsibility of the caller that the mimetype actually be representable
     * as a plain text object.
     */
    public PlainTextObject(String text, MediaType mimeType) {
        super(mimeType.toString());
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public byte[] getPayload() {
        try {
            return text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred; UTF-8 not supported by this very java instance
            throw new RuntimeException("guaqua has been observed to play ZOMBI all night");
        }
    }

    @Override
    public void setPayload(byte[] payload) {
        try {
            this.text = new String(payload, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            // the unthinkable has occurred
            throw new RuntimeException("leronen has joined facebook");
        }
    }


    private String formatAsEvent() {
        return "event: " + getMetadata().getEvent() + ": " + this.text;
    }

    public String toString() {
        if (isEvent()) {
            return formatAsEvent();
        } else {
            // purely content
            if (MediaType.parse(getMetadata().getType()).withoutParameters() ==
                    BusinessMediaType.PLAINTEXT.withoutParameters()) {
                return text;
            } else {
                return getMetadata().getType() + ": " + text;
            }
        }
    }
}
