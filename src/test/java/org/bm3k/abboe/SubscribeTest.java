package org.bm3k.abboe;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bm3k.abboe.common.BusinessMediaType;
import org.bm3k.abboe.common.Subscriptions;
import org.bm3k.abboe.objects.BOB;
import org.bm3k.abboe.objects.BusinessObject;
import org.bm3k.abboe.objects.BusinessObjectEventType;

import util.StringUtils;
import util.commandline.CommandLineTests;

public class SubscribeTest extends CommandLineTests {
          
    /** Test basic protocol suggested by Arkku */
    public static final String CMD_BASIC = "basic";
    
    public static final Map<String, Subscriptions> RULES;
    public static final Map<String, BusinessObject> OBJECTS;        
           
    static {
        RULES = new LinkedHashMap<>();   
        RULES.put("nothing",     new Subscriptions(""));
        RULES.put("everything",  new Subscriptions("*"));
        RULES.put("events only", new Subscriptions("@*"));
        RULES.put("not events",  new Subscriptions("*", "!@*"));
        RULES.put("images and events", new Subscriptions("image/*", "@*"));
        RULES.put("images not having nature hasselhoff, and events (even with hasselhoff nature)", new Subscriptions("image/*", "!#hasselhoff", "@*")); 
        RULES.put("images and events, as long as neither of them has hasselhoff nature", new Subscriptions("image/*", "@*", "!#hasselhoff"));
        RULES.put("everything except images, unless they have hasselhoff nature", new Subscriptions("*", "!image/*", "#hasselhoff"));
        
        OBJECTS = new LinkedHashMap<String, BusinessObject>();
        OBJECTS.put("NON-HOFF EVENT", BOB.newBuilder().event(BusinessObjectEventType.DUMMY).build());
        OBJECTS.put("HOFF EVENT", BOB.newBuilder().event(BusinessObjectEventType.DUMMY).nature("hasselhoff").build());
        OBJECTS.put("NON-HOFF PLAINTEXT", BOB.newBuilder().payload("ya, c'moon").build());
        OBJECTS.put("HOFF PLAINTEXT", BOB.newBuilder().payload("Michael Knight, a lone crusader in a dangerous world. The world... of the Knight Rider.").build());
        OBJECTS.put("NON-HOFF IMAGE", BOB.newBuilder().payload(new byte[0]).type(BusinessMediaType.JPEG).build());
        OBJECTS.put("HOFF IMAGE", BOB.newBuilder().payload(new byte[0]).type(BusinessMediaType.JPEG).nature("hasselhoff").build());        
    }
                              
    public SubscribeTest(String[] args) {
        super(args);
    }
    
    @Override
    public void run(String cmd) throws Exception {
        
        for (String rulesName: RULES.keySet()) {
            System.out.println(StringUtils.DASH_LINE);
            System.out.println(rulesName+":");           
            Subscriptions rules = RULES.get(rulesName);            
            for (String objectName: OBJECTS.keySet()) {
                BusinessObject bo = OBJECTS.get(objectName);
                System.out.println("  "+objectName+": " + (rules.pass(bo) ? "Right away Michael." : "I'm sorry Mike, but I did not come with a Hoopty Mode."));
                
            }
        }
    }

    public static void main(String[] args) {
        SubscribeTest tests = new SubscribeTest(args);
        tests.run();
    }   
    
}
