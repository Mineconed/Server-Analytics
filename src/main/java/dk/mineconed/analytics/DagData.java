package dk.mineconed.analytics;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Made by Mineconed
 *
 * All numbers for one day. Saved as data/yyyy_MM_dd.yml
 */
public final class DagData {

    public static final DateTimeFormatter FIL = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    public final LocalDate dato;
    public final long[] timeSum = new long[24];
    public final int[] timeAntal = new int[24];
    public final int[] timePeak = new int[24];
    public int peak;
    public String peakTid = "";
    public final Set<String> unikke = new LinkedHashSet<String>();
    public final Set<String> nye = new LinkedHashSet<String>();
    public int joins;
    public long sessionSum;
    public int sessionAntal;
    public final Map<String, Long> spilletid = new HashMap<String, Long>();
    public final Map<String, String> navne = new HashMap<String, String>();
    public double tpsSum;
    public int tpsAntal;
    public double tpsMin = 20.0;

    public DagData(LocalDate dato) {
        this.dato = dato;
    }

    public static File fil(File mappe, LocalDate d) {
        return new File(new File(mappe, "data"), d.format(FIL) + ".yml");
    }

    public static boolean findes(File mappe, LocalDate d) {
        return fil(mappe, d).exists();
    }

    public static DagData hent(File mappe, LocalDate d) {
        DagData x = new DagData(d);
        File f = fil(mappe, d);
        if (!f.exists()) {
            return x;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (int t = 0; t < 24; t++) {
            x.timeSum[t] = y.getLong("timer." + t + ".sum", 0);
            x.timeAntal[t] = y.getInt("timer." + t + ".antal", 0);
            x.timePeak[t] = y.getInt("timer." + t + ".peak", 0);
        }
        x.peak = y.getInt("peak", 0);
        x.peakTid = y.getString("peak_tid", "");
        x.unikke.addAll(y.getStringList("unikke"));
        x.nye.addAll(y.getStringList("nye"));
        x.joins = y.getInt("joins", 0);
        x.sessionSum = y.getLong("session_sum", 0);
        x.sessionAntal = y.getInt("session_antal", 0);
        if (y.getConfigurationSection("spilletid") != null) {
            for (String k : y.getConfigurationSection("spilletid").getKeys(false)) {
                x.spilletid.put(k, y.getLong("spilletid." + k));
            }
        }
        if (y.getConfigurationSection("navne") != null) {
            for (String k : y.getConfigurationSection("navne").getKeys(false)) {
                x.navne.put(k, y.getString("navne." + k));
            }
        }
        x.tpsSum = y.getDouble("tps_sum", 0);
        x.tpsAntal = y.getInt("tps_antal", 0);
        x.tpsMin = y.getDouble("tps_min", 20.0);
        return x;
    }

    public void gem(File mappe) {
        YamlConfiguration y = new YamlConfiguration();
        for (int t = 0; t < 24; t++) {
            if (timeAntal[t] > 0) {
                y.set("timer." + t + ".sum", timeSum[t]);
                y.set("timer." + t + ".antal", timeAntal[t]);
                y.set("timer." + t + ".peak", timePeak[t]);
            }
        }
        y.set("peak", peak);
        y.set("peak_tid", peakTid);
        y.set("unikke", new ArrayList<String>(unikke));
        y.set("nye", new ArrayList<String>(nye));
        y.set("joins", joins);
        y.set("session_sum", sessionSum);
        y.set("session_antal", sessionAntal);
        for (Map.Entry<String, Long> e : spilletid.entrySet()) {
            y.set("spilletid." + e.getKey(), e.getValue());
        }
        for (Map.Entry<String, String> e : navne.entrySet()) {
            y.set("navne." + e.getKey(), e.getValue());
        }
        y.set("tps_sum", tpsSum);
        y.set("tps_antal", tpsAntal);
        y.set("tps_min", tpsMin);
        try {
            File f = fil(mappe, dato);
            f.getParentFile().mkdirs();
            y.save(f);
        } catch (IOException e) {
            // ignore
        }
    }

    // ─────────── Udregninger ───────────

    public boolean tom() {
        int n = 0;
        for (int a : timeAntal) {
            n += a;
        }
        return n == 0 && unikke.isEmpty();
    }

    public double snit(int t) {
        return timeAntal[t] == 0 ? 0 : timeSum[t] / (double) timeAntal[t];
    }

    public double snitHeleDagen() {
        long s = 0;
        int n = 0;
        for (int t = 0; t < 24; t++) {
            s += timeSum[t];
            n += timeAntal[t];
        }
        return n == 0 ? 0 : s / (double) n;
    }

    /** Busiest hour (highest average), or negative if no data. */
    public int travlesteTime() {
        int bedst = Integer.MIN_VALUE;
        double max = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < 24; t++) {
            if (timeAntal[t] > 0 && snit(t) > max) {
                max = snit(t);
                bedst = t;
            }
        }
        return bedst;
    }

    public int stilleste() {
        int bedst = Integer.MIN_VALUE;
        double min = Double.POSITIVE_INFINITY;
        for (int t = 0; t < 24; t++) {
            if (timeAntal[t] > 0 && snit(t) < min) {
                min = snit(t);
                bedst = t;
            }
        }
        return bedst;
    }

    public long samletSpilletid() {
        long s = 0;
        for (long v : spilletid.values()) {
            s += v;
        }
        return s;
    }

    public long snitSession() {
        return sessionAntal == 0 ? 0 : sessionSum / sessionAntal;
    }

    public double snitTps() {
        return tpsAntal == 0 ? 20.0 : tpsSum / tpsAntal;
    }

    /** uuid list sorted by playtime, most first */
    public List<String> topSpilletid(int n) {
        List<Map.Entry<String, Long>> l = new ArrayList<Map.Entry<String, Long>>(spilletid.entrySet());
        java.util.Collections.sort(l, new java.util.Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        List<String> ud = new ArrayList<String>();
        for (int i = 0; i < n && i < l.size(); i++) {
            ud.add(l.get(i).getKey());
        }
        return ud;
    }
}
