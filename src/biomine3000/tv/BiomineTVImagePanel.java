package biomine3000.tv;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import util.dbg.Logger;

public class BiomineTVImagePanel extends JPanel implements
        java.awt.image.ImageObserver {
    
    String msg;
    BufferedImage img;

    int xoff = 0;
    int yoff = 0;

    public BiomineTVImagePanel(String initialMessage) {
        setContent(initialMessage);
        img = null;
        // setContent(urlStr);
    }
    
    public void setContent(String msg) {
        if (this.msg != null) {
            synchronized(this.msg) {
                this.msg = msg;
            }
        }
        else {
            this.msg = msg;
        }
        
        repaint();
    }
    
    public void setContent(BufferedImage img) {
        if (this.img != null) {
            synchronized(this.img) {
                // synchronization needed, otherwise we could change the image
                // while it is being painted
                this.img = img;                    
            }
        }
        else {
            // no image yet, nothing to synchronize
            this.img = img;
        }
        repaint();
        
    }        

    private void paintMessage(Graphics g) {
        if (msg == null) {
            return;
        }
        
        int w = getWidth();
        int h = getHeight();        
        synchronized(msg) {
            FontMetrics fm = g.getFontMetrics();
            int msgWidth = fm.stringWidth(msg);
            int msgHeight = fm.getHeight();
            int x = (w - msgWidth) / 2;
            int y = h/2 + msgHeight / 2;
            g.drawString(msg, x, y);
        }
    }

    public void paintComponent(Graphics g) {
        
        
        if (img != null) {
            paintImage(g);
        }
        else {
            // if no image, let's go for an opaque background
            super.paintComponent(g);
        }
        
        if (msg != null) {
            paintMessage(g);
        }
    }
    
    public void paintImage(Graphics g) {        
        int w = getWidth();
        int h = getHeight();
        int iw = img.getWidth();
        int ih = img.getHeight();                    
        
        double imageAspectRatio = ((double)iw)/ih;
        double panelAspectRatio = ((double)w)/h;       
        
        int x1 = 0;
        int x2 = w;
        int y1 = 0;
        int y2 = h;
        
        if ( imageAspectRatio > panelAspectRatio) {
            // by which factor the image would have to be made taller to make it suitable for the tv:            
            double yFactor = imageAspectRatio / panelAspectRatio;
            int iHeightOnPanel= (int)(h / yFactor);
            int extraHeight = h - iHeightOnPanel;
            
            y1 = extraHeight/2;
            y2 = h - extraHeight/2;
        }
        else if (imageAspectRatio < panelAspectRatio) {
            // by which factor the image would have to be made wider to make it suitable for the tv:            
            double xFactor = panelAspectRatio / imageAspectRatio;
            int iWidthOnPanel= (int)(w / xFactor);
            int extraWidth = w - iWidthOnPanel;
            
            x1 = extraWidth/2;
            x2 = w - extraWidth/2;
        }
        else {
            Logger.warning("WOOT?");
        }
                 
        g.drawImage(img,
                    x1, y1, x2, y2,
                    0, 0, iw, ih,
                    this);               
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }

}
