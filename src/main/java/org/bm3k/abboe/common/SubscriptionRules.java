package org.bm3k.abboe.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bm3k.abboe.objects.BusinessObject;

public class SubscriptionRules {
    List<SubscriptionRule> rules;
    
    public SubscriptionRules(List<String> rules) {
        this.rules = new ArrayList<SubscriptionRule>(rules.size());
        for (String r: rules) {
            SubscriptionRule rule = new SubscriptionRule(r);            
            this.rules.add(rule);
        }
    }
    
    public SubscriptionRules(String... rules) {
        this(Arrays.asList(rules));
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
}
