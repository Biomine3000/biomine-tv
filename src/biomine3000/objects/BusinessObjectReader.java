package biomine3000.objects;

import java.io.IOException;
import java.io.InputStream;

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
    
    public BusinessObjectReader(InputStream is, Listener listener, String name) {
        this.is = is;
        this.listener = listener;
        this.name = name;
    }
            
    public void run() {
        
        log("Starting run()");
        
        try {
            log("Reading packet...");
            Pair<BusinessObjectMetadata, byte[]> packet = BusinessObject.readPacket(is);            
        
            while (packet != null) {
                BusinessObject bo = new BusinessObject(packet.getObj1(), packet.getObj2());                
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
