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
public abstract class AbstractClient implements BusinessObjectReader.Listener {
                   
    protected ILogger log;
    private String name;
    boolean echo;
    String user;
    
    private Socket socket = null;       
    
    /**
     * A dedicated sender used to send stuff (that is: buziness objects)
     * in a non-blocking manner. 
     */
    NonBlockingSender sender = null;

    private BusinessObjectReader reader = null;
    
    private boolean senderFinished = false;
    private boolean receiverFinished = false;
    
    private boolean socketClosed = false;
    private boolean closeRequested = false;     
    
    /**
     * @param socket the server socket 
     * @name arbitrary name for the client (will be sent to server)
     * @param construcDedicatedImplementations Use dedicated java implementation class for business ' 
     *        object where applicable (as dictated by {@link Biomine3000Mimetype}, instead of the 
     *        generic {@link BusinessObject}. 
     * @param log the log
     * @throws UnknownHostException
     * @throws IOException
     */
    public AbstractClient(Socket socket, String name, 
                          boolean construcDedicatedImplementations, 
                          boolean echo,
                          ILogger log) throws UnknownHostException, IOException {                                
        this.socket = socket;
        this.name = name;
        this.log = log;
        this.echo = echo;
        this.user = System.getenv("USER");
        if (user == null) {
            user = "anonymous";
        }
    
        MyShutdown sh = new MyShutdown();            
        Runtime.getRuntime().addShutdownHook(sh);
        log.info("Initialized shutdown hook");
        
        // init sender
        sender = new NonBlockingSender(socket, new SenderListener());
                      
        // send register packet to server
        BusinessObject registerObj = Biomine3000Utils.makeRegisterPacket(name);
        sender.send(registerObj.bytes());
        
        // send set echo=false packet to server
        log.info("Setting echo to false");
        setEcho(false);        
        
        // start listening to objects from server
        log.info("Starting listening to server...");
        startReaderThread();
    }                     
    
    protected void send(BusinessObject object) throws IOException {
        object.getMetaData().setSender(user);
        sender.send(object.bytes());
    }       
        
    public String getName() {
        return name;
    }
    
    private synchronized void closeSocketIfNeeded() {
        if (senderFinished && receiverFinished && !socketClosed) {
            log.info("Closing socket");
            try {
                socket.close();
                log.info("Closed socket");
            }
            catch (IOException e) {
                log.error("Failed closing socket", e);
            }
        }
        else {
            log.info("Socket already closed");
        }
    }
            
   /* 
    * Closing occurs by requesting a sender to send a special stop packet that causes 
    * it to stop (done using method stop()), which after some intermediate processing 
    * should lead to our beloved SenderListener being notified, at which point actual
    * closing will occur. 
    */              
    protected synchronized void requestClose() {
        
        if (!closeRequested) {
            closeRequested = true;
            log.info("Requesting sender to close");
            sender.stop();
        }
    }       
    
    protected void startReaderThread() throws IOException {
        reader = new BusinessObjectReader(socket.getInputStream(), this, name, true, log);
        reader.setName(name);
        Thread readerThread = new Thread(reader);
        readerThread.start();
    }
    
    private class SenderListener implements NonBlockingSender.Listener {
        public void senderFinished() {
            synchronized(AbstractClient.this) {
                log.info("SenderListener.finished()");
                senderFinished = true;
                closeSocketIfNeeded();
                log.info("finished SenderListener.finished()");
            }
        }
    }       
    
    protected void setEcho(boolean value) throws IOException {
        BusinessObject obj = new BusinessObject();
        BusinessObjectMetadata meta = obj.getMetaData();
        meta.setEvent(BusinessObjectEventType.SET_PROPERTY);
        meta.setName("echo");
        meta.setBoolean("value", echo);
        send(obj);
    }
    
    class MyShutdown extends Thread {
        public void run() {
            System.err.println("Requesting close at shutdown thread...");
            requestClose();
        }
    }        
           
    /** 
     * This should be implemented by subclasses. Always receive a non-null object. 
     * Probably {@link #send(BusinessObjec)} will need to be called. This is synchronized 
     * by default, so the subclass need not bother with synchronization    
     */
    @Override
    public abstract void objectReceived(BusinessObject bo);       
    
    /** Called when nothing more to read from stream */
    @Override
    public abstract void noMoreObjects();
    
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
    
    protected abstract void handleException(Exception e);
}

