package biomine3000.tv;

import gui.image.Edge;
import gui.image.ImageUtils;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

import biomine3000.objects.ImageObject;

import util.dbg.Logger;

public class BiomineTVImagePanel extends JPanel implements
        java.awt.image.ImageObserver {

    private BiomineTV tv;
    
    String msg;
    BufferedImage img;

    int xoff = 0;
    int yoff = 0;

    public BiomineTVImagePanel(BiomineTV tv, String initialMessage) {
        this.tv = tv;
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
    
    public void setContent(ImageObject img) {
        if (this.img != null) {
            synchronized(this.img) {
                // synchronization needed, otherwise we could change the image
                // while it is being painted
                // TODO: do not defer getting buffered image so far                
                this.img = img.getImage();                               
            }
        }
        else {
            // no image yet, nothing to synchronize
            this.img = img.getImage();
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
            
            // render top and bottom rectangles with uniform color that is average of colors on the corresponding edges of the image
            try {
                log("Rendering top rect");
                
                Color topColor = ImageUtils.getEdgeColor(img, Edge.TOP);
                g.setColor(topColor);
                g.fillRect(0, 0, w, extraHeight/2);
                
                log("Rendering bottom rect");
                Color bottomColor = ImageUtils.getEdgeColor(img, Edge.BOTTOM);
                g.setColor(bottomColor);
                g.fillRect(0, y2, w, extraHeight/2);
            }
            catch (RuntimeException e) {
                e.printStackTrace();
                tv.close();
            }
        }
        else if (imageAspectRatio < panelAspectRatio) {
            // by which factor the image would have to be made wider to make it suitable for the tv:            
            double xFactor = panelAspectRatio / imageAspectRatio;
            int iWidthOnPanel= (int)(w / xFactor);
            int extraWidth = w - iWidthOnPanel;
            
            x1 = extraWidth/2;
            x2 = w - extraWidth/2;
            
           // render left and right rectangles with uniform color that is average of colors on the corresponding edges of the image
            Color leftColor = ImageUtils.getEdgeColor(img, Edge.LEFT);
            g.setColor(leftColor);
            g.fillRect(0, 0, extraWidth/2, h);
            
            Color rightColor = ImageUtils.getEdgeColor(img, Edge.RIGHT);
            g.setColor(rightColor);
            g.fillRect(x2, 0, extraWidth/2, h);
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


    private void log(String msg) {
        System.out.println(msg);
    }
}
