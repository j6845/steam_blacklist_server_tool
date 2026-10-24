package blacklist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Groups servers per IP and flags IPs matching any of the criteria. */
final class ServerScan {

    /** Disabled criteria: empty set / false / empty list. */
    record Criteria(Set<String> countries, boolean cyrillic, List<String> nameKeywords) {
    }

    /** All servers sharing one IP. */
    record IpGroup(String ip, String country, int serverCount, int players,
                   String sampleName, String sampleGame, String reason) {

        boolean flagged() {
            return !reason.isEmpty();
        }
    }

    private ServerScan() {
    }

    static boolean hasCyrillic(String s) {
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.CYRILLIC);
    }

    /** Servers on IPs where no server name contains Cyrillic; only these need a game description lookup. */
    static List<SteamServerQuery.ServerInfo> withoutCyrillicName(List<SteamServerQuery.ServerInfo> servers) {
        Set<String> cyrillicIps = new HashSet<>();
        for (SteamServerQuery.ServerInfo s : servers) {
            if (hasCyrillic(s.name())) {
                cyrillicIps.add(s.ip());
            }
        }
        return servers.stream().filter(s -> !cyrillicIps.contains(s.ip())).toList();
    }

    /**
     * @param games game description per "ip:queryPort" from {@link A2sQuery}, may be empty
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
            int players = 0;
            String keywordHit = null;
            SteamServerQuery.ServerInfo cyrillicHit = null;
            String sampleGame = "";
            for (SteamServerQuery.ServerInfo s : list) {
                players += s.players();
                String game = games.getOrDefault(s.ip() + ":" + s.queryPort(), "");
                if (sampleGame.isEmpty()) {
                    sampleGame = game;
                }
                if (keywordHit == null) {
                    keywordHit = matchKeyword(s.name(), c.nameKeywords());
                }
                if (cyrillicHit == null && (hasCyrillic(s.name()) || hasCyrillic(game))) {
                    cyrillicHit = s;
                    sampleGame = game;
                }
            }

            List<String> reasons = new ArrayList<>();
            if (c.countries().contains(country)) {
                reasons.add("Land " + country);
            }
            if (c.cyrillic() && cyrillicHit != null) {
                reasons.add("Kyrillisch");
            }
            if (keywordHit != null) {
                reasons.add("Name '" + keywordHit + "'");
            }
            // Show the server that triggered the Cyrillic rule, it explains the flag best.
            String sampleName = (c.cyrillic() && cyrillicHit != null ? cyrillicHit : list.get(0)).name();
            result.add(new IpGroup(ip, country, list.size(), players,
                    sampleName, sampleGame, String.join(", ", reasons)));
        }
        result.sort((a, b) -> Integer.compare(b.serverCount(), a.serverCount()));
        return result;
    }

    private static String matchKeyword(String name, List<String> keywords) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String k : keywords) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) {
                return k;
            }
        }
        return null;
    }
}
