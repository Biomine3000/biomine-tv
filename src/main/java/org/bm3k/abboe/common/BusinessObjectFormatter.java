package org.bm3k.abboe.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectMetadata;


/** Format objects according to their nature */
public class BusinessObjectFormatter {
    
	public static String formatIRCTime() {
		Date date = new Date(System.currentTimeMillis());
		return new SimpleDateFormat("HH:mm").format(date); 			
	}
	
	public static String format(BusinessObject o) {
		BusinessObjectMetadata meta = o.getMetadata();
		Set<String> natures = o.getMetadata().getNatures();
		StringBuffer buf = new StringBuffer();
							
		if (natures.contains("irc")) {
			String channel = meta.getString("channel").replace("MESKW", "").replace("meskw", "");			
			String sender = meta.getString("sender");
			if (sender == null) {
			    sender = meta.getString("user");
			}
			String ircTime = formatIRCTime();
			buf.append(channel + " " + ircTime + " <" + sender + "> ");
		}
		else if (natures.contains("message")) {		             
            String sender = meta.getString("sender");
            if (sender == null) {
                sender = meta.getString("user");
            }
            String ircTime = formatIRCTime();
            buf.append(ircTime + " <" + sender + "> ");
		}			
						
	    if (natures.contains("wurldget")) {
            String title = meta.getString("title");
            String tinyurl = meta.getString("tinyurl");
            buf.append("title="+title + " tinyurl="+tinyurl );
	    }
	    
	    if (natures.contains("url")) {
            buf.append("url: ");          
        }

	    if (natures.contains("hypertext")) {
            buf.append("hypertext: ");          
        }
	    
	    if (o.getMetadata().hasPlainTextPayload()) {
	        buf.append(Biomine3000Utils.plainTextPayload(o));
	    }
	    else if (o.getMetadata().hasPayload()){
	        buf.append(" <payload of "+o.getMetadata().getSize()+">"); 
	    }
	    else {
	        buf.append(" <no payload>");
	    }
	    
	    return buf.toString();
	    
		
	}
	
    
}
