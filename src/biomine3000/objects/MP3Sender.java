package biomine3000.objects;


import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import util.ExceptionUtils;
import util.IOUtils;
import util.dbg.Logger;

public class MP3Sender {
    
    private Socket socket = null;
    
    public MP3Sender(ServerAddress server) throws UnknownHostException, IOException {
        init(server.host, server.port);
    }
    
    public MP3Sender(String host, int port) throws UnknownHostException, IOException {                              
        init(host, port);                                             
    }         
    
    public void init(String host, int port) throws UnknownHostException, IOException {               
                                               
        // init communications with the server
        try {       
            socket = new Socket(host, port);                  
            log("Connected to server");                      
        }
        catch (UnknownHostException e) {
            warning("Cannot connect to cache server: Unknown host: "+host);
            throw e;
        } 
        catch (IOException e) {
            warning("Error while establishing connection: "+
                           ExceptionUtils.format(e, " ")+". "+                    
                          "A probable reason is that a server is not running at "+
                          host+":"+port+", as supposed.");
            // e.printStackTrace();            
            throw e;
        }        
    }                
       
    public void send(String file) throws IOException {
               
        log("Reading file: "+file);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] payload = IOUtils.readBytes(bis);
        bis.close();
        BusinessObject bo = new BusinessObject(BiomineTVMimeType.MP3, payload);
        bo.getMetaData().put("name", file);
        
        
        log("Sending packet...");
        byte[] packet = bo.bytes();                
        IOUtils.writeBytes(socket.getOutputStream(), packet);
        log("Sent packet");                
    }

    public static void main(String[] args) throws Exception {
        String file = args[0];
        MP3Sender sender = null;
        try {
            sender = new MP3Sender(ServerAddress.LERONEN_HIMA);
        }
        catch (IOException e) {
            log("No server at LERONEN_HIMA");
        }
        if (sender == null) {
            sender = new MP3Sender(ServerAddress.LERONEN_KAPSI);
            log("Connected to LERONEN_KAPSI");
        }
        sender.send(file);
        log("Sent mp3 file, closing");
        sender.socket.close();
    }
    
    @SuppressWarnings("unused")
    private static void error(String msg) {
        Logger.error("MP3Sender: "+msg);
    }
    
    @SuppressWarnings("unused")
    private static void error(String msg, Exception e) {
        Logger.error("MP3Sender: "+msg, e);
    }
        
    private static void warning(String msg) {
        Logger.warning("MP3Sender: "+msg);
    }
    
    private static void log(String msg) {
        Logger.info("MP3Sender: "+msg);
    }
    
}
