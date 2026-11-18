package dk.mineconed.analytics;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Made by Mineconed
 *
 * ServerAnalytics: tracks players every minute and posts a daily
 * (and weekly) report with a chart to Discord.
 */
public final class ServerAnalytics extends JavaPlugin implements Listener {

    private Tekster msg;
    private DagData idag;
    private final Map<UUID, Long> joinTid = new HashMap<UUID, Long>();
    private long sidsteMaaling = System.nanoTime();
    private double egenTps = 20.0;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        indlaesBeskeder();
        idag = DagData.hent(getDataFolder(), nu().toLocalDate());
        long tid = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            joinTid.put(p.getUniqueId(), tid);
        }
        getServer().getPluginManager().registerEvents(this, this);
        // own TPS measurement (used if the server has no TPS method)
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                long n = System.nanoTime();
                double s = Math.subtractExact(n, sidsteMaaling) / 1.0E9;
                sidsteMaaling = n;
                if (s > 0) {
                    egenTps = Math.min(20.0, 100.0 / s);
                }
            }
        }, 100L, 100L);
        // every minute: sample + check report time
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                maal();
                tjekRapport();
            }
        }, 1200L, 1200L);
        // save every 5 minutes
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                idag.gem(getDataFolder());
            }
        }, 6000L, 6000L);
        getLogger().info("ServerAnalytics is enabled. Made by Mineconed.");
    }

    @Override
    public void onDisable() {
        long tid = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : joinTid.entrySet()) {
            idag.sessionSum += Math.subtractExact(tid, e.getValue()) / 1000L;
            idag.sessionAntal++;
        }
        if (idag != null) {
            idag.gem(getDataFolder());
        }
    }

    // ═══════════════ HJÆLPERE ═══════════════

    private void indlaesBeskeder() {
        for (String n : new String[]{"messages_en.yml", "messages_da.yml"}) {
            if (!new File(getDataFolder(), n).exists()) {
                saveResource(n, false);
            }
        }
        String sprog = getConfig().getString("language", "en");
        File f = new File(getDataFolder(), "messages_" + sprog + ".yml");
        if (!f.exists()) {
            f = new File(getDataFolder(), "messages_en.yml");
            sprog = "en";
        }
        msg = Tekster.laes(f, getResource(sprog.equals("da") ? "messages_da.yml" : "messages_en.yml"));
    }

    String m(String k, String... ph) {
        String s = msg.get(k, k);
        for (int i = 0; i + 1 < ph.length; i += 2) {
            s = s.replace(ph[i], ph[i + 1]);
        }
        return s;
    }

    private String c(String k, String... ph) {
        return ChatColor.translateAlternateColorCodes('&', m(k, ph));
    }

    private ZoneId zone() {
        String z = getConfig().getString("timezone", "");
        try {
            return z == null || z.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(z);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    private ZonedDateTime nu() {
        return ZonedDateTime.now(zone());
    }

    private String varighed(long sek) {
        long t = sek / 3600L;
        long mn = (sek % 3600L) / 60L;
        if (t > 0) {
            return t + m("unit_h") + " " + mn + m("unit_m");
        }
        return mn + m("unit_m");
    }

    private static String time(int t) {
        return t < 0 ? "?" : String.format("%02d:00", t);
    }

    private String datoTekst(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern(getConfig().getString("date_format", "dd/MM/yyyy")));
    }

    private double tps() {
        double t = egenTps;
        try {
            Method mt = Bukkit.getServer().getClass().getMethod("getTPS");
            t = ((double[]) mt.invoke(Bukkit.getServer()))[0];
        } catch (Throwable paper) {
            try {
                Object ms = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                Class<?> c = ms.getClass();
                Field f = null;
                while (c != null && f == null) {
                    try {
                        f = c.getDeclaredField("recentTps");
                    } catch (NoSuchFieldException e) {
                        c = c.getSuperclass();
                    }
                }
                if (f != null) {
                    f.setAccessible(true);
                    t = ((double[]) f.get(ms))[0];
                }
            } catch (Throwable spigot) {
                t = egenTps;
            }
        }
        return Math.min(20.0, t);
    }

    private boolean usynlig(Player p) {
        if (!getConfig().getBoolean("hide_vanished", true)) {
            return false;
        }
        for (MetadataValue v : p.getMetadata("vanished")) {
            if (v.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private boolean taeller(Player p) {
        return !usynlig(p) && !p.hasPermission("serveranalytics.ignore");
    }

    // ═══════════════ MÅLING ═══════════════

    private void nyDag() {
        LocalDate d = nu().toLocalDate();
        if (!d.equals(idag.dato)) {
            idag.gem(getDataFolder());
            idag = DagData.hent(getDataFolder(), d);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (taeller(p)) {
                    idag.unikke.add(p.getUniqueId().toString());
                    idag.navne.put(p.getUniqueId().toString(), p.getName());
                }
            }
        }
    }

    private void maal() {
        nyDag();
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!taeller(p)) {
                continue;
            }
            n++;
            String u = p.getUniqueId().toString();
            Long s = idag.spilletid.get(u);
            idag.spilletid.put(u, (s == null ? 0L : s) + 60L);
            idag.navne.put(u, p.getName());
        }
        ZonedDateTime z = nu();
        int t = z.getHour();
        idag.timeSum[t] += n;
        idag.timeAntal[t]++;
        idag.timePeak[t] = Math.max(idag.timePeak[t], n);
        if (n > idag.peak) {
            idag.peak = n;
            idag.peakTid = z.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        double tp = tps();
        idag.tpsSum += tp;
        idag.tpsAntal++;
        idag.tpsMin = Math.min(idag.tpsMin, tp);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        nyDag();
        joinTid.put(p.getUniqueId(), System.currentTimeMillis());
        if (!taeller(p)) {
            return;
        }
        String u = p.getUniqueId().toString();
        idag.unikke.add(u);
        idag.navne.put(u, p.getName());
        idag.joins++;
        if (!p.hasPlayedBefore()) {
            idag.nye.add(u);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Long t = joinTid.remove(e.getPlayer().getUniqueId());
        if (t != null && taeller(e.getPlayer())) {
            idag.sessionSum += Math.subtractExact(System.currentTimeMillis(), t) / 1000L;
            idag.sessionAntal++;
        }
    }

    // ═══════════════ RAPPORT ═══════════════

    private File stateFil() {
        return new File(getDataFolder(), "state.yml");
    }

    private void tjekRapport() {
        if (!getConfig().getBoolean("discord.daily_report", true)) {
            return;
        }
        String url = getConfig().getString("discord.webhook_url", "");
        if (url == null || !url.startsWith("http")) {
            return;
        }
        ZonedDateTime z = nu();
        LocalTime tid;
        try {
            tid = LocalTime.parse(getConfig().getString("discord.report_time", "23:55"));
        } catch (Exception ex) {
            tid = LocalTime.of(23, 55);
        }
        if (z.toLocalTime().isBefore(tid)) {
            return;
        }
        YamlConfiguration st = YamlConfiguration.loadConfiguration(stateFil());
        String i = z.toLocalDate().format(DagData.FIL);
        if (i.equals(st.getString("sidst_daglig"))) {
            return;
        }
        st.set("sidst_daglig", i);
        boolean uge = getConfig().getBoolean("discord.weekly_report", true)
                && z.getDayOfWeek() == ugedag() && !i.equals(st.getString("sidst_ugentlig"));
        if (uge) {
            st.set("sidst_ugentlig", i);
        }
        try {
            st.save(stateFil());
        } catch (IOException ignore) {
            // ignore
        }
        idag.gem(getDataFolder());
        sendDaglig(idag, null);
        if (uge) {
            sendUge(null);
        }
    }

    private DayOfWeek ugedag() {
        try {
            return DayOfWeek.valueOf(getConfig().getString("discord.weekly_day", "SUNDAY").toUpperCase());
        } catch (Exception e) {
            return DayOfWeek.SUNDAY;
        }
    }

    private String server() {
        return getConfig().getString("server_name", "Minecraft");
    }

    private static String pil(double nu, double foer) {
        if (foer <= 0) {
            return "";
        }
        long pct = Math.round((nu / foer) * 100.0) + Math.negateExact(100L);
        if (pct > 0) {
            return " ▲ " + pct + "%";
        }
        if (pct < 0) {
            return " ▼ " + Math.abs(pct) + "%";
        }
        return " ● 0%";
    }

    private String felt(String navn, String vaerdi, boolean inline) {
        return "{\"name\":\"" + Webhook.esc(navn) + "\",\"value\":\"" + Webhook.esc(vaerdi) + "\",\"inline\":" + inline + "}";
    }

    /** Builds and sends the daily report (async). cs = who asked (or null). */
    private void sendDaglig(final DagData d, final CommandSender cs) {
        final String url = getConfig().getString("discord.webhook_url", "");
        DagData igaar = DagData.hent(getDataFolder(), d.dato.minusDays(1));
        List<String> f = new ArrayList<String>();
        f.add(felt(m("r_peak"), "**" + d.peak + "** " + m("r_at") + " " + (d.peakTid.isEmpty() ? "?" : d.peakTid) + pil(d.peak, igaar.peak), true));
        f.add(felt(m("r_average"), String.format("**%.1f**", d.snitHeleDagen()), true));
        f.add(felt(m("r_unique"), "**" + d.unikke.size() + "**" + pil(d.unikke.size(), igaar.unikke.size()), true));
        f.add(felt(m("r_new"), "**" + d.nye.size() + "**" + pil(d.nye.size(), igaar.nye.size()), true));
        f.add(felt(m("r_joins"), "**" + d.joins + "**", true));
        f.add(felt(m("r_playtime"), "**" + varighed(d.samletSpilletid()) + "**", true));
        f.add(felt(m("r_session"), "**" + varighed(d.snitSession()) + "**", true));
        f.add(felt(m("r_busiest"), "**" + time(d.travlesteTime()) + "**", true));
        f.add(felt(m("r_quietest"), "**" + time(d.stilleste()) + "**", true));
        f.add(felt(m("r_tps"), String.format("**%.1f** (min %.1f)", d.snitTps(), d.tpsMin), true));
        StringBuilder top = new StringBuilder();
        String[] med = {"🥇", "🥈", "🥉"};
        int i = 0;
        for (String u : d.topSpilletid(3)) {
            top.append(med[i]).append(" `").append(d.navne.containsKey(u) ? d.navne.get(u) : "?").append("` ")
                    .append(varighed(d.spilletid.get(u))).append("\n");
            i++;
        }
        f.add(felt(m("r_top"), top.length() == 0 ? m("r_none") : top.toString(), false));

        final String titel = m("r_title", "{server}", server(), "{date}", datoTekst(d.dato));
        byte[] png = getConfig().getBoolean("discord.chart_image", true)
                ? Graf.tegn(d, m("chart_title", "{server}", server(), "{date}", datoTekst(d.dato)), m("chart_footer")) : null;
        String beskrivelse = png == null ? "`" + Graf.linje(d) + "`\n`00          12        23`" : "";
        final String json = "{\"username\":\"" + Webhook.esc(getConfig().getString("discord.bot_name", "ServerAnalytics"))
                + "\",\"embeds\":[{\"title\":\"" + Webhook.esc(titel) + "\",\"description\":\"" + Webhook.esc(beskrivelse)
                + "\",\"color\":" + getConfig().getInt("discord.color", 8086271) + ",\"fields\":[" + String.join(",", f) + "]"
                + (png != null ? ",\"image\":{\"url\":\"attachment://chart.png\"}" : "")
                + ",\"footer\":{\"text\":\"" + Webhook.esc(m("r_footer")) + "\"},\"timestamp\":\"" + Instant.now().toString() + "\"}]}";
        final byte[] fPng = png;
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final int kode = Webhook.send(url, json, fPng);
                if (kode < 200 || kode >= 300) {
                    getLogger().warning("Discord webhook answered " + kode + ". Check discord.webhook_url in config.yml");
                }
                if (cs != null) {
                    Bukkit.getScheduler().runTask(ServerAnalytics.this, new Runnable() {
                        @Override
                        public void run() {
                            cs.sendMessage(c(kode >= 200 && kode < 300 ? "sent" : "send_failed", "{code}", String.valueOf(kode)));
                        }
                    });
                }
            }
        });
    }

    private List<DagData> uge() {
        List<DagData> l = new ArrayList<DagData>();
        LocalDate d = nu().toLocalDate();
        for (int i = 6; i >= 0; i = Math.subtractExact(i, 1)) {
            LocalDate x = d.minusDays(i);
            l.add(x.equals(idag.dato) ? idag : DagData.hent(getDataFolder(), x));
        }
        return l;
    }

    private void sendUge(final CommandSender cs) {
        final String url = getConfig().getString("discord.webhook_url", "");
        List<DagData> l = uge();
        Set<String> unikke = new HashSet<String>();
        int nye = 0;
        long spil = 0;
        int bedstPeak = 0;
        LocalDate bedstDag = null;
        StringBuilder dage = new StringBuilder();
        for (DagData d : l) {
            unikke.addAll(d.unikke);
            nye += d.nye.size();
            spil += d.samletSpilletid();
            if (d.peak > bedstPeak) {
                bedstPeak = d.peak;
                bedstDag = d.dato;
            }
            dage.append("`").append(datoTekst(d.dato)).append("` ").append(m("r_peak")).append(" **").append(d.peak)
                    .append("** • ").append(m("r_unique")).append(" **").append(d.unikke.size()).append("**\n");
        }
        List<String> f = new ArrayList<String>();
        f.add(felt(m("r_unique"), "**" + unikke.size() + "**", true));
        f.add(felt(m("r_new"), "**" + nye + "**", true));
        f.add(felt(m("r_playtime"), "**" + varighed(spil) + "**", true));
        f.add(felt(m("r_best_day"), bedstDag == null ? m("r_none") : "**" + datoTekst(bedstDag) + "** (" + bedstPeak + ")", false));
        final String json = "{\"username\":\"" + Webhook.esc(getConfig().getString("discord.bot_name", "ServerAnalytics"))
                + "\",\"embeds\":[{\"title\":\"" + Webhook.esc(m("r_week_title", "{server}", server())) + "\",\"description\":\""
                + Webhook.esc(dage.toString()) + "\",\"color\":" + getConfig().getInt("discord.color", 8086271) + ",\"fields\":["
                + String.join(",", f) + "],\"footer\":{\"text\":\"" + Webhook.esc(m("r_footer")) + "\"},\"timestamp\":\""
                + Instant.now().toString() + "\"}]}";
        Bukkit.getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                final int kode = Webhook.send(url, json, null);
                if (cs != null) {
                    Bukkit.getScheduler().runTask(ServerAnalytics.this, new Runnable() {
                        @Override
                        public void run() {
                            cs.sendMessage(c(kode >= 200 && kode < 300 ? "sent" : "send_failed", "{code}", String.valueOf(kode)));
                        }
                    });
                }
            }
        });
    }

    // ═══════════════ KOMMANDO ═══════════════

    /** Minecraft cannot show emojis, so they are removed in chat. */
    private static String ren(String t) {
        StringBuilder b = new StringBuilder();
        for (char ch : t.toCharArray()) {
            if (!Character.isSurrogate(ch) && ch != '\uFE0F' && ch != '\u200D') {
                b.append(ch);
            }
        }
        return b.toString().trim();
    }

    private void visDag(CommandSender s, DagData d, String titel) {
        s.sendMessage(c("chat_line"));
        s.sendMessage(c("chat_title", "{title}", titel, "{date}", datoTekst(d.dato)));
        s.sendMessage(c("chat_chart", "{chart}", Graf.linje(d)));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_peak")), "{value}", d.peak + " " + m("r_at") + " " + (d.peakTid.isEmpty() ? "?" : d.peakTid)));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_average")), "{value}", String.format("%.1f", d.snitHeleDagen())));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_unique")), "{value}", String.valueOf(d.unikke.size())));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_new")), "{value}", String.valueOf(d.nye.size())));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_playtime")), "{value}", varighed(d.samletSpilletid())));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_busiest")), "{value}", time(d.travlesteTime())));
        s.sendMessage(c("chat_row", "{name}", ren(m("r_tps")), "{value}", String.format("%.1f (min %.1f)", d.snitTps(), d.tpsMin)));
        s.sendMessage(c("chat_line"));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("serveranalytics.use")) {
            s.sendMessage(c("no_permission"));
            return true;
        }
        String a1 = a.length > 0 ? a[0].toLowerCase() : "today";
        if (a1.equals("reload")) {
            reloadConfig();
            indlaesBeskeder();
            s.sendMessage(c("reloaded"));
            return true;
        }
        if (a1.equals("report") || a1.equals("rapport")) {
            String url = getConfig().getString("discord.webhook_url", "");
            if (url == null || !url.startsWith("http")) {
                s.sendMessage(c("no_webhook"));
                return true;
            }
            s.sendMessage(c("sending"));
            if (a.length > 1 && a[1].equalsIgnoreCase("week")) {
                sendUge(s);
            } else {
                sendDaglig(idag, s);
            }
            return true;
        }
        if (a1.equals("yesterday") || a1.equals("igaar")) {
            visDag(s, DagData.hent(getDataFolder(), nu().toLocalDate().minusDays(1)), m("yesterday"));
            return true;
        }
        if (a1.equals("week") || a1.equals("uge")) {
            s.sendMessage(c("chat_line"));
            s.sendMessage(c("chat_title", "{title}", m("week"), "{date}", ""));
            for (DagData d : uge()) {
                s.sendMessage(c("chat_week_row", "{date}", datoTekst(d.dato), "{peak}", String.valueOf(d.peak),
                        "{unique}", String.valueOf(d.unikke.size()), "{new}", String.valueOf(d.nye.size())));
            }
            s.sendMessage(c("chat_line"));
            return true;
        }
        if (!a1.equals("today") && !a1.equals("idag")) {
            s.sendMessage(c("usage", "{label}", label));
            return true;
        }
        visDag(s, idag, m("today"));
        return true;
    }
}
