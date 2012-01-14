package biomine3000.objects;

import static biomine3000.objects.Biomine3000Constants.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;


import util.collections.Pair;
import util.dbg.Logger;

/**
 * Thread for reading business objects from a stream and notifying a single registered {@link Listener} 
 * about received objects.
 * 
 * Stops on first exception, notifying the listener appropriately. Caller is responsible for closing the 
 * input stream once done (one of handle(XXXexception) methods called, or noMoreObjects() called.
 * 
 * TODO: generalize this to obtain a generic PacketReader.
 */
public class BusinessObjectReader implements Runnable {
           
    private InputStream is;
    private Listener listener;
    private String name;
    private boolean constructDedicatedImplementations;
    
    public BusinessObjectReader(InputStream is, Listener listener, String name, boolean constructDedicatedImplementations) {
        this.is = is;
        this.listener = listener;
        this.name = name;
        this.constructDedicatedImplementations = constructDedicatedImplementations;
    }
            
    public void run() {
        
        log("Starting run()");
        
        try {
            log("Reading packet...");
            Pair<BusinessObjectMetadata, byte[]> packet = BusinessObject.readPacket(is);            
        
            while (packet != null) {
                BusinessObject bo;
                if (constructDedicatedImplementations) {
                    bo = BusinessObject.makeObject(packet);
                }
                else {
                    bo = new BusinessObject(packet.getObj1(), packet.getObj2());
                }
                listener.objectReceived(bo);                                        
                
                log("Reading packet...");
                packet = BusinessObject.readPacket(is);
            }
                        
            listener.noMoreObjects();
        }
        catch (IOException e) {
            listener.handle(e);
        }
        catch (InvalidPacketException e) {
            listener.handle(e);
        }
        catch (BusinessObjectException e) {
            listener.handle(e);
        }
        catch (RuntimeException e) {
            listener.handle(e);
        }
        
        log("Finished run()");
    }
    
    public String toString() {
        return name;
    }       
    
    private void log(String msg) {
        Logger.info(this+": "+msg);
    }
    
    /** Test by connecting to the server and reading everything. */
    public static void main(String[] args) throws Exception {
        Socket socket = new Socket("localhost", LERONEN_HIMA_PORT);
        BusinessObjectReader readerRunnable = new BusinessObjectReader(socket.getInputStream(), new DefaultListener(), "dummy reader", true);
        Thread readerThread = new Thread(readerRunnable);
        readerThread.start();
    }
    
    public static class DefaultListener implements Listener {                                                  

        @Override
        public void objectReceived(BusinessObject bo) {
            log("Received business object: "+bo);
        }

        @Override
        public void noMoreObjects() {
            log("noMoreObjects (client closed connection).");                                                             
        }

        protected void handleException(Exception e) {            
            error("Exception while reading", e);                                                            
        }
        
        @Override
        public void handle(IOException e) {
            handleException(e);
        }

        @Override
        public void handle(InvalidPacketException e) {
            handleException(e);            
        }

        @Override
        public void handle(BusinessObjectException e) {
            handleException(e);            
        }

        @Override
        public void handle(RuntimeException e) {
            handleException(e);            
        }                
    
        private void log(String msg) {
            Logger.info("BusinessObjectReader.DummyListener: "+msg);
        }
        
        private void error(String msg, Exception e) {
            Logger.error("BusinessObjectReader.DummyListener: "+msg, e);
        }   
    }
    
    public interface Listener {    
        /** Always receive a non-null object */
        public void objectReceived(BusinessObject bo);
        /** Called when nothing more to read from stream */
        public void noMoreObjects();        
        public void handle(IOException e);
        public void handle(InvalidPacketException e);
        public void handle(BusinessObjectException e);
        public void handle(RuntimeException e);
    }
}
