package org.bm3k.abboe.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/* duplicates image as BufferedImage on demand. Superclass byte buffer still always used to store bytes. */  
public class ImagePayload extends Payload {
    
    /** Created on demand */
    private BufferedImage image;
    
    /** Exception caught during decoding image, if any. Not raised, but instead stored for later reference */
    private IOException imageDecodingException;
    
    public ImagePayload() {
        super(null);
    }
    
    public ImagePayload(byte[] payload) {
        super(payload);
    }
                                          
    private void decodeImage() throws IOException {        
        byte[] payload = getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(payload);
        // le cargo-culte:
        image = ImageIO.read(is);
    }
    
    /**
     * KOVA PÄÄTÖS: return null on failure to load bytes. Caller may obtain 
     * exception by method getException to alleviate wondering about receiving a null.
     */
    public BufferedImage getImage() {
        if (imageDecodingException != null) {
            // already failed decoding
            return null;
        }
            
        if (image == null) {
            // not yet decoded        
            try {
                decodeImage();
            }
            catch (IOException e) {
                imageDecodingException = e; 
            }
        }
        
        return image;
    }       

}
