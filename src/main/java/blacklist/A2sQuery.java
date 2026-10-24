package blacklist;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Asks game servers directly for their game description (the "Game" column of the server browser)
 * using the Source A2S_INFO UDP query. The Steam Web API does not provide this field.
 */
final class A2sQuery {

    private static final byte[] HEADER = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] PAYLOAD = "TSource Engine Query\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final int BATCH = 25;
    private static final long BATCH_PAUSE_MS = 50;
    private static final long WAIT_MS = 2500;

    private A2sQuery() {
    }

    /**
     * Returns game description per "ip:queryPort". Servers that don't answer are missing.
     *
     * <p>Every queried address occupies an entry in the router's NAT table, and consumer routers
     * start dropping answers after a few thousand. So only up to {@code maxPerIp} servers per IP
     * are asked (distinct server names first), at a moderate rate, with one retry.
     */
    static Map<String, String> queryGames(List<SteamServerQuery.ServerInfo> servers, int maxPerIp,
                                          Consumer<String> progress) throws IOException {
        Map<String, String> games = new HashMap<>();
        List<InetSocketAddress> targets = interleaveByIp(servers, maxPerIp);
        ByteBuffer buf = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        try (DatagramChannel ch = DatagramChannel.open()) {
            ch.configureBlocking(false);
            ch.bind(null);
            for (int pass = 1; pass <= 2; pass++) {
                List<InetSocketAddress> todo = new ArrayList<>();
                for (InetSocketAddress t : targets) {
                    if (!games.containsKey(key(t))) {
                        todo.add(t);
                    }
                }
                for (int i = 0; i < todo.size(); i++) {
                    ch.send(ByteBuffer.wrap(request(null)), todo.get(i));
                    if (i % BATCH == BATCH - 1) {
                        sleep(BATCH_PAUSE_MS);
                        drain(ch, buf, games);
                    }
                    if (i % 500 == 499) {
                        progress.accept("Spielnamen (Durchgang " + pass + "): " + (i + 1) + "/" + todo.size()
                                + " abgefragt, " + games.size() + " Antworten");
                    }
                }
                long end = System.currentTimeMillis() + WAIT_MS;
                while (System.currentTimeMillis() < end) {
                    sleep(BATCH_PAUSE_MS);
                    drain(ch, buf, games);
                }
            }
        }
        progress.accept("Spielnamen: " + games.size() + " von " + targets.size() + " Servern antworteten");
        return games;
    }

    private static String key(InetSocketAddress a) {
        return a.getAddress().getHostAddress() + ":" + a.getPort();
    }

    /** Round-robin over IPs so a host with hundreds of ports doesn't get them all at once. */
    private static List<InetSocketAddress> interleaveByIp(List<SteamServerQuery.ServerInfo> servers, int maxPerIp) {
        Map<String, List<SteamServerQuery.ServerInfo>> grouped = new LinkedHashMap<>();
        for (SteamServerQuery.ServerInfo s : servers) {
            if (s.queryPort() > 0) {
                grouped.computeIfAbsent(s.ip(), k -> new ArrayList<>()).add(s);
            }
        }
        Map<String, Deque<InetSocketAddress>> byIp = new LinkedHashMap<>();
        for (Map.Entry<String, List<SteamServerQuery.ServerInfo>> e : grouped.entrySet()) {
            // Distinct server names first, then fill up with the remaining ports.
            Set<String> names = new HashSet<>();
            Set<InetSocketAddress> picks = new LinkedHashSet<>();
            for (SteamServerQuery.ServerInfo s : e.getValue()) {
                if (picks.size() < maxPerIp && names.add(s.name())) {
                    picks.add(new InetSocketAddress(s.ip(), s.queryPort()));
                }
            }
            for (SteamServerQuery.ServerInfo s : e.getValue()) {
                if (picks.size() < maxPerIp) {
                    picks.add(new InetSocketAddress(s.ip(), s.queryPort()));
                }
            }
            byIp.put(e.getKey(), new ArrayDeque<>(picks));
        }
        List<InetSocketAddress> result = new ArrayList<>(servers.size());
        List<Deque<InetSocketAddress>> queues = new ArrayList<>(byIp.values());
        while (!queues.isEmpty()) {
            queues.removeIf(q -> {
                result.add(q.poll());
                return q.isEmpty();
            });
        }
        return result;
    }

    private static void drain(DatagramChannel ch, ByteBuffer buf, Map<String, String> games) throws IOException {
        Map<SocketAddress, byte[]> challenges = new HashMap<>();
        while (true) {
            buf.clear();
            SocketAddress from = ch.receive(buf);
            if (from == null) {
                break;
            }
            buf.flip();
            if (buf.remaining() < 5 || buf.getInt() != -1) {
                continue; // split packets (0xFFFFFFFE) are not used for A2S_INFO in practice
            }
            byte type = buf.get();
            if (type == 0x41 && buf.remaining() >= 4) {
                byte[] challenge = new byte[4];
                buf.get(challenge);
                challenges.put(from, challenge);
            } else if (type == 0x49 && from instanceof InetSocketAddress isa) {
                String game = parseGame(buf);
                if (game != null) {
                    games.put(key(isa), game);
                }
            }
        }
        for (Map.Entry<SocketAddress, byte[]> e : challenges.entrySet()) {
            ch.send(ByteBuffer.wrap(request(e.getValue())), e.getKey());
        }
    }

    /** A2S_INFO response: protocol(byte) name map folder game ... (null-terminated strings). */
    private static String parseGame(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            return null;
        }
        buf.get();
        String game = null;
        for (int i = 0; i < 4; i++) {
            game = readString(buf);
            if (game == null) {
                return null;
            }
        }
        return game;
    }

    private static String readString(ByteBuffer buf) {
        int start = buf.position();
        while (buf.hasRemaining()) {
            if (buf.get() == 0) {
                int len = buf.position() - start - 1;
                return new String(buf.array(), buf.arrayOffset() + start, len, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static byte[] request(byte[] challenge) {
        int len = HEADER.length + PAYLOAD.length + (challenge != null ? challenge.length : 0);
        ByteBuffer b = ByteBuffer.allocate(len);
        b.put(HEADER).put(PAYLOAD);
        if (challenge != null) {
            b.put(challenge);
        }
        return b.array();
    }

    private static void sleep(long ms) throws IOException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        }
    }
}
