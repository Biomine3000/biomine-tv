package biomine3000.objects;

import java.awt.image.BufferedImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;


import util.StringUtils;


/**
 * Represent image both as java-decoded BufferedImage and the original bytes
 * (bytes managed by superclass). An image usually has property "name".
 * 
 * Initialize the BufferedImage from payload on demand. 
 * 
 * Unfortunately this means that any errors in decoding are deferred until image access.
 */ 
public class ImageObject extends BusinessObject {
            
    
    /** Created on demand */
    private BufferedImage image;
    
    /** Exception caught during decoding image, if any. Not raised, but instead stored for later reference */
    private IOException imageDecodingException;
    
    /** Create unitialized instance. */
    public ImageObject() {
        super();
    }
                       
    /** Create a new business object to be sent; payload length will be set to metadata automatically */
    public ImageObject(Biomine3000Mimetype type, byte[] payload) {
        super(type, payload);
    }
    
    /** Create a new business object to be sent; payload length will be set to metadata automatically */
    public ImageObject(byte[] payload, String fileName) throws UnknownImageTypeException {
        super();
        String extension = StringUtils.getExtension(fileName);
        if (extension == null) {
            throw new UnknownImageTypeException(fileName);
        }
        
        Biomine3000Mimetype type = Biomine3000Mimetype.getByExtension(extension);
        if (type == null) {
            throw new UnknownImageTypeException(fileName);
        }
            
        BusinessObjectMetadata meta = new BusinessObjectMetadata(type);
        meta.put("name", fileName);
        setMetadata(meta);
        
        setPayload(payload);
        
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
    
    public String toString() {
        String name = getMetaData().getString("name");
        if (name == null) {
            name = "(no name)";
        }
        return getMetaData().getType()+": "+name;
    }
            


}
