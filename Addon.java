import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.*;

public class Addon {
  static final String PLAYLIST = System.getenv("https://wecima.cx/") != null
      ? System.getenv("https://wecima.cx/") : "https://iptv-org.github.io/iptv/languages/ara.m3u";
  static final String PREFIX = "arabtv:";

  record Ch(String name, String logo, String url) {}

  static List<Ch> channels = List.of();
  static long loaded = 0;
  static final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

  static synchronized List<Ch> load() {
    if (!channels.isEmpty() && System.currentTimeMillis() - loaded < 6 * 3600_000L) return channels;
    try {
      String body = http.send(HttpRequest.newBuilder(URI.create(PLAYLIST)).build(),
          HttpResponse.BodyHandlers.ofString()).body();
      List<Ch> out = new ArrayList<>();
      Pattern lg = Pattern.compile("tvg-logo=\"([^\"]*)\"");
      String name = null, logo = "";
      for (String l : body.split("\\R")) {
        l = l.trim();
        if (l.startsWith("#EXTINF")) {
          Matcher m = lg.matcher(l);
          logo = m.find() ? m.group(1) : "";
          name = l.substring(l.lastIndexOf(',') + 1).trim();
        } else if (!l.isEmpty() && !l.startsWith("#")) {
          if (name == null) { name = l.substring(l.lastIndexOf('/') + 1); logo = ""; }
          if (l.startsWith("https://")) out.add(new Ch(name, logo, l));
          name = null;
        }
      }
      if (!out.isEmpty()) { channels = out; loaded = System.currentTimeMillis(); }
    } catch (Exception e) { e.printStackTrace(); }
    return channels;
  }

  static String esc(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  static String metaJson(int i, Ch c) {
    String logo = c.logo().isEmpty() ? "" : ",\"poster\":\"" + esc(c.logo()) + "\",\"logo\":\"" + esc(c.logo()) + "\"";
    return "{\"id\":\"" + PREFIX + i + "\",\"type\":\"tv\",\"name\":\"" + esc(c.name()) + "\"" + logo + "}";
  }

  public static void main(String[] args) throws Exception {
    String p = System.getenv("PORT");
    int port = p == null ? 7000 : Integer.parseInt(p);
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(Executors.newFixedThreadPool(8));

    server.createContext("/", ex -> {
      String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
      String json = "{}";
      try {
        if (path.equals("/manifest.json")) {
          json = "{\"id\":\"org.zak.arabictv\",\"version\":\"1.0.0\",\"name\":\"Arabic TV\","
              + "\"description\":\"Free Arabic live TV channels\",\"resources\":[\"catalog\",\"meta\",\"stream\"],"
              + "\"types\":[\"tv\"],\"idPrefixes\":[\"" + PREFIX + "\"],"
              + "\"catalogs\":[{\"type\":\"tv\",\"id\":\"arabic\",\"name\":\"Arabic TV\"}]}";
        } else if (path.startsWith("/catalog/tv/arabic")) {
          List<Ch> list = load();
          StringBuilder sb = new StringBuilder("{\"metas\":[");
          for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(metaJson(i, list.get(i)));
          }
          json = sb.append("]}").toString();
        } else if (path.startsWith("/meta/tv/") || path.startsWith("/stream/tv/")) {
          String id = path.substring(path.lastIndexOf('/') + 1).replace(".json", "");
          int i = Integer.parseInt(id.substring(PREFIX.length()));
          Ch c = load().get(i);
          if (path.startsWith("/meta/")) json = "{\"meta\":" + metaJson(i, c) + "}";
          else json = "{\"streams\":[{\"title\":\"" + esc(c.name()) + "\",\"url\":\"" + esc(c.url()) + "\"}]}";
        }
      } catch (Exception e) {
        json = path.startsWith("/stream/") ? "{\"streams\":[]}" : "{}";
      }
      send(ex, json);
    });

    server.start();
    System.out.println("Running on port " + port + " -> /manifest.json");
  }

  static void send(HttpExchange ex, String body) throws IOException {
    byte[] b = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    ex.getResponseHeaders().add("Content-Type", "application/json");
    ex.sendResponseHeaders(200, b.length);
    ex.getResponseBody().write(b);
    ex.close();
  }
}
