package biomine3000.tv;

import java.io.ByteArrayInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javazoom.jl.player.Player;

public class BMTVMp3Player {
    private final Logger logger = LoggerFactory.getLogger(BMTVMp3Player.class);

    private Player player; 
    
    public void close() { 
        if (player != null) { 
            player.close();        
            player = null;
        }           
    }

    // play a MP3 to the sound card
    public void play(byte[] data) {
        close();
        
        try {            
            ByteArrayInputStream bais = new ByteArrayInputStream(data);            
            player = new Player(bais);            
        }
        catch (Exception e) {
            logger.error("Failed creating player", e);
            return;
        }

        // run in new thread to play in background
        logger.info("Creating thread to play");
        new Thread() {
            public void run() {
                try { 
                    player.play();
                    logger.info("Returned from player.play");
                }
                catch (Exception e) {
                    logger.error("Failed playing", e);
                }
            }
        }.start();
    }
}


