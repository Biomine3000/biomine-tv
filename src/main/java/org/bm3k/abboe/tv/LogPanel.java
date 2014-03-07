package org.bm3k.abboe.tv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;


@SuppressWarnings("serial")
public class LogPanel extends JPanel {    
    private final Logger log = LoggerFactory.getLogger(LogPanel.class);

    JTextArea textArea;
    JScrollPane scrollPane;
        
    /** Should only be called from the event dispatch thread */
    public void appendText(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.warn("appendText called from outside event dispatch thread");
            SwingUtilities.invokeLater(new Appender(text));            
        }
        else {
            // in event dispatch thread
            textArea.append(text+"\n");            
            Dimension d = textArea.getSize();
            // log.info("Text area dimensions: "+d);
            Rectangle r = new Rectangle(0, d.height-10, d.width, 10);
            // log.info("Rectangle to make visible: "+r);
//            log.info("appended text: "+text);
            textArea.scrollRectToVisible(r);
        }                              
    }
    
    private class Appender implements Runnable {
        String text;
        Appender(String text) {
            this.text = text;
        }
        public void run() {
            appendText(text);
        }
    }
    
    public LogPanel() {
        textArea = new JTextArea();
        scrollPane = new JScrollPane(textArea);
        
        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
    }
}
