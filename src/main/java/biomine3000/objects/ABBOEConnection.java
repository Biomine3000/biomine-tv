package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;



import util.dbg.ILogger;
import util.net.NonBlockingSender;


/**
 * Connection to an ABBOE server. Implementors should use method 
 * {@link #send(LegacyBusinessObject)} to send stuff. 
 */
public class ABBOEConnection {
                   
    private ILogger log;
    
    private ClientParameters clientParameters;

    /**
     * Client callback interface for receiving objects and connection state change
     * notifications (currently only termination of connection; might be that there is no need
     * for any additions, though)
     */
    private BusinessObjectHandler objectHandler;
    
    /** Internal listener for businessobjectreader */
    private BusinessObjectReader.Listener readerListener;    
    private Socket socket = null;
    
    /** Internal state */
    private State state;
    
    /** Simplified state, as seen by the client */
    private ClientState clientState;
    
    private NonBlockingSender sender = null;
    private BusinessObjectReader reader = null;
    
    /** State of send and receive threads and socket */
    private boolean senderFinished = false;
    private boolean receiverFinished = false;    
    private boolean socketClosed = false;
    private boolean closeOutputRequested = false;         
    
    
    /**
     * Actual initialization of communications done later by calling {@link #init()}
     * to allow for peaceful registration into data structures e.g., before beginning
     * the actual business of performing any communications
     * (nothing will be sent or received before {@link #init()} is called).
     */
    public ABBOEConnection(ClientParameters clientParameters,
                           Socket socket,                           
                           ILogger log) throws UnknownHostException, IOException {                                        
        this.socket = socket;
        this.clientParameters = clientParameters;        
        this.log = log;
                
        this.state = State.NOT_INITIALIZED;
        this.clientState = ClientState.NOT_INITIALIZED;
                           
        MyShutdown sh = new MyShutdown();            
        Runtime.getRuntime().addShutdownHook(sh);               
    }
    
   /**
    * Note that businessobject handler is not yet passed in constructor, as constructing the handler
    * might require a reference to the connection.
    */
    public void init(BusinessObjectHandler businessObjectHandler) throws IOException {
                
        if (this.state != State.NOT_INITIALIZED) {
            throw new IllegalStateException();
        }
        
        this.objectHandler = businessObjectHandler;
        
        this.state = State.INITIALIZING;
        this.clientState = ClientState.ACTIVE;        
                
        this.readerListener = new ReaderListener();                                       
        this.sender = new NonBlockingSender(socket, new SenderListener());
                      
        // send registration to server
        LegacyBusinessObject registerObj = Biomine3000Utils.makeRegisterPacket(clientParameters);
        log.info("Sending register packet:" +new String(registerObj.bytes()));
        sender.send(registerObj.bytes());
        this.state = State.ACTIVE;        
        
        // start listening to objects from server
        log.info("Starting reader thread...");
        startReaderThread();               
    }                     
                   
    /** Put object to queue of objects to be sent*/
    public void send(IBusinessObject object) throws IOException {        
        if (clientParameters.sender != null) {
            object.getMetadata().setSender(clientParameters.sender);
        }
        this.sender.send(object.bytes());        
    }       
        
    public void sendClientListRequest() throws IOException {            
        send(new LegacyBusinessObject(BusinessObjectEventType.CLIENTS_LIST));
    }
    
    public synchronized String getName() {
        return clientParameters.name;
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
            state = State.SHUT_DOWN;
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
     * Initiate shutting down of connection. This will not be immediate:
     * closing occurs by requesting a sender to send a special stop packet that causes 
     * it to stop (done using method stop()), which after some intermediate processing 
     * should lead to our beloved SenderListener being notified, at which point actual
     * closing of socket output half will occur. After this, server is expected
     * (having read everything that was send before the close, if any, and also having 
     * sent everything it wants to send, if any) to close output half of its connection.
     * Finally, this will be noticed as noMoreObjects in the reader listener, at which 
     * point we will also close the input half of the socket and also the whole socket,
     * the connection will be considered to be genuinely closed.   
     */
    public synchronized void initiateShutdown() {
        if (!socketClosed && !closeOutputRequested) {
            state = State.SHUTTING_DOWN;
            closeOutputRequested = true;
            log.dbg("Requesting sender to finish");
            sender.requestStop();
        }
    }
    
   /**
    * Closing occurs by requesting a sender to send a special stop packet that causes 
    * it to stop (done using method stop()), which after some intermediate processing 
    * should lead to our beloved SenderListener being notified, at which point actual
    * closing will occur.
    * 
    * Closing of output will only be requested if ALL of the following conditions hold:
    * <ul>
    *   <li>socket has not been yet</li>
    *   <li>sender has not finished yet</li>
    *   <li>closing of output has not been requested yet</li> 
    * </ul> 
    * 
    * If some of said conditions do not hold, calling this shall have no effect.
    */              
    protected synchronized void requestCloseOutputIfNeeded() {        
        if (!socketClosed && !senderFinished && !closeOutputRequested) {
            state = State.SHUTTING_DOWN;
            closeOutputRequested = true;
            log.dbg("Requesting sender to finish");
            sender.requestStop();
        }
    }       
    
    private void startReaderThread() throws IOException {
        if (readerListener == null) {
            throw new RuntimeException("No readederListener");
        }
        reader = new BusinessObjectReader(socket.getInputStream(), readerListener, "reader-"+socket.getRemoteSocketAddress().toString(), true, log);
        
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
            synchronized(ABBOEConnection.this) {
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
        state = State.SHUTTING_DOWN;
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
            log.dbg("Executing ABBOEConnection shutdown thread");
            if (state == ABBOEConnection.State.ACTIVE) {
                state = ABBOEConnection.State.SHUTTING_DOWN;            
                // requesting closing of socket output stream should be sufficient to commence a complete 
                // clean up of the connection, should that not have occurred yet                
                
                if (!socketClosed && !senderFinished && !closeOutputRequested) {
                    requestCloseOutputIfNeeded();
                }
                else {
                    log.dbg("No cleanup actions necessary");
                }
            }
            else {
                log.dbg("No cleanup actions performed in state: "+state);
            }
        }
    }        
             
    @Override
    public String toString() {
        return socket.getRemoteSocketAddress().toString();
    }
    
    
    public interface BusinessObjectHandler extends biomine3000.objects.IBusinessObjectHandler {
        
        /** Self-explanatory */
        public void handleObject(IBusinessObject obj);
        
        /**
         * Connection to server has been terminated somehow "normally". 
         * It is at least in the current protocol undefined whether this has occurred  
         * on the clients request or for some other reason known only to the server.
         * 
         * Client should attempt no more sending after receiving this.
         * 
         * DefaultClient implementation is responsible for closing the connection;
         * the implementor of this interface does not need bother with such banalities.  
         */ 
        public void connectionTerminated();
        
        /**
         * Connection to server has been terminated due to some error condition. 
         * The connection will be (or already has been) closed anyway,
         * as it is not possible in the current protocol to recover from any errors
         * within a session (a lifetime of a TCP connection). This means that no recovery
         * actions by the client are possible; the only option is to reconnect to the server.
         * 
         * DefaultClient implementation is responsible for closing the connection;
         * the implementor of this interface does not need bother with that.
         * 
         * Note that a similar cleanup or resources (not related to server connection), if any, 
         * should probably performed on receiving this as is done with the exceptionless
         * version of this method {@link #connectionTerminated()}.
         */
        public void connectionTerminated(Exception e);
    }

    /**
     * Internal listener for businessobjectreader; translates notifications received from there
     * to client notifications notified through interface BusinessObjectHandler.     
     */
    private class ReaderListener extends BusinessObjectReader.AbstractListener {
        
        @Override
        public void objectReceived(IBusinessObject bo) {
            objectHandler.handleObject(bo);        
        }    
        
        @Override
        public void noMoreObjects() {                                   
            log.dbg("Server closed connection");
            handleNoMoreObjects();
                        
            if (clientState != ClientState.FINISHED) {
                objectHandler.connectionTerminated();
                clientState = ClientState.FINISHED;
            }
        }
    
        @Override
        public void handleException(Exception e) {
            log.error("Exception in DefaultClient.readerListener", e);
            handleNoMoreObjects();
            
            if (clientState != ClientState.FINISHED) {
                objectHandler.connectionTerminated(e);
                clientState = ClientState.FINISHED;
            }
        }

        /** Do not consider this as an error; just notify handler that connection has been terminated */
        @Override
        public void connectionReset() {
            log.dbg("Connection reset by server");
            handleNoMoreObjects();
            
            objectHandler.connectionTerminated();
        }
    }

    
    protected enum State {
        NOT_INITIALIZED,
        INITIALIZING,
        ACTIVE,
        SHUTTING_DOWN,
        SHUT_DOWN;
    }
    
    /**
     * Used to keep track of the state as seen by the client.
     * This is a simplified version of the actual internal state managed within AbstractClient.
     */
    protected enum ClientState {
        NOT_INITIALIZED,
        ACTIVE,
        FINISHED;
    }
    
}

