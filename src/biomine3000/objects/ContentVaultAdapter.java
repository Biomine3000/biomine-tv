package biomine3000.objects;


import util.ExceptionUtils;
import util.dbg.Logger;

import biomine3000.objects.ContentVaultProxy;
import biomine3000.objects.ContentVaultProxy.ContentVaultListener;
import biomine3000.objects.ContentVaultProxy.InvalidStateException;


/**
 * Adapts an arbitraty {@link BusinessObjectHandler} to receive periodic
 * updates from an content vault.  
 */
public class ContentVaultAdapter {
    
    private BusinessObjectHandler handler;
    private boolean firstImageLoaded;
    private ContentVaultProxy contentVaultProxy;
    private ContentListener contentListener;
    private Sender sender;    
    
    /**
     * Creates a content vault and starts listening to it. Caller needs to call startLoading 
     * to instruct content vault to start loading content; this shall later trigger
     * a sender thread to actually push content to the handler. 
     */
    public ContentVaultAdapter(BusinessObjectHandler handler) {
        this.handler = handler;
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
        log("stop requested");
       sender.stop(); 
    }
    
    /** Sends text as a PlainTextObject */
    private void send(String msg) {
        log("Sending message: "+msg);
        PlainTextObject obj = new PlainTextObject(msg);
        handler.handle(obj);               
    }       
    
    private class ContentListener implements ContentVaultListener {       
        
        @Override
        public void loadedImageList() {
            String msg = "Loaded urls for "+contentVaultProxy.getNumLoadedObjects()+" business objects";
            send(msg);
        }

        @Override
        public void loadedImage(String image) {
            String msg = "Loaded "+contentVaultProxy.getNumLoadedObjects()+"/"+contentVaultProxy.getTotalNumObjects()+" business objects";            
            send(msg);            
            if (firstImageLoaded == false) { 
                log("First image loaded, starting sender thread to push content to handler");    
                firstImageLoaded = true;
                sender = new Sender();
                new Thread(sender).start();
            }
        }       
    }
    
    /** Sends random content from vault every 3 seconds to handler */
    private class Sender implements Runnable {
        private boolean stop = false;
        
        private void stop() {
            log("stop requested");
            stop = true;
        }
                       
        public void run() {
            log("Sender running");
            
            while (!stop) {
                try {                    
                    ImageObject randomContent = contentVaultProxy.sampleImage();
                    handler.handle(randomContent);
                    Thread.sleep(3000);
                }
                catch (InvalidStateException e) {
                    send(ExceptionUtils.format(e,"; "));
                    try {
                        Thread.sleep(3000);
                    }
                    catch (InterruptedException ie) {
                        // no action
                    }
                }
                catch (InterruptedException e) {
                    // no action
                }
            }                        
            
            log("stopped");
        }
        
        private void log(String msg) {
            Logger.info("ContentVaultAdapter.Sender: "+msg);
        }
    }      
      
    /** Unit testing */
    public static void main(String[] args) {
        ContentVaultAdapter adapter = new ContentVaultAdapter(
                new BusinessObjectHandler() {                                        
                    @Override
                    public void handle(BusinessObject bo) {                        
                        log("DUMMY HANDLER received object: "+bo);
                    }
                });
        adapter.startLoading();                        
    }
       
    private static void log(String msg) {
        Logger.info("ContentVaultAdapter: "+msg);
    }    
           
    
}
