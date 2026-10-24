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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Downloads the game server list via Steam Web API IGameServersService/GetServerList. */
final class SteamServerQuery {

    /** One game server as reported by the master server. */
    record ServerInfo(String ip, int port, String name, String map, int players, int maxPlayers, int bots) {
    }

    private static final String ENDPOINT = "https://api.steampowered.com/IGameServersService/GetServerList/v1/";
    private static final int[] REGIONS = {0, 1, 2, 3, 4, 5, 6, 7, 255};
    // The API caps results per request, so the query is split into region x (has players | empty).
    private static final String[] PLAYER_FILTERS = {"\\empty\\1", "\\noplayers\\1"};
    private static final int LIMIT = 50000;

    private SteamServerQuery() {
    }

    static List<ServerInfo> fetch(String apiKey, int appId, Consumer<String> progress) throws IOException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
        List<String> filters = new ArrayList<>();
        for (int region : REGIONS) {
            for (String pf : PLAYER_FILTERS) {
                filters.add("\\appid\\" + appId + "\\region\\" + region + pf);
            }
        }
        progress.accept("Frage " + filters.size() + " Teillisten bei Steam ab ...");
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String filter : filters) {
            String url = ENDPOINT + "?key=" + enc(apiKey) + "&limit=" + LIMIT + "&filter=" + enc(filter);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).build();
            futures.add(http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(resp -> {
                if (resp.statusCode() == 401 || resp.statusCode() == 403) {
                    throw new CompletionException(new IOException("Steam API Key ungültig (HTTP " + resp.statusCode() + ")"));
                }
                if (resp.statusCode() != 200) {
                    throw new CompletionException(new IOException("Steam API HTTP " + resp.statusCode()));
                }
                return resp.body();
            }));
        }

        // Same server can show up in several sublists; dedupe by ip:port.
        Map<String, ServerInfo> servers = new LinkedHashMap<>();
        int done = 0;
        for (CompletableFuture<String> f : futures) {
            String body;
            try {
                body = f.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw cause instanceof IOException io ? io : new IOException(cause.getMessage(), cause);
            }
            for (ServerInfo s : parse(body)) {
                servers.putIfAbsent(s.ip() + ":" + s.port(), s);
            }
            done++;
            progress.accept("Teilliste " + done + "/" + futures.size() + " - " + servers.size() + " Server");
        }
        return new ArrayList<>(servers.values());
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
            int port = num(m.get("gameport"));
            if (port == 0) {
                port = Math.max(Ip.portOf(addr), 0);
            }
            result.add(new ServerInfo(ip, port, str(m.get("name")), str(m.get("map")),
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
