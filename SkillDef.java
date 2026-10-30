package dev.mythiclite;

import java.util.LinkedHashMap;
import java.util.Map;

public class SkillDef {
    public final Map<String, Object> data = new LinkedHashMap<>();
    public final String trigger, type;
    public final int interval;
    public final double chance;

    public SkillDef(Map<?, ?> raw) {
        raw.forEach((k, v) -> data.put(String.valueOf(k).toLowerCase(), v));
        trigger = str("trigger", "TIMER").toUpperCase();
        type = str("type", "MESSAGE").toUpperCase();
        interval = (int) dbl("interval", 100);
        chance = dbl("chance", 100);
    }

    public String str(String k, String d) { Object o = data.get(k); return o == null ? d : String.valueOf(o); }
    public double dbl(String k, double d) { return Text.num(data.get(k), d); }
    public boolean bool(String k, boolean d) { Object o = data.get(k); return o == null ? d : Boolean.parseBoolean(String.valueOf(o)); }
}
