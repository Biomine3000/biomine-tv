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
import org.bm3k.abboe.objects.BusinessObjectMetadata;
import org.bm3k.abboe.senders.ContentVaultProxy;
import org.bm3k.abboe.senders.ContentVaultProxy.InvalidStateException;
import org.json.JSONArray;
import org.json.JSONObject;

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

    private static final String NODE_NAME = "java-A.B.B.O.E";
    
    public static final DateFormat DEFAULT_DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

    private ServerSocket serverSocket;
    private int serverPort;
    private String serverRoutingId;

    /** For sending welcome images */
    private ContentVaultProxy contentVaultProxy;

    /** all access to this client list should be synchronized on the ABBOEServer instance */
    private List<Client> clients;

    /** Shortcuts for clients, to be used for interactive server management only */
    private Map<Integer, Client> clientShortcuts;
    
    private State state;    

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
        this.serverRoutingId = Biomine3000Utils.generateId();
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
        Socket socket;
        BufferedInputStream is;
        OutputStream os;
        boolean subscribed = false;
        boolean senderFinished;
        boolean receiverFinished;
        /** Please do not call send of this client directly, even within this class, except in the one dedicated place */
        NonBlockingSender sender;
        BusinessObjectReader reader;
        ReaderListener readerListener;        
        Subscriptions subscriptions = new Subscriptions();
        boolean echo = false; // should echo objects back to sender?
        boolean closed;
        String routingId;     // primary routing id of the client
 
        /** actual name of client program, not including user or addr */
        String clientName;
        String user;
        /* Obtained by getRemoteSocketAddress() */
        String addr;
        /** Derived from clientName, user and addr */
        String name;                
        
        /** services implemented by client */
        LinkedHashSet<String> services = new LinkedHashSet<String>();
        ClientRole role;

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
        
        
        public synchronized void setClientName(String clientName) {
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

        /**
         * Caller needs to first ensure that client is willing to receive such a packet
         * by calling {@link #shouldSend(Client, BusinessObject)} or {@link #receiveEvents()}.
         * @param obj
         */
        private void send(BusinessObject bo) {
            bo.getMetadata().put("sender", NODE_NAME);
            if (bo.hasNature("error")) {
                log.error("Sending: "+bo);
            }
            else if (bo.hasNature("warning")) {
                log.warn("Sending: "+bo);
            }
            else {
                log.info("Sending: "+bo);
            }
            send(bo.toBytes());
        }

        /**
         * Send a warning message to client (natures=[warning,message], contenttype=plaintext, sender=java-A.B.B.O.E.) 
         * Sending is not conditional on subscriptions (they should be checked by this point if needed).
         **/
        private void sendWarning(String text) {                       
            log("Sending warning to client " + this + ": " + text);
            BusinessObject reply = BOB.newBuilder()
                    .natures("message", "warning")
                    .attribute("sender", NODE_NAME)
                    .payload(text).build();
            send(reply);
        }
        
        /**
         * Send a message to client (nature=message, contenttype=plaintext, sender=java-A.B.B.O.E.) 
         * Sending is not conditional on subscriptions (they should be checked by this point if needed).
         **/
        private void sendMessage(String text) {                                                           
            log("Sending message to client " + this + ": " + text);
            BusinessObject reply = BOB.newBuilder()
                    .natures("message")
                    .attribute("sender", NODE_NAME)
                    .payload(text).build();
            send(reply);
        }                

        /** should a object generated by the ABBOE be sent to this client? */
        public boolean shouldSend(BusinessObject bo) {
            return subscriptions.pass(bo);
        }
        
        /** should a object from a certain client be sent to this client (source only used for echo logic) */
        public boolean shouldSend(Client source, BusinessObject bo) {
            if (source == this && echo == false) {
                return false;
            }
            
            return subscriptions.pass(bo);
        }
                
       /**
        * Put object to queue of messages to be sent (to this one client) and return immediately.
        * Assume send queue has unlimited capacity.
        */
        private void send(byte[] packet) {
            if (senderFinished) {
                log.warn("No more sending business for client "+this);
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

            BusinessObjectMetadata meta = new BusinessObjectMetadata();
            meta.put("routing-id", routingId);
            sendToAllClients(this,
                    BOB.newBuilder()
                            .event(ROUTING_DISCONNECT)
                            .metadata(meta)
                            .payload("Client " + this + " disconnected").build()
                            
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

            BusinessObjectMetadata meta = new BusinessObjectMetadata();
            meta.put("routing-id", routingId);
            sendToAllClients(this,
                    BOB.newBuilder()
                            .event(ROUTING_DISCONNECT)
                            .metadata(meta)
                            .payload("Client " + this + " disconnected").build()
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
            
            log.info("Sending shutdown event to client: "+this);
            if (shouldSend(notification)) {
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
       
        public void sendPong(BusinessObject bo) {
            BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        
            if (bo.getMetadata().hasKey("id")) {
                metadata.put("in-reply-to", bo.getMetadata().getString("id"));
            }
        
            send(BOB.newBuilder().event(PONG).metadata(metadata).build());
        }

    }

    private void acceptSingleClient(Socket clientSocket) {
        Client client;

        try {
            client = new Client(clientSocket);

            // suggest registration, if client has not done so within a second of its registration...
            new SubscriptionCheckerThread(client).start();
            
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

    private class SubscriptionCheckerThread extends Thread {
        Client client;
        SubscriptionCheckerThread(Client client) {
            this.client = client;
        }

        public void run() {
            try {
                Thread.sleep(1000);
                if (state == ABBOEServer.State.SHUTTING_DOWN) return;
                if (!client.subscribed) {
                    log.warn("Client has not subscribed during the first second: "+client);
                    client.sendMessage("Please subscribe by sending a \""+ROUTING_SUBSCRIPTION+"\" event");
                }
            }
            catch (InterruptedException e) {
                log.error("SubscriptionCheckerThread interrupted");
            }

        }
    }

    /** Handle a services/register event */
    private void handleServicesRegisterEvent(Client client, BusinessObject bo) {
        BusinessObjectMetadata meta = bo.getMetadata();
        List<String> names = meta.getList("names");
        String name = meta.getString("name");        
                
        List<String> warnings = new ArrayList<String>();
        List<String> errors = new ArrayList<String>();
        if (name != null && names != null) {
            // client has decided to generously provide both name and names; 
            // let's as generously handle this admittably deranged request            
            warnings.add("It is rather pompous to provide both name and names; we have, however, decided to generously process your request including both");
            names = new ArrayList<String>(names);
            names.add(name);
        }
        else if (name != null && names == null) {
            names = Collections.singletonList(name);
        }
        else if (name == null && names != null) {
            // no action needed (names remains names)
        }
        else {
            // both null
            errors.add("No name nor names in services/register event");            
        }

        BusinessObjectMetadata replyMeta = new BusinessObjectMetadata();
        replyMeta.put("in-reply-to", bo.getMetadata().getString("id"));
        String message = null;
        if (errors.size() > 0) { 
            replyMeta.setNatures("message", "error");
            message = StringUtils.collectionToString(errors, ",");
        }
        else if (warnings.size() > 0) { 
            replyMeta.setNatures("message", "warning");
            message = StringUtils.collectionToString(warnings, ",");
        }        
        
        BusinessObject reply = BOB.newBuilder()
                .event(SERVICES_REGISTER_REPLY)
                .metadata(replyMeta)
                .payload(message) // may be null, in which case no payload
                .build();
        
        client.send(reply);        
               
        if (names != null) {
            client.registerServices(names);
        }
    }

    private void handleClientsListEvent(Client requestingClient, BusinessObject bo ) {
        
        BusinessObject reply;
        
        synchronized(ABBOEServer.this) {                        
            JSONArray clientsJSON = new JSONArray();
            for (Client client: clients) {
                JSONObject clientJSON = new JSONObject();
                if (client.clientName != null) {
                    clientJSON.put("client", client.clientName);
                }
                if  (client.user != null) { 
                    clientJSON.put("user", client.user);
                }
                clientJSON.put("routing-id", client.routingId);
                clientsJSON.put(clientJSON);
                log.info("clientsJSON in clients list reply: "+clientsJSON);
            }                        
            
            BusinessObjectMetadata replyMeta = new BusinessObjectMetadata();
            String requestId = bo.getMetadata().getString("id");
            if (requestId != null) {
                replyMeta.put("in-reply-to", requestId);
            }
            replyMeta.asJSON().put("clients", clientsJSON);
            replyMeta.asJSON().put("name", "clients");
            replyMeta.asJSON().put("request", "list");
            reply = BOB.newBuilder().event(SERVICES_REPLY).metadata(replyMeta).build();
        }

        requestingClient.send(reply);
    }
    
    /** handle a routing/subscribe event */
    private void handleRoutingSubscribeEvent(Client client, BusinessObject subscribeEvent) throws InvalidBusinessObjectMetadataException {
        BusinessObjectMetadata subscribeMeta = subscribeEvent.getMetadata();        
        ArrayList<String> errors = new ArrayList<>();
                
        // role
        String role = subscribeMeta.getString("role");
        if (role == null || role.equals("client")) {
            client.role = ClientRole.CLIENT;
        } else if (role.equals("server")) {            
            client.role = ClientRole.SERVER;
        } else {
            errors.add("Unknown role in "+ROUTING_SUBSCRIPTION.getEventName()+": "+role+". Setting role to CLIENT");
            client.role = ClientRole.CLIENT;
        }
        
        // routing id of client
        String routingIdFromClient = subscribeMeta.getString("routing-id");        
        if (client.role == ClientRole.CLIENT) {
            if (routingIdFromClient !=  null) {
                errors.add("A client should not specify a routing id; overridden by one generated by server");
            }
            client.routingId = Biomine3000Utils.generateId(client.addr);
        }
        else if (client.role == ClientRole.SERVER) {
            if (routingIdFromClient !=  null) {
                log.info("Using routing id specified by connected server: "+routingIdFromClient);
                client.routingId = routingIdFromClient;
            }
            else {
                errors.add("Server did not speficy a routing id; generating one...");
                client.routingId = Biomine3000Utils.generateId(client.addr);
            }                
        }
        else {
            throw new RuntimeException("Should not be possible: client role is: "+client.role);
       }        
                                    
        // Additional routing id's are possibly a deprecated feature, not high on to-do        
        if (subscribeMeta.getString("routing-ids") != null) {
            errors.add("List of additional routing-id:s not supported by java-ABBOE");
        }

        // Echo mode
        Boolean echo = subscribeMeta.getBoolean("echo");
        if (echo == null || echo == false) {
            client.echo = false;
        } else {
            client.echo = true;
        }

        if (subscribeMeta.hasKey("subscriptions")) {
            List<String> subscriptions = subscribeMeta.getList("subscriptions");
            client.subscriptions = new Subscriptions(subscriptions);        
        }
        else {
            // no subscriptions (perhaps, just perhaps this is valid)
        }

        BusinessObjectMetadata responseMetadata = new BusinessObjectMetadata();
        String subscribeEventId = subscribeMeta.getString("id");
        if (subscribeEventId != null ) {
            responseMetadata.put("in-reply-to", subscribeEventId);
        }
        responseMetadata.put("routing-id", client.routingId);  // the unique routing id of the client

        client.subscribed = true;
        
        BusinessObject response = BOB.newBuilder()
                .event(ROUTING_SUBSCRIBE_REPLY)
                .metadata(responseMetadata)
                .build();
        client.send(response);
                
        // send additional informative messages to client (non-events)
        String abboeUser = Biomine3000Utils.getUser();
        client.sendMessage("Welcome to this fully operational java-A.B.B.O.E., run by " + abboeUser);
        client.sendMessage("You made the following subscriptions: " + client.subscriptions.toStringList());
        client.sendMessage("Being narcistic: " + client.echo);                        
        if (Biomine3000Utils.isBMZTime()) {
            client.sendMessage("For relaxing times, make it Zombie time");
        }
                               
        if (contentVaultProxy.getState() == ContentVaultProxy.State.INITIALIZED_SUCCESSFULLY) {
            try {
                BusinessObject image = contentVaultProxy.sampleImage();
                if (client.shouldSend(image)) {
                    client.send(image);
                }
            }
            catch (InvalidStateException e) {
                log.error("Invalid state while getting content from vault", e);
            }
        }                                     
        
        // "register back", if server and if this is not already a back-registration by the other server
        if (client.role == ClientRole.SERVER  && !subscribeEvent.getMetadata().hasKey("in-reply-to")) {            
            BusinessObject returnSubscribeEvent = BOB.newBuilder()
                    .event(BusinessObjectEventType.ROUTING_SUBSCRIPTION)
                    .attribute("id", Biomine3000Utils.generateId())
                    .attribute("in-reply-to", subscribeEvent.getMetadata().getString("id"))
                    .attribute("routing-id", serverRoutingId)
                    .build();
            
            client.send(returnSubscribeEvent);
        }
        
        // notify other clients
        BusinessObjectMetadata notificationMeta = new BusinessObjectMetadata();
        notificationMeta.put("routing-id", client.routingId);

        if (client.role == ABBOEServer.ClientRole.SERVER) {                               
            notificationMeta.put("role", "server");
        }                               
            
        sendToAllClients(client,
                BOB.newBuilder()
                        .event(ROUTING_SUBSCRIBE_NOTIFICATION)
                        .metadata(notificationMeta)
                        .payload("Client " + client + " subscribed")                        
                        .build()
        );
    }
    
    private void handleClientJoinRequest(Client client, BusinessObject bo) throws InvalidBusinessObjectMetadataException {
        BusinessObjectMetadata meta = bo.getMetadata();
        String clientName = meta.getString("client");
        String user = meta.getString("user");       

        if (user == null) {
            log.warn("No user in register packet from {}", client);
        }
        if (clientName == null) {
            log.warn("No client in register packet from {}", client);
        }
        
        client.setClientName(clientName);
        client.setUser(user);                                                     

        StringBuffer msg = new StringBuffer("Registered you as \""+client.name);              

        BusinessObjectMetadata replyMeta = new BusinessObjectMetadata();
        String requestId = bo.getMetadata().getString("id");
        if (requestId != null) { 
            replyMeta.put("in-reply-to", requestId);
        }
        replyMeta.put("to", client.routingId);
        replyMeta.put("name", "clients");
        replyMeta.put("request", "join");        
        BusinessObject replyObj = BOB.newBuilder().event(SERVICES_REPLY).metadata(replyMeta).payload(msg).build();        
        client.send(replyObj);

        // TODO: in the current protocol, there is no event to notify this. However, only at this point do we have
        // human-readable identification info for the client...
        sendToAllClients(client,
                BOB.newBuilder()
                        .nature("message")
                        .attribute("sender", NODE_NAME)
                        .payload("Client " + client + " registered using clients/join service provided by java-ABBOE (no event nor nature has been specified for such an occurence)")                        
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
                    if (et == SERVICES_REQUEST) {                                            
                        String serviceName = bo.getMetadata().getString("name");
                        
                        if (serviceName.equals("clients")) {
                            String request = bo.getMetadata().getString("request");
                            
                            if (request.equals("join")) {
                                handleClientJoinRequest(client, bo);
                            }
                            else if (request.equals("leave")) {
                                client.sendWarning("Unhandled request: clients/join (request id: "+bo.getMetadata().get("id"));         
                            }
                            else if (request.equals("list")) {
                                handleClientsListEvent(client, bo);
                            }
                            else {
                                client.sendWarning("Unknown request to clients service: " + request + " (request id: "+bo.getMetadata().get("id"));
                            }
                        }
                        else {
                            client.sendWarning("Forwarding service requests not implemented. (service=" + serviceName+ " ;request id: "+bo.getMetadata().get("id"));        
                        }
                        
                        forwardEvent = false;
                    }
                    else if (et == SERVICES_REGISTER) {
                        handleServicesRegisterEvent(client, bo);
                        forwardEvent = false;
                    }
                    else if (et == ROUTING_SUBSCRIPTION) {
                        handleRoutingSubscribeEvent(client, bo);
                        forwardEvent = false;
                    }
                    else if (et == PING) {
                        client.sendPong(bo);
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
                log.info("Received content: {}", Biomine3000Utils.formatBusinessObject(bo));
                
                
//                if (bo.getPayload() != null &&
//                        bo.getMetadata().getType() == BusinessMediaType.PLAINTEXT.withoutParameters().toString()) {
//                    BusinessObject pto = BOB.newBuilder()
//                            .payload(bo.getPayload())
//                            .metadata(bo.getMetadata().clone())
//                            .build();
//                    log.info("Received content: {}", Biomine3000Utils.formatBusinessObject(pto));
//                }
//                else {
//                    log.info("Received content: {}", bo);
//                }
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

    private enum ClientRole {
        CLIENT,
        SERVER;
    }
    
    private enum State {
        NOT_RUNNING,
        ACCEPTING_CLIENTS,
        SHUTTING_DOWN;
    }

}