package org.bm3k.abboe.tv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.*;
import org.json.JSONArray;
import org.json.JSONException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrivialClient {
    private final Logger log = LoggerFactory.getLogger(TrivialClient.class);
              
    private static final ClientParameters CLIENT_PARAMS =
            new ClientParameters("TrivialClient", new Subscriptions("text/plain"), false);
    
    private ABBOEConnection connection;
    private boolean stopYourStdinReading = false;    
    private SystemInReader systemInReader;    
    private String user; 
    
    /** Note that superclasses also have their own state related to the connection to the server */
    private StdinReaderState stdinReaderState;
    
    /** Call {@link startMainReadLoop()} to perform actual processing */
    public TrivialClient(Socket socket, String user) throws IOException, JSONException {        
        this.stdinReaderState = StdinReaderState.NOT_YET_READING;
        ClientParameters clientParams = new ClientParameters(CLIENT_PARAMS);        
        this.user = user;
        this.connection = new ABBOEConnection(clientParams, socket);
        this.connection.init(new ObjectHandler());
        this.connection.sendClientListRequest();
    }
       
    /** Start a SystemInReader thread */
    private void startMainReadLoop() {
        systemInReader = new SystemInReader();
        systemInReader.start();
    }
    
    private class SystemInReader extends Thread {
        public void run() {
            try {
                stdInReadLoop();
            }
            catch (IOException e)  {
                log.error("IOException in SystemInReader", e);
                log.info("Requesting closing output if needed...");
                connection.requestCloseOutputIfNeeded();
            }
        }
    }
    
    /**
     * FOO: it does not seem to be possible to interrupt a thread waiting on system.in, even
     * by closing System.in... Thread.interrupt does not work either...
     * it seems that it is not possible to terminate completely cleanly, then.
     */
    private void stdInReadLoop() throws IOException {
        stdinReaderState = StdinReaderState.READING;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));            
        String line = br.readLine();
        while (line != null && !stopYourStdinReading) {
            if (line.equals("stop")) {
                stopYourStdinReading = true;
                break;
            }
            else if (line.equals("s")) {
                stopYourStdinReading = true;
                break;
            }
            else if (line.equals("clients")) {
                connection.sendClientListRequest();
            }
            else {
                
                List<String> natures = new ArrayList<String>(); 
                natures.add("message");
                if (line.startsWith("http://")) {
                    natures.add("url");
                    natures.add("hyperlink");
                }                
                BusinessObjectMetadata meta = new BusinessObjectMetadata();
                meta.putStringList("natures", natures);
                meta.put("sender", user);
                BusinessObject sendObj = BOB.newBuilder().metadata(meta).payload(line).build();
                connection.send(sendObj);
            }
            line = br.readLine();
        }
        
        log.info("Tranquilly finished reading stdin");
        log.info("Likewise harmoniously requesting closing output...");
        stdinReaderState = StdinReaderState.FINISHED_READING;
        connection.requestCloseOutputIfNeeded();        
    }
    
    /** To be called when connection to server has already been terminated */
    private void terminateStdinReadLoopIfNeeded() {
        
        if (stdinReaderState == StdinReaderState.READING) {
            stopYourStdinReading = true;
            
            // actually, setting the above flag is not enough, so let's just:
            log.debug("Forcibly exiting");
            System.exit(0);
        }
        
        // NOTE: there seems to be NO SAFE WAY to terminate a thread that is waiting on reading System.in
        // the only thing we can do here is to wait for a line to be read, after which actual 
        // closing can occur.
        
        // - Thread.interrupt does not work (nothing happens)
        // - System.in.notify does not work (error if not a owner of the monitor; trying to become owner by 
        //                                   synchronizing on System.in waits for the current read to complete)
        // - closing system.in does not work (it only leads to a null being read AFTER finishing the current read...)
        
        // TODO: actually, maybe we should just exit, after ensuring that all other activities have finished...         
    }
    
    /** Client receive buzinezz logic contained herein */
    private class ObjectHandler implements ABBOEConnection.BusinessObjectHandler {

        @Override
        public void handleObject(BusinessObject bo) {
            if (bo.isEvent()) {                
                BusinessObjectEventType et = bo.getMetadata().getKnownEvent();
                if (et == BusinessObjectEventType.SERVICES_REPLY) {
                    String serviceName = bo.getMetadata().getString("name");
                    String request = bo.getMetadata().getString("request");
                    
                    if (serviceName != null && request != null && serviceName.equals("clients") && request.equals("list")) { 
                        // clients list reply
                        JSONArray clients = bo.getMetadata().asJSON().getJSONArray("clients");
                        System.out.println("Clients on this server:\n"+clients.toString(4));
                    }
                    else {
                        System.out.println("SERVICE_REPLY: "+Biomine3000Utils.formatBusinessObject(bo));
                    }
                }
                else if (et == BusinessObjectEventType.ROUTING_SUBSCRIBE_REPLY) {
                    System.out.println("Subscribed successfully to the server: "+bo);
                }
                else if (et == BusinessObjectEventType.ROUTING_SUBSCRIBE_NOTIFICATION) {
                    String name = bo.getMetadata().getString("routing-id");
                    System.out.println("Client "+name+" subscribed to ABBOE");
                }
                else if (et == BusinessObjectEventType.ROUTING_DISCONNECT) {
                    String name = bo.getMetadata().getString("routing-id");
                    System.out.println("Client "+name+" parted from ABBOE");
                }
                else {
                    String formatted = Biomine3000Utils.formatBusinessObject(bo);
                    System.out.println(formatted);
                }
                
            }
            else {
                String formatted = Biomine3000Utils.formatBusinessObject(bo);
                System.out.println(formatted);
            }
        }

        @Override
        public void connectionTerminated() {
            terminateStdinReadLoopIfNeeded();
        }

        @Override
        public void connectionTerminated(Exception e) {
            log.error("Connection terminated", e);
            terminateStdinReadLoopIfNeeded();
        }
        
    }       
        
    public static void main(String pArgs[]) throws Exception {        
        
        Biomine3000Args args = new Biomine3000Args(pArgs, true);
        Socket socket = Biomine3000Utils.connectToServer(args);        
        String user = args.getUser();
        if (user == null) {
            user = Biomine3000Utils.getUser();
        }
        if (user == null) {
        	user = "anonymous";
        }
        TrivialClient client = new TrivialClient(socket, user);
        client.startMainReadLoop();
    }
    
    private enum StdinReaderState {
        NOT_YET_READING,
        READING,
        FINISHED_READING;
    }
}
