package org.bm3k.abboe.common;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.bm3k.abboe.objects.BusinessObject;

/**
 * Subscription rule, as proposed by Arkku around 2014-02-10.
 * 
 * Logic suggested by Arkku:
 * 
 * The array of subscription rules is an ordered list. The last matching rule in the list determines pass/no pass. If no rules match (or the list is empty), the default is to pass.
 *
 *  • Each rule is a string
 *  • A ! prefix on a rule (before the type prefix, if any) negates it (i.e., don't pass if it matches)
 *  • Rules prefixed with # apply to natures, @ to events, and without a prefix to types
 *  • Rules may end in a *, which is a wildcard matching any number of characters
 *  • Implementations may support more general wildcards at their discretion 
 */
public class SubscriptionRule {
    
     private String ruleStr; 
     private boolean negated;
     private Type type;
     private String pattern; // text without (optiona) prefix and (optiona) '*'-suffix
     private boolean wildcard; // has wildcard suffix
     
     public SubscriptionRule(String ruleStr) {
         this.ruleStr = ruleStr;
         
         String pattern = ruleStr;
         
         if (pattern .startsWith("!")) {
             negated = true;
             pattern  = pattern .substring(1);
         }
         else {
             negated = false;
         }
         
         if (pattern .startsWith("#")) {
             // rule concerns natures
             type = Type.NATURE;
             pattern  = pattern .substring(1);
         }
         else if (pattern .startsWith("@")) {
             // rule concerns events
             type = Type.EVENT;
             pattern  = pattern .substring(1);
         }
         else {
             type = Type.CONTENTTYPE;
         }
         
         if (pattern .endsWith("*")) {
             wildcard = true;
             pattern  = pattern .substring(0, pattern .length()-1);
         }
         else {
             wildcard = false;
         }
         
         this.pattern = pattern;
     }
     
     public boolean negated() {
         return negated;
     }
     
    /** 
     * Check whether a given value (a event, contenttype or nature) matches the rule text.
     * @return false for null values
     **/
     private boolean matches(String value) {
         
         if (value == null) {
             // if just wildcard, return true also for missing
             return wildcard && pattern.equals("");
         }              
         if (wildcard) {
             return value.startsWith(pattern);
         }
         else {
             return value.equals(pattern);
         }
     }
                    
     /** 
      * Check whether a given object matches a rule, and if so, return pass (true) or no pass (false),
      * depending on whether this is a negative rule.
      * @return null, if no match */
     public Boolean matches(BusinessObject bo) {
         BusinessObjectMetadata meta = bo.getMetadata();
                                          
         switch (type) {
             case NATURE:                 
                 for (String nature: meta.getNatures()) {
                     if (matches(nature)) {
                         return true;
                     }
                 }
                 return false;
             case EVENT:
                 String event = meta.getEvent();
                 if (event != null) { 
                     return matches(event);
                 } else {
                     return false;
                 }
             case CONTENTTYPE:
                 return matches(meta.getType());
            default:
                throw new RuntimeException("Un-possible"); 
         }                                   
     }

     /** Format as official JSON-able ABBOE rule string */
     public String format() {
         return ruleStr;
     }
     
     public String getRuleString() {
         return ruleStr;
     }
     
     private enum Type { 
         EVENT, NATURE, CONTENTTYPE;
     }                  
     
     public String toString() {
         return new ToStringBuilder(this).
                 append("ruleStr", ruleStr).                 
                 append("negated", negated).
                 append("type", type).
                 append("wildcard", wildcard).
                 append("pattern", pattern).build();                                                   
     }
     
     
}
