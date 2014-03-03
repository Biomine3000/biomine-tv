package org.bm3k.abboe.senders;


import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.objects.PlainTextObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ExceptionUtils;


/**
 * Adapts an arbitraty {@link org.bm3k.abboe.objects.IBusinessObjectHandler} to receive periodic
 * updates from an content vault.  
 */
public class ContentVaultAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ContentVaultAdapter.class);

    private IBusinessObjectHandler handler;
    private boolean firstImageLoaded;
    private ContentVaultProxy contentVaultProxy;
    private ContentListener contentListener;
    private Sender sender;
    /** Time between sends (in millis) */
    private int sendInterval;
    
    /**
     * Creates a content vault and starts listening to it. Caller needs to call startLoading 
     * to instruct content vault to start loading content; this shall later trigger
     * a sender thread to actually push content to the handler periodically, as dictated by 
     * sendInterval.
     * 
     * @param sendInterval interval between sent objects (in milliseconds).
     */
    public ContentVaultAdapter(IBusinessObjectHandler handler, int sendInterval) {
        this.handler = handler;
        this.sendInterval = sendInterval;
        // init communications with the server
        firstImageLoaded = false;
        contentVaultProxy = new ContentVaultProxy();
        contentListener = new ContentListener();
        contentVaultProxy.addListener(contentListener);        
    }
    
    public void startLoading() {
        contentVaultProxy.startLoading();
    }
    
    /** Stop your business (instruct sender thread to stop sending content). */
    public void stop() {
       logger.info("stop requested");
       sender.stop(); 
    }
    
    /** Sends event text as a PlainText "service/state-changed" event */
    private void sendEvent(String msg) {
        logger.info("Sending message: {}", msg);
        PlainTextObject obj = new PlainTextObject(msg);
        obj.getMetadata().setEvent(BusinessObjectEventType.SERVICES_STATE_CHANGED);
        handler.handleObject(obj);               
    }       
    
    private class ContentListener implements ContentVaultProxy.ContentVaultListener {
        
        @Override
        public void loadedImageList() {
            String msg = "Loaded urls for all "+contentVaultProxy.getNumLoadedObjects()+" business objects";
            sendEvent(msg);
        }

        @Override
        public void loadedImage(String image) {
            String msg = "Loaded "+contentVaultProxy.getNumLoadedObjects()+"/"+contentVaultProxy.getTotalNumObjects()+" business objects";            
            sendEvent(msg);            
            if (firstImageLoaded == false) { 
                logger.info("First image loaded, starting sender thread to push content to handler");
                firstImageLoaded = true;
                sender = new Sender();
                new Thread(sender).start();
            }
        }

        @Override
        public void loadedAllImages() {
            logger.info("All images have been loaded");
        }
    }
    
    /** Sends random content from vault every 3 seconds to handler */
    private class Sender implements Runnable {
        private boolean stop = false;
        
        private void stop() {
            logger.info("stop requested");
            stop = true;
        }
                       
        public void run() {
            logger.info("Sender running");
            
            while (!stop) {
                try {                    
                    BusinessObject randomContent = contentVaultProxy.sampleImage();
                    handler.handleObject(randomContent);
                    Thread.sleep(sendInterval);
                }
                catch (ContentVaultProxy.InvalidStateException e) {
                    handler.handleObject(new BusinessObjectFactory().makePlainTextObject(ExceptionUtils.format(e, "; "), BusinessObjectEventType.ERROR));
                    try {
                        Thread.sleep(sendInterval);
                    }
                    catch (InterruptedException ie) {
                        // no action
                    }
                }
                catch (InterruptedException e) {
                    // no action
                }
            }                        
            
            logger.info("stopped");
        }
    }
      
    /** Unit testing */
    public static void main(String[] args) {
        ContentVaultAdapter adapter = new ContentVaultAdapter(
                new IBusinessObjectHandler() {                                        
                    @Override
                    public void handleObject(BusinessObject bo) {
                        logger.info("DUMMY HANDLER received object: {}", bo);
                    }
                }, 3000);
        adapter.startLoading();                        
    }
}
