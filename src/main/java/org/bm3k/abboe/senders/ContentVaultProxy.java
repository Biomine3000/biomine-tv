package org.bm3k.abboe.senders;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

import org.bm3k.abboe.common.BusinessMediaType;
import org.bm3k.abboe.common.BusinessObjectMetadata;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.MediaType;

import util.IOUtils;
import util.RandUtils;
import util.StringUtils;


/**
 * A proxy to a remote content vault accessible via the web. This trivial version loads all content, and notifies listeners 
 * after each piece of content has been loaded.
 */
public class ContentVaultProxy {
    private static final Logger logger = LoggerFactory.getLogger(ContentVaultProxy.class);

    public static String LERONEN_IMAGE_VAULT_URL = "http://www.cs.helsinki.fi/u/leronen/biomine3000/biomine_tv_image_vault";
    public static String LERONEN_IMAGE_VAULT_FILELIST_URL = LERONEN_IMAGE_VAULT_URL+"/filelist.txt";
    
    /** only contains successfully loaded images */
    private State state;
    private List<String> urls; 
    private Map<String, BusinessObject> loadedImagesByURL = new TreeMap<>();
    private List<ContentVaultListener> listeners = new ArrayList<>();
    
    /**
     * Create and uninitialized vault proxy with no images. Do not start loading images yet
     * until called for.
     *
     */
    public ContentVaultProxy() {        
        state = State.UNINITIALIZED;
    }
        
    public int getNumLoadedObjects() {
        return loadedImagesByURL.keySet().size();
    }
    
    public int getTotalNumObjects() {
        return urls.size(); 
    }
    
    public void addListener(ContentVaultListener vaultListener) {
        listeners.add(vaultListener);
    }
    
    public void removeListener(ContentVaultListener vaultListener) {
        listeners.remove(vaultListener);
    }
    
    /** 
     * Start loading images from the vault. Listeners shall be listened upon completion 
     * of each image. Returns immediately. Remember to add listeners before calling this (?)
     */
    public void startLoading() {
        new Thread(new Loader()).start();        
    }
    
    /**
     * Should only be called if called is certain that images have been loaded.
     * Never return null.
     */
    public BusinessObject sampleImage() throws InvalidStateException {
        synchronized(loadedImagesByURL) {    
            if (loadedImagesByURL == null || loadedImagesByURL.size() == 0) {
                throw new InvalidStateException("No images to sample from");
            }
            
            String url = RandUtils.sampleOne(loadedImagesByURL.keySet());

            return loadedImagesByURL.get(url);
        }
    }
    
    /** Load list of available images from a remote file. */
    private static List<String> loadImageList(String filelistURLString) throws IOException {
        List<String> result = new ArrayList<String>();
        URL url= new URL(filelistURLString);
        String protocol = url.getProtocol();
        String hostName = url.getHost();               
        File path = new File(url.getPath());
        String dir = path.getParent().replace('\\', '/'); // windows...        
        String baseName = protocol+"://"+hostName+dir;
                        
        try {     
            // Create a URL for the desired page                   
            BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));            
            String name;
            while ((name = in.readLine()) != null) {     
                String imageURL = baseName+"/"+name;
                result.add(imageURL);
            }   
            return result;
   
        } catch (IOException e) {
            logger.error("Could now read file list from image vault", e);
            return result;
        }
            
    }
       
               
    private class Loader implements Runnable {
        public void run() {
            if (state != State.UNINITIALIZED) {
                throw new RuntimeException("Should only be called when state is "+State.UNINITIALIZED);
            }
            
            state = State.LOADING_FILELIST;
                        
            try {
                urls = loadImageList(LERONEN_IMAGE_VAULT_FILELIST_URL);
                state = State.LOADING_IMAGES;
                logger.info("Filelist loaded");
                for (ContentVaultListener listener: listeners) {
                    listener.loadedImageList();
                }
            }
            catch (IOException e) {
                state = State.FAILED_LOADING_FILELIST;
                logger.warn("Failed loading filelist", e);
                return;
            }
            
            for (String url: urls) {
                logger.debug("Loading image: {}", url);
                try {
                   
                    InputStream is = new URL(url).openStream();
                    byte[] bytes = IOUtils.readBytes(is);                    
                    
                    BusinessObjectMetadata metadata = new BusinessObjectMetadata();
                    metadata.put("name", url);
                    String extension = StringUtils.getExtension(url);
                    MediaType type = BusinessMediaType.getByExtension(extension);
                    
                    if (type == null) {
                    	logger.warn("File with unknown extension in content vault proxy: "+url);
                    	continue;
                    }
                                        
                    BusinessObject img = BOB.newBuilder()
                            .metadata(metadata)
                            .type(type)
                            .payload(bytes)
                            .build();

                    logger.debug("Loaded image: {}", url);
                    synchronized(loadedImagesByURL) {                    
                        loadedImagesByURL.put(url,  img);
                    }
                    for (ContentVaultListener listener: listeners) {
                        listener.loadedImage(url);
                        if (loadedImagesByURL.size() == urls.size()) {
                            listener.loadedAllImages();
                        }
                    }
                }
                catch (IOException e) {
                    logger.warn("Failed loading image: " + url, e);
                }
            }
                                                  
            if (loadedImagesByURL.size() == 0) {
                state = State.FAILED_LOADING_IMAGES;
                logger.error("Failed to load any images");
            }
            else {            
                state = State.INITIALIZED_SUCCESSFULLY;
                logger.info("Loaded {} images", loadedImagesByURL.keySet().size()+"/"+urls.size());
            }
        }
    }
            
    public interface ContentVaultListener {
        /**
         * Called after vault has loaded the list of images. Note that caller is reponsible 
         * for doing the actual responding in a synchronized way (more spefifically, this 
         * will not be called from the event dispatch thread)
         */
        public void loadedImageList();
        
       /** Called after vault has loaded each image.
         * Note that caller is reponsible for doing the actual responding in a synchronized way (more spefifically, this 
         * will not be called from the event dispatch thread)
         */
        public void loadedImage(String image);
        
        /**
         * Called after all images have been loaded. Note that loading the last image
         * has event has already been notified through {@link #loadedImage(String)}
         * when this is called.
         */
        public void loadedAllImages();
                    
    }
    
    public static void main(String[] args) throws IOException {
        List<String> urls = loadImageList(LERONEN_IMAGE_VAULT_FILELIST_URL);
        for (String url: urls) {
            System.out.println(url);
        }
        
        ContentVaultProxy content = new ContentVaultProxy();
        content.startLoading();
    }    
    
    public State getState() {
        return state;
    }
    
    /** State of vault */
    public enum State {
        UNINITIALIZED,
        LOADING_FILELIST,
        LOADING_IMAGES,
        FAILED_LOADING_FILELIST,
        FAILED_LOADING_IMAGES,
        INITIALIZED_SUCCESSFULLY;
        
    }
    
    @SuppressWarnings("serial")
	public class InvalidStateException extends Exception {
        public InvalidStateException(String msg) {
            super(msg);
        }
    }
}
