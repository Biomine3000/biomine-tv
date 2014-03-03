package org.bm3k.abboe.senders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;


import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BusinessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;
import util.StringUtils;

public class MP3Sender {
    private static final Logger log = LoggerFactory.getLogger(MP3Sender.class);

    private Socket socket = null;
    
    public MP3Sender(Socket socket) {
        this.socket = socket;
    }
          
    /** Channel and user may be null, file may not. */
    public void send(java.io.File file, String channel, String user) throws IOException {
        
        // read file.
        log.info("Reading file: "+file);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] payload = IOUtils.readBytes(bis);
        bis.close();
        BusinessObject bo = new BusinessObjectFactory().makeObject(BusinessMediaType.MP3, payload);
        bo.getMetadata().put("name", file.getName());       
        if (channel != null) {
            bo.getMetadata().put("channel", channel);
        }
        if (user != null) {
            bo.getMetadata().put("user", user);
        }
        
        // Subscribe & registr
        BusinessObject subscription = ClientUtils.makeSubscriptionObject(ClientReceiveMode.NONE,
                Subscriptions.NONE);
        log.info("Writing subscription object: {}", subscription);
        IOUtils.writeBytes(socket.getOutputStream(), subscription.toBytes());

        BusinessObject registration = ClientUtils.makeRegistrationObject("MP3Sender");
        log.info("Writing register object: {}", registration);
        IOUtils.writeBytes(socket.getOutputStream(), registration.toBytes());
        
        // write actual mp3
        byte[] bytes = bo.toBytes();
        log.info("Writing "+StringUtils.formatSize(bytes.length)+" bytes");
        IOUtils.writeBytes(socket.getOutputStream(), bo.toBytes());
        log.info("Sent packet");                
    }
                        
    public static void main(String[] pArgs) throws Exception {
        Biomine3000Args args = new Biomine3000Args(pArgs);
        log.info("args: "+args);
        Socket socket = Biomine3000Utils.connectToServer(args);                
        String channel = args.getChannel();
        String user = args.getUser();
        File file;
        if (args.hasPositionalArgs()) {
            file = new File(args.shift());
        }
        else {
            file = Biomine3000Utils.randomFile(".");
        }
            
        MP3Sender sender = new MP3Sender(socket);
        sender.send(file, channel, user);
        
        socket.close();
    }
    
}