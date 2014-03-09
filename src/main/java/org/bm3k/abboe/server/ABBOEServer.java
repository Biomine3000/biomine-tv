package org.bm3k.abboe.server;

import static org.bm3k.abboe.objects.BusinessObjectEventType.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.bm3k.abboe.senders.ContentVaultProxy;
import org.bm3k.abboe.senders.ContentVaultProxy.InvalidStateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import util.CmdLineArgs2;
import util.StringUtils;
import util.net.NonBlockingSender;

/**
 * Advanced Business Objects Exchange Server.
 *
 * Reads business objects from each client, broadcasting back everything it reads
 * to all clients.
 *
 * Two dedicated threads will created for each client, one for sending and one for reading {@link
 * org.bm3k.abboe.objects.BusinessObject}s.
 *
 * Once a client closes its sockets outputstream (the inputstream of the server's socket),
 * the server stops sending to that client and closes the socket.
 *
 */
public class ABBOEServer {
    private final Logger log = LoggerFactory.getLogger(ABBOEServer.class);

    public static final DateFormat DEFAULT_DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

    private ServerSocket serverSocket;
    private int serverPort;

    /** For sending welcome images */
    private ContentVaultProxy contentVaultProxy;

    /** all access to this client list should be synchronized on the ABBOEServer instance */
    private List<Client> clients;

    /** Shortcuts for clients, to be used for interactive server management only */
    private Map<Integer, Client> clientShortcuts;

    private State state;

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

    /** Generates a map (small int) => (client) for later reference. */
    private synchronized Map<Integer, Client> clientShortcuts() {
        Map<Integer, Client> map = new HashMap<Integer, Client>();
        int i=0;
        for (Client client: clients) {
            map.put(++i, client);
        }
        return map;
    }

    /** Create server data structures and start listening */
    public ABBOEServer(int port) throws IOException {
        state = State.NOT_RUNNING;
        this.serverPort = port;
        serverSocket = new ServerSocket(serverPort);
        clients = new ArrayList<Client>();
        log.info("Listening.");
        contentVaultProxy = new ContentVaultProxy();
        contentVaultProxy.addListener(new ContentVaultListener());
        contentVaultProxy.startLoading();
    }

    /** Send some random image from the content vault to all clients */
    private void sendImageToAllClients() {
        synchronized(clients) {
            BusinessObject image;
            try {
                image = contentVaultProxy.sampleImage();
                for (Client client: clients) {
                    client.send(image);
                }
            }
            catch (InvalidStateException e) {
                log.error("Content vault at invalid state after loading all images?");
            }
        }

    }

    private class ContentVaultListener implements ContentVaultProxy.ContentVaultListener {

        @Override
        public void loadedImageList() {
            // TODO Auto-generated method stub            
        }

        @Override
        public void loadedImage(String image) {
            // TODO Auto-generated method stub

        }

        @Override
        public void loadedAllImages() {
            sendImageToAllClients();
        }

    }

    /**
     * Send an object to all applicable clients. Does not block, as sending is done
     * using a dedicated thread for each client.
     */
    private synchronized void sendToAllClients(Client src, BusinessObject bo) {
        // defer coming up with toBytes to send until the time comes
        // to send to the first applicable client (there might be none) 
        byte[] bytes = null;
        for (Client client: clients) {
            if (client.shouldSend(src, bo)) {
                if (bytes == null) {
                    bytes = bo.toBytes();
                }
                client.send(bytes);
            }
        }
    }

    /**
     * Should never return. Only way to exit is through client request "stop",
     * {@link org.bm3k.abboe.common.UnrecoverableServerException}, or stop signal.
     */
    private void mainLoop() {

        state = State.ACCEPTING_CLIENTS;

        while (state == State.ACCEPTING_CLIENTS) {
            log.info("Waiting for client...");

            try {
                Socket clientSocket = serverSocket.accept();
                if (state == State.ACCEPTING_CLIENTS) {
                    log.info("Client connected from {}", clientSocket.getRemoteSocketAddress());
                    acceptSingleClient(clientSocket);
                }
                else {
                    // while waiting, someone seems to have changed our policy to "not accepting clients any more"
                    // TODO: more graceful way of rejecting this connection
                    log.info("Not accepting client from: {}", clientSocket.getRemoteSocketAddress());
                    clientSocket.close();
                }
            }
            catch (IOException e) {
                if (state == State.ACCEPTING_CLIENTS) {
                    log.error("Accepting a client failed", e);
                }
                // else we are shutting down, and failure is to be expected to result from server socket having been closed 
            }
        }

        log.info("Finished ABBOE main loop");
    }

    /** Actually, a connection to a client */
    private class Client implements NonBlockingSender.Listener {
        boolean registered = false;
        Socket socket;
        BufferedInputStream is;
        OutputStream os;
        /** Please do not call send of this client directly, even within this class, except in the one dedicated place */
        NonBlockingSender sender;
        BusinessObjectReader reader;
        ReaderListener readerListener;
        ClientReceiveMode receiveMode = ClientReceiveMode.ALL;
        LegacySubscriptions subscriptions = LegacySubscriptions.ALL;
        boolean closed;
        /** actual name of client, not including user or addr */
        String clientName;
        String user;
        String addr;
        /** Derived from clientName, user and addr */
        String name;
        boolean senderFinished;
        boolean receiverFinished;
        /** services implemented by client */
        LinkedHashSet<String> services = new LinkedHashSet<String>();

        Client(Socket socket) throws IOException {
            senderFinished = false;
            receiverFinished = false;
            this.socket = socket;
            addr = socket.getRemoteSocketAddress().toString();
            initName();
            is = new BufferedInputStream(socket.getInputStream());
            os = socket.getOutputStream();
            sender = new NonBlockingSender(socket, this);
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
            sender.setName("client-"+this.name);
            reader.setName("reader-"+this.name);
        }

        private synchronized void setUser(String user) {
            this.user = user;
            initName();
            sender.setName("client-"+name);
            reader.setName("reader-"+name);
        }

        /**
         * Caller needs to first ensure that client is willing to receive such a packet
         * by calling {@link #shouldSend(Client, BusinessObject)} or {@link #receiveEvents()}.
         * @param obj
         */
        private void send(BusinessObject obj) {
            log.info("Sending: "+obj);
            send(obj.toBytes());
        }

        private void send(String text) {                       
            BusinessObjectMetadata meta = new BusinessObjectMetadata();
            meta.put("sender", "java-A.B.B.O.E.");
            meta.setNatures("message");                              
            log("Sending plain text to client "+this+": "+text);
            BusinessObject reply = BOB.newBuilder().metadata(meta).payload(text).build();
            send(reply);
        }

        /** Should events be sent to this client? */
        public boolean receiveEvents() {
            return receiveMode != ClientReceiveMode.NONE;
        }

        /** Should the object <bo> from client <source> be sent to this client? */
        public boolean shouldSend(Client source, BusinessObject bo) {

            boolean result;

            if (receiveMode == ClientReceiveMode.ALL) {
                result = true;
            }
            else if (receiveMode == ClientReceiveMode.NONE) {
                result = false;
            }
            else if (receiveMode == ClientReceiveMode.EVENTS_ONLY) {
                result = bo.isEvent();
            }
            else if (receiveMode == ClientReceiveMode.NO_ECHO) {
                result = (source != this);
            }
            else {
                log.error("Unknown receive mode: "+receiveMode+"; not sending!");
                result = false;
            }

            if (result == true) {
                result = subscriptions.shouldSend(bo);
            }

            return result;
        }

       /**
        * Put object to queue of messages to be sent (to this one client) and return immediately.
        * Assume send queue has unlimited capacity.
        */
        private void send(byte[] packet) {
            if (senderFinished) {
                log.warn("No more sending business");
                return;
            }

            try {
                sender.send(packet);
            }
            catch (IOException e) {
                log.error("Failed sending to client "+this, e);
                doSenderFinished();
            }
        }

        private synchronized void registerServices(List<String> names) {
            services.addAll(names);
        }

        private void startReaderThread() {
            reader = new BusinessObjectReader(is, readerListener, name);
            Thread readerThread = new Thread(reader);
            readerThread.start();
        }

        /**
         * Forcibly terminate a connection with a client (supposedly called when
         * client steadfastly refuses to close the connection when requested)
         */
        private void forceClose() {
            if (closed) {
                error("Attempting to close a client multiple times", null);
            }
            log("Forcing closing of connection with client: "+this);

            try {
                // closing socket also closes streams if needed
                socket.close();
            }
            catch (IOException e) {
                error("Failed closing socket", e);
            }

            synchronized(ABBOEServer.this) {
                clients.remove(this);
                closed = true;
                if (state == State.SHUTTING_DOWN && clients.size() == 0) {
                    // last client closed and we are shutting down, finalize shutdown sequence...
                    log.info("No more clients, finalizing shutdown sequence...");
                    finalizeShutdownSequence();
                }
            }


            // TODO: should send routing/disconnect and proper routing/announcement
            sendToAllClients(this,
                    BOB.newBuilder()
                            .payload("Client " + this + " disconnected")
                            .event(CLIENTS_PART_NOTIFY).build()
            );
        }

        /**
         * Gracefully dispose of a single client after ensuring both receiver and client
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
                if (state == State.SHUTTING_DOWN && clients.size() == 0) {
                    // last client closed, no more clients, finalize shutdown sequence...
                    log.info("No more clients, finalizing shutdown sequence...");
                    finalizeShutdownSequence();
                }
            }

            sendToAllClients(this,
                    BOB.newBuilder()
                            .payload("Client " + this + " disconnected")
                            .event(CLIENTS_PART_NOTIFY)
                            .build()
            );
        }

        @Override
        public void senderFinished() {
            doSenderFinished();
        }

        /**
         * Initiate shutting down of proceedings with this client.
         *
         * Actually, just initiate closing of output channel. On noticing this,
         * client should close its socket, which will then be noticed on this server
         * as a {@link BusinessObjectReader.Listener#noMoreObjects()} notification from the {@link #reader}.
         */
        public void initiateClosingSequence(BusinessObject notification) {
            log.info("Initiating closing sequence for client: "+this);

            if (receiveEvents()) {
                log.info("Sending shutdown event to client: "+this);
                send(notification);
            }

            sender.requestStop();
        }

        private synchronized void doSenderFinished() {
            log("doSenderFinished");
            if (senderFinished) {
                log("Already done");
                return;
            }

            senderFinished = true;

            try {
                socket.shutdownOutput();
            }
            catch (IOException e) {
                log.error("Failed closing socket output after finishing client", e);
            }


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

            // request stop of client
            sender.requestStop();

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

    }

    private void acceptSingleClient(Socket clientSocket) {

        Client client;

        try {
            client = new Client(clientSocket);
            String abboeUser = Biomine3000Utils.getUser();
            client.send("Welcome to this fully operational java-A.B.B.O.E., run by "+abboeUser);
            if (Biomine3000Utils.isBMZTime()) {
                client.send("For relaxing times, make it Zombie time");
            }
            // suggest registration, if client has not done so within a second of its registration...
            new RegisterSuggesterThread(client).start();
            if (contentVaultProxy.getState() == ContentVaultProxy.State.INITIALIZED_SUCCESSFULLY) {
                try {
                    client.send(contentVaultProxy.sampleImage());
                }
                catch (InvalidStateException e) {
                    log.error("Invalid state while getting content from vault", e);
                }
            }
        }
        catch (IOException e) {
            log.error("Failed creating streams on socket", e);
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

    private class SystemInReader extends Thread {
        public void run() {
            try {
                stdInReadLoop();
            }
            catch (IOException e)  {
                log.error("IOException in SystemInReader", e);
                log.error("Terminating...");
                shutdown();
            }
        }
    }

    /**
     * FOO: it does not seem to be possible to interrupt a thread waiting on system.in, even
     * by closing System.in... Thread.interrupt does not work either...
     * it seems that it is not possible to terminate completely cleanly, then.
     */
    private void stdInReadLoop() throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line = br.readLine();
        boolean gotStopRequest = false;
        while (line != null && !gotStopRequest) {
            if (line.equals("stop") || line.equals("s")) {
                // this is the end
                gotStopRequest = true;
                break;
            }
            else if (line.equals("image") || line.equals("i")) {
                sendImageToAllClients();
            }
            else if (line.equals("clients") || line.equals("c")) {
                clientShortcuts = clientShortcuts();
                for (Integer key: clientShortcuts.keySet()) {
                    System.out.println(key+": "+clientShortcuts.get(key).name);
                }
            }
            else if (line.startsWith("close ") || line.startsWith("c ")) {
                // this is the end for one client
                String shortcutStr;
                if (line.startsWith("close ")) {
                    shortcutStr = line.replace("close ", "");
                }
                else if (line.startsWith("c ")) {
                    shortcutStr = line.replace("c ", "");
                }
                else {
                    log.error("WhatWhatWhat");
                    continue;
                }
                if (!(StringUtils.isIntegral(shortcutStr))) {
                    log.error("Not a valid shortcut string: {}", shortcutStr);
                    continue;
                }
                if (clientShortcuts == null) {
                    log.error("No client map!");
                    continue;
                }
                int shortcut = Integer.parseInt(shortcutStr);

                Client client = clientShortcuts.get(shortcut);
                if (client == null) {
                    log.error("No such client shortcut: {}", shortcut);
                }
                log.info("Closing connection to client: {}", client);
                String admin  = Biomine3000Utils.getUser();
                               
                BusinessObject closeNotification = BOB.newBuilder()
                        .payload("ABBOE IS GOING TO CLOSE THIS CONNECTION NOW (as requested by the ABBOE adminstrator, "+admin+")")
                        .event(ABBOE_CLOSE_NOTIFY).build();
                client.initiateClosingSequence(closeNotification);

                ClientShutdownThread cst = new ClientShutdownThread(client);
                cst.start();

            }
            else {
                // just a message to be broadcast
                sendToAllClients(null, BOB.newBuilder().payload(line).build());
            }
            line = br.readLine();
        }

        if (gotStopRequest) {
            log.info("Got stop request");
        }
        else {
            log.info("Tranquilly finished reading stdin");
        }

        log.info("Harmoniously closing down server by closing output of all client sockets");
        shutdown();

    }


    private synchronized void shutdown() {
        state = State.SHUTTING_DOWN;

        // TODO: more delicate termination needed?
        log.info("Initiating shutdown sequence");

        if (clients.size() > 0) {
            for (Client client: clients) {
                BusinessObject shutdownNotification = BOB.newBuilder()
                        .payload("ABBOE IS GOING TO SHUTDOWN in 5 seconds")
                        .event(ABBOE_SHUTDOWN_NOTIFY).build();
                client.initiateClosingSequence(shutdownNotification);
            }

            // start a thread to ensure shutdown in case some clients fail to close their connections
            ShutdownThread shutdownThread = new ShutdownThread();
            shutdownThread.start();
        }
        else {
            // no clients to close!
            finalizeShutdownSequence();
        }

        // System.exit(pExitCode);
    }

    /** Finalize shutdown sequence after closing all clients (if any) */
    private void finalizeShutdownSequence() {
        try {
            log.info("Finalizing shutdown sequence by closing server socket");
            serverSocket.close();
        }
        catch (IOException e) {
            // foo
        }

        log.info("Exiting");
        System.exit(0);
    }


   /**
    * Ensure shutdown in case some client(s) fail to close their connection properly
    * Note that tempting as it might be, it is not possible to send any "you fool" message
    * to these clients at this stage, as any outgoing connections have already been shut down.
    */
    private class ShutdownThread extends Thread {
        public void run() {
            try {
                Thread.sleep(5000);
                log.error("Following clients have failed to close their connection properly: " +
                           StringUtils.collectionToString(clients,", ")+
                		  "; forcing shutdown...");
                finalizeShutdownSequence();
            }
            catch (InterruptedException e) {
                log.error("Shutdownthread interrupted");
            }
        }
    }

    private class ClientShutdownThread extends Thread {
        Client client;
        ClientShutdownThread(Client client) {
            this.client = client;
        }
        public void run() {
            try {
                Thread.sleep(3000);
                if (!client.closed) {
                    log.error("Client "+client.name+" has failed to shut down properly, forcing shutdown...");
                    client.forceClose();
                }
            }
            catch (InterruptedException e) {
                log.error("Shutdownthread for client: "+client.name+" interrupted, connection to client has not been shutdown");
            }
        }
    }

    private class RegisterSuggesterThread extends Thread {
        Client client;
        RegisterSuggesterThread(Client client) {
            this.client = client;
        }

        public void run() {
            try {
                Thread.sleep(1000);
                if (state == ABBOEServer.State.SHUTTING_DOWN) return;
                if (!client.registered) {
                    client.send("Please register by sending a \""+CLIENTS_REGISTER+"\" event");
                }
            }
            catch (InterruptedException e) {
                log.error("RegisterSuggesterThread interrupted");
            }

        }
    }

    private void handleServicesRegisterEvent(Client client, BusinessObject bo) {
        BusinessObjectMetadata meta = bo.getMetadata();
        List<String> names = meta.getList("names");
        String name = meta.getString("name");
        if (name != null && names != null) {
            // client has decided to generously provide both name and names; 
            // let's as generously handle this admittably deranged request
            names = new ArrayList<String>(names);
            names.add(name);
        }
        else if (name != null && names == null) {
            names = Collections.singletonList(name);
        }
        else if (name == null && names != null) {
            // no action
        }
        else {
            // both null
            sendErrorReply(client, "No name nor names in services/register event");
            return;
        }

        // names now contains the services to register
        client.registerServices(names);
    }

    private void handleClientsListEvent(Client requestingClient) {

        BusinessObject clientReport;

        synchronized(ABBOEServer.this) {
            clientReport = BOB.newBuilder()
                    .payload(StringUtils.colToStr(clientReport(requestingClient), "\n"))
                    .build();
            List<String> clientNames = new ArrayList<>();
            for (Client client: clients) {
                if (client != requestingClient) {
                    clientNames.add(client.name);
                }
            }
            clientReport.getMetadata().put("you", requestingClient.name);
            clientReport.getMetadata().setEvent(CLIENTS_LIST_REPLY);
            clientReport.getMetadata().putStringList("others", clientNames);
        }

        requestingClient.send(clientReport);
    }

    private void handleClientRegisterEvent(Client client, BusinessObject bo) {
        BusinessObjectMetadata meta = bo.getMetadata();
        String name = meta.getString("name");
        String user = meta.getString("user");
        String receiveModeName = meta.getString(ClientReceiveMode.KEY);
        if (name != null) {
            client.setName(name);
        }
        else {
            log.warn("No name in register packet from {}", client);
        }
        if (user != null) {
            client.setUser(user);
        }
        else {
            log.warn("No user in register packet from {}", client);
        }

        String msg = "Registered you as \""+name+"-"+user+"\".";

        if (receiveModeName != null) {
            ClientReceiveMode recvMode = ClientReceiveMode.getMode(receiveModeName);
            if (recvMode == null) {
                sendErrorReply(client, "Unrecognized rcv mode in packet: "+recvMode+", using the default: "+client.receiveMode);
            }
            else {
                client.receiveMode = recvMode;
                msg+=" Your receive mode is set to: \""+recvMode+"\".";
            }
        }
        else {
            msg+=" No receive mode specified, using the default: "+client.receiveMode;
        }

        LegacySubscriptions subscriptions = null;
        try {
            subscriptions = meta.getSubscriptions();
        }
        catch (InvalidJSONException e) {
            sendErrorReply(client, "Unrecognized subscriptions in packet: "+e.getMessage());
        }

        if (subscriptions != null) {
            msg+=" Your subscriptions are set to: "+subscriptions+".";
        }
        else {
            msg+=" You did not specify subscriptions; using the default: "+client.subscriptions;
        }

        BusinessObject replyObj = BOB.newBuilder().payload(msg).event(CLIENTS_LIST_REPLY).build();
        client.send(replyObj);

        // only set after sending the plain text reply                        
        if (subscriptions != null) {
            client.subscriptions = subscriptions;
        }

        client.registered = true;
        // TODO: send routing/subscribe/notification
        sendToAllClients(client,
                BOB.newBuilder()
                        .payload("Client " + client + " registered")
                        .event(CLIENTS_REGISTER_NOTIFY)
                        .build()
        );
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
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();
                // does this event need to be sent to other clients?
                boolean forwardEvent = true;
                if (et != null) {
                    log.info("Received {} event: ", bo);
                    if (et == CLIENT_REGISTER) {
                        sendErrorReply(client, "Using deprecated name for client registration; the " +
                                      "present-day jargon defines that event type be \""+
                                CLIENTS_REGISTER.toString()+"\"");
                        handleClientRegisterEvent(client, bo);
                        forwardEvent = false;
                    }
                    else if (et == CLIENTS_REGISTER) {
                        // deprecated version
                        handleClientRegisterEvent(client, bo);
                        forwardEvent = false;
                    }
                    else if (et == CLIENTS_LIST) {
                        handleClientsListEvent(client);
                        forwardEvent = false;
                    }
                    else if (et == SERVICES_REGISTER) {
                        handleServicesRegisterEvent(client, bo);
                        forwardEvent = false;
                    }
                    else {
                        log.info("Received known event which this ABBOE implementation does not handle: {}", bo);
                    }
                }
                else {
                    log.info("Received unknown event: {}", bo.getMetadata().getEvent());
                }

                // send the event if needed 
                if (forwardEvent) {
                    log.info("Sending the very same event to all clients...");
                    ABBOEServer.this.sendToAllClients(client, bo);
                }
            }
            else {
                // not an event, assume mythical "content"

                if (bo.getPayload() != null &&
                        bo.getMetadata().getType() == BusinessMediaType.PLAINTEXT.withoutParameters().toString()) {
                    BusinessObject pto = BOB.newBuilder()
                            .payload(bo.getPayload())
                            .metadata(bo.getMetadata().clone())
                            .build();
                    log.info("Received content: {}", Biomine3000Utils.formatBusinessObject(pto));
                }
                else {
                    log.info("Received content: {}", bo);
                }
                // log("Sending the very same content to all clients...");
                ABBOEServer.this.sendToAllClients(client, bo);
            }

        }



        @Override
        public void noMoreObjects() {
            log.info("connectionClosed (client closed connection).");
            client.doReceiverFinished();
        }

        private void handleException(Exception e) {
            if (e.getMessage() != null && e.getMessage().equals("Connection reset")) {
                log.info("Connection reset by client: "+this.client);
            }
            else {
                log.error("Exception while reading objects from client " + client, e);
            }
            client.doReceiverFinished();
        }

        @Override
        public void handle(IOException e) {
            handleException(e);
        }

        @Override
        public void handle(InvalidBusinessObjectException e) {
            handleException(e);

        }

        @Override
        public void handle(RuntimeException e) {
            handleException(e);
        }

        public void connectionReset() {
            log.error("Connection reset by client: {}", this.client);
            client.doReceiverFinished();
        }
    }

    private void sendErrorReply(Client client, String error) {
        BusinessObject reply =
                BOB.newBuilder().payload(error).event(ERROR).build();
        log.info("Sending error reply to client {}: {}", client, error);
        client.send(reply);
    }

    private void startSystemInReadLoop() {
        SystemInReader systemInReader = new SystemInReader();
        systemInReader.start();
    }

    public static void main(String[] pArgs) throws Exception {
    	
    	
        Logger log = LoggerFactory.getLogger(ABBOEServer.class);
        log.debug("ABBOE!");

        CmdLineArgs2 args = new CmdLineArgs2(pArgs);

        Integer port = args.getInt("port");

        if (port == null) {
            port = Biomine3000Utils.conjurePortByHostName();            
        }
        
        log.info("Using port: "+port);

        if (port == null) {
            log.error("No -port");
            System.exit(1);
        }

        log.info("Starting ABBOE at port "+port);

        try {
            ABBOEServer server = new ABBOEServer(port);
            // start separate thread for reading system.in
            server.startSystemInReadLoop();
            // the current thread will start executing the main loop
            server.mainLoop();
        }
        catch (IOException e) {
            log.error("Failed initializing ABBOE", e);
        }
    }

    private enum State {
        NOT_RUNNING,
        ACCEPTING_CLIENTS,
        SHUTTING_DOWN;
    }

}