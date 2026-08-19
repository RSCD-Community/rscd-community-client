package org.rscdaemon.client.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/*
 * The community world list: what the Worlds screen shows.
 *
 * WHAT THIS SENDS. One HTTP GET of Config.API_URL, with no query string, no
 * headers identifying you, no cookies and no request body. The registry
 * therefore learns exactly what any web server learns from any request -- that
 * an address asked for a file -- and nothing about who you are, what your
 * username is, or which server you end up playing on. Joining a server is a
 * connection from you to that server; the registry is not told about it and is
 * not involved.
 *
 * WHAT IT DOES WITH THE ANSWER. Parses it, shows it in a list, and remembers
 * your choice in your own settings.ini. Favorites are kept in that file rather
 * than in an account somewhere, so nobody -- us included -- can reorder or
 * clear them.
 *
 * The fetch runs on its own thread because the game loop must keep drawing:
 * the screen says "Loading..." while it is in flight, and a registry that is
 * down produces a message and a still-usable screen (favorites and the direct
 * address box both work with no network at all).
 */
public final class WorldList {
   public static final int IDLE = 0;
   public static final int LOADING = 1;
   public static final int READY = 2;
   public static final int FAILED = 3;

   private static final int CONNECT_TIMEOUT = 8000;
   private static final int READ_TIMEOUT = 15000;
   /* A listing this size is already implausible; the cap is to stop a hostile
      or broken endpoint making the client chew through an endless document. */
   private static final int MAX_BYTES = 512000;

   private volatile int state = IDLE;
   private volatile String message = "";
   private volatile List<Row> rows = new ArrayList<Row>();
   private Thread fetching;

   /*
    * One world on one server -- the unit the list displays and joins. Worlds
    * are grouped under their server, because a server with four worlds is one
    * community, not four.
    */
   public static final class Row {
      public String serverId = "";
      public String serverName = "";
      public String host = "";
      public int port;
      public int world = 1;
      public int online;
      public int capacity;
      public String cacheUrl = "";
      /* This world's WebSocket bridge, if it advertises one. Per-world rather
         than per-server: each world is its own process on its own port, so a
         server with four worlds fronts four bridges. Empty means "not
         advertised", which the browser build reads as the port+1 default. */
      public String wsUrl = "";
      public String welcome1 = "";
      public String welcome2 = "";
      public int protocol;
      /* True for every world of a server after its first, so the screen can
         indent them under the name instead of repeating it. */
      public boolean grouped;

      public String target() {
         return this.host + ":" + this.port;
      }

      public boolean isFull() {
         return this.capacity > 0 && this.online >= this.capacity;
      }

      /*
       * The server publishes its server_version as "protocol", and refuses any
       * login below it -- PlayerLogin response 4, "The client has been
       * updated." Without this the list gives no warning at all: the world
       * looks joinable, the player picks it, and the first thing they learn is
       * a refusal on the login screen with no way back to why.
       *
       * The client protocol is passed in rather than read here, so this package
       * stays free of the client it describes.
       *
       * Zero means the registry did not say, which is the fallback for a
       * server that omits the field entirely -- silence is not "too new".
       */
      public boolean needsNewerClient(int clientProtocol) {
         return this.protocol > clientProtocol;
      }

   }

   public int getState() {
      return this.state;
   }

   public String getMessage() {
      return this.message;
   }

   public List<Row> getRows() {
      return this.rows;
   }

   /*
    * Rows matching a search, favorites first. Filtering happens here rather
    * than at the registry so that searching still works while the list is
    * stale, or was never fetched at all.
    */
   public List<Row> filter(String search) {
      List<Row> out = new ArrayList<Row>();
      String needle = search == null ? "" : search.trim().toLowerCase();

      for (Row row : this.rows) {
         if (needle.length() == 0 || row.serverName.toLowerCase().indexOf(needle) >= 0 || row.host.toLowerCase().indexOf(needle) >= 0) {
            out.add(row);
         }
      }

      return out;
   }

   public void refresh() {
      if (this.fetching != null && this.fetching.isAlive()) {
         return;
      }

      this.state = LOADING;
      this.message = "Loading...";
      this.fetching = new Thread(new Runnable() {
         @Override
         public void run() {
            WorldList.this.fetch();
         }
      });
      this.fetching.setDaemon(true);
      this.fetching.start();
   }

   private void fetch() {
      String url = Config.API_URL;

      try {
         List<Row> parsed = parse(read(url));
         if (parsed == null) {
            this.state = FAILED;
            this.message = "The world list could not be read.";
         } else {
            this.rows = parsed;
            this.state = READY;
            this.message = parsed.isEmpty() ? "No servers are listed right now." : "";
         }
      } catch (Exception e) {
         this.state = FAILED;
         // The address is worth printing: when this fails it is almost always
         // because api_url points somewhere that is not there any more, and
         // the player can edit that themselves.
         this.message = "Could not reach " + url;
         System.out.println("World list fetch failed: " + e);
      }
   }

   private static String read(String url) throws Exception {
      HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
      connection.setConnectTimeout(CONNECT_TIMEOUT);
      connection.setReadTimeout(READ_TIMEOUT);
      connection.setRequestMethod("GET");

      try {
         InputStream in = connection.getInputStream();
         ByteArrayOutputStream out = new ByteArrayOutputStream();
         byte[] buffer = new byte[4096];

         int read;
         while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) {
               throw new Exception("world list is too large");
            }
         }

         in.close();
         return new String(out.toByteArray(), "UTF-8");
      } finally {
         connection.disconnect();
      }
   }

   /*
    * { "servers": [ { name, host, port, cache_url, welcome1, welcome2,
    *                  protocol, worlds: [ { world, port, online, capacity } ] } ] }
    *
    * Every field has a fallback. A server that leaves half of them out still
    * appears in the list, because a listing that vanishes when one key is
    * missing is a listing whose author cannot tell what went wrong.
    */
   private static List<Row> parse(String text) {
      Map<String, Object> root = Json.asObject(Json.parse(text));
      List<Object> servers = root == null ? null : Json.asArray(root.get("servers"));
      if (servers == null) {
         return null;
      }

      List<Row> out = new ArrayList<Row>();

      for (Object element : servers) {
         Map<String, Object> server = Json.asObject(element);
         if (server == null) {
            continue;
         }

         String host = Json.getString(server, "host", "").trim();
         if (host.length() == 0) {
            continue;
         }

         String id = Json.getString(server, "id", host);
         String name = Json.getString(server, "name", host);
         int basePort = Json.getInt(server, "port", 43594);
         String cacheUrl = Json.getString(server, "cache_url", "");
         /* A server-level ws_url is the single-world shorthand. Any world that
            names its own overrides it, because the bridge is per-process. */
         String serverWsUrl = Json.getString(server, "ws_url", "");
         String welcome1 = Json.getString(server, "welcome1", "");
         String welcome2 = Json.getString(server, "welcome2", "");
         int protocol = Json.getInt(server, "protocol", 0);

         List<Object> worlds = Json.asArray(server.get("worlds"));
         int first = out.size();
         if (worlds != null) {
            for (Object worldElement : worlds) {
               Map<String, Object> world = Json.asObject(worldElement);
               if (world != null) {
                  Row row = new Row();
                  row.world = Json.getInt(world, "world", out.size() - first + 1);
                  row.port = Json.getInt(world, "port", basePort + row.world - 1);
                  row.online = Json.getInt(world, "online", 0);
                  row.capacity = Json.getInt(world, "capacity", 0);
                  row.wsUrl = Json.getString(world, "ws_url", serverWsUrl);
                  out.add(fill(row, id, name, host, cacheUrl, welcome1, welcome2, protocol));
               }
            }
         }

         // A server that lists no worlds is still a server with one.
         if (out.size() == first) {
            Row row = new Row();
            row.world = 1;
            row.port = basePort;
            row.online = Json.getInt(server, "online", 0);
            row.capacity = Json.getInt(server, "capacity", 0);
            row.wsUrl = serverWsUrl;
            out.add(fill(row, id, name, host, cacheUrl, welcome1, welcome2, protocol));
         }

         for (int i = first + 1; i < out.size(); i++) {
            out.get(i).grouped = true;
         }
      }

      sort(out);
      return out;
   }

   private static Row fill(Row row, String id, String name, String host, String cacheUrl, String welcome1, String welcome2, int protocol) {
      row.serverId = id;
      row.serverName = name;
      row.host = host;
      row.cacheUrl = cacheUrl;
      row.welcome1 = welcome1;
      row.welcome2 = welcome2;
      row.protocol = protocol;
      return row;
   }

   /*
    * Servers are ranked, not worlds: a server's position comes from its total
    * population, so registering four worlds instead of one gains nothing.
    * Favorites float to the top, and a server's own worlds always stay
    * together underneath it in world order.
    */
   private static void sort(List<Row> rows) {
      final java.util.Map<String, Integer> totals = new java.util.HashMap<String, Integer>();
      for (Row row : rows) {
         Integer running = totals.get(row.serverId);
         totals.put(row.serverId, Integer.valueOf((running == null ? 0 : running.intValue()) + row.online));
      }

      Collections.sort(rows, new Comparator<Row>() {
         @Override
         public int compare(Row a, Row b) {
            if (!a.serverId.equals(b.serverId)) {
               boolean favouriteA = isFavourite(a.serverId);
               boolean favouriteB = isFavourite(b.serverId);
               if (favouriteA != favouriteB) {
                  return favouriteA ? -1 : 1;
               }

               int totalA = totals.get(a.serverId).intValue();
               int totalB = totals.get(b.serverId).intValue();
               if (totalA != totalB) {
                  return totalB - totalA;
               }

               return a.serverName.compareToIgnoreCase(b.serverName);
            }

            return a.world - b.world;
         }
      });
   }

   /* ---------------- Favorites, which live in the player's own file ---------------- */

   public static boolean isFavourite(String serverId) {
      for (String id : favourites()) {
         if (id.equals(serverId)) {
            return true;
         }
      }

      return false;
   }

   public static List<String> favourites() {
      List<String> out = new ArrayList<String>();
      String stored = Config.settings().get("favorites", "");

      for (String part : stored.split(",")) {
         part = part.trim();
         if (part.length() > 0) {
            out.add(part);
         }
      }

      return out;
   }

   public static boolean toggleFavourite(String serverId) {
      if (serverId == null || serverId.length() == 0) {
         return false;
      }

      List<String> ids = favourites();
      boolean added;
      if (ids.contains(serverId)) {
         ids.remove(serverId);
         added = false;
      } else {
         ids.add(serverId);
         added = true;
      }

      StringBuilder joined = new StringBuilder();
      for (String id : ids) {
         if (joined.length() > 0) {
            joined.append(",");
         }

         joined.append(id);
      }

      Config.settings().set("favorites", joined.toString());
      Config.settings().save();
      return added;
   }

   /*
    * Records the server the player just chose, so the next launch goes
    * straight to its sign-in screen. This is the whole of what "default
    * server" means -- a line in the player's own file, which they can edit or
    * delete, and which no server or registry can set for them.
    */
   public static void remember(Row row) {
      Settings settings = Config.settings();
      settings.set("default_server", row.target());
      settings.set("default_server_name", row.serverName);
      settings.set("default_server_cache", row.cacheUrl);
      settings.set("default_server_ws", row.wsUrl);
      settings.set("default_server_welcome1", row.welcome1);
      settings.set("default_server_welcome2", row.welcome2);
      settings.save();

      Config.DEFAULT_TARGET = row.target();
      Config.SERVER_NAME = row.serverName;
      Config.WELCOME_LINE1 = row.welcome1;
      Config.WELCOME_LINE2 = row.welcome2;
      if (row.cacheUrl.length() > 0) {
         Config.CACHE_URL = row.cacheUrl;
      }

      /*
       * Assigned unconditionally, unlike CACHE_URL above -- including when the
       * row advertises nothing.
       *
       * Keeping the previous world's value would be the federation bug in its
       * worst form: the player picks world B, the browser build dials world A's
       * bridge because that is what was still in the field, and lands in a
       * world it did not choose with no error anywhere. A wrong connection that
       * looks like a working one is harder to diagnose than no connection, so
       * this clears rather than persists. The empty case costs nothing -- it
       * falls back to the port+1 convention, which is what every world that has
       * not been told about ws_url yet is already reachable on.
       */
      Config.WS_URL = row.wsUrl == null ? "" : row.wsUrl;

      Config.applyTarget(row.target());
      Config.SERVER_WORLD = row.world;
   }

   /*
    * The escape hatch: an address typed by hand. Delisting a server from the
    * registry takes away its discovery, not its players -- anyone who knows
    * the address can still reach it, which is also how you reach localhost, a
    * server that has not registered yet, and a private one that never will.
    */
   public static Row direct(String address) {
      String host = address == null ? "" : address.trim();
      if (host.length() == 0) {
         return null;
      }

      int port = 43594;
      int colon = host.lastIndexOf(58);
      if (colon > 0) {
         try {
            port = Integer.parseInt(host.substring(colon + 1).trim());
         } catch (NumberFormatException e) {
            return null;
         }

         host = host.substring(0, colon).trim();
      }

      if (host.length() == 0 || port <= 0 || port > 65535) {
         return null;
      }

      Row row = new Row();
      row.serverId = host + ":" + port;
      row.serverName = host;
      row.host = host;
      row.port = port;
      row.world = 1;
      return row;
   }
}
