package biomine3000.objects;


import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.CmdLineArgs2;
import util.DateUtils;
import util.IOUtils;
import util.dbg.ILogger;
import util.dbg.Logger;
import util.io.SkippingStreamReader;
import biomine3000.objects.ContentVaultProxy;

/**
 * Sends objects from the notorious content vault with a constant interval
 * to provide a tuning image for BiomineTV®.
 * 
 * Note that sending is synchronous, that is we do not want to accumulate content which 
 * will not be read by the server anyway.
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
    
    
    /**
     * {@link #startLoadingContent} has to be called separately.
     * @param nToSend number of objects to send, null for no limit. 
     * @param sendInterval send interval in milliseconds.
     */
    private ContentVaultSender(Socket socket, Integer nToSend, Integer sendInterval) throws UnknownHostException, IOException {
        this.socket = socket;
        
        // register to server
        BusinessObject registerObj = Biomine3000Utils.makeRegisterPacket("ContentVaultSender", ClientReceiveMode.NONE);
        socket.getOutputStream().write(registerObj.bytes());
        
        this.nToSend = nToSend;        
        
        // init state information
        this.stopped = false;
        this.nSent = 0;
        
        // init adapter which we will use to periodically receive business objects from the content vault proxy
        this.vaultAdapter = new ContentVaultAdapter(this, sendInterval);
               
        // init communications with the server
        this.serverReaderListener = new ServerReaderListener();
        SkippingStreamReader serverReader = new SkippingStreamReader(socket.getInputStream(), serverReaderListener);
        Thread readerThread = new Thread(serverReader);                       
        readerThread.start();              
    }
    
    /** Start your business */
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
                
    @Override
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
    
    public static void main(String[] pArgs) throws Exception {
                
        CmdLineArgs2 args = new CmdLineArgs2(pArgs);
        log("Starting at "+DateUtils.formatOrderableDate());
        Logger.addStream("ContentVaultSender.log", 1);
        ILogger log = new Logger.ILoggerAdapter();
                
        Integer nToSend = args.getIntOpt("n");
        if (nToSend != null) {
            log("Only sending "+nToSend+" objects");
        }
        
        Integer sendInterval = args.getIntOpt("send_interval", 3000);
        if (nToSend != null) {
            log("Only sending "+nToSend+" objects");
        }
                        
        String host = args.getOpt("host");
        Integer port = args.getIntOpt("port");
                    
        ContentVaultSender sender = null;
        try {
            Socket socket = Biomine3000Utils.connectToServer(host, port, log);
            sender = new ContentVaultSender(socket, nToSend, sendInterval);
        }
        catch (IOException e) {
            error("Could not find a server");
            System.exit(1);
        }
            
//            try {
//                sender = new ContentVaultSender(ServerAddress.LERONEN_HIMA, nToSend);
//            }
//            catch (IOException e) {
//                log("No server at LERONEN_HIMA");
//            }
//            if (sender == null) {
//                sender = new ContentVaultSender(ServerAddress.LERONEN_KAPSI, nToSend);
//                log("Connected to LERONEN_KAPSI");
//            }
                        
        log("Request startLoadingContent");
        sender.startLoadingContent();
        log("Exiting main thread");        
    }
    
    private static void log(String msg) {
        Logger.info("ContentVaultSender: "+msg);
    }    
    
    @SuppressWarnings("unused")
    private static void warn(String msg) {
        Logger.warning("ContentVaultSender: "+msg);
    }        
    
    private static void error(String msg) {
        Logger.error("ContentVaultSender: "+msg);
    }
    
    private static void error(String msg, Exception e) {
        Logger.error("ContentVaultSender: "+msg, e);
    }    
    
}
