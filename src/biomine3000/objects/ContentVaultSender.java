package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.DateUtils;
import util.ExceptionUtils;
import util.IOUtils;
import util.dbg.Logger;
import biomine3000.objects.ContentVaultProxy;
import biomine3000.objects.ContentVaultProxy.ContentVaultListener;
import biomine3000.objects.ContentVaultProxy.InvalidStateException;

/**
 * Sends objects from the notorious content vault with a constant interval
 * to provide a tuning image for BiomineTV®.
 * 
 * Use a {@link ContentVaultProxy} for loading the stuff over the web.
 */
public class ContentVaultSender {

    private boolean firstImageLoaded;
    private ContentVaultProxy content;
    private ContentListener contentListener;
    private Sender sender;
    private Socket socket;
    
    private ContentVaultSender(String host, int port) throws UnknownHostException, IOException {                                                                                
        // init communications with the server
        socket = new Socket(host, port);                                                            
        firstImageLoaded = false;
        content = new ContentVaultProxy();
        contentListener = new ContentListener();
        content.addListener(contentListener);        
    }
    
    private void startLoading() {
        content.startLoading();
    }
    
    private void send(String msg) {
        log("Sending message: "+msg);
        PlainTextObject obj = new PlainTextObject(msg);
        send(obj);               
    }
    
    private void send(BusinessObject obj) {
        obj.getMetaData().put("channel", "virityskuva");        
        
        try {
            byte[] bytes = obj.bytes();        
            log("Writing a message of "+obj+" bytes");
            IOUtils.writeBytes(socket.getOutputStream(), bytes);
            socket.getOutputStream().flush();
            log("Wrote "+bytes.length+" bytes");
        } catch (IOException e) {
            error("Failed writing business object, stopping", e);
            sender.stop = true;
        }
    }
    
    private class ContentListener implements ContentVaultListener {       
        
        @Override
        public void loadedImageList() {
            String msg = "Loaded urls for "+content.getNumLoadedObjects()+" business objects";
            send(msg);
        }

        @Override
        public void loadedImage(String image) {
            String msg = "Loaded "+content.getNumLoadedObjects()+"/"+content.getTotalNumObjects()+" business objects";            
            send(msg);            
            if (firstImageLoaded == false) {
                // Logger.info("First image loaded, starting sender thread");
                log("First image loaded, starting sender thread to loop tuning image channel");    
                firstImageLoaded = true;
                sender = new Sender();
                new Thread(sender).start();
            }
        }       
    }
    
    private class Sender implements Runnable {
        private boolean stop = false;
                       
        public void run() {
            log("Sender running");
            
            while (!stop) {
                try {                    
                    ImageObject randomContent = content.sampleImage();
                    send(randomContent);
                    Thread.sleep(3000);
                }
                catch (InvalidStateException e) {
                    send(ExceptionUtils.formatWithCauses(e,"; "));
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
            
            log("Sender closing socket");
            try {
                socket.close();
            }
            catch (IOException e) {
                log("FYI: failed closing socket");
            }
            
            log("Sender stopped");
        }
    }
    
    public static void main(String[] args) throws IOException {
        
        log("Starting at "+DateUtils.formatDate());
                       
        try {
            ContentVaultSender sender= new ContentVaultSender(TestServer.DEFAULT_HOST, TestServer.DEFAULT_PORT);
            sender.startLoading();
        }
        catch (IOException e) {
            error("Failed initializing server", e);
        }
    }
    
    private static void log(String msg) {
        Logger.info("ContentVaultSender: "+msg);
    }    
    
    @SuppressWarnings("unused")
    private static void warn(String msg) {
        Logger.warning("ContentVaultSender: "+msg);
    }        
        
    private static void error(String msg, Exception e) {
        Logger.error("ContentVaultSender: "+msg, e);
    }
    
}
