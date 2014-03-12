package org.bm3k.abboe.senders;

import java.io.*;
import java.net.Socket;

import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.StringUtils;

public class MP3Sender {
    private static final Logger log = LoggerFactory.getLogger(MP3Sender.class);

    private Socket socket = null;
    
    public MP3Sender(Socket socket) {
        this.socket = socket;
    }
          
    /** Channel and user may be null, file may not. */
    public void send(File file, String channel, String user) throws IOException {
        log.info("Reading file {}", file);
        byte[] payload = Files.toByteArray(file);
        log.info("Read payload of {} bytes", payload.length);

        BusinessObject bo = BOB.newBuilder().type(BusinessMediaType.MP3).payload(payload).build();
        bo.getMetadata().put("name", file.getName());
        if (channel != null) {
            bo.getMetadata().put("channel", channel);
        }
        if (user != null) {
            bo.getMetadata().put("user", user);
        }
        
        try (OutputStream os = socket.getOutputStream()) {
            // Subscribe & register
            BusinessObject subscription = ClientUtils.makeSubscriptionObject(Subscriptions.NONE);
            log.info("Writing subscription object: {} ", subscription);
            IOUtils.write(subscription.toBytes(), os);

            BusinessObject registration = ClientUtils.makeRegistrationObject("MP3Sender");
            log.info("Writing register object: {} ", registration);
            IOUtils.write(registration.toBytes(), os);

            // Write actual MP3 object
            byte[] bytes = bo.toBytes();
            log.info("Writing {} bytes", StringUtils.formatSize(bytes.length));
            IOUtils.write(bo.toBytes(), os);
            log.info("Sent object.");

            try {
                log.info("Sleeping for 1 second to not get the object dropped by buggy Objectoplex");
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
