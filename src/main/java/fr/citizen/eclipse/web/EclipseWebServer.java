package fr.citizen.eclipse.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.citizen.eclipse.CitizenEclipse;
import fr.citizen.eclipse.config.EclipseConfig;
import fr.citizen.eclipse.schedule.Eclipse;
import fr.citizen.eclipse.schedule.EclipseSchedule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Petit site web intégré (aucune dépendance) :
 *   /               calendrier des éclipses
 *   /api/eclipses   données JSON
 *   /eclipses.ics   abonnement agenda (téléphone, Google Agenda…)
 */
public final class EclipseWebServer {
    private EclipseWebServer() {}

    private static ServerSocket socket;
    private static ExecutorService executor;
    private static Thread acceptThread;
    private static byte[] indexHtml;

    private static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /*
     * Serveur HTTP minimal écrit avec java.net (module java.base uniquement), pour ne dépendre
     * d'aucun module JDK optionnel qui pourrait manquer dans l'environnement NeoForge.
     */
    public static synchronized void start() {
        stop();
        EclipseConfig.Web cfg = EclipseConfig.get().web;
        try (InputStream in = EclipseWebServer.class.getResourceAsStream("/citizeneclipse_web/index.html")) {
            indexHtml = in == null ? "<h1>Citizen Eclipse</h1>".getBytes(StandardCharsets.UTF_8) : in.readAllBytes();
        } catch (IOException e) {
            indexHtml = "<h1>Citizen Eclipse</h1>".getBytes(StandardCharsets.UTF_8);
        }
        try {
            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(cfg.adresseEcoute, cfg.port), 32);
            socket = ss;
            executor = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "CitizenEclipse-Web");
                t.setDaemon(true);
                return t;
            });
            ExecutorService pool = executor;
            acceptThread = new Thread(() -> {
                while (!ss.isClosed()) {
                    try {
                        Socket client = ss.accept();
                        client.setSoTimeout(5000);
                        pool.execute(() -> serve(client));
                    } catch (Exception e) {
                        if (ss.isClosed()) break;
                    }
                }
            }, "CitizenEclipse-WebAccept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            CitizenEclipse.LOGGER.info("[Eclipse] Calendrier web actif sur le port {} ({})", cfg.port, cfg.urlPublique);
        } catch (Exception e) {
            CitizenEclipse.LOGGER.error("[Eclipse] Impossible d'ouvrir le calendrier web sur le port {} : {}", cfg.port, e.toString());
            stop();
        }
    }

    public static synchronized void stop() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            socket = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        acceptThread = null;
    }

    private static void serve(Socket client) {
        try (client) {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
            String requestLine = in.readLine();
            if (requestLine == null) return;
            // On lit (et ignore) les en-têtes.
            String line;
            int guard = 0;
            while ((line = in.readLine()) != null && !line.isEmpty() && guard++ < 100) { }

            String[] parts = requestLine.split(" ");
            OutputStream out = client.getOutputStream();
            if (parts.length < 2) {
                write(out, 400, "text/plain; charset=utf-8", "Requete invalide".getBytes(StandardCharsets.UTF_8), false);
                return;
            }
            String method = parts[0].toUpperCase();
            boolean head = method.equals("HEAD");
            if (!method.equals("GET") && !head) {
                write(out, 405, "text/plain; charset=utf-8", "Methode non autorisee".getBytes(StandardCharsets.UTF_8), false);
                return;
            }
            String path = parts[1];
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            switch (path) {
                case "/", "/index.html" -> write(out, 200, "text/html; charset=utf-8", indexHtml, head);
                case "/api/eclipses" -> write(out, 200, "application/json; charset=utf-8", json().getBytes(StandardCharsets.UTF_8), head);
                case "/eclipses.ics" -> write(out, 200, "text/calendar; charset=utf-8", ics().getBytes(StandardCharsets.UTF_8), head);
                case "/favicon.ico" -> write(out, 204, "text/plain", new byte[0], true);
                default -> write(out, 404, "text/plain; charset=utf-8", "Introuvable".getBytes(StandardCharsets.UTF_8), head);
            }
        } catch (Exception ignored) {
        }
    }

    private static void write(OutputStream out, int code, String type, byte[] body, boolean head) throws IOException {
        String status = switch (code) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Error";
        };
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + (code == 204 ? 0 : body.length) + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        if (!head && code != 204) out.write(body);
        out.flush();
    }

    private static String json() {
        EclipseConfig cfg = EclipseConfig.get();
        long now = System.currentTimeMillis();
        JsonObject root = new JsonObject();
        root.addProperty("now", now);
        root.addProperty("timezone", cfg.zone().getId());
        root.addProperty("server", cfg.web.nomServeur);
        root.addProperty("approachMinutes", cfg.approcheMinutes);

        JsonObject hours = new JsonObject();
        for (DayOfWeek d : DayOfWeek.values()) {
            EclipseConfig.Window w = cfg.window(d);
            hours.addProperty(d.name(), w == null ? "" : w.start() + "-" + w.end());
        }
        root.add("openingHours", hours);

        JsonArray arr = new JsonArray();
        List<Eclipse> list = EclipseSchedule.get().snapshot();
        for (Eclipse e : list) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id);
            o.addProperty("approachStart", e.approachStart);
            o.addProperty("totalityStart", e.totalityStart);
            o.addProperty("totalityEnd", e.totalityEnd);
            o.addProperty("recedeEnd", e.recedeEnd);
            o.addProperty("source", e.source);
            o.addProperty("phase", e.phase(now).key);
            arr.add(o);
        }
        root.add("eclipses", arr);
        return root.toString();
    }

    private static String ics() {
        StringBuilder sb = new StringBuilder();
        String nl = "\r\n";
        sb.append("BEGIN:VCALENDAR").append(nl)
                .append("VERSION:2.0").append(nl)
                .append("PRODID:-//Citizen//Eclipse//FR").append(nl)
                .append("CALSCALE:GREGORIAN").append(nl)
                .append("X-WR-CALNAME:Éclipses Citizen").append(nl);
        String stamp = ICS.format(Instant.now());
        for (Eclipse e : EclipseSchedule.get().snapshot()) {
            sb.append("BEGIN:VEVENT").append(nl)
                    .append("UID:eclipse-").append(e.id).append("-").append(e.totalityStart).append("@citizen").append(nl)
                    .append("DTSTAMP:").append(stamp).append(nl)
                    .append("DTSTART:").append(ICS.format(Instant.ofEpochMilli(e.approachStart))).append(nl)
                    .append("DTEND:").append(ICS.format(Instant.ofEpochMilli(e.recedeEnd))).append(nl)
                    .append("SUMMARY:☾ Éclipse sur Citizen").append(nl)
                    .append("DESCRIPTION:La lune s'approche du soleil. Éclipse totale à ")
                    .append(fr.citizen.eclipse.Texts.hour(e.totalityStart))
                    .append(". Prévoyez un casque d'éclipse !").append(nl)
                    .append("BEGIN:VALARM").append(nl)
                    .append("TRIGGER:-PT30M").append(nl)
                    .append("ACTION:DISPLAY").append(nl)
                    .append("DESCRIPTION:Éclipse sur Citizen dans 30 minutes").append(nl)
                    .append("END:VALARM").append(nl)
                    .append("END:VEVENT").append(nl);
        }
        sb.append("END:VCALENDAR").append(nl);
        return sb.toString();
    }
}
