package org.bm3k.abboe.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bm3k.abboe.objects.BusinessObject;
import org.json.JSONArray;

public class Subscriptions {
    
    public static final String RULE_ALL = "*";
    public static final String RULE_EVENTS = "@*";
    
    
    public static final Subscriptions NONE = new Subscriptions("");
    public static final Subscriptions EVERYTHING = new Subscriptions("*");
    public static final Subscriptions EVENTS_ONLY =  new Subscriptions("@*");
    public static final Subscriptions MESSAGES_ONLY =  new Subscriptions("#message");       
    
    List<SubscriptionRule> rules;
    
    /** ∅-subscription */ 
    public Subscriptions() {
        rules = Collections.emptyList();
    }
    
    public Subscriptions(List<String> rules) {
        this.rules = new ArrayList<SubscriptionRule>(rules.size());
        for (String r: rules) {
            SubscriptionRule rule = new SubscriptionRule(r);            
            this.rules.add(rule);
        }
    }        
    
    public Subscriptions(String... rules) {
        this(Arrays.asList(rules));
    }
    
    /** needs to be removed from API if more complex subscriptions are to be implemented */
    public List<String> toStringList() {
        List<String> result = new ArrayList<String>();
        for (SubscriptionRule rule: rules) {
            result.add(rule.format());
        }
        return result;
    }
           
    public boolean pass(BusinessObject bo) {
        boolean pass = false;
        
        for (SubscriptionRule rule: rules) {
            if (rule.matches(bo)) {
                pass = !rule.negated();
            }
        }
        
        return pass;
    }
    
    public String toString() {
        JSONArray arr = new JSONArray();
        for (SubscriptionRule rule: rules) {
            arr.put(rule.getRuleString());
        }
        return arr.toString();
    }
}
