package blacklist;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Downloads the game server list via Steam Web API IGameServersService/GetServerList.
 *
 * <p>The API returns at most {@value #CAP} servers per request. Any sublist that hits the cap is split
 * further by the next filter dimension until every sublist is complete.
 */
final class SteamServerQuery {

    /** One game server as reported by the master server. */
    /** {@code tags} is the comma-separated tag list (API field "gametype"), shown as "Tags" in the browser. */
    record ServerInfo(String ip, int port, int queryPort, String name, String map, String tags,
                      int players, int maxPlayers, int bots) {

        String addr() {
            return ip + ":" + port;
        }
    }

    /** Download result; {@code truncated} counts sublists that could not be split below the cap. */
    record Result(List<ServerInfo> servers, int requests, int truncated) {
    }

    private static final String ENDPOINT = "https://api.steampowered.com/IGameServersService/GetServerList/v1/";
    private static final int CAP = 10000;
    private static final int PARALLEL = 6;
    private static final int MAP_SPLIT = 20;

    /**
     * Each dimension partitions the servers: every server matches exactly one part.
     * {@code \nor\N\a\b...} matches servers not matching any of the N conditions (the "rest").
     */
    private static final List<List<String>> DIMENSIONS = List.of(
            regions(),
            List.of("\\empty\\1", "\\noplayers\\1"),
            List.of("\\secure\\1", "\\nor\\1\\secure\\1"),
            List.of("\\dedicated\\1", "\\nor\\1\\dedicated\\1"),
            List.of("\\linux\\1", "\\nor\\1\\linux\\1"),
            List.of("\\password\\0", "\\nor\\1\\password\\0"));

    private SteamServerQuery() {
    }

    private static List<String> regions() {
        int[] codes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 255};
        List<String> parts = new ArrayList<>();
        StringBuilder rest = new StringBuilder("\\nor\\" + codes.length);
        for (int code : codes) {
            parts.add("\\region\\" + code);
            rest.append("\\region\\").append(code);
        }
        // Catches region -1 and anything else, which cannot be filtered directly.
        parts.add(rest.toString());
        return parts;
    }

    private record Sublist(String filter, int depth, List<ServerInfo> servers) {
    }

    static Result fetch(String apiKey, int appId, Consumer<String> progress) throws IOException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        Map<String, ServerInfo> servers = new LinkedHashMap<>();
        int requests = 0;
        int truncated = 0;
        try {
            List<Sublist> pending = List.of(new Sublist("\\appid\\" + appId, 0, null));
            while (!pending.isEmpty()) {
                List<Future<Sublist>> futures = new ArrayList<>();
                for (Sublist s : pending) {
                    futures.add(pool.submit(download(http, apiKey, s)));
                }
                List<Sublist> next = new ArrayList<>();
                for (Future<Sublist> f : futures) {
                    Sublist done = join(f);
                    requests++;
                    for (ServerInfo info : done.servers()) {
                        servers.putIfAbsent(info.addr(), info);
                    }
                    if (done.servers().size() >= CAP) {
                        List<Sublist> parts = split(done);
                        if (parts.isEmpty()) {
                            truncated++;
                        }
                        next.addAll(parts);
                    }
                    progress.accept(requests + " Anfragen, " + servers.size() + " Server ...");
                }
                pending = next;
            }
        } finally {
            pool.shutdownNow();
        }
        return new Result(new ArrayList<>(servers.values()), requests, truncated);
    }

    /** Splits a capped sublist by the next dimension, finally by its most frequent maps. */
    private static List<Sublist> split(Sublist s) {
        List<Sublist> parts = new ArrayList<>();
        if (s.depth() < DIMENSIONS.size()) {
            for (String part : DIMENSIONS.get(s.depth())) {
                parts.add(new Sublist(s.filter() + part, s.depth() + 1, null));
            }
            return parts;
        }
        if (s.depth() > DIMENSIONS.size()) {
            return parts;
        }
        List<String> maps = s.servers().stream()
                .map(ServerInfo::map)
                .filter(m -> !m.isEmpty() && m.indexOf('\\') < 0)
                .collect(Collectors.groupingBy(m -> m, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(MAP_SPLIT)
                .map(Map.Entry::getKey)
                .toList();
        StringBuilder rest = new StringBuilder("\\nor\\" + maps.size());
        for (String map : maps) {
            parts.add(new Sublist(s.filter() + "\\map\\" + map, s.depth() + 1, null));
            rest.append("\\map\\").append(map);
        }
        parts.add(new Sublist(s.filter() + rest, s.depth() + 1, null));
        return parts;
    }

    private static Callable<Sublist> download(HttpClient http, String apiKey, Sublist s) {
        return () -> {
            String url = ENDPOINT + "?key=" + enc(apiKey) + "&limit=" + CAP + "&filter=" + enc(s.filter());
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                throw new IOException("Steam API Key ungültig (HTTP " + resp.statusCode() + ")");
            }
            if (resp.statusCode() != 200) {
                throw new IOException("Steam API HTTP " + resp.statusCode());
            }
            return new Sublist(s.filter(), s.depth(), parse(resp.body()));
        };
    }

    private static Sublist join(Future<Sublist> f) throws IOException {
        try {
            return f.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw cause instanceof IOException io ? io : new IOException(cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ServerInfo> parse(String body) throws IOException {
        List<ServerInfo> result = new ArrayList<>();
        Object root;
        try {
            root = Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new IOException("Ungültige Antwort von Steam: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("response") instanceof Map<?, ?> response)) {
            return result;
        }
        if (!(response.get("servers") instanceof List<?> list)) {
            return result;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) o;
            String addr = str(m.get("addr"));
            String ip = Ip.hostOf(addr);
            if (ip == null) {
                continue;
            }
            // "addr" carries the query port, "gameport" the port players connect to.
            int queryPort = Math.max(Ip.portOf(addr), 0);
            int port = num(m.get("gameport"));
            if (port == 0) {
                port = queryPort;
            }
            result.add(new ServerInfo(ip, port, queryPort, str(m.get("name")), str(m.get("map")), str(m.get("gametype")),
                    num(m.get("players")), num(m.get("max_players")), num(m.get("bots"))));
        }
        return result;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
