package org.bm3k.abboe.senders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

import com.google.common.io.Files;
import com.google.common.net.MediaType;
import org.bm3k.abboe.common.*;
import org.bm3k.abboe.objects.LegacyBusinessObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.IOUtils;
import util.StringUtils;

public class ImageSender {
    private final Logger log = LoggerFactory.getLogger(ImageSender.class);

    private Socket socket = null;
    
    public ImageSender(Socket socket) {
        this.socket = socket;
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
        
        LegacyBusinessObject bo = new LegacyBusinessObject(type, payload);
        bo.getMetadata().put("name", file.getName());       
        if (channel != null) {
            bo.getMetadata().put("channel", channel);
        }
        if (user != null) {
            bo.getMetadata().put("user", user);
        }
        
        // write register object
        LegacyBusinessObject registerObj = Biomine3000Utils.makeRegisterPacket(
                "ImageSender",
                ClientReceiveMode.NONE,
                Subscriptions.NONE);
        log.info("Writing register object:" +registerObj);        
        IOUtils.writeBytes(socket.getOutputStream(), registerObj.toBytes());
        
        // write actual image
        byte[] bytes = bo.toBytes();
        log.info("Writing "+StringUtils.formatSize(bytes.length)+" bytes");
        IOUtils.writeBytes(socket.getOutputStream(), bo.toBytes());
        log.info("Sent packet");                
    }

    public static void main(String[] pArgs) throws Exception {        
        Biomine3000Args args = new Biomine3000Args(pArgs);
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
        ImageSender sender = new ImageSender(socket);
        sender.send(file, channel, user);
        
        socket.close();
    }
    
}
