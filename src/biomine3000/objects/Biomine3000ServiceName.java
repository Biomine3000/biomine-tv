package biomine3000.objects;

import java.util.HashMap;
import java.util.Map;

public enum Biomine3000ServiceName {
    /**
     * params: <none>
     */
    IRCLOGMANAGER_LIST_LOGS("irclogmanager/list-logs"),
    /**
     * params: <logfile>
     */
    IRCLOGMANAGER_TAIL("irclogmanager/tail");

    private static Map<String, Biomine3000ServiceName> byName;
    private String name;

    static {
        byName = new HashMap<String, Biomine3000ServiceName>();
        for (Biomine3000ServiceName type : values()) {
            byName.put(type.name, type);
        }
    }

    private Biomine3000ServiceName(String typeString) {
        this.name = typeString;
    }

    public static Biomine3000ServiceName getType(String name) {
        return byName.get(name);
    }


    /**
     * Note that the actual names are accessed via {@link #toString()}, not via {@link #name()}, which
     * is final in java's enum class, and returns the name of the actual java language enum constant object,
     * which naturally cannot be same as the actual mime type string.
     * <p/>
     * We remind the reader that using toString for such a business-critical purpose
     * is against normal leronen policies, but is however allowable in the case of enums.
     */
    public String toString() {
        return this.name;
    }
}
