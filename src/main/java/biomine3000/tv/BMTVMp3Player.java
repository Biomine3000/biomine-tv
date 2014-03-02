package biomine3000.tv;

import java.io.ByteArrayInputStream;
import util.dbg.Logger;

import javazoom.jl.player.Player;

public class BMTVMp3Player {    
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
            error("Failed creating player", e);
            return;
        }

        // run in new thread to play in background
        log("Creating thread to play");
        new Thread() {
            public void run() {
                try { 
                    player.play();
                    log("Returned from player.play");
                }
                catch (Exception e) {
                    error("Failed playing", e);
                }
            }
        }.start();
    }
        
    private static void log(String msg) {
        Logger.info("BMTVMp3Player: "+msg);
    }
        
    
    @SuppressWarnings("unused")
	private static void warn(String msg) {
        Logger.warning("BMTVMp3Player: "+msg);
    }        
        
    private static void error(String msg, Exception e) {
        Logger.error("BMTVMp3Player: "+msg, e);
    }
}


