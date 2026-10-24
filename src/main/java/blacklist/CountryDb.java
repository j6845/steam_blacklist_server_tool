package blacklist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * IPv4 -> country lookup based on the public RIR "delegated-extended" statistics.
 * Country = registration country of the address block, not the physical location.
 */
final class CountryDb {

    private static final String[] SOURCES = {
        "https://ftp.ripe.net/pub/stats/ripencc/delegated-ripencc-extended-latest",
        "https://ftp.arin.net/pub/stats/arin/delegated-arin-extended-latest",
        "https://ftp.apnic.net/stats/apnic/delegated-apnic-extended-latest",
        "https://ftp.lacnic.net/pub/stats/lacnic/delegated-lacnic-extended-latest",
        "https://ftp.afrinic.net/pub/stats/afrinic/delegated-afrinic-extended-latest",
    };
    private static final Duration MAX_AGE = Duration.ofDays(7);

    private final long[] starts;
    private final long[] ends;
    private final String[] countries;

    private CountryDb(long[] starts, long[] ends, String[] countries) {
        this.starts = starts;
        this.ends = ends;
        this.countries = countries;
    }

    static Path cacheDir() {
        return Path.of(System.getProperty("user.home"), ".steam_blacklist_tool", "rir");
    }

    /** Loads from cache, downloading files that are missing or older than 7 days. */
    static CountryDb load(Consumer<String> progress) throws IOException {
        Path dir = cacheDir();
        Files.createDirectories(dir);
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        List<Range> ranges = new ArrayList<>();
        for (String url : SOURCES) {
            Path file = dir.resolve(url.substring(url.lastIndexOf('/') + 1));
            if (isStale(file)) {
                progress.accept("Lade " + file.getFileName() + " ...");
                try {
                    download(http, url, file);
                } catch (IOException e) {
                    if (!Files.exists(file)) {
                        throw new IOException("Download fehlgeschlagen: " + url + " (" + e.getMessage() + ")", e);
                    }
                    progress.accept("Download fehlgeschlagen, nutze Cache: " + file.getFileName());
                }
            }
            progress.accept("Lese " + file.getFileName() + " ...");
            parse(file, ranges);
        }
        ranges.sort((a, b) -> Long.compare(a.start, b.start));
        int n = ranges.size();
        long[] s = new long[n];
        long[] e = new long[n];
        String[] c = new String[n];
        for (int i = 0; i < n; i++) {
            Range r = ranges.get(i);
            s[i] = r.start;
            e[i] = r.end;
            c[i] = r.country;
        }
        progress.accept(n + " IP-Bereiche geladen");
        return new CountryDb(s, e, c);
    }

    /** ISO country code (e.g. "RU") or "??" if unknown. */
    String lookup(String ip) {
        long v = Ip.toLong(ip);
        if (v < 0) {
            return "??";
        }
        int idx = Arrays.binarySearch(starts, v);
        if (idx < 0) {
            idx = -idx - 2;
        }
        if (idx >= 0 && v <= ends[idx]) {
            return countries[idx];
        }
        return "??";
    }

    private static boolean isStale(Path file) throws IOException {
        return !Files.exists(file)
                || Files.getLastModifiedTime(file).toInstant().isBefore(Instant.now().minus(MAX_AGE));
    }

    private static void download(HttpClient http, String url, Path target) throws IOException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build();
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try {
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = resp.body()) {
                if (resp.statusCode() != 200) {
                    throw new IOException("HTTP " + resp.statusCode());
                }
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Line format: registry|cc|ipv4|start|count|date|status[|...] */
    private static void parse(Path file, List<Range> out) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] f = line.split("\\|", -1);
                if (f.length < 7 || !f[2].equals("ipv4") || f[1].length() != 2) {
                    continue;
                }
                if (!f[6].equals("allocated") && !f[6].equals("assigned")) {
                    continue;
                }
                long start = Ip.toLong(f[3]);
                long count;
                try {
                    count = Long.parseLong(f[4]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (start >= 0 && count > 0) {
                    out.add(new Range(start, start + count - 1, f[1].toUpperCase()));
                }
            }
        }
    }

    private record Range(long start, long end, String country) {
    }
}
