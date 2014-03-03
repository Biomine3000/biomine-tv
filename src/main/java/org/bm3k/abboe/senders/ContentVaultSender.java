package org.bm3k.abboe.senders;


import java.io.IOException;
import java.net.Socket;

import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BusinessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends objects from the notorious content vault with a constant interval
 * to provide a tuning image for BiomineTV®.
 * 
 * Note that sending is synchronous, that is we do not want to accumulate content which 
 * will not be read by the server anyway.
 * 
 * Use a {@link ContentVaultProxy} for loading the stuff over the web.
 */
public class ContentVaultSender implements IBusinessObjectHandler {
    private final Logger log = LoggerFactory.getLogger(ContentVaultSender.class);

    private static final ClientParameters CLIENT_PARAMS =
            new ClientParameters("ContentVaultSender", ClientReceiveMode.NONE,
                    Subscriptions.NONE, false);
    
    private boolean stopped;
    private ContentVaultAdapter vaultAdapter;
    private int nSent;
    private Integer nToSend;
    private String channel;
        
    private ABBOEConnection connection;
           
    /**
     * {@link #startLoadingContent} has to be called separately.
     * @param nToSend number of objects to send, null for no limit. 
     * @param sendInterval send interval in milliseconds.
     */
    private ContentVaultSender(Socket socket, Integer nToSend, Integer sendInterval, String channel)
            throws IOException {
        // init state information
        this.nToSend = nToSend;
        this.channel = channel;
        
        this.nSent = 0;
        this.stopped = false;
        ClientParameters clientParams = new ClientParameters(CLIENT_PARAMS);
        clientParams.client = "ContentVaultSender-"+Biomine3000Utils.getUser();
        
        this.connection = new ABBOEConnection(CLIENT_PARAMS, socket);
        this.connection.init(new ObjectHandler());
                       
        // init adapter which we will use to periodically receive business objects from the content vault proxy
        this.vaultAdapter = new ContentVaultAdapter(this, sendInterval);                            
    }
    
    /** Start your business */
    public void startLoadingContent() {
        vaultAdapter.startLoading();
    }
    
    /** Stop your business */
    public void stopSending() {
        log.info("stopSending requested");
        stopped = true;
        vaultAdapter.stop();
        connection.initiateShutdown();        
    }
    
    /** Stop your business */
    public void serverClosedConnection() {
        log.info("server closed connection");
        stopped = true;
        vaultAdapter.stop();            
    }
               
    /** Handle object from the vault adapter */
    @Override
    public void handleObject(BusinessObject obj) {
        if (stopped) {
            // no more buizness
            log.info("No more buizness");
            return;
        }
        
        if (obj.isEvent()) {
            log.info("Not sending event: "+obj);
            return;
        }
        
        obj.getMetadata().put("channel", channel);        
        log.info("Writing an object with following metadata: "+obj.getMetadata());
        
        try {
            connection.send(obj);
            nSent++;
            if (nToSend != null && nSent >= nToSend) {
                stopSending();
            }
        } catch (IOException e) {
            log.error("Failed sending business object, stopping", e);
            vaultAdapter.stop();
        }         
    }    
            
    public static void main(String[] pArgs) throws Exception {                
        Biomine3000Args args = new Biomine3000Args(pArgs, true);

        org.slf4j.Logger log = LoggerFactory.getLogger(ContentVaultSender.class);
                
        Integer nToSend = args.getInt("n");
        if (nToSend != null) {
            log.info("Only sending "+nToSend+" objects");
        }
        
        Integer sendInterval = args.getIntOpt("send_interval", 3000);
        if (nToSend != null) {
            log.info("Only sending "+nToSend+" objects");
        }          
            
        String channel = args.getChannel();
        if (channel == null) {
        	channel = "virityskuva";
        }
        
        ContentVaultSender sender = null;
        try {
            Socket socket = Biomine3000Utils.connectToServer(args);            
            sender = new ContentVaultSender(socket, nToSend, sendInterval, channel);
        }
        catch (IOException e) {
            log.error("Could not find a server");
            System.exit(1);
        }                       
                        
        log.info("Request startLoadingContent");
        sender.startLoadingContent();
        log.info("Exiting main thread");        
    }
    
    /** Client receive buzinezz logic contained herein */
    private class ObjectHandler implements ABBOEConnection.BusinessObjectHandler {

        @Override
        public void handleObject(BusinessObject bo) {
            // no action           
        }

        @Override
        public void connectionTerminated() {
            log.info("Connection to server terminated");
            serverClosedConnection();
        }

        @Override
        public void connectionTerminated(Exception e) {
            log.error("Connection to server terminated", e);
            serverClosedConnection();
        }
        
    }            
    
}
