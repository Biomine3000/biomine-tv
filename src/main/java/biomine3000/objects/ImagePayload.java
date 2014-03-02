package biomine3000.objects;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public class ImagePayload extends Payload {
    
    /** Created on demand */
    private BufferedImage image;
    
    /** Exception caught during decoding image, if any. Not raised, but instead stored for later reference */
    private IOException imageDecodingException;
    
    /** Create unitialized instance. */
    public ImagePayload(byte[] payload) {
        super(payload);
    }
                                          
    private void initImage() throws IOException {        
        byte[] payload = getPayload();
        ByteArrayInputStream bais = new ByteArrayInputStream(payload);
        image = ImageIO.read(bais);
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
                initImage();
            }
            catch (IOException e) {
                imageDecodingException = e; 
            }
        }
        
        return image;
    }       

}
