package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.DateUtils;
import util.IOUtils;
import util.dbg.Logger;
import util.io.SkippingStreamReader;
import biomine3000.objects.ContentVaultProxy;

/**
 * Sends objects from the notorious content vault with a constant interval
 * to provide a tuning image for BiomineTV®.
 * 
 * Use a {@link ContentVaultProxy} for loading the stuff over the web.
 */
public class ContentVaultSender implements BusinessObjectHandler {

    private boolean stopped;
    private ContentVaultAdapter vaultAdapter;
    private int nSent;
    private Integer nToSend;
    
    
    private Socket socket;
    /** Listens to (skipping) reader that reads input stream of server socket */
    private ServerReaderListener serverReaderListener;
    
    /** {@link #startLoadingContent} has to be called separately */
    private ContentVaultSender(String host, int port, Integer nToSend) throws UnknownHostException, IOException {                                                                                
        // init content vault proxy
        this.stopped = false;
        this.nSent = 0;
        this.nToSend = nToSend; 
        this.vaultAdapter = new ContentVaultAdapter(this);
               
        // init communications with the server
        this.socket = new Socket(host, port);
        this.serverReaderListener = new ServerReaderListener();
        SkippingStreamReader serverReader = new SkippingStreamReader(socket.getInputStream(), serverReaderListener);
        Thread readerThread = new Thread(serverReader);                       
        readerThread.start();              
    }
    
    /** Stop your business */
    public void startLoadingContent() {
        vaultAdapter.startLoading();
    }
    
    /** Stop your business */
    public void stopSending() {
        log("stopSending requested");
        stopped = true;
        vaultAdapter.stop();
        try {
            socket.shutdownOutput();
        }
        catch (IOException e) {
            error("Failed shutting down socket output", e);
        }        
    }
                
    public void handle(BusinessObject obj) {
        if (stopped) {
            // no more buizness
            log("No more buizness");
            return;
        }
        
        if (obj.isEvent()) {
            log("Not sending event: "+obj);
            return;
        }
        
        obj.getMetaData().put("channel", "virityskuva");        
        log("Writing an object with following metadata: "+obj.getMetaData());
        
        try {
            byte[] bytes = obj.bytes();                    
            IOUtils.writeBytes(socket.getOutputStream(), bytes);
            socket.getOutputStream().flush();
            nSent++;
            if (nToSend != null && nSent >= nToSend) {
                stopSending();
            }
        } catch (IOException e) {
            error("Failed writing business object, stopping", e);
            vaultAdapter.stop();
        }
        
        
    }    
    
    /** Listens to a single dedicated reader thread reading objects from the input stream of a single client */
    private class ServerReaderListener implements SkippingStreamReader.Listener {

        @Override
        public void noMoreBytesInStream() {
            log("Received noMoreBytesInStream from SkippingStreamReader");  
            // TODO: should probably stop sending as well?
        }

        private void handleException(Exception e) {
            error("Exception in SkippingStreamReader", e);
            // TODO: should probably stop sending as well?
        }
        
        @Override
        public void handle(IOException e) {
            handleException(e);
        }
        
        @Override
        public void handle(RuntimeException e) {
            handleException(e);
            
        }        
    }
    
    public static void main(String[] args) throws IOException {
                
        log("Starting at "+DateUtils.formatDate());
        Logger.addStream("ContentVaultSender.log", 1);            
        
        try {
            Integer nToSend = null;
            if (args.length > 0) {
                nToSend = Integer.parseInt(args[0]);
                log("Only sending "+nToSend+" objects");
            }                                                        
//                log("Creating stopper thread");
//                int nsec = Integer.parseInt(args[0]);
//                Stopper stopper = new Stopper(sender, nsec);
//                stopper.start();
//                log("Started stopper thread");
//            }
            else {
                log("No args");
            }
            
            ContentVaultSender sender= new ContentVaultSender(TestServer.DEFAULT_HOST, TestServer.DEFAULT_PORT, nToSend);
                        
            
            
            log("Request startLoadingContent");
            sender.startLoadingContent();
            log("Exiting main thread");
        }
        catch (IOException e) {
            error("Failed initializing server", e);
        }                
    }
    
//    private static class Stopper extends Thread {
//        ContentVaultSender sender;
//        int nsec;
//        
//        private Stopper(ContentVaultSender sender, int nsec) {
//            this.sender = sender;
//            this.nsec = nsec;
//        }
//        
//        public void run() {
//            try {
//                log("Sleeping for "+nsec+" seconds before requesting STOP");
//                Thread.sleep(1000*nsec);
//            }
//            catch (InterruptedException e) {
//                // nuisance
//            }
//            log("Requesting stop");
//            sender.stopSending();
//            Logger.endLog();
//        }
//    }
    
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
