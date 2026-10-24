package blacklist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Groups servers per IP and flags IPs matching any of the criteria. */
final class ServerScan {

    /** Disabled criteria: empty set / 0 / empty list. */
    record Criteria(Set<String> countries, int minServersPerIp, int maxPlayersAbove, List<String> nameKeywords) {
    }

    /** All servers sharing one IP. */
    record IpGroup(String ip, String country, int serverCount, int maxPlayers, int players,
                   String sampleName, String reason) {

        boolean flagged() {
            return !reason.isEmpty();
        }
    }

    private ServerScan() {
    }

    static List<IpGroup> analyze(List<SteamServerQuery.ServerInfo> servers, CountryDb geo, Criteria c) {
        Map<String, List<SteamServerQuery.ServerInfo>> byIp = new LinkedHashMap<>();
        for (SteamServerQuery.ServerInfo s : servers) {
            byIp.computeIfAbsent(s.ip(), k -> new ArrayList<>()).add(s);
        }
        List<IpGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<SteamServerQuery.ServerInfo>> e : byIp.entrySet()) {
            String ip = e.getKey();
            List<SteamServerQuery.ServerInfo> list = e.getValue();
            String country = geo != null ? geo.lookup(ip) : "??";
            int maxPlayers = 0;
            int players = 0;
            String keywordHit = null;
            for (SteamServerQuery.ServerInfo s : list) {
                maxPlayers = Math.max(maxPlayers, s.maxPlayers());
                players += s.players();
                if (keywordHit == null) {
                    keywordHit = matchKeyword(s.name(), c.nameKeywords());
                }
            }

            List<String> reasons = new ArrayList<>();
            if (c.countries().contains(country)) {
                reasons.add("Land " + country);
            }
            if (c.minServersPerIp() > 0 && list.size() >= c.minServersPerIp()) {
                reasons.add(list.size() + " Server/IP");
            }
            if (c.maxPlayersAbove() > 0 && maxPlayers > c.maxPlayersAbove()) {
                reasons.add("max_players " + maxPlayers);
            }
            if (keywordHit != null) {
                reasons.add("Name '" + keywordHit + "'");
            }
            result.add(new IpGroup(ip, country, list.size(), maxPlayers, players,
                    list.get(0).name(), String.join(", ", reasons)));
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
