package biomine3000.objects;

// import static biomine.db.query.CrawlerCacheServer.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;


import util.CollectionUtils;
import util.ExceptionUtils;
import util.IOUtils;
import util.SU;
import util.Timer;
import util.collections.Pair;
import util.dbg.Logger;


public class TestTCPClient {
    
    public static boolean DBG = true;
    
    
    private String DEFAULT_HOST = "localhost";
    private int DEFAULT_PORT = 9876;
    
    private Socket mSocket = null;
    private PrintWriter mSocketWriter = null;
    private BufferedReader mSocketReader = null;       

    public TestTCPClient() throws UnknownHostException, IOException {
        Timer.startTiming("CrawlerCacheServerProxy: init");               
        
        init();
        
        Timer.endTiming("CrawlerCacheServerProxy: init");                               
    }    
       
    
    public void init() throws UnknownHostException, IOException {               
                                               
        // init communications with the server
        try {       
            mSocket = new Socket(DEFAULT_HOST, DEFAULT_PORT);                                      
            mSocketWriter = new PrintWriter(mSocket.getOutputStream   (), true);                        
            mSocketReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
      
            Logger.info("Connected to server");                                  
        }
        catch (UnknownHostException e) {
            Logger.warning("Cannot connect to cache server: Unknown host: "+DEFAULT_HOST);
            throw e;
        } 
        catch (IOException e) {
            Logger.warning("Error while establishing connection: "+
                           ExceptionUtils.formatWithCauses(e, " ")+". "+                    
                          "A probable reason is that a cache server is not running at "+
                          DEFAULT_HOST+":"+DEFAULT_PORT+", as supposed.");
            // e.printStackTrace();            
            throw e;
        }        

    }
                
    
    /** Actually, we need to close after each request... */
    public void close() {        
        Logger.info("Closing connection to ccs...");
        // Logger.info("Closing writer...");        
        mSocketWriter.flush();
        mSocketWriter.close();
        // Logger.info("Closing reader...");
        try {
            mSocketReader.close();
        }
        catch (IOException e) {
            // foo
        }
        
        // Logger.info("Closing socket...");
        try {
            mSocket.close();
        }
        catch (IOException e) {
            // foo
        }
    }
    

    
    
    /** Just for trivial testing */
    public static void main(String[] pArgs) throws Exception {
         // 
        
//        List<String> args = Arrays.asList(pArgs);
//        String cmd = args.get(0);
//        if (cmd.equals(REQUEST_ID_QUERY)) {
//            List<String> input = IOUtils.readLines();
//            List<String> argList = CollectionUtils.tailList(args, 1);
//            Integer id = ccsp.getId(input, argList);
//            Logger.info("Got id: "+id);
//        }
//        else if (cmd.equals(REQUEST_ID_NEED_TO_STORE)) {
//            List<String> input = IOUtils.readLines();
//            List<String> argList = CollectionUtils.tailList(args, 1);
//            Pair<Integer, File> idAndPath= ccsp.needToStore(input, argList);            
//            if (idAndPath != null) {
//                Logger.info("Need to store as id: "+idAndPath);
//                
//            }
//            else {
//                Logger.info("No need to store");
//            }
//        }
//        else if (cmd.equals(REQUEST_ID_STORING_DONE)) {            
//            int id = Integer.parseInt(args.get(1));
//            ccsp.notifyStored(id);
//        }
            
        
    }
    
//    private class LoggingWriter extends PrintWriter {
//      
//        public LoggingWriter(OutputStream p1, boolean p2) {
//            super(p1, p2);
//        }
//      
//        public void println(String p) {
//            if (BMSettings.logCacheServerInput) {
//                System.err.println("Line to server: "+p);
//            }
//            super.println(p);
//        }
//      
//        public void println(Object p) {
//            if (BMSettings.logCacheServerInput) {
//                System.err.println("Line to server: "+p);
//            }            
//            super.println(p);
//        }       
//    }
}

