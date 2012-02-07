package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.dbg.ILogger;
import util.net.NonBlockingSender;


/**
 * Absract class for implementing a simple ABBOE client. Actual client needs to override 
 * only methods in BusinessObjectReader.Listener. Implementors should use method 
 * {@link #send(BusinessObject)} to send stuff. 
 */
public class AbstractClient  {
                   
    protected ILogger log;
    
    private ClientReceiveMode receiveMode;
    private Subscriptions subscriptions;
    private String name;        
    private BusinessObjectReader.Listener readerListener;
    
    private Socket socket = null;       
    
    /**
     * A dedicated sender used to send stuff (that is: buziness objects)
     * in a non-blocking manner. 
     */
    private NonBlockingSender sender = null;

    private BusinessObjectReader reader = null;
    
    private boolean senderFinished = false;
    private boolean receiverFinished = false;
    
    private boolean socketClosed = false;
    private boolean closeOutputRequested = false;     
    
    /**
     * @param socket the server socket 
     * @name arbitrary name for the client (will be sent to server)
     * @param construcDedicatedImplementations Use dedicated java implementation class for business ' 
     *        object where applicable (as dictated by {@link Biomine3000Mimetype}, instead of the 
     *        generic {@link BusinessObject}.
     * @param echo should server echo everything 
     * @param log the log
     * @throws UnknownHostException
     * @throws IOException
     */
    public AbstractClient(ClientParameters clientParameters,
                          ILogger log) throws UnknownHostException, IOException {                                        
                
        this.name = clientParameters.name;
        this.receiveMode = clientParameters.receiveMode;                
        this.subscriptions = clientParameters.subscriptions;
        this.log = log;        
            
//        log.dbg("Initialized shutdown hook");
        MyShutdown sh = new MyShutdown();            
        Runtime.getRuntime().addShutdownHook(sh);
    }
     
    protected void init(BusinessObjectReader.Listener readerListener,
                        Socket socket) throws IOException {
        this.readerListener = readerListener;
        this.socket = socket;
        
        // init sender
        sender = new NonBlockingSender(socket, new SenderListener());
                      
        // send registration to server
        BusinessObject registerObj = Biomine3000Utils.makeRegisterPacket(name, receiveMode, subscriptions);
        log.info("Sending register packet:" +new String(registerObj.bytes()));
        sender.send(registerObj.bytes());
                
        // start listening to objects from server
        log.info("Starting listening to server...");
        startReaderThread();               
    }                     
    
    /** Put object to queue of objects to be sent; additionally, attach user info */
    protected void send(BusinessObject object) throws IOException {
        // object.getMetaData().setSender(user);
        sender.send(object.bytes());        
    }       
        
    public synchronized String getName() {
        return name;
    }
    
    /** Closing of socket is to be done only after both sender and receiver have finished. */
    private synchronized void closeSocketIfNeeded() {
        log.dbg("closeSocketIfNeeded");
        if (senderFinished && receiverFinished && !socketClosed) {
            log.dbg("Closing socket");
            try {
                socket.close();
                log.dbg("Closed socket");
            }
            catch (IOException e) {
                log.error("Failed closing socket", e);
            }
        }
        else if (!senderFinished) {            
            log.dbg("Sender not yet finished, not closing socket");
        }
        else if (!receiverFinished) {
            log.dbg("Receiver not yet finished, not closing socket");
        }
        else {
            log.dbg("Socket already closed");
        }
    }
            
   /**
    * Closing occurs by requesting a sender to send a special stop packet that causes 
    * it to stop (done using method stop()), which after some intermediate processing 
    * should lead to our beloved SenderListener being notified, at which point actual
    * closing will occur. 
    * 
    * Only the first call to this method will have any effect.
    */              
    protected synchronized void requestCloseOutputIfNeeded() {        
        if (!socketClosed && !senderFinished && !closeOutputRequested) {
            closeOutputRequested = true;
            log.dbg("Requesting sender to finish");
            sender.requestStop();
        }
    }       
    
    private void startReaderThread() throws IOException {
        if (readerListener == null) {
            throw new RuntimeException("No readederListener");
        }
        reader = new BusinessObjectReader(socket.getInputStream(), readerListener, "reader-"+name, true, log);
        
        Thread readerThread = new Thread(reader);
        readerThread.start();
    }
    
    /**
     * Need to listen to sender sending it's last packet (or having received
     * an error). At this point it is necessary to close the output channel 
     * of the socket and possibly the whole socket (if also input has been closed) 
     */
    private class SenderListener implements NonBlockingSender.Listener {
        public void senderFinished() {
            synchronized(AbstractClient.this) {
                log.dbg("Sender finished");
                log.dbg("Closing socket output");
                senderFinished = true;
                try {
                    socket.shutdownOutput();
                }
                catch (IOException e) {
                    log.error("Failed shutting down send channel", e);
                }
                
                closeSocketIfNeeded();                
            }
        }
    }       
    
    /**
     * To be called from subclass reader listener when receiving a noMoreObjects notification from
     * the reader (probably resulting from the fact that server has closed connection). 
     * 
     * This method:<pre> 
     *  • sets {@link #receiverFinished} to true to indicate that receiving has been finished
     *  • closes input of socket 
     *  • calls {@link #closeSocketIfNeeded} to shutdown socket, is also sending has been finished earlier.</pre> 
     * Requiring this call to be performed just by convention is not an very satisfactory solution, 
     * as there is no way of enforcing the subclass implementation to do so, possibly
     * leading to an inconsistent state of the client.
     */
    protected synchronized void handleNoMoreObjects() {
        log.dbg("handleNoMoreObjects");
        receiverFinished = true;
        try {           
            socket.shutdownInput();
        }
        catch (IOException e) {
            log.error("Failed shutting down socket input", e);
        }

        requestCloseOutputIfNeeded();
        
        closeSocketIfNeeded();        
    }
    
    class MyShutdown extends Thread {
        public void run() {
            // requesting closing of socket output stream should be sufficient to commence a complete 
            // clean up of the connection, should that not have occurred yet
            log.dbg("Running AbstractClient shutdown hook...");
            
            if (!socketClosed && !senderFinished && !closeOutputRequested) {
                requestCloseOutputIfNeeded();
            }
            else {
                log.dbg("No cleanup actions necessary");
            }
        }
    }        
             

}

