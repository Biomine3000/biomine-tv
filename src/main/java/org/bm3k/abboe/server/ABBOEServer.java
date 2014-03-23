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
 * Reads business objects from each neighbor, broadcasting back everything it reads
 * to all neighbors.
 *
 * Two dedicated threads will created for each neighbor, one for sending and one for reading {@link
 * org.bm3k.abboe.objects.BusinessObject}s.
 *
 * Once a neighbor closes its sockets outputstream (the inputstream of the server's socket),
 * the server stops sending to that neighbor and closes the socket.
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

    /** all access to this neighbor list should be synchronized on the ABBOEServer instance */
    private List<Neighbor> neighbors;

    /** Shortcuts for neighbors, to be used for interactive server management only */
    private Map<Integer, Neighbor> neighborShortcuts;
    
    private State state;    

    /** Generates a map (small int) => (neighbor) for later reference. */
    private synchronized Map<Integer, Neighbor> neighborShortcuts() {
        Map<Integer, Neighbor> map = new HashMap<Integer, Neighbor>();
        int i=0;
        for (Neighbor neighbor: neighbors) {
            map.put(++i, neighbor);
        }
        return map;
    }    

    /** Create server data structures and start listening */
    public ABBOEServer(int port) throws IOException {
        state = State.NOT_RUNNING;
        this.serverPort = port;
        this.serverRoutingId = Biomine3000Utils.generateId();
        serverSocket = new ServerSocket(serverPort);
        neighbors = new ArrayList<Neighbor>();
        log.info("Listening.");
        contentVaultProxy = new ContentVaultProxy();
        contentVaultProxy.addListener(new ContentVaultListener());
        contentVaultProxy.startLoading();
    }

    /** Send some random image from the content vault to all neighbors */
    private void sendImageToAllNeighbors() {
        synchronized(neighbors) {
            BusinessObject image;
            try {
                image = contentVaultProxy.sampleImage();
                for (Neighbor neighbor: neighbors) {
                    neighbor.send(image);
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
            // no action            
        }

        @Override
        public void loadedImage(String image) {
            // no action

        }

        @Override
        public void loadedAllImages() {
            sendImageToAllNeighbors();
        }
    }          
    
    /** return empty set if no clients */
    private List<Neighbor> listNeighborsWithRoutingId(String routingId) {
        List<Neighbor> result = Collections.emptyList();
        for (Neighbor neighbor: neighbors) {
            if (neighbor.routingId.equals(routingId)) {
                if (result.size() == 0) {
                    result = Collections.singletonList(neighbor);               
                }
                else if (result.size() == 1) { 
                    result = new ArrayList<>(result);
                    result.add(neighbor);
                }
                else {
                    result.add(neighbor);
                }
            }
        }
        return result;
    }
    
    /** return empty set if no connected servers */
    private List<Neighbor> listNeighboringServers() {
        List<Neighbor> result = Collections.emptyList();
        for (Neighbor neighbor: neighbors) {
            if (neighbor.role == Role.SERVER) {
                if (result.size() == 0) {
                    result = Collections.singletonList(neighbor);               
                }
                else if (result.size() == 1) { 
                    result = new ArrayList<>(result);
                    result.add(neighbor);
                }
                else {
                    result.add(neighbor);
                }
            }
        }
        return result;
    }
    
    /** Implemented by just calling {@link #forward()} with null src */
    private void sendServerGeneratedObject(BusinessObject bo) {
        forward(bo, null);
    }
    
    /**
     * Send an object not consumed by the ABBOE to all applicable neighbors.
     * 
     * All decisions on which neighbors to send to and all modifications to route attribute 
     * done within this method.
     * 
     * This method implements the algorithm given in 
     *  https://github.com/Biomine3000/protocol-specification/wiki/ABBOE-Protocol-Specification (section "Routing")
     *        
     * Should not block for long, as sending is done using a dedicated thread for each neighbor.
     * 
     * @param src neighbor from where this object originated from. Null, if this message originates from this very ABBOE.
     */
    private synchronized void forward(BusinessObject bo, Neighbor src) {        
        
        String to = bo.getMetadata().getString("to");
        List<Neighbor> potentialDestinations = new ArrayList<>(); 
        if (to == null) {
            // if these is no "to" attribute, the object is forwarded to all servers and clients as per their subscriptions.
            for (Neighbor neighbor: neighbors) {
                if (neighbor.subscriptions.pass(bo)) {
                    potentialDestinations.add(neighbor);
                }
            }
        }
        else {
            // If there is a "to" attribute, the object is forwarded only directly connected clients whose routing ids match 
            // the ones in "to". In case no clients with routing ids in "to" are directly connected, the object is forwarded to all servers.
            List<Neighbor> clientsWithMatchingRoutingId = listNeighborsWithRoutingId(to);
            if (clientsWithMatchingRoutingId.size() > 0) {
                potentialDestinations = clientsWithMatchingRoutingId;
            }
            else {
                potentialDestinations = listNeighboringServers();
            }            
        }        

        // (potential) destinations have been resolved
        if (potentialDestinations.size() == 0) {
            return; // no action needed ( a trivial routing dead end, supposedly )
        }
        
        // UPDATED VERSION OF TEXT IN SPECS (should copy there...):
        // handle route attribute (creating / updating route), eliminate destinations already on route)
        // The route array in an object must always be checked against forwarding destinations. Objects must not 
        // be forwarded to nodes whose routing-id appears in the route array. If the route array does not exist, 
        // the forwarding server must create it as containing the originating node's routing-id (only for 
        // clients; for servers, not having route array id is considered an error). If the object
        // originates from this java-ABBOE, add only the servers routing id there
        // The forwarding server also adds its own routing-id to the end of route, if it is not already in the array.
        BusinessObjectMetadata meta = bo.getMetadata(); 
        List<String> route = meta.getList("route");
        if (route == null) {
            // create one           
            route = new ArrayList<>();
            if (src != null) route.add(src.routingId); // coming from adjacent client
            route.add(serverRoutingId);
        }
        else {
            if (!route.contains(serverRoutingId)) {
                route.add(serverRoutingId);
            }
        }
        
        log.info("Route of object to be sent: "+route);
        
        // Objects must not be forwarded to any destination whose routing-id is in route.
        ArrayList<Neighbor> destinations = new ArrayList<>(potentialDestinations.size());
        for (Neighbor destination: potentialDestinations) {
            log.info("Checking potential destination: "+destination);
            if (!route.contains(destination.routingId)) {
                log.info("Route does not contain destination: "+destination);
                destinations.add(destination);                
            }
            else {
                log.info("Route already contains destination, not sending: "+destination);
            }
        }
        
        // final list of destinations at hand        
        if (destinations.size() == 0) {
            return; // all destinations removed due to cycle elimination 
        }
        
        // After forwarding destinations have been determined as described above, but before the object is forwarded, the 
        // forwarding server must add to route the routing-id of each other server the object is being forwarded to. 
        // This prevents other servers from forwarding the same object to each other via a different route (e.g., A→B-C, A→C→B).
        
        // note: it is not strictly necessary to add route to packets going to clients, but let's do that anyway for now
        for (Neighbor destination: destinations) {
            route.add(destination.routingId);
        }
        
        bo.getMetadata().putStringArray("route",  route);
        
        for (Neighbor neighbor: destinations) { 
            // legacy support: modify metadata for each neighbor to exclude the neighbor itself from the route
            neighbor.send(makeCopyWithOneIdRemovedFromRoute(bo, neighbor.routingId));
            
            // neighbor.send(bo); // enable after removing legacy support above
        }        
    }

    /**
     * Should never return. Only way to exit is through neighbor request "stop",
     * {@link org.bm3k.abboe.common.UnrecoverableServerException}, or stop signal.
     */
    private void mainLoop() {

        state = State.ACCEPTING_CLIENTS;

        while (state == State.ACCEPTING_CLIENTS) {
            log.info("Waiting for neighbor...");

            try {
                Socket neighborSocket = serverSocket.accept();
                if (state == State.ACCEPTING_CLIENTS) {
                    log.info("Neighbor connected from {}", neighborSocket.getRemoteSocketAddress());
                    acceptSingleNeighbor(neighborSocket);
                }
                else {
                    // while waiting, someone seems to have changed our policy to "not accepting neighbors any more"
                    // TODO: more graceful way of rejecting this connection
                    log.info("Not accepting neighbor from: {}", neighborSocket.getRemoteSocketAddress());
                    neighborSocket.close();
                }
            }
            catch (IOException e) {
                if (state == State.ACCEPTING_CLIENTS) {
                    log.error("Accepting a neighbor failed", e);
                }
                // else we are shutting down, and failure is to be expected to result from server socket having been closed 
            }
        }

        log.info("Finished ABBOE main loop");
    }

    /** Actually, a connection to a neighbor */
    private class Neighbor implements NonBlockingSender.Listener {                       
        Socket socket;
        BufferedInputStream is;
        OutputStream os;
        boolean subscribed = false;
        boolean senderFinished;
        boolean receiverFinished;
        /** Please do not call send of this neighbor directly, even within this class, except in the one dedicated place */
        NonBlockingSender sender;
        BusinessObjectReader reader;
        ReaderListener readerListener;        
        Subscriptions subscriptions = new Subscriptions();        
        boolean closed;
        String routingId;     // primary routing id of the neighbor
 
        /** actual name of neighbor program, not including user or addr */
        String neighborName;
        String user;
        /* Obtained by getRemoteSocketAddress() */
        String addr;
        /** Derived from neighborName, user and addr */
        String name;                
        
        /** services implemented by neighbor */
        LinkedHashSet<String> services = new LinkedHashSet<String>();
        Role role;

        Neighbor(Socket socket) throws IOException {
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

            log("Neighbor connected from "+socket.getInetAddress());
            synchronized(ABBOEServer.this) {
                neighbors.add(this);
            }
        }

        private void initName() {
            StringBuffer buf = new StringBuffer();
            if (neighborName != null) {
                buf.append(neighborName+"-");
            }
            if (user != null) {
                buf.append(user+"-");
            }
            buf.append(addr);
            name = buf.toString();
        }
        
        
        public synchronized void setNeighborName(String neighborName) {
            this.neighborName = neighborName;
            initName();
            sender.setName("sender-"+this.name);
            reader.setName("reader-"+this.name);
        }
                       
        
        /**
         * Forward a business object to a neighbor. It must already be verified that the object
         * indeed is to be routed to said node, according to possible to-attribute and node role.
         * The forwarding might still be cancelled, e.g. to avoid cyclic routing. This method
         * also modifies or sets the route attribute.
         * 
         * Specs:
         *  The route array in an object must always be checked against forwarding destinations. 
         *  Objects must not be forwarded to nodes whose routing-id appears in the route array.
         *  If the route array does not exist, the forwarding server must create it as containing 
         *  the originating node’s routing-id (if any), and the forwarding server’s own routing-id (
         *  in that order).'
         *
         * After forwarding destinations have been determined as described above, but before the object
         *  is forwarded, the forwarding server must add to route the routing-id of each other 
         *  server the object is being forwarded to. This prevents other servers from forwarding 
         *  the same object to each other via a different route (e.g., A→B-C, A→C→B).
         * 
         * Note that route is only relevant 
         * to forwarded objects. Note that route is added also to objects being sent to clients, even if only servers are 
         * expected to use them. That is to avoid the need of duplicating the object to be sent
         * (it needs to be immutable during the sending, as different clients process
         * the sending concurrently).
         * 
         * Specs: When a server forwards an object it appends its own routing id to the route list. 
         * If the list does not exist, it is first created with the routing-id of the originating 
         * client before the server’s own id. Objects originating from a server only have the server’s 
         * own id in route.
         * 
         * Invariant: it has been verified beforehand that if the object originates from a server, 
         * it has the route attribute.
         * */
        /*
        public void forward(BusinessObject bo) {
            List<String> 
            if (role == NeighborRole.SERVER) {                     
                route = bo.getMetadata().asJSON().optJSONArray("route");
            }
            else if (role == CLIENT) {                        
                if (bo.getMetadata().hasKey("route")) {
                    client.sendError("A client should not specify a route", bo.getMetadata().getString("id"));
                    return;
                }
                route = new JSONArray();
                bo.getMetadata().put("route", route);
                break;
            }
            // otherwise not consider subscribed
        }
                        
*/
        
        private synchronized void setUser(String user) {
            this.user = user;
            initName();
            sender.setName("sender-"+name);
            reader.setName("reader-"+name);
        }


        /**
         * Send a warning message to a neighbor (natures=[warning,message], contenttype=plaintext, sender=java-A.B.B.O.E.) 
         * Sending is not conditional on subscriptions (they should be checked by this point if needed).
         **/
        private void sendWarning(String text) {
            BusinessObject reply = BOB.newBuilder()
                    .natures("message", "warning")
                    .attribute("to", this.routingId)
                    .attribute("sender", NODE_NAME)
                    .payload(text).build();
            send(reply);
        }
        
                
        /**
         * Send an error reply to processing of an event RECEIVED FROM THIS NEIGHBOR.
         * 
         * See leronen-tv-todo for specs!
         * 
         * Reply will always be an event=error. It will also have natures "message", "error".
         * 
         * Send a error message to neighbor (natures=[error,message], contenttype=plaintext, sender=java-A.B.B.O.E.) 
         * Sending is not conditional on subscriptions (they should be checked by this point if needed).
         **/
        private void sendErrorReply(String errorMessage, BusinessObject receivedObject) {                       
            String inReplyTo = receivedObject.getMetadata().getString("id");
                        
            if (inReplyTo == null) {
                log.warn("Sending an error reply to an object with unknown id: "+receivedObject);
            }
            
            BusinessObject reply = BOB.newBuilder()
                    .event(BusinessObjectEventType.ERROR)
                    .natures("message", "error")                    
                    .attribute("in-reply-to", inReplyTo)
                    .attribute("sender", NODE_NAME)
                    .payload(errorMessage).build();
            
            String to;
            
            if (this.role == Role.CLIENT) {
                to = this.routingId;                                            
            }
            else {
                JSONArray route = receivedObject.getMetadata().asJSON().getJSONArray("route");                
                if (route == null) {
                    // no route in object from server; actually this should mean that we are sending error reply namely to said server
                    // (a horrendous implicit assumption)), and thus we can just use that very server as "to"
                    to = this.routingId;
                }
                else {
                    to = route.getString(0);
                }                
            }
            
            reply.getMetadata().put("to", to);
             
            sendServerGeneratedObject(reply);
        }
        
        /**
         * Send a message to client (nature=message, contenttype=plaintext, sender=java-A.B.B.O.E.) 
         * Sending is not conditional on subscriptions or routing (they should be checked by this point if needed).
         **/
        private void sendMessage(String text) {                                                           
            // log("Sending message to client " + this + ": " + text);
            BusinessObject reply = BOB.newBuilder()
                    .natures("message")
                    .attribute("sender", NODE_NAME)
                    .payload(text).build();
            send(reply);
        }                                      
        
        /**
         * Put object to queue of messages to be sent (to this one client) and return immediately.
         * Sending is not conditional on subscriptions or routing (they should be checked by this point if needed).
         * Assume send queue has unlimited capacity.
         */
        private void send(BusinessObject bo) {
          if (senderFinished) {
              log.warn("No more sending business for client "+this);
              return;
          }

          if (bo.hasNature("error")) {
              log.error("Sending to "+this+" : "+bo);
          }
          else if (bo.hasNature("warning")) {
              log.warn("Sending to: "+this+" : "+bo);
          }
          else {
              log.info("Sending to: "+this+" : "+bo);
          }
          
          try {
              sender.send(bo.getMetadata().toString().getBytes("UTF-8"), false);
              byte[] payload = bo.getPayload();
              if (payload != null) {              
                  sender.send(Biomine3000Utils.NULL_BYTE_ARRAY, false);                 
                  sender.send(bo.getPayload(), true);
              }
              else {
                  sender.send(Biomine3000Utils.NULL_BYTE_ARRAY, true);
              }
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
         * Forcibly terminate a connection with a neighbor (supposedly called when
         * neighbor steadfastly refuses to close the connection when requested)
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
                neighbors.remove(this);
                closed = true;
                if (state == State.SHUTTING_DOWN && neighbors.size() == 0) {
                    // last neighbor closed and we are shutting down, finalize shutdown sequence...
                    log.info("No more neighbors, finalizing shutdown sequence...");
                    finalizeShutdownSequence();
                }
            }
                                           
            sendServerGeneratedObject(makeRoutingDisconnectEvent());                                                                                      
        }

        /** Make event signaling the departure of this neighbor */
        private BusinessObject makeRoutingDisconnectEvent() {
            return BOB.newBuilder()
                    .event(ROUTING_DISCONNECT)
                    .attribute("routing-id", routingId)                            
                    .payload(""+this.role + " " + this + " disconnected").build();
        }
        
        /**
         * Gracefully dispose of a single neighbor after ensuring both receiver and neighbor
         * have finished
         */
        private void doClose() {
            if (closed) {
                error("Attempting to close a connection with neighbor " + this + " multiple times", null);
            }

            log("Closing connection with "+this.role+": "+this);

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
                neighbors.remove(this);
                closed = true;
                if (state == State.SHUTTING_DOWN && neighbors.size() == 0) {
                    // last neighbor closed, no more neighbors, finalize shutdown sequence...
                    log.info("No more neighbors, finalizing shutdown sequence...");
                    finalizeShutdownSequence();
                }
            }
            
            sendServerGeneratedObject(makeRoutingDisconnectEvent());
        }
        
        
        @Override
        public void senderFinished() {
            doSenderFinished();
        }

        /**
         * Initiate shutting down of proceedings with this neighbor.
         *
         * Actually, just initiate closing of output channel. On noticing this,
         * neighbor should close its socket, which will then be noticed on this server
         * as a {@link BusinessObjectReader.Listener#noMoreObjects()} notification from the {@link #reader}.
         */
        public void initiateClosingSequence(BusinessObject notification) {
            log.info("Initiating closing sequence for neighbor: "+this);
            
            log.info("Sending shutdown event to neighbor: "+this);
            if (subscriptions.pass(notification)) {
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
                log.error("Failed closing socket output after finishing neighbor", e);
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

            // request stop of neighbor
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

    /**
     * "clone" a business object, with the only difference being in the route attribute. In practice,
     * only metadata is cloned, the payload (if any) can be recycled as is.
     * 
     * This is only needed if different route attribute is needed for objects sent to different neighbors
     * (in final implementation, this should not be the case).
     */
    private BusinessObject makeCopyWithOneIdRemovedFromRoute(BusinessObject bo, String idToRemove) {
        List<String> oldRoute = bo.getMetadata().getList("route");
        ArrayList<String> newRoute = new ArrayList<>(oldRoute.size()-1);
        for (String id: oldRoute) {
            if (!id.equals(idToRemove)) {
                newRoute.add(id);
            }
        }
        BusinessObjectMetadata metaClone = bo.getMetadata().clone();
        metaClone.putStringArray("route", newRoute);
        return BOB.newBuilder()
                .metadata(metaClone)
                .payload(bo.getPayload())
                .build();                                
    }
    
    private void acceptSingleNeighbor(Socket neighborSocket) {
        Neighbor neighbor;

        try {
            neighbor = new Neighbor(neighborSocket);

            // suggest registration, if neighbor has not done so within a second of its registration...
            new SubscriptionCheckerThread(neighbor).start();
            
        }
        catch (IOException e) {
            log.error("Failed creating streams on socket", e);
            try {
                neighborSocket.close();
            }
            catch (IOException e2) {
                // failed even this, no further action possible
            }
            return;
        }

        neighbor.startReaderThread();
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
                sendImageToAllNeighbors();
            }
            else if (line.equals("neighbors") || line.equals("n")) {
                neighborShortcuts = neighborShortcuts();
                for (Integer key: neighborShortcuts.keySet()) {
                    System.out.println(key+": "+neighborShortcuts.get(key).name);
                }
            }
            else if (line.equals("clients") || line.equals("c")) {
                neighborShortcuts = neighborShortcuts();
                for (Integer key: neighborShortcuts.keySet()) {
                    Neighbor n = neighborShortcuts.get(key);
                    if (n.role == Role.CLIENT) {
                        System.out.println(key+": "+n.name);
                    }
                }
                
            }
            else if (line.startsWith("close ") || line.startsWith("c ")) {
                // this is the end for one neighbor
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
                if (neighborShortcuts == null) {
                    log.error("No client map!");
                    continue;
                }
                int shortcut = Integer.parseInt(shortcutStr);

                Neighbor client = neighborShortcuts.get(shortcut);
                if (client == null) {
                    log.error("No such client shortcut: {}", shortcut);
                }
                log.info("Closing connection to client: {}", client);
                String admin  = Biomine3000Utils.getUser();
                               
                BusinessObject closeNotification = BOB.newBuilder()
                        .payload("ABBOE IS GOING TO CLOSE THIS CONNECTION NOW (as requested by the ABBOE adminstrator, "+admin+")")
                        .event(ABBOE_CLOSE_NOTIFY).build();
                client.initiateClosingSequence(closeNotification);

                NeighborShutdownThread cst = new NeighborShutdownThread(client);
                cst.start();

            }
            else {
                // just a message to be broadcast
                sendServerGeneratedObject(BOB.newBuilder().natures("message").payload(line).build());
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

        if (neighbors.size() > 0) {
            for (Neighbor client: neighbors) {
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
                           StringUtils.collectionToString(neighbors,", ")+
                		  "; forcing shutdown...");
                finalizeShutdownSequence();
            }
            catch (InterruptedException e) {
                log.error("Shutdownthread interrupted");
            }
        }
    }

    private class NeighborShutdownThread extends Thread {
        Neighbor client;
        NeighborShutdownThread(Neighbor client) {
            this.client = client;
        }
        public void run() {
            try {
                Thread.sleep(3000);
                if (!client.closed) {
                    log.error("Neighbor "+client.name+" has failed to shut down properly, forcing shutdown...");
                    client.forceClose();
                }
            }
            catch (InterruptedException e) {
                log.error("Shutdownthread for client: "+client.name+" interrupted, connection to client has not been shutdown");
            }
        }
    }

    private class SubscriptionCheckerThread extends Thread {
        Neighbor client;
        SubscriptionCheckerThread(Neighbor client) {
            this.client = client;
        }

        public void run() {
            try {
                Thread.sleep(1000);
                if (state == ABBOEServer.State.SHUTTING_DOWN) return;
                if (!client.subscribed) {
                    log.warn("Neighbor has not subscribed during the first second: "+client);
                    client.sendMessage("Please subscribe by sending a \""+ROUTING_SUBSCRIPTION+"\" event");
                }
            }
            catch (InterruptedException e) {
                log.error("SubscriptionCheckerThread interrupted");
            }

        }
    }

    /**
     * Handle warnings by adding nature "warning", and setting warnings to attribute "warnings"
     * as an array. It remains unclear, whether such warnings should be put to payload at all. 
     * 
     * @return message
     */
    private void addWarnings(BusinessObject bo, List<String> warnings) {
        
        if (warnings.size() == 0) {
            return;
        }
                                                                               
        bo.getMetadata().addNature("warning");
                 
        for (String warning: warnings) {                       
            bo.getMetadata().addWarning(warning);    
        }                                    
        
    }
   
    
    /**
     * Handle a services/register event. TODO: current implementation just keeps track of services for each 
     * neighbor. This should probably be done in a separate service registry service (?) but then again,
     * how to ensure that each connected component of the ABBOE network has its own service registry?
     * Rather then keep track of services in each ABBOE, such as, but not limited to, this one.
     * 
     * TODO: the present implementation dates from the time when there was no routing support.
     * Consequently, it can only keep track of services for immediate neighbors...
     *   TODO: to implement this service sensibly, we indeed would have to implement a complete client registry,
     *   which has to be distinct from the neighbor registry currently implemented...
     */
    private void handleServicesRegisterEvent(Neighbor client, BusinessObject bo) {
        if (client.role == Role.SERVER) {
            client.sendErrorReply("services/register events currently supported only for neighboring clients", bo);
            return;
        }        
        
        BusinessObjectMetadata meta = bo.getMetadata();
        List<String> names = meta.getList("names");
        String name = meta.getString("name");        
                
        List<String> warnings = new ArrayList<String>();        
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
            client.sendErrorReply("No name nor names in services/register event", bo);
            return;
        }

        BusinessObjectMetadata replyMeta = new BusinessObjectMetadata();
        replyMeta.put("in-reply-to", bo.getMetadata().getString("id"));         
        
        if (names != null) {
            client.registerServices(names);
        }
        
        BusinessObject reply = BOB.newBuilder()
                .event(SERVICES_REGISTER_REPLY)
                .attribute("in-reply-to", bo.getMetadata().getString("id"))
                .payload("Registered services to "+ABBOEServer.this+": "+StringUtils.collectionToString(names))
                .build();
        
        addWarnings(reply, warnings);
        
        client.send(reply);        
               
        
    }

    /**
     * Todo: should the implemented client registry service also include servers? What prevents servers from registering as clients,
     * so probably this should not matter.     
     */
    private void handleClientsListEvent(Neighbor requestingNeighbor, BusinessObject bo ) {
        
        BusinessObject reply;
        
        synchronized(ABBOEServer.this) {                        
            JSONArray neighborsJSON = new JSONArray();
            for (Neighbor neighbor: neighbors) {
                JSONObject neighborJSON = new JSONObject();
                if (neighbor.neighborName != null) {
                    neighborJSON.put("client", neighbor.neighborName);
                }
                if  (neighbor.user != null) { 
                    neighborJSON.put("user", neighbor.user);
                }
                neighborJSON.put("routing-id", neighbor.routingId);
                neighborsJSON.put(neighborJSON);
                log.info("neighborsJSON in neighbors list reply: "+neighborsJSON);
            }                        
                        
            reply = BOB.newBuilder()
                    .attribute("in-reply-to", bo.getMetadata().getString("id"))
                    .attribute("clients", neighborsJSON)
                    .attribute("name", "clients")
                    .attribute("request", "list")
                    .event(SERVICES_REPLY)
                    .build();
                    
        }

        requestingNeighbor.send(reply);
    }
    
    /** handle a routing/subscribe event */
    private void handleRoutingSubscribeEvent(Neighbor neighbor, BusinessObject subscribeEvent) throws InvalidBusinessObjectMetadataException {
        BusinessObjectMetadata subscribeMeta = subscribeEvent.getMetadata();                
        ArrayList<String> warnings = new ArrayList<>();
                
        // role
        String role = subscribeMeta.getString("role");
        if (role == null || role.equals("neighbor")) {
            neighbor.role = Role.CLIENT;
        } else if (role.equals("server")) {            
            neighbor.role = Role.SERVER;
        } else {
            warnings.add("Unknown role in "+ROUTING_SUBSCRIPTION.getEventName()+": "+role+". Setting role to CLIENT");
            neighbor.role = Role.CLIENT;
        }
        
        // routing id of neighbor
        String routingIdFromNeighbor = subscribeMeta.getString("routing-id");        
        if (neighbor.role == Role.CLIENT) {
            if (routingIdFromNeighbor !=  null) {
                warnings.add("A client should not specify a routing id; overridden by one generated by server");
            }
            neighbor.routingId = Biomine3000Utils.generateId(neighbor.addr);
        }
        else if (neighbor.role == Role.SERVER) {
            if (routingIdFromNeighbor !=  null) {
                log.info("Using routing id specified by connected server: "+routingIdFromNeighbor);
                neighbor.routingId = routingIdFromNeighbor;
            }
            else {
                neighbor.sendErrorReply("No routing id in subscription from server", subscribeEvent);
                return;
            }                
        }
        else {
            throw new RuntimeException("Should not be possible: neighbor role is: "+neighbor.role);
       }        
              
        String subscriptionId = subscribeEvent.getMetadata().getString("id");
        if (neighbor.role == Role.SERVER && subscriptionId == null) {
            neighbor.sendErrorReply("No id in subscription from server", subscribeEvent);
            return;
        }
        
        // Additional routing id's are possibly a deprecated feature, not high on to-do        
        if (subscribeMeta.getString("routing-ids") != null) {
            warnings.add("List of additional routing-id:s not presently supported by java-ABBOE");
        }

        if (subscribeMeta.hasKey("echo")) {
            warnings.add("Ignoring deprecated attribute echo"); 
        }
        
        if (subscribeMeta.hasKey("subscriptions")) {
            List<String> subscriptions = subscribeMeta.getList("subscriptions");
            neighbor.subscriptions = new Subscriptions(subscriptions);        
        }
        else {
            // no subscriptions (perhaps, just perhaps this is valid)
            warnings.add("No subscriptions specified, nothing shall be sent");
        }               
               
        neighbor.subscribed = true;
        
        BusinessObject response = BOB.newBuilder()
                .event(ROUTING_SUBSCRIBE_REPLY)
                .attribute("in-reply-to", subscribeMeta.getString("id"))
                .attribute("routing-id", neighbor.routingId)                
                .build();
        
        addWarnings(response, warnings); warnings = null;
        neighbor.send(response);
           
        // "register back", if server and if this is not already a back-registration by a server contacted by us earlier                
        if (neighbor.role == Role.SERVER  && !subscribeEvent.getMetadata().hasKey("in-reply-to")) {            
            BusinessObject returnSubscribeEvent = BOB.newBuilder()
                    .event(BusinessObjectEventType.ROUTING_SUBSCRIPTION)
                    .attribute("id", Biomine3000Utils.generateId())
                    .attribute("in-reply-to", subscriptionId)
                    .attribute("routing-id", serverRoutingId)
                    .build();
            
            neighbor.send(returnSubscribeEvent);
        }
        
        // send additional complimentary messages to clients (but not servers) 
        if (neighbor.role == Role.CLIENT ) {
            String abboeUser = Biomine3000Utils.getUser();
            neighbor.sendMessage("Welcome to this fully operational java-A.B.B.O.E., run by " + abboeUser);
            neighbor.sendMessage("You made the following subscriptions: " + neighbor.subscriptions.toStringList());
            if (Biomine3000Utils.isBMZTime()) {
                neighbor.sendMessage("For relaxing times, make it Zombie time");
            }
        }
                               
        if (contentVaultProxy.getState() == ContentVaultProxy.State.INITIALIZED_SUCCESSFULLY && neighbor.role == Role.CLIENT) {
            // send a raw image as a token of goodwill (not to competing servers, naturally)
            try {
                BusinessObject image = contentVaultProxy.sampleImage();
                if (neighbor.subscriptions.pass(image)) {
                    neighbor.send(image);
                }
            }
            catch (InvalidStateException e) {
                log.error("Invalid state while getting content from vault", e);
            }
        }                                     
        
        //         
        // notify other neighbors
        BusinessObjectMetadata notificationMeta = new BusinessObjectMetadata();
        notificationMeta.put("routing-id", neighbor.routingId);

        if (neighbor.role == ABBOEServer.Role.SERVER) {                               
            notificationMeta.put("role", "server");
        }                               
            
        sendServerGeneratedObject(
                BOB.newBuilder()
                    .event(ROUTING_SUBSCRIBE_NOTIFICATION)
                    .attribute("routing-id", neighbor.routingId)
                    .attribute("role", neighbor.role.name)
                    .payload("Neighbor " + neighbor + " subscribed").build());                    
                
    }
    
    private void handleClientJoinRequest(Neighbor neighbor, BusinessObject bo) throws InvalidBusinessObjectMetadataException {
        BusinessObjectMetadata meta = bo.getMetadata();
        String clientName = meta.getString("client");
        String user = meta.getString("user");
        List<String> warnings = new ArrayList<>();

        if (user == null) {
            warnings.add("No attribute user in register clients/register event");            		
        }
        if (clientName == null) {
            warnings.add("No attribute client in clients/register event");
        }
        
        neighbor.setNeighborName(clientName);
        neighbor.setUser(user);                                                     
                     
        BusinessObjectMetadata replyMeta = new BusinessObjectMetadata();
        String requestId = bo.getMetadata().getString("id");
        if (requestId != null) { 
            replyMeta.put("in-reply-to", requestId);
        }
        replyMeta.put("to", neighbor.routingId);
        replyMeta.put("name", "clients");
        replyMeta.put("request", "join");        
        BusinessObject replyObj = BOB.newBuilder()
                .event(SERVICES_REPLY)
                .attribute("in-reply-to", requestId)
                .attribute("to", neighbor.routingId)
                .attribute("name", "clients")
                .attribute("request", "join")                
                .natures("message")
                .payload("Registered you as \""+neighbor.name)
                .build();        
        neighbor.send(replyObj);

        // TODO: in the current protocol, there is no event to notify this. However, only at this point do we have
        // human-readable identification info for the neighbor, which warrants that a broadcast take place
        BusinessObject notification = BOB.newBuilder()
                .nature("message")
                .attribute("sender", NODE_NAME)                        
                .payload(""+neighbor.role + " " + neighbor + " registered using clients/join service provided by java-ABBOE (no event nor nature has been specified for such an occurence)")                        
                .build();
        sendServerGeneratedObject(notification);
    }
    
    
    /** Listens to a single dedicated reader thread reading objects from the input stream of a single neighbor */
    private class ReaderListener implements BusinessObjectReader.Listener {
        Neighbor source;

        ReaderListener(Neighbor neighbor) {
            this.source = neighbor;
        }

        @Override
        public void objectReceived(BusinessObject bo) {
            if (bo.isEvent()) {
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();                                                                                                                                                                       
                                                
                // assert that route is set for server-originating object, and not set for client-originating ones.
                if (source.role != null) {   
                    switch (source.role) {
                    case SERVER:
                        if (!bo.getMetadata().hasKey("route")) {
                            source.sendErrorReply("No route in object from server", bo);
                            return;
                        }
                        break;
                    case CLIENT:                        
                        if (bo.getMetadata().hasKey("route")) {
                            source.sendErrorReply("A client should not specify a route", bo);
                            return;
                        }
                        break;
                    }
                }
                
                boolean forwardEvent = true;  // does this event need to be sent to other neighbors?
                if (et != null) {
                    log.info("Received event from {} : {} ", source, bo);                    
                    if (et == SERVICES_REQUEST) {                                            
                        String serviceName = bo.getMetadata().getString("name");
                        
                        if (serviceName.equals("clients")) {
                            String request = bo.getMetadata().getString("request");
                            
                            if (request.equals("join")) {
                                handleClientJoinRequest(source, bo);
                            }
                            else if (request.equals("leave")) {
                                source.sendWarning("Unhandled request: clients/leave (request id: "+bo.getMetadata().get("id"));         
                            }
                            else if (request.equals("list")) {
                                handleClientsListEvent(source, bo);
                            }
                            else {
                                source.sendWarning("Unknown request to clients service: " + request + " (request id: "+bo.getMetadata().get("id"));
                            }
                        }
                        else {
                            source.sendWarning("Forwarding service requests not implemented. (service=" + serviceName+ " ;request id: "+bo.getMetadata().get("id"));        
                        }
                        
                        forwardEvent = false;
                    }
                    else if (et == SERVICES_REGISTER) {
                        handleServicesRegisterEvent(source, bo);
                        forwardEvent = false;
                    }
                    else if (et == ROUTING_SUBSCRIPTION) {
                        handleRoutingSubscribeEvent(source, bo);
                        forwardEvent = false;
                    }
                    else if (et == PING) {
                        source.sendPong(bo);
                        forwardEvent = false;
                    }
                    else {
                        log.info("Received known event which this ABBOE implementation does not handle: {}", bo);
                    }
                }
                else {
                    log.info("Received unknown event: {}", bo.getMetadata().getEvent());
                }

                // forward the event if needed 
                if (forwardEvent) {
                    log.info("Forwarding event to all clients...");
                    ABBOEServer.this.forward(bo, source);
                }
            }
            else {
                // not an event, assume mythical "content"
                log.info("Received content: {}", Biomine3000Utils.formatBusinessObject(bo));
                                
                ABBOEServer.this.forward(bo, source);
            }
        }

        @Override
        public void noMoreObjects() {
            log.info("connectionClosed (neighbor closed connection).");
            source.doReceiverFinished();
        }

        private void handleException(Exception e) {
            if (e.getMessage() != null && e.getMessage().equals("Connection reset")) {
                log.info("Connection reset by neighbor: "+this.source);
            }
            else {
                log.error("Exception while reading objects from neighbor " + source, e);
            }
            source.doReceiverFinished();
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
            log.error("Connection reset by neighbor: {}", this.source);
            source.doReceiverFinished();
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

    private enum Role {        
        CLIENT("client"),
        SERVER("server");
        
        String name;
        
        Role(String name) {
            this.name = name;            
        }
        
        public String toString() {
            return name;
        }
        
    }
    
    private enum State {
        NOT_RUNNING,
        ACCEPTING_CLIENTS,
        SHUTTING_DOWN;
    }
    
    public String toString() {
        return "java-ABBOE";        
    }

}