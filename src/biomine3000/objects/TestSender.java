package biomine3000.objects;

// import static biomine.db.query.CrawlerCacheServer.*;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import util.ExceptionUtils;
import util.collections.Pair;
import util.dbg.Logger;
import util.net.NonBlockingSender;


/** Sends some trivial objects to default address and exits. */ 
public class TestSender {
                      
    private Socket socket = null;
    
    /**
     * A dedicated sender used to send stuff (that is: buziness objects)
     * in a non-blocking manner. 
     */
    private NonBlockingSender sender = null;
    
    private boolean senderFinished = false;
    private boolean receiverFinished = false;
    
    private boolean socketClosed = false;
    private boolean closeRequested = false;

    public TestSender(String host, int port) throws UnknownHostException, IOException {                              
        init(host, port);                                             
    }         
    
    public void init(String host, int port) throws UnknownHostException, IOException {               
                                               
        // init communications with the server
        try {       
            socket = new Socket(host, port);
            sender = new NonBlockingSender(socket, new SenderListener());
      
            info("TestTCPClient Connected to server");
            
            MyShutdown sh = new MyShutdown();            
            Runtime.getRuntime().addShutdownHook(sh);
            info("Initialized shutdown hook");
        }
        catch (UnknownHostException e) {
            warning("Cannot connect to cache server: Unknown host: "+host);
            throw e;
        } 
        catch (IOException e) {
            warning("Error while establishing connection: "+
                           ExceptionUtils.formatWithCauses(e, " ")+". "+                    
                          "A probable reason is that a server is not running at "+
                          host+":"+port+", as supposed.");
            // e.printStackTrace();            
            throw e;
        }        
    }                
       
    public void send(BusinessObject object) throws IOException {        
        sender.send(object.bytes());
    }
    
    /** Return null when no more business objects available */
    public BusinessObject receive() throws IOException, InvalidPacketException {
        Pair<BusinessObjectMetadata, byte[]> packet = BusinessObject.readPacket(socket.getInputStream());               
//        Logger.info("Received packet: "+packet);
//        Logger.info("Making business object...");
        BusinessObject bo = BusinessObject.makeObject(packet);
        return bo;
    }
        
    private synchronized void closeSocketIfNeeded() {
        if (senderFinished && receiverFinished && !socketClosed) {
            info("Closing socket");
            try {
                socket.close();
                info("Closed socket");
            }
            catch (IOException e) {
                error("Failed closing socket", e);
            }
        }
        else {
            info("Socket already closed");
        }
    }
            
   /* 
    * Closing occurs by sending a packet that causes NonBlockingSender to stop, which after
    * some intermediate processing should lead to our SenderListener being notified,
    * at which point actual closing will occur. 
    */              
    public synchronized void requestClose() {
        
        if (!closeRequested) {
            closeRequested = true;
            info("Requesting sender to close");
            sender.stop();
        }
    }       
    
    /** Just for trivial testing */
    public static void main(String[] pArgs) throws Exception {
        TestSender client = new TestSender(TestServer.DEFAULT_HOST, TestServer.DEFAULT_PORT);
        BusinessObject sendObj, rcvObj;
         
        sendObj = new PlainTextObject("This is a ZOMBI notification");
        info("Sending object 1: "+sendObj);
        client.send(sendObj);
        rcvObj = client.receive();
        info("Received object 1: "+rcvObj);
         
        sendObj = new PlainTextObject("This is a COMPETITION declaration");
        info("Sending object 2: "+sendObj);
        client.send(sendObj);
        rcvObj = client.receive();
        info("Received object 2: "+rcvObj);
        
        client.requestClose();        
         
        client.receiverFinished = true;
        info("Ending main thread");
    }

    private class SenderListener implements NonBlockingSender.Listener {
        public void senderFinished() {
            synchronized(TestSender.this) {
                info("SenderListener.finished()");
                senderFinished = true;
                closeSocketIfNeeded();
                info("finished SenderListener.finished()");
            }
        }
    }       
    
    class MyShutdown extends Thread {
        public void run() {
            System.err.println("Requesting close at shutdown thread...");
            requestClose();
        }
    }
    
    @SuppressWarnings("unused")
    private static void error(String msg) {
        Logger.error("TestTCPClient: "+msg);
    }
        
    private static void error(String msg, Exception e) {
        Logger.error("TestTCPClient: "+msg, e);
    }
        
    private static void warning(String msg) {
        Logger.warning("TestTCPClient: "+msg);
    }
    
    private static void info(String msg) {
        Logger.info("TestTCPClient: "+msg);
    }
    
}

