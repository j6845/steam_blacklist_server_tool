package blacklist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Groups servers per IP and flags IPs matching any of the criteria. Rules look at name, game and tags. */
final class ServerScan {

    /** ".ru" domain, e.g. "SGaming.RU", but not ".rules". */
    private static final Pattern RU_DOMAIN = Pattern.compile("(?i)\\.ru(?![a-z0-9])");
    /** "ru" as a whole tag, e.g. "bhop,ru,startmoney". */
    private static final Pattern RU_TAG = Pattern.compile("(?i)(^|,)\\s*ru\\s*(,|$)");
    /** "NoSteam", "no-steam", "no steam", "non-steam", "nonsteam" (but not "onlysteam"). */
    private static final Pattern NO_STEAM = Pattern.compile("(?i)(?<![a-z])non?[-_ ]?steam(?![a-z])");

    /** Disabled criteria: empty set / false / empty list. */
    record Criteria(Set<String> countries, boolean russian, boolean noGame, boolean noSteam,
                    List<String> keywords) {
    }

    /** All servers sharing one IP. */
    record IpGroup(String ip, String country, int serverCount, int players,
                   String sampleName, String sampleGame, String sampleTags, String reason) {

        boolean flagged() {
            return !reason.isEmpty();
        }
    }

    private ServerScan() {
    }

    static boolean hasCyrillic(String s) {
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC);
    }

    /** Game description made only of symbols, e.g. "█ █ █ █". Unknown (null) is not "missing". */
    static boolean gameMissing(String game) {
        return game != null && game.codePoints().noneMatch(Character::isLetterOrDigit);
    }

    /**
     * @param games game description per "ip:queryPort" from {@link A2sQuery}; servers that didn't answer are missing
     */
    static List<IpGroup> analyze(List<SteamServerQuery.ServerInfo> servers, CountryDb geo,
                                 Map<String, String> games, Criteria c) {
        Map<String, List<SteamServerQuery.ServerInfo>> byIp = new LinkedHashMap<>();
        for (SteamServerQuery.ServerInfo s : servers) {
            byIp.computeIfAbsent(s.ip(), k -> new ArrayList<>()).add(s);
        }
        List<IpGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<SteamServerQuery.ServerInfo>> e : byIp.entrySet()) {
            String ip = e.getKey();
            List<SteamServerQuery.ServerInfo> list = e.getValue();
            String country = geo != null ? geo.lookup(ip) : "??";

            // Each reason is reported once per IP; the first server that triggered anything is shown as sample.
            Set<String> reasons = new LinkedHashSet<>();
            if (c.countries().contains(country)) {
                reasons.add("Land " + country);
            }
            SteamServerQuery.ServerInfo sample = null;
            int players = 0;
            for (SteamServerQuery.ServerInfo s : list) {
                players += s.players();
                String game = games.get(s.ip() + ":" + s.queryPort());
                int before = reasons.size();
                check(s, game, c, reasons);
                if (sample == null && reasons.size() > before) {
                    sample = s;
                }
            }
            if (sample == null) {
                sample = list.get(0);
            }
            String sampleGame = games.getOrDefault(sample.ip() + ":" + sample.queryPort(), "");
            result.add(new IpGroup(ip, country, list.size(), players, sample.name(), sampleGame, sample.tags(),
                    String.join(", ", reasons)));
        }
        result.sort((a, b) -> Integer.compare(b.serverCount(), a.serverCount()));
        return result;
    }

    private static void check(SteamServerQuery.ServerInfo s, String game, Criteria c, Set<String> reasons) {
        String[] texts = {s.name(), game != null ? game : "", s.tags()};
        if (c.russian()) {
            if (anyMatch(texts, ServerScan::hasCyrillic)) {
                reasons.add("Kyrillisch");
            }
            if (anyMatch(texts, t -> RU_DOMAIN.matcher(t).find())) {
                reasons.add(".ru");
            }
            if (RU_TAG.matcher(s.tags()).find()) {
                reasons.add("Tag ru");
            }
        }
        if (c.noGame() && gameMissing(game)) {
            reasons.add("Spiel fehlt");
        }
        if (c.noSteam() && anyMatch(texts, t -> NO_STEAM.matcher(t).find())) {
            reasons.add("NoSteam");
        }
        for (String k : c.keywords()) {
            String lower = k.toLowerCase(Locale.ROOT);
            if (anyMatch(texts, t -> t.toLowerCase(Locale.ROOT).contains(lower))) {
                reasons.add("Wort '" + k + "'");
            }
        }
    }

    private static boolean anyMatch(String[] texts, Predicate<String> p) {
        for (String t : texts) {
            if (p.test(t)) {
                return true;
            }
        }
        return false;
    }
}
