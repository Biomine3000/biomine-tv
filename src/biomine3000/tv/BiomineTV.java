package biomine3000.tv;

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedList;

import javax.swing.*;

import biomine3000.objects.ImageObject;
import biomine3000.tv.ContentVaultProxy.ContentVaultListener;
import biomine3000.tv.ContentVaultProxy.InvalidStateException;

import util.dbg.Logger;

/** protokolla: http://www.zeromq.org/ */
public class BiomineTV extends JFrame {
	   
    /////////////////
    // GUI
	private JLabel zombiLabel;
	private JTextArea logArea;
	private JLabel statusLabel;
	private LinkedList<String> logLines;
	private BiomineTVImagePanel contentPanel = new BiomineTVImagePanel("Initializing content...");

	//////////////////
	// BUSINESS
        
	/** Kontenttia */
	private ContentVaultProxy content;
	private ContentListener contentListener;
    private Updater updater;
	        	
    
    public BiomineTV() throws IOException {
	    init();
    }

    static int LOG_SIZE = 10;
        
    private void init() throws IOException {               
        
        content = new ContentVaultProxy();        
        
	    setTitle("Biomine TV®");
	    zombiLabel = new JLabel("For relaxing times, make it zombie time");
	    logArea = new JTextArea();
	    logArea.setSize(400, 400);
	    logLines = new LinkedList<String>();
	    statusLabel = new JLabel();
	    setLayout(new BorderLayout());
	    add(zombiLabel, BorderLayout.NORTH);
	    add(logArea, BorderLayout.EAST);
	    add(contentPanel, BorderLayout.CENTER);
	    add(statusLabel, BorderLayout.SOUTH);
	    log("Testing");	    
	    logArea.setFocusable(false);
	    // logArea.addKeyListener(new BMTKeyListener());
	    addKeyListener(new BMTKeyListener());
	    
	    addWindowListener(new WindowAdapter() {
	 	  	public void windowClosing(WindowEvent e) {
	 		    close();
	 	  	} //windowClosing
	 	} );
	    
	    contentListener = new ContentListener();
	    content.addListener(contentListener);
	    content.startLoading();	    	    	    
    }
 
    private class ContentListener implements ContentVaultListener {

        boolean firstImageLoaded = false;
        
        @Override
        public void loadedImageList() {
            // TODO Auto-generated method stub
            contentPanel.setContent("Loaded urls for "+content.getNumLoadedObjects()+" business objects");
        }

        @Override
        public void loadedImage(String image) {
            // TODO Auto-generated method stub
            String msg = "Loaded "+content.getNumLoadedObjects()+"/"+content.getTotalNumObjects()+" business objects";
            Logger.info(msg);
            contentPanel.setContent(msg);
            if (firstImageLoaded == false) {
                // Logger.info("First image loaded, starting updater thread");
                Logger.info("First image loaded, starting updater thread to loop tuning image channel");    
                firstImageLoaded = true;
                updater = new Updater();
                new Thread(updater).start();
            }
        }
        
    }
    
    private void log(String s) {
    	logLines.addLast(s);
    	if (logLines.size() > LOG_SIZE) {
    		logLines.removeFirst();
    	}
    	logArea.setText("");
    	for (String l: logLines) {
    		logArea.append(l+"\n");
    	}
    	// statusLabel.setText("Logarea size after appendlog: "+logArea.getSize());
    	
    }
    
    private class Updater implements Runnable {
        private boolean stop = false;
                       
        public void run() {
            Logger.info("TV Updater running!");
            
            while (!stop) {
                try {                    
                    // BufferedImage randomContent = content.sampleImage();
                    ImageObject randomContent = content.sampleImage();
                    contentPanel.setContent(randomContent);
                    Thread.sleep(3000);
                }
                catch (InvalidStateException e) {
                    contentPanel.setContent("Invalid state in ContentVaultProxy: "+e.getMessage());
                }
                catch (InterruptedException e) {
                    // no action
                }
            }
            Logger.info("TV Updater stopped.");
        }
    }
    
    public static void main(String[] args) throws IOException {
        JFrame f = new BiomineTV();        
        f.setSize(800,600); // default size is 0,0
        f.setLocation(300,300); // default is 0,0 (top left corner)
        f.setVisible(true);
    }
  
    private void close() {
        Logger.info("Starting BiomineTV.close");
        updater.stop = true;
    	System.exit(0);
    }
  
    private class BMTKeyListener implements KeyListener {

	    @Override
	    public void keyTyped(KeyEvent e) {
	        // no action
		}
	
		@Override
		public void keyPressed(KeyEvent e) {
//			log("Pressed");
			// no action
			int keyCode = e.getKeyCode();			
	    	if (keyCode == KeyEvent.VK_W && e.isControlDown()) {
//	    		log("Should close");
	    		close();
	    	}
	    	else if (keyCode == KeyEvent.VK_Q && e.isControlDown()) {
//	    		log("Should close");
	    		close();
	    	}
	    	displayInfo(e);
		}
	
		@Override
		public void keyReleased(KeyEvent e) {
			// no action
		}
		
		// TODO: move out-commented code to some keytester class, instead of removing ,
		// as the knowledge contained therein might be needed at a later stage
		private void displayInfo(KeyEvent e){
	        
	        //You should only rely on the key char if the event
	        //is a key typed event.
//	        int id = e.getID();
//	        String keyString;
//	        if (id == KeyEvent.KEY_TYPED) {
//	            char c = e.getKeyChar();
//	            keyString = "key character = '" + c + "'";
//	        } else {
//	            int keyCode = e.getKeyCode();
//	            keyString = "key code = " + keyCode
//	                    + " ("
//	                    + KeyEvent.getKeyText(keyCode)
//	                    + ")";
//	        }
//	        
//	        int modifiersEx = e.getModifiersEx();
//	        String modString = "extended modifiers = " + modifiersEx;
//	        String tmpString = KeyEvent.getModifiersExText(modifiersEx);
//	        if (tmpString.length() > 0) {
//	            modString += " (" + tmpString + ")";
//	        } else {
//	            modString += " (no extended modifiers)";
//	        }
//	        
//	        String actionString = "action key? ";
//	        if (e.isActionKey()) {
//	            actionString += "YES";
//	        } else {
//	            actionString += "NO";
//	        }
//	        
//	        String locationString = "key location: ";
//	        int location = e.getKeyLocation();
//	        if (location == KeyEvent.KEY_LOCATION_STANDARD) {
//	            locationString += "standard";
//	        } else if (location == KeyEvent.KEY_LOCATION_LEFT) {
//	            locationString += "left";
//	        } else if (location == KeyEvent.KEY_LOCATION_RIGHT) {
//	            locationString += "right";
//	        } else if (location == KeyEvent.KEY_LOCATION_NUMPAD) {
//	            locationString += "numpad";
//	        } else { // (location == KeyEvent.KEY_LOCATION_UNKNOWN)
//	            locationString += "unknown";
//	        }
//	        	        
//	        log(keyString);
//	        log(modString);
//	        log(actionString);
//	        log(locationString);	       
	    }

			
	}
    

}