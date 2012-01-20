package biomine3000.objects;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import util.CmdLineArgs2;
import util.DateUtils;
import util.StringUtils;
import util.dbg.ILogger;
import util.dbg.Logger;
import util.net.NonBlockingSender;

/**
 * Advanced Business Objects Exchange Server.
 * 
 * Reads business objects from each client, broadcasting back everything it reads
 * to all clients.
 * 
 * Two dedicated threads will created for each client, one for sending and one for reading {@link
 * BusinessObject}s.  
 * 
 * Once a client closes its sockets outputstream (the inputstream of the server's socket),
 * the server stops sending to that client and closes the socket. 
 *
 */
public class ABBOEServer {   
   
    private static ILogger log = new Logger.ILoggerAdapter(null, new DateUtils.BMZGenerator());
    
    public static final DateFormat DEFAULT_DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        
    private ServerSocket serverSocket;    
    private int serverPort;
    private List<Client> clients;
    
    
    private synchronized List<String> clientReport(Client you) {
        List<String> result = new ArrayList<String>();
        for (Client client: clients) {
            if (client == you) {
                result.add(client.name+" (you)");
            }
            else {
                result.add(client.name);
            }
        }
        return result;
    }
    
    /** Create server data structures and start listening */
    public ABBOEServer(int port) throws IOException {                                                                   
        this.serverPort = port;
        serverSocket = new ServerSocket(serverPort);        
        clients = new ArrayList<Client>();
        log("Listening.");
    }                            

    private synchronized void sendToAllClients(Client src, byte[] packet) {
        for (Client client: clients) {
            if (client != src || client.echo) {
                client.send(packet);               
            }
        }
    }          
    
    /** 
     * Should never return. Only way to exit is through client request "stop",
     * {@link UnrecoverableServerException}, or stop signal.
     */
    private void mainLoop() {         
                          
        while (true) {
            log("Waiting for client...");
            
            try {
                Socket clientSocket = serverSocket.accept();
                log("Client connected from "+clientSocket.getRemoteSocketAddress());
                processSingleClient(clientSocket);
            } 
            catch (IOException e) {
                error("Accepting a client failed", e);
            }                                                            
        }                                        
    }
            
    private class Client implements NonBlockingSender.Listener {        
        Socket socket;
        BufferedInputStream is;
        OutputStream os;        
        NonBlockingSender sender;
        BusinessObjectReader reader;
        ReaderListener readerListener;
        boolean closed;
        String clientName;
        String user;
        String addr;
        /** Derived from clientName, user and addr */
        String name;
        boolean senderFinished;
        boolean receiverFinished;
        boolean echo = true;
                
        Client(Socket socket) throws IOException {
            senderFinished = false;
            receiverFinished = false;
            this.socket = socket;
            addr = socket.getRemoteSocketAddress().toString();
            initName();
            is = new BufferedInputStream(socket.getInputStream());
            os = socket.getOutputStream();            
            sender = new NonBlockingSender(socket, this, log);
            sender.setName(name);
            readerListener = new ReaderListener(this);
            closed = false;
            
            log("Client connected");
            synchronized(ABBOEServer.this) {
                clients.add(this);
            }            
        }
        
        private void initName() {
            StringBuffer buf = new StringBuffer();
            if (clientName != null) {
                buf.append(clientName+"-");
            }
            if (user != null) {
                buf.append(user+"-");
            }
            buf.append(addr);
            name = buf.toString();
        }
        
        private synchronized void setName(String clientName) {
            this.clientName = clientName;
            initName();
            sender.setName("sender-"+this.name);  
            reader.setName("reader-"+this.name);
        }
        
        private synchronized void setUser(String user) {
            this.user = user;
            initName();
            sender.setName("sender-"+name);  
            reader.setName("reader-"+name);
        }
        
        private void send(BusinessObject obj) {
            obj.getMetaData().setSender("ABBOE");
            send(obj.bytes());
        }
        
       /**
        * Put object to queue of messages to be sent (to this one client) and return immediately.        
        * Assume send queue has unlimited capacity.
        */
        private void send(byte[] packet) {
            if (senderFinished) {
                warn("No more sending business");
                return;
            }
            
            try {
                sender.send(packet);
            }
            catch (IOException e) {
                error("Failed sending to client "+this, e);
                doSenderFinished();
            }
        }       
        
        private void startReaderThread() {
            reader = new BusinessObjectReader(is, readerListener, name, false, log);
            reader.setName(name);
            Thread readerThread = new Thread(reader);
            readerThread.start();
        }               
        
        /**
         * Gracefully dispose of a single client after ensuring both receiver and sender 
         * have finished
         */ 
        private void doClose() {
            if (closed) {
                error("Attempting to close a client multiple times", null);
            }
            
            log("Closing connection with client: "+this);

            try {                                        
                os.flush();
            }
            catch (IOException e) {
                // let's not bother to even log the exception at this stage
            }
            
            try {
                // closing socket also closes streams if needed
                socket.close();
            }
            catch (IOException e) {
                // let's not bother to even log the exception at this stage
            }
            
            synchronized(ABBOEServer.this) {
                clients.remove(this);
                closed = true;
            }
        }                
        
        @Override
        public void senderFinished() {
//            log("SenderListener.finished()");
            doSenderFinished();
//            log("finished SenderListener.finished()");                      
        }
                
        private synchronized void doSenderFinished() {
            log("doSenderFinished");
            if (senderFinished) {
                log("Already done");
                return;
            }
            
            senderFinished = true;
            
            if (receiverFinished) {
                doClose();
            }                                 
        }

        private synchronized void doReceiverFinished() {
            log("doReceiverFinished");
            if (receiverFinished) {
                log("Already done");
                return;
            }
            
            // request stop of sender
            sender.stop();
            
            receiverFinished = true;
            
            if (senderFinished) {
                doClose();
            }                         
        }
        
        private void error(String msg, Exception e) {
            log.error(name+": "+msg, e);
        }
        
        private void log(String msg) {
            log.info(name+": "+msg);
        }
        
        public String toString() {
            return name;
        }

        public void setEcho(boolean val) {
            echo = val;            
        }
    }
                    
    private void processSingleClient(Socket clientSocket) {
        
        Client client;
         
        try {
            client = new Client(clientSocket);
            String abboeUser = Biomine3000Utils.getUser();
            String msg = "Welcome to this fully operational java-A.B.B.O.E., run by "+abboeUser+".\n"+
                         "List of connected clients:\n"+
                         StringUtils.collectionToString(clientReport(client));                        
            BusinessObject welcomeObj = new PlainTextObject(msg);
                                      
            client.send(welcomeObj);
        }
        catch (IOException e) {
            error("Failed creating streams on socket", e);
            try {
                clientSocket.close();
            }
            catch (IOException e2) {
                // failed even this, no further action possible
            }
            return;
        }        

        client.startReaderThread();
    }          
    
    @SuppressWarnings("unused")
    private void terminate(int pExitCode) {
        // TODO: more delicate termination needed?
        log("Shutting down the server at "+DateUtils.formatDate());
        try {
            serverSocket.close();
        }
        catch (IOException e) {
            Logger.warning("Error while closing server socket in cleanUp(): ", e);                            
        }
        System.exit(pExitCode);
    }
                     
    /** Listens to a single dedicated reader thread reading objects from the input stream of a single client */
    private class ReaderListener implements BusinessObjectReader.Listener {
        Client client;
        
        ReaderListener(Client client) {
            this.client = client;
        }

        @Override
        public void objectReceived(BusinessObject bo) {                        
            if (bo.isEvent()) {
                BusinessObjectEventType et = bo.getMetaData().getKnownEvent();
                if (et != null) {
                    log("Received "+et+" event: "+bo);
                    if (et == BusinessObjectEventType.CLIENT_REGISTER) {                        
                        String name = bo.getMetaData().getName();
                        String user = bo.getMetaData().getUser();
                        if (name != null) {
                            client.setName(name);
                        }
                        else {
                            warn("No name in register packet from "+client);
                        }
                        if (user != null) {
                            client.setUser(user);
                        }     
                        else {
                            warn("No user in register packet from "+client);
                        }
                                                
                        String msg = "Registered you as "+name+"-"+user;                                     
                                     
                        BusinessObject replyObj = new PlainTextObject(msg);                                                
                        client.send(replyObj);
                    }
                    else if (et == BusinessObjectEventType.SET_PROPERTY) {
                        BusinessObjectMetadata meta = bo.getMetaData();
                        String name = meta.getName();                                                
                        log.info("name: "+name);                        
                        if (name == null) {
                            sendErrorReply(client, "No name in SET_PROPERTY event");
                        }                        
                        else  {
                            if (name.equals("echo")) {                                
                                Boolean value = meta.getBoolean("value");
                                log.info("value: "+value);
                                if (value== null) {
                                    sendErrorReply(client, "No value in SET_PROPERTY event");
                                }
                                else {
                                    client.setEcho(value);                                    
                                    sendTextReply(client, "Echo set to "+value);
                                }                                                                
                            }
                            else {
                                sendErrorReply(client, "Unknown property: "+name+", no effect");
                            }
                        }
                    }
                    else {
                        log("Reveiced known event which this ABBOE implementation does not handle: "+bo);
                    }
                }
                else {
                    log("Reveiced unknown event: "+bo.getMetaData().getEvent());
                }
            }
            else {
                // not an event, assume mythical "content"
                log("Received content: "+bo);
                log("Sending the very same content to all clients...");
                ABBOEServer.this.sendToAllClients(client, bo.bytes());
            }
            
        }

        
        
        @Override
        public void noMoreObjects() {
            log("noMoreObjects (client closed connection).");                                  
            client.doReceiverFinished();            
        }

        private void handleException(Exception e) {            
            error("Exception while reading objects from client "+client, e);                                            
            e.printStackTrace();
            client.doReceiverFinished();
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
        
    private void sendErrorReply(Client client, String error) {
        PlainTextObject reply = new PlainTextObject();
        reply.getMetaData().setEvent(BusinessObjectEventType.ERROR);
        reply.setText(error);
        log("Sending error reply to client "+client+": "+error);
        client.send(reply);        
    }
       
    private void sendTextReply(Client client, String text) {
        PlainTextObject reply = new PlainTextObject();        
        reply.setText(text);
        log("Sending plain text reply to client "+client+": "+text);
        client.send(reply);        
    }

    public static void main(String[] pArgs) throws Exception {
        
        CmdLineArgs2 args = new CmdLineArgs2(pArgs);
                        
        Integer port = args.getIntOpt("port");
        
        if (port == null) {             
            port = Biomine3000Utils.conjurePortByHostName();
        }
        
        if (port == null) {
            error("No port");
            System.exit(1);
        }
        
        log("Starting test server at port "+port);
                       
        try {
            ABBOEServer server = new ABBOEServer(port);
            server.mainLoop();
        }
        catch (IOException e) {
            error("Failed initializing server", e);
        }
    }
    
    private static void log(String msg) {
        log.info(msg);
    }    
    
    private static void warn(String msg) {
        log.warning(msg);
    }        
    
    private static void error(String msg) {
        log.error(msg);
    }
    
    private static void error(String msg, Exception e) {
        log.error(msg, e);
    }
          
}