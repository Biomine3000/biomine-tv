package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.dbg.ILogger;


/**
 * Augments AbstractClient by providing a default BusinessObjectReader.Listener implementation,
 * requiring subclasses only to implement the simpler interface {@link BusinessObjectHandler}.
 */
public class DefaultClient extends AbstractClient {
    
    private BusinessObjectReader.Listener readerListener;
    private BusinessObjectHandler objectHandler;
    
    public DefaultClient(ClientParameters clientParameters,                                                   
                         ILogger log) throws UnknownHostException, IOException {
        super(clientParameters, log);              
    }
    
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
         * the implementor of this interface does not need bother with that.  
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
         * Note that a similar cleanup or resources (not related to server connection)
         * should probably performed on receiving this as is done with the exceptionless
         * version of this method.
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
            DefaultClient.super.handleNoMoreObjects();
            
            objectHandler.connectionTerminated();
        }
    
        @Override
        public void handleException(Exception e) {
            log.error("Exception in DefaultClient.readerListener", e);
            DefaultClient.super.handleNoMoreObjects();
            
            objectHandler.connectionTerminated(e);
        }

        /** Do not consider this as an error; just notify handler that connection has been terminated */
        @Override
        public void connectionReset() {
            log.dbg("Connection reset by server");
            DefaultClient.super.handleNoMoreObjects();
            
            objectHandler.connectionTerminated();
        }
    }
    
    
    
}
