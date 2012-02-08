package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.dbg.ILogger;


/**
 * Augments AbstractClient by providing a default BusinessObjectReader.Listener implementation,
 * requiring subclasses only to implement the simpler interface {@link BusinessObjectHandler}.
 * 
 * TODO: merge AbstractClient into this class. Make a separate AbstractClient using an
 * ABBOEConnection to implement the connection, if needed.
 */
public class ABBOEConnection extends AbstractClient {
    
    private BusinessObjectReader.Listener readerListener;
    private BusinessObjectHandler objectHandler;
    
    /**
     * Actual initialization done later by calling {@link #init(biomine3000.objects.BusinessObjectReader.Listener, Socket)}
     */
    public ABBOEConnection(ClientParameters clientParameters, ILogger log)
            throws UnknownHostException, IOException {
        super(clientParameters, log);              
    }
    
    /**
     * Note that businessobject handler is not yet passed in constructor, as it might be implemented
     * using an inner class which cannot be constructed within a call to a superclass constructor... 
     */
    public void init(Socket socket,
                     BusinessObjectHandler businessObjectHandler) throws IOException {        
        this.objectHandler = businessObjectHandler;
        readerListener = new ReaderListener();
        
        super.init(readerListener, socket);
    }
        
    public interface BusinessObjectHandler {
        
        /** Self-explanatory */
        public void handleObject(BusinessObject obj);
        
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
    
    private class ReaderListener extends BusinessObjectReader.AbstractListener {
        
        @Override
        public void objectReceived(BusinessObject bo) {
            objectHandler.handleObject(bo);        
        }    
        
        @Override
        public void noMoreObjects() {                                   
            log.dbg("Server closed connection");
            ABBOEConnection.super.handleNoMoreObjects();
            
            objectHandler.connectionTerminated();
        }
    
        @Override
        public void handleException(Exception e) {
            log.error("Exception in DefaultClient.readerListener", e);
            ABBOEConnection.super.handleNoMoreObjects();
            
            objectHandler.connectionTerminated(e);
        }

        /** Do not consider this as an error; just notify handler that connection has been terminated */
        @Override
        public void connectionReset() {
            log.dbg("Connection reset by server");
            ABBOEConnection.super.handleNoMoreObjects();
            
            objectHandler.connectionTerminated();
        }
    }
    
    
    
    
}
