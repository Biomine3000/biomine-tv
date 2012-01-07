package biomine3000.tv;

import java.awt.BorderLayout;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.LinkedList;

import javax.swing.*;

import biomine3000.objects.BusinessObject;
import biomine3000.objects.BusinessObjectHandler;
import biomine3000.objects.ContentVaultAdapter;
import biomine3000.objects.ImageObject;
import biomine3000.objects.PlainTextObject;

import util.dbg.Logger;

public class BiomineTV extends JFrame implements BusinessObjectHandler {
	   
    /////////////////
    // GUI
	private JLabel zombiLabel;
	private JTextArea logArea;
	private JLabel statusLabel;
	private LinkedList<String> logLines;
	private BiomineTVImagePanel contentPanel;

	//////////////////
	// BUSINESS
        
	/** Kontenttia */
	private ContentVaultAdapter contentVaultAdapter;
	        	   
    public BiomineTV() throws IOException {
	    init();
    }

    static int LOG_SIZE = 10;
        
    private void init() throws IOException {               
    
        contentPanel = new BiomineTVImagePanel(this, "Initializing content...");
                               
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
//	    log("Testing");	    
	    logArea.setFocusable(false);
	    // logArea.addKeyListener(new BMTKeyListener());
	    addKeyListener(new BMTVKeyListener());
	    
	    addWindowListener(new WindowAdapter() {
	 	  	public void windowClosing(WindowEvent e) {
	 		    close();
	 	  	}
	 	});
	    
	    contentVaultAdapter = new ContentVaultAdapter(this);
	    contentVaultAdapter.startLoading();
	    	    	    	   
    } 
    
    @SuppressWarnings("unused")
    private void logToGUI(String s) {
    	logLines.addLast(s);
    	if (logLines.size() > LOG_SIZE) {
    		logLines.removeFirst();
    	}
    	logArea.setText("");
    	for (String l: logLines) {
    		logArea.append(l+"\n");
    	}    	
    }    
    
    public static void main(String[] args) throws IOException {
        JFrame f = new BiomineTV();
        f.setSize(800,600);
        f.setLocation(300,300);
        f.setVisible(true);
    }
  
    public void close() {
        Logger.info("Starting BiomineTV.close");
        contentVaultAdapter.stop();
    	System.exit(0);
    }
  
    /** Handle arbitrary business object */
    public void handle(BusinessObject bo) {       
        
        if (bo instanceof ImageObject) {
            contentPanel.setContent((ImageObject)bo);
        }
        else if (bo instanceof PlainTextObject) {
            contentPanel.setContent(((PlainTextObject)bo).getText());
        }
    }
    
    /**
     * For now, the sole purpose of this listener is to enable closing the 
     * tv using ctrl+q instead of the abodominable ALT+F4.
     */
    private class BMTVKeyListener implements KeyListener {

	    @Override
	    public void keyTyped(KeyEvent e) {
	        // no action
		}
	
		@Override
		public void keyPressed(KeyEvent e) {
			int keyCode = e.getKeyCode();			
	    	if (keyCode == KeyEvent.VK_W && e.isControlDown()) {
	    		close();
	    	}
	    	else if (keyCode == KeyEvent.VK_Q && e.isControlDown()) {	    		
	    		close();
	    	}	    	
		}
	
		@Override
		public void keyReleased(KeyEvent e) {
			// no action
		}					
	}    
    
    @SuppressWarnings("unused")
    private static void log(String msg) {
        Logger.info("BiomineTV: "+msg);
    }    
    
    @SuppressWarnings("unused")
    private static void warn(String msg) {
        Logger.warning("BiomineTV: "+msg);
    }        
    
    @SuppressWarnings("unused")
    private static void error(String msg, Exception e) {
        Logger.error("BiomineTV: "+msg, e);
    }

}