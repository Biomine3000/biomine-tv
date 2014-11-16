package org.bm3k.abboe.senders;

import static org.bm3k.abboe.services.Biomine3000ServiceName.*;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CmdLineArgs2.IllegalArgumentsException;
import util.IOUtils;
import util.StringUtils;
import util.io.FileNameCondition;
import util.io.FileUtils;

/**
 * 
 */
public class IRCLogManager  {
    private final Logger log = LoggerFactory.getLogger(IRCLogManager.class);

    private static Subscriptions SUBSCRIPTIONS;
    private static ClientParameters CLIENT_PARAMS;
        
    static {
        SUBSCRIPTIONS = new Subscriptions("irc");    
        CLIENT_PARAMS = new ClientParameters("IRCLogManager", SUBSCRIPTIONS);
        CLIENT_PARAMS.addServices(IRCLOGMANAGER_LIST_LOGS, IRCLOGMANAGER_TAIL);
    }
    
    private Biomine3000Args args;
    private ABBOEConnection connection;
    
    private Map<String, LogFile> logFileByName;
       
    /**
     * {@link #readLogs()} and {@link #connectToABBOE()} need to be called later, manually.
     */
    private IRCLogManager(Biomine3000Args args) {
        this.args = args;       
    }
     
    @SuppressWarnings("deprecation")
    private void readLogs() throws IOException, IllegalArgumentsException {
        logFileByName = new LinkedHashMap<String, LogFile>();
        String basedir = args.get("basedir");
        if (basedir == null) {
            if (Biomine3000Utils.atVoodoomasiina()) {
                basedir = "D:\\leronen-svn\\irclogs";
            }
            else if (Biomine3000Utils.atVoodoomasiina()) {
                basedir = "/fs-3/c/leronen/irclogs";
            }
            else {
                throw new IllegalArgumentsException("No -basedir");                
            }
        }        
        if (!(new File(basedir).exists())) {
            throw new IOException("No such basedir: "+basedir);
        }
        
        List<File> logFiles = FileUtils.find(new File(basedir), new FileNameCondition(".*\\.log"));
//        List<File> logFiles = FileUtils.find(new File(basedir));
        String msg = "We have the following logfiles available:\n\t"+
                     StringUtils.collectionToString(logFiles, "\n\t");
        System.out.println(msg);
        
        for (File f: logFiles) {
            log.info("Reading log file: "+f);
            List<String> lines = IOUtils.readLines(f);            
            String relativePath = FileUtils.getPathRelativeTo(f, new File(basedir));
            LogFile logFile = new LogFile(relativePath, lines);
            logFileByName.put(relativePath, logFile);
        }
        
        log.info("Finished reading log files");
        log.info(logFileReport());
        
        // assume we have basedir at this stage
    }
    
    private String logFileReport() {
        StringBuffer buf = new StringBuffer();
        for (String name: logFileByName.keySet()) {
            LogFile file = logFileByName.get(name);
            buf.append(name+" ("+file.data.size()+" lines)"+"\n");            
        }
        return buf.toString();
    }
    
        
    /** Only after logs have been read? */
    @SuppressWarnings("unused")
    private void connectToABBOE() throws IOException, IllegalArgumentsException {
        Socket socket = Biomine3000Utils.connectToServer(args);
        this.connection = new ABBOEConnection(CLIENT_PARAMS, socket);
        this.connection.init(new ObjectHandler());
                            
    }
    
    private void showLine(String line) {
        System.out.println(line);
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
                        showLine("Clients on this server:\n"+clients.toString(4));
                    }
                    else {
                        showLine("SERVICE_REPLY: "+Biomine3000Utils.formatBusinessObject(bo));
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
            log.info("Lost connection to server");
            // TODO: should exit! or should we keep polling for a server?
        } 

        @Override
        public void connectionTerminated(Exception e) {
            log.error("Lost connection to server", e);
         // TODO: should exit! or should we keep polling for a server?
        }
        
    }       
        
    public static void main(String pArgs[]) throws Exception {
        Biomine3000Args args = new Biomine3000Args(pArgs, true);
        IRCLogManager manager = new IRCLogManager(args);
        manager.readLogs();
    }
    
    private class LogFile {
    	@SuppressWarnings("unused")
        String name;
        List<String> data;
        
        LogFile(String name, List<String> data) {
            this.name = name;
            this.data = data;
        }
        
    }
        
}
