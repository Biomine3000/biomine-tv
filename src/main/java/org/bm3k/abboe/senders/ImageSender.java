package org.bm3k.abboe.senders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

import com.google.common.io.Files;
import com.google.common.net.MediaType;
import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;
import util.StringUtils;

public class ImageSender {
    private final Logger log = LoggerFactory.getLogger(ImageSender.class);
    private Biomine3000Args args;
    private Socket socket = null;    
       
    private ImageSender(Biomine3000Args args) {
    	this.args = args;    	
    }
          
    /** Channel and user may be null, file may not. */
    public void send(File file, String channel, String user) throws IOException, UnsuitableFiletypeException {
        
        // read file.
        log.info("Reading file: "+file);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] payload = IOUtils.readBytes(bis);
        bis.close();

        MediaType type = BusinessMediaType.getByExtension(Files.getFileExtension(file.getName()));
        if (type == null) {
            throw new UnsuitableFiletypeException(StringUtils.getExtension(file));
        }        
        
        BusinessObject bo = BOB.newBuilder().type(type).payload(payload).build();
        bo.getMetadata().put("name", file.getName());       
        if (channel != null) {
            bo.getMetadata().put("channel", channel);
        }
        if (user != null) {
            bo.getMetadata().put("user", user);
        }
        
        // Subscribe & registrer
        BusinessObject subscription = ClientUtils.makeSubscriptionObject(Subscriptions.NONE);
        log.info("Writing subscription object: {}", subscription);
        IOUtils.writeBytes(socket.getOutputStream(), subscription.toBytes());

        BusinessObject registration = ClientUtils.makeRegistrationObject("ImageSender");
        log.info("Writing register object: {}", registration);
        IOUtils.writeBytes(socket.getOutputStream(), registration.toBytes());

        // write actual image
        byte[] bytes = bo.toBytes();
        log.info("Writing "+StringUtils.formatSize(bytes.length)+" bytes");
        IOUtils.writeBytes(socket.getOutputStream(), bo.toBytes());
        log.info("Sent packet");
                              
    }
    
    private void run() throws Exception {
    	socket = Biomine3000Utils.connectToServer(args);                
        String channel = args.getChannel();
        String user = args.getUser();
        File file;        
        if (args.hasPositionalArgs()) {
        	while (args.hasPositionalArgs()) {
        		file = new File(args.shift());
        		log.info("sending: "+file);
        		send(file, channel, user);
        		if (args.hasPositionalArgs()) {
        			Thread.sleep(3000);
        		}
        	}        	            
        }
        
        socket.close();
    }
    
    public static void main(String[] pArgs) throws Exception {        
        Biomine3000Args args = new Biomine3000Args(pArgs);
        ImageSender sender = new ImageSender(args);
        sender.run();                       
    }
    
}
