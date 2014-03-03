package biomine3000.objects;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.collections.Pair;
import util.net.NonBlockingSender;


/**
 * Reads lines from stdin and writes them to the server as PlainTextObjects synchronously.
 * After writing each line reads one object from the server, again synchronously (the assumption is
 * that the read object will be the line we just wrote, although that is not guaranteed in any way). 
 */
public class TrivialClient_old {
    private final Logger logger = LoggerFactory.getLogger(TrivialClient_old.class);
                      
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
     
    
    public TrivialClient_old(Socket socket) throws UnknownHostException, IOException {               
                                               
        // init communications with the server

        this.socket = socket;
        sender = new NonBlockingSender(socket, new SenderListener());
  
        logger.info("TestTCPClient Connected to server");
        
        MyShutdown sh = new MyShutdown();            
        Runtime.getRuntime().addShutdownHook(sh);
        logger.info("Initialized shutdown hook");
    }                
       
    public void send(LegacyBusinessObject object) throws IOException {        
        sender.send(object.bytes());
    }
    
    /** Return null when no more business objects available */
    public BusinessObject receive() throws IOException, InvalidBusinessObjectException {
        Pair<BusinessObjectMetadata, byte[]> packet = LegacyBusinessObject.readPacket(socket.getInputStream());               
        BusinessObject bo = BusinessObjectFactory.makeObject(packet);
        return bo;
    }
        
    private synchronized void closeSocketIfNeeded() {
        if (senderFinished && receiverFinished && !socketClosed) {
            logger.info("Closing socket");
            try {
                socket.close();
                logger.info("Closed socket");
            }
            catch (IOException e) {
                logger.error("Failed closing socket", e);
            }
        }
        else {
            logger.info("Socket already closed");
        }
    }
            
   /* 
    * Closing occurs by requesting a sender to send a special stop packet that causes 
    * it to stop (done using method stop()), which after some intermediate processing 
    * should lead to our beloved SenderListener being notified, at which point actual
    * closing will occur. 
    */              
    public synchronized void requestClose() {
        
        if (!closeRequested) {
            closeRequested = true;
            logger.info("Requesting sender to close");
            sender.requestStop();
        }
    }       
    
//    /** Just for trivial testing */
//    public static void main(String[] pArgs) throws Exception {
//        Socket socket = Biomine3000Utils.connectToServer(pArgs);        
//        TrivialClient client = new TrivialClient(socket);
//                       
//        BusinessObject sendObj, rcvObj;
//
//        // register to server
//        sendObj = Biomine3000Utils.makeRegisterPacket("TrivialClient");
//        sender.send(sendObj);
//        rcvObj = sender.receive();
//        System.out.println("Received object: "+rcvObj);                        
//        
//        
//        // start reading user input
//        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//        String line = br.readLine();
//        while (line != null) {
//            sendObj = new PlainTextObject(line);
//            System.out.println("Sending object: "+sendObj );
////            System.out.write(sendObj.bytes());
////            System.out.println("");
//            sender.send(sendObj);
//            rcvObj = sender.receive();
//            System.out.println("Received object: "+rcvObj);
////            System.out.write(rcvObj.bytes());
////            System.out.println("");
//            info(" "+rcvObj);
//            line = br.readLine();
//        }
//        
////        sendObj = new PlainTextObject("This is a ZOMBI notification");
////        info("Sending object 1: "+sendObj);
////        sender.send(sendObj);
////        rcvObj = sender.receive();
////        info("Received object 1: "+rcvObj);
////         
////        sendObj = new PlainTextObject("This is a COMPETITION declaration");
////        info("Sending object 2: "+sendObj);
////        sender.send(sendObj);
////        rcvObj = sender.receive();
////        info("Received object 2: "+rcvObj);
//        
//        sender.requestClose();        
//         
//        sender.receiverFinished = true;
//        info("Ending main thread");
//    }

    private class SenderListener implements NonBlockingSender.Listener {
        public void senderFinished() {
            synchronized(TrivialClient_old.this) {
                logger.info("SenderListener.finished()");
                senderFinished = true;
                closeSocketIfNeeded();
                logger.info("finished SenderListener.finished()");
            }
        }
    }       
    
    class MyShutdown extends Thread {
        public void run() {
            System.err.println("Requesting close at shutdown thread...");
            requestClose();
        }
    }
}

