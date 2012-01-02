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
            oldSigTERM = Signal.handle(new Signal("TERM"), new MySignalHandler());
            
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
            System.err.println("["+formatDate()+"] waiting for client...");
            
            try {
                Socket clientSocket = serverSocket.accept();                
                System.err.println("Client connected, processing client requests.");
                processSingleClient(clientSocket);
            } 
            catch (IOException e) {
                System.err.println("Accepting a client failed.");
                System.err.println(formatException(e, "; "));
                e.printStackTrace();                
            }                                                            
        }                                        
    }
            
    private class Client implements NonBlockingSender.Listener {
        Socket socket;
        BufferedInputStream is;
        OutputStream os;        
        NonBlockingSender sender;
        
        Client(Socket socket) throws IOException {
            this.socket = socket; 
            is = new BufferedInputStream(socket.getInputStream());
            os = socket.getOutputStream();            
            sender = new NonBlockingSender(socket, this);
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
                is.close();
                os.flush();
                os.close();                
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
                     formatException(e, "; "));            
        Logger.printStackTrace(e);
    }
    
    private void processSingleClient(Socket clientSocket) {
        
        Client client;
        
        try {
            client = new Client(clientSocket);
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
        System.err.println("Shutting down the server at "+formatDate());
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            Logger.warning("Error while closing server socket in cleanUp(): "+
                           formatException(e, "; "));
        }
        System.exit(pExitCode);
    }
    
    
    /**
     * A very robust exception formatter (done because ExceptionUtils.formatWithCauses
     * can fail to classpath problems if jar has been updated but server not 
     * restarted...)
     */ 
    private String formatException(Exception e, String pSeparator) {
        try {
            return ExceptionUtils.formatWithCauses(e, pSeparator);
        }
        catch (Throwable t) {
            String result = e.getClass().getName()+": "+e.getMessage();
            if (t.getCause() != null) {
                result += " (cannot format exception causes due to severe error during"+
                          " exception formatting: "+t.getClass()+": "+t.getMessage();
                          
            }
            return result;                                    
        }
    }
    
    public abstract class ServerException extends Exception {
        public ServerException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);        
        }

        public ServerException(String pMsg) {
            super(pMsg);       
        }
        
        public ServerException(Throwable pCause) {
            super(pCause);          
        }
    }
    
    /** 
     * An exception after only one client is lost, and it is still possible to 
     * accept new clients. TODO: define how closing of connection with 
     * client is ensured!
     */
    public class RecoverableServerException extends ServerException {
    
        public RecoverableServerException(String pMsg, Throwable pCause) {
            super(pMsg, pCause);        
        }

        public RecoverableServerException(String pMsg) {
            super(pMsg);       
        }
        
        public RecoverableServerException(Throwable pCause) {
            super(pCause);          
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
        
        System.err.println("Starting Crawler cache server at "+formatDate());
                       
        TestServer server = new TestServer(DEFAULT_PORT);
        server.run();                        
    }
    
    
    
    
    private static String formatDate() {
        Date date = new Date(System.currentTimeMillis());                            
        return DEFAULT_DATE_FORMAT.format(date);
    }
          
}