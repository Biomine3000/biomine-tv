package biomine3000.objects;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



import sun.misc.Signal;
import sun.misc.SignalHandler;

import util.DateUtils;
import util.ExceptionUtils;
import util.IOUtils;
import util.SU;
import util.Timer;
import util.Utils;
import util.collections.OneToOneBidirectionalMap;
import util.collections.Pair;
import util.dbg.Logger;
import util.net.NonBlockingSender;

public class TestServer {

    public static String DEFAULT_HOST = "localhost";
    public static int DEFAULT_PORT = 9876;    
    public static final DateFormat DEFAULT_DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        
    private SignalHandler oldSigTERM;
    private ServerSocket serverSocket;    
    private int serverport;
            
    
    public TestServer(int port) {                                                                   
        this.serverport = port;
    }                    
    
    private final void run() {

        try {
            init();
        }
        catch (ServerException e) {
            Utils.die("Failed initializing server", e);
            System.exit(1);               
        }
        
        // Should never return from the main loop!
        mainLoop();
        
        Logger.warning("Returned from the main loop; this code should be unreachable!");                                 
    }
        
    
    private void init() throws ServerException {                        
                        
        try {                                 
           // try out shutdown hook:
            MyShutdown sh = new MyShutdown();
            Runtime.getRuntime().addShutdownHook(sh);
            
            // try out signal handling:
            oldSigTERM = Signal.handle(new Signal("INT"), new MySignalHandler());
            
            serverSocket = new ServerSocket(serverport);
            Logger.info(getClass().getName()+" running at port: "+serverport);            
            
        } 
        catch (IOException e) {
            throw new UnrecoverableServerException("Could not listen on port: "+serverport, e);            
        }        
    }
    
    private void log(String msg) {
        Logger.info("TestTCPServer: "+msg);
    }

    private void doExit() {
        log("Should do some exiting");
    }
    
    // Example shutdown hook class
    class MyShutdown extends Thread {
        public void run() {
            log("Starting shutdown thread.");
            doExit();
            log("Finished shutdown thread.");                                   
        }
    }
        
    class MySignalHandler implements SignalHandler {                
        public void handle(Signal signal) {
            log("Starting SIGTERM handler.");                        
            doExit();
            if (oldSigTERM != null) {
                log("Starting old SIGTERM handler.");
                oldSigTERM.handle(signal);
                log("Done old SIGTERM handler.");
            }
            log("Done SIGTERM handler.");
            
        }
    }
         
    
    
    /** 
     * Should never return. Only way to exit is through client request "stop",
     * {@link UnrecoverableServerException}, or stop signal.
     */
    private void mainLoop() {         
                          
        while (true) {
            System.err.println("["+DateUtils.formatDate()+"] waiting for client...");
            
            try {
                Socket clientSocket = serverSocket.accept();                
                System.err.println("Client connected, processing client requests.");
                processSingleClient(clientSocket);
            } 
            catch (IOException e) {
                System.err.println("Accepting a client failed.");
                System.err.println(ExceptionUtils.formatWithCauses(e, "; "));
                e.printStackTrace();                
            }                                                            
        }                                        
    }
            
    private class ClientConnection implements NonBlockingSender.Listener {
        Socket socket;
        BufferedInputStream is;
        OutputStream os;        
        NonBlockingSender sender;
        
        ClientConnection(Socket socket) throws IOException {
            this.socket = socket; 
            is = new BufferedInputStream(socket.getInputStream());
            os = socket.getOutputStream();            
            sender = new NonBlockingSender(socket, this);
        }        
        
        public void send(byte[] packet) {
            try {
                sender.send(packet);
            }
            catch (IOException e) {
                handleException(e);
                close();
            }
        }       
        
        @Override
        public void senderFinished() {
            synchronized(this) {
                log("SenderListener.finished()");               
                close();
                log("finished SenderListener.finished()");
            }
        }
        
        private void close() {
            try {                                        
                os.flush();
                // closing socket should also close streams
                socket.close();
            }
            catch (IOException ioe) {
                // let's not bother to even log the exception at this
                // stage
            }
        }                
    }
            
    /** Currently, exception is just logged. */
    private void handleException(Exception e) {
        Logger.error("Exception while processing client request: "+
                     ExceptionUtils.formatWithCauses(e, "; "));            
        Logger.printStackTrace(e);
    }
    
    private void processSingleClient(Socket clientSocket) {
        
        ClientConnection client;
        
        try {
            client = new ClientConnection(clientSocket);
        }
        catch (IOException e) {
            System.err.println("Failed creating streams on socket: "+e+": "+e.getMessage());
            try {
                clientSocket.close();
            }
            catch (IOException e2) {
                // failed even this, no further action possible
            }
            return;
        }        

        BusinessObjectReader reader = new BusinessObjectReader(client.is, null); // täällä        
        
        try {
            Logger.info("Reading packet...");
            Pair<BusinessObjectMetadata, byte[]> packet = BusinessObject.readPacket(client.is);
            
            while (packet != null) {
//                Logger.info("Received packet: "+packet);
//                Logger.info("Making business object...");
//                BusinessObject bo = BusinessObject.makeObject(packet);
                BusinessObject bo = new BusinessObject(packet.getObj1(), packet.getObj2());                
                Logger.info("Received business object: "+bo);
                                
                Logger.info("Sending the very same object...");
                client.sender.send(bo.bytes());                
                
                Logger.info("Reading packet...");
                packet = BusinessObject.readPacket(client.is);                
                
            }
            
            
            
            // got null packet, which means client closed connection
            Logger.info("Client closed connection.");                                  
            client.close();            
        }
        catch (InvalidPacketException e) {
            // The following handler will send an error message to the client,    
            // and write the stack trace to the server log:
            handleException(e);
            
            // close the connection with the current client
            // TODO: add recovery when possible, and retry handling the request?
            client.close();           
        }
        catch (IOException e) {
            // The following handler will send an error message to the client,    
            // and write the stack trace to the server log:
            handleException(e);
            
            // close the connection with the current client
            // TODO: add recovery when possible, and retry handling the request?
            client.close();           
        }        
        catch (RuntimeException e) {
            // wrap the runtime exception as a server exception, for uniform 
            // handling...
            String msg = "RuntimeException while processing request.";                                                         
            handleException(new UnrecoverableServerException(msg, e));
            
            // close the connection with the current client
            // TODO: add recovery when possible, and retry handling the request?
            client.close();                       
        }
    }      
    
    
    @SuppressWarnings("unused")
    private void terminate(int pExitCode) {
        // TODO: more delicate termination needed?
        System.err.println("Shutting down the server at "+DateUtils.formatDate());
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            Logger.warning("Error while closing server socket in cleanUp(): "+
                            ExceptionUtils.formatWithCauses(e, "; "));
        }
        System.exit(pExitCode);
    }
                 
    
    /** Listens to the input stream of a single client */
    private class ReaderListener implements BusinessObjectReader.Listener {
        ClientConnection client;
        
        ReaderListener(ClientConnection client) {
            this.client = client;
        }

        @Override
        public void objectReceived(BusinessObject bo) {
            Logger.info("Received business object: "+bo);
            
            Logger.info("Sending the very same object...");
            // TODO: send to all clients!
            client.send(bo.bytes());           
        }

        @Override
        public void noMoreObjects() {
            Logger.info("noMoreObjects (client closed connection).");                                  
            client.close();            
        }

        private void handleException(Exception e) {
            TestServer.this.handleException(e);
            client.close();
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
        
    }
    
    /**
     * An exception which requires shutting down the server.
     */
    public class UnrecoverableServerException extends ServerException {
    
        public UnrecoverableServerException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);        
        }

        public UnrecoverableServerException(String pMsg) {
            super(pMsg);       
        }
        
        public UnrecoverableServerException(Throwable pCause) {
            super(pCause);          
        }
    }
    
    /**
     * An exception which requires shutting down the server.
     */
    public class ParseException extends RecoverableServerException {
    
        public ParseException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);        
        }

        public ParseException(String pMsg) {
            super(pMsg);       
        }
        
        public ParseException(Throwable pCause) {
            super(pCause);          
        }
    }
    
    
    public static void main(String[] args) {
        
        System.err.println("Starting test server at "+DateUtils.formatDate());
                       
        TestServer server = new TestServer(DEFAULT_PORT);
        server.run();                        
    }
            
          
}