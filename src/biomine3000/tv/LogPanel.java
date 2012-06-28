package biomine3000.tv;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;


import util.dbg.ILogger;

public class LogPanel extends JPanel {

    ILogger log;
    JTextArea textArea;
    JScrollPane scrollPane;

    /**
     * Should only be called from the event dispatch thread
     */
    public void appendText(String text) {
        if (!SwingUtilities.isEventDispatchThread()) {
            // log.warning("appendText called from outside event dispatch thread");
            SwingUtilities.invokeLater(new Appender(text));
        } else {
            // in event dispatch thread
            textArea.append(text);
            Dimension d = textArea.getSize();
            log.info("Text area dimensions: " + d);
            Rectangle r = new Rectangle(0, d.height - 10, d.width, 10);
            log.info("Rectangle to make visible: " + r);
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

    public LogPanel(ILogger log) {
        this.log = log;
        textArea = new JTextArea();
        scrollPane = new JScrollPane(textArea);

        setLayout(new BorderLayout());
        add(scrollPane, BorderLayout.CENTER);
    }
}
