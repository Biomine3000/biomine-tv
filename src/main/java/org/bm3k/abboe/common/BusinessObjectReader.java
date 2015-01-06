package org.bm3k.abboe.common;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;


import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectMetadata;
import org.bm3k.abboe.objects.BusinessObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.net.MediaType;

import util.collections.Pair;

/**
 * Thread for reading business objects from a stream and notifying a single registered {@link Listener} 
 * about received objects.
 * 
 * Stops on first exception, notifying the listener appropriately. Caller is responsible for closing the 
 * input stream once done (one of handle(XXXexception) methods called, or noMoreObjects() called.
 * noMoreObjects() WILL NOT be called if execution ends to an exception!
 * 
 * TODO: generalize this to obtain a generic PacketReader.
 */
public class BusinessObjectReader implements Runnable {
    private final Logger log = LoggerFactory.getLogger(BusinessObjectReader.class);    
    
    private State state;
    
    private InputStream is;
    private Listener listener;
    private String name;    
    
    public BusinessObjectReader(InputStream is, Listener listener, String name) {
        this.state = State.NOT_STARTED; 
        this.is = is;
        this.listener = listener;
        this.name = name;        
    }
            
    public void setName(String name) {
        this.name = name;
    }
    
    public void run() {            
    	
        dbg("Starting run()");        
        
        try {
            // log("Reading packet...");
            this.state = State.READING_PACKET;
            Pair<BusinessObjectMetadata, byte[]> packet = BusinessObjectUtils.readPacket(is);
        
            while (packet != null) {                                
                BusinessObjectMetadata meta = packet.getObj1();
                byte[] payload;
                
                if (meta.hasPayload()) {
                	MediaType type = meta.getOfficialType();
                	byte[] data = packet.getObj2();
                    if (type != null) {
                        payload = data;
                    }
                    else { 
                    	log.warn("Cannot process payload. Metadata: "+meta);
                    	payload = null;
                    }                                       
                }
                else {
                	// no payload
                	payload = null;
                }
                                               
                BusinessObject bo = BOB.newBuilder()
                    .metadata(meta)
                    .payload(payload)
                    .build();
                                               
                this.state = State.EXECUTING_LISTENER_OBJECT_RECEIVED;
                listener.objectReceived(bo);                                        
                
                // log("Reading packet...");
                this.state = State.READING_PACKET;
                packet = BusinessObjectUtils.readPacket(is);
            }
                        
            listener.noMoreObjects();
        }
        catch (SocketException e) {
            log.warn("Got SocketException {}", e);
            if (e.getMessage().equals("Connection reset")) {
                // message hardcoded in ORACLE java's SocketInputStream read method... TODO: check that this works in other
            	// virtual machines as well...
                listener.connectionReset();
            }
            else {
                // handle as generic IOException
                listener.handle(e);
            }
        }
        catch (IOException e) {        	        
            log.warn("Got IOException", e);
            listener.handle(e);
        }
        catch (InvalidBusinessObjectException e) {
            log.warn("Got InvalidBusinessObjectException {}", e);
            listener.handle(e);
        }        

        dbg("Finished.");
    }
    
    /**
     * Note that trivially it is not guaranteed that a thread getting the state can operate assuming 
     * the state will remain the same.
     */
    public State getState() {
        return state;
    }
    
    public String toString() {
        return name;
    }       
    
    private void dbg(String msg) {
        log.debug(name+": "+msg);
    }    
    
    /** Test by connecting to first available server and reading everything. */
    public static void main(String[] args) throws Exception {        
		Socket socket = Biomine3000Utils.connectToFirstAvailableServer();
        BusinessObjectReader readerRunnable = new BusinessObjectReader(socket.getInputStream(), new DefaultListener(), "dummy reader");
        Thread readerThread = new Thread(readerRunnable);
        readerThread.start();
    }
    
    public static abstract class AbstractListener implements Listener {                                                  
        public AbstractListener() {
        }
        
        /** Generic handling for all exception types, including RuntimeExceptions. */
        public abstract void handleException(Exception e);            
                                                                               
        
        @Override
        public final void handle(IOException e) {
            handleException(e);
        }

        @Override
        public final void handle(InvalidBusinessObjectException e) {
            handleException(e);            
        }       

        @Override
        public final void handle(RuntimeException e) {
            handleException(e);            
        }                             
    }
    
    public static class DefaultListener extends AbstractListener {
        private Logger log = LoggerFactory.getLogger(DefaultListener.class);
        public DefaultListener() {
        }
        
        @Override
        public void objectReceived(BusinessObject bo) {
            log("Received business object: "+bo);
        }

        @Override
        public void noMoreObjects() {
            log("noMoreObjects (client closed connection).");                                                             
        }
        
        @Override
        public void connectionReset() {
            log("Connection reset by client");                                                             
        }

        @Override
        public void handleException(Exception e) {            
            error("Exception while reading", e);                                                            
        }
                     
        private void log(String msg) {
            log.info("BusinessObjectReader.DummyListener: "+msg);
        }
        
        private void error(String msg, Exception e) {
            log.error("BusinessObjectReader.DummyListener: "+msg, e);
        }   
    }

    
    public interface Listener {    
        /** Always receive a non-null object */
        public void objectReceived(BusinessObject bo);
        
        /** 
         * Called when nothing more to read from stream (but there are no Exceptions).
         * Typically this is a result of the sender closing the output of the socket at the
         * other end of the connection. Typically the receiver should close its end 
         * of the connection also at this stage. 
         */
        public void noMoreObjects();
        
        /**
         * Generic response on an IOException is to 
         */
        public void handle(IOException e);

        /**
         * Generic response on receiving an invalid packet is to close the connection, as there is
         * currently no way to locate the beginning of a new packet...    
         */
        public void handle(InvalidBusinessObjectException e);

        /**
         * Generic response on receiving a RuntimeException is to close the connection, as there is 
         * currently no way to locate the beginning of a new packet...    
         */
        public void handle(RuntimeException e);

        /** No more connection */
        public void connectionReset();            
        
    }
    
    public enum State {
        NOT_STARTED,
        READING_PACKET,
        EXECUTING_LISTENER_OBJECT_RECEIVED,
        FINISHED;        
    }
}
