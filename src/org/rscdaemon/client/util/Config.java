package org.rscdaemon.client.util;

import java.io.File;
import java.io.IOException;

/*
 * One launch mode: the desktop client. initConfig(String) reads settings.ini,
 * and system properties win over it, which keeps the client scriptable from a
 * launcher without rewriting the file on disk.
 *
 * There were two. The other was applet mode -- no settings file exists next to
 * an applet, so values came from system properties with loopback defaults, and
 * CACHE_URL was pinned to WEB_IP because an applet may only talk back to its own
 * codebase host. That mode is gone with the Applet API (JDK 24, JEP 504), and it
 * is not missed here: its defaults were the ones that silently replaced a real
 * settings.ini with loopback whenever anything reached Config before main() did.
 *
 * The client downloads the whole asset set from CACHE_URL into memory on every
 * launch and keeps nothing on disk -- see mudclient.loadcache() and Assets. It
 * used to read whatever shipped alongside the jar, which meant a content change
 * needed a new client build.
 */
public class Config {
   public static String SERVER_IP;
   public static String CONF_DIR;
   public static String MEDIA_DIR;
   /* Where scripts are read from. Relative paths resolve next to the jar. */
   public static String SCRIPT_DIR;
   /* Where calculators are read from, likewise. */
   public static String CALCULATOR_DIR;
   public static String WEB_IP;
   /* Base URL the assets are pulled from: CACHE_URL/<name> per gamefiles entry. */
   public static String CACHE_URL;
   /*
    * Where the chosen world's WebSocket bridge is fronted, as advertised by
    * that world's own registry entry. Only the browser build reads it -- the
    * desktop client dials the game port over TCP and never needs a bridge --
    * but it belongs here rather than in the web layer, because it arrives with
    * the rest of a world's listing and has to survive a restart the same way
    * CACHE_URL does.
    *
    * Empty is the normal case and means "assume the documented default", not
    * "cannot connect": a world that advertises nothing is still reachable at
    * the port+1 convention both ends ship with. What this field buys is the
    * world whose bridge is NOT at port+1 -- fronted on :443 behind a TLS proxy,
    * for instance, which is the only shape that works for a browser at all.
    *
    * Scoped by DEFAULT_TARGET, never trusted on its own: see WorldList.remember.
    */
   public static String WS_URL = "";
   public static final String DEFAULT_WEB_IP = "rscd-community.org";
   public static final String DEFAULT_CACHE_URL = "https://" + DEFAULT_WEB_IP + "/cache_data";
   public static int SERVER_PORT;
   public static int SERVER_WORLD;
   public static int MOVIE_FPS;
   public static long START_TIME;

   /*
    * The community registry. This is a plain JSON document over HTTP and needs
    * no credential to read, so it can live in a file the player owns -- which
    * is the point. A registry that only ever existed as a constant inside the
    * jar could not be replaced without a fork, and could not be replaced at all
    * once installs were in the field; retrofitting it later is impossible, so
    * it ships as a setting from the first build that has a Worlds screen.
    */
   public static String API_URL;
   public static final String DEFAULT_API_URL = "https://api.rscd-community.org/worlds.json";

   /*
    * The server the player last chose to play on, as host:port, plus the bits
    * of that server's listing worth remembering so the client can go straight
    * to its sign-in screen without waiting on the registry.
    *
    * An empty DEFAULT_TARGET is how "this player has never joined anything"
    * is known -- there is no separate first-run flag, and no default server is
    * baked in. A fresh install goes to the Worlds screen.
    */
   public static String DEFAULT_TARGET;
   public static String SERVER_NAME;
   /* The two lines the welcome panel shows. They belong to the server, not us. */
   public static String WELCOME_LINE1;
   public static String WELCOME_LINE2;

   private static boolean initialised;
   private static Settings settings;

   /*
    * The live settings.ini, for the things the player changes from inside the
    * client -- their default server and their favorites. Never null: in applet
    * mode, or when no file was found, this is an in-memory Settings whose
    * save() is a no-op, so callers do not branch on it.
    */
   public static Settings settings() {
      if (settings == null) {
         settings = Settings.load(null);
      }

      return settings;
   }

   /*
    * The mudclient constructor builds one of these, which in the webclient was
    * the only thing that ever configured the client. main() now configures it
    * first and from a file, so this has to stand down or it silently reverts
    * every settings.ini value -- which is exactly what it did to cache_url and
    * web.
    *
    * The fallback, for anything that does reach Config before main(), is the
    * desktop path with no file: every key has a working default and a missing
    * settings.ini is not an error. It used to be the applet path, whose
    * defaults were loopback -- so the failure mode was a client quietly
    * pointed at a machine with nothing listening.
    */
   public Config() {
      if (!initialised) {
         try {
            initConfig("settings.ini");
         } catch (Exception var2) {
         }
      }
   }

   /*
    * Desktop mode. Mirrors the key names the Revamped V10 build used, so an
    * existing settings.ini works unchanged:
    *   server, port, config_dir, media_dir, movie_fps, web, cache_url
    * System properties still win where set, which keeps the client scriptable
    * from a launcher without rewriting the file on disk.
    *
    * A missing file is not an error -- every key below has a working default,
    * so a bare jar with no settings.ini beside it still starts and talks to the
    * real host. It used to fall through to initConfig() instead, quietly
    * configuring the client for an applet and pointing it at loopback.
    */
   public static void initConfig(String file) throws IOException {
      initialised = true;
      START_TIME = System.currentTimeMillis();

      // Read through Settings rather than Properties, which this used to do.
      // Same file, same key names, same '#' comments and '='/':' separators --
      // but the handle stays live, so the Worlds screen can write the player's
      // default and favorites back without Properties.store() flattening the
      // comments and reordering the keys underneath them.
      File source = new File(file);
      if (!source.isFile()) {
         System.out.println("No " + source.getPath() + ", using defaults");
      }

      Settings props = Settings.load(source);
      settings = props;

      SERVER_IP = System.getProperty("rscd.server", props.get("server", "127.0.0.1"));
      SERVER_PORT = Integer.parseInt(System.getProperty("rscd.port", props.get("port", "43594")));
      CONF_DIR = System.getProperty("rscd.conf", props.get("config_dir", "conf/client"));
      MEDIA_DIR = System.getProperty("rscd.media", props.get("media_dir", "media"));
      SCRIPT_DIR = System.getProperty("rscd.scripts", props.get("script_dir", "scripts"));
      CALCULATOR_DIR = System.getProperty("rscd.calculators", props.get("calculator_dir", "calculators"));
      WEB_IP = System.getProperty("rscd.web", props.get("web", DEFAULT_WEB_IP));
      CACHE_URL = System.getProperty("rscd.cacheurl", props.get("cache_url", DEFAULT_CACHE_URL));
      MOVIE_FPS = props.getInt("movie_fps", 5);

      API_URL = System.getProperty("rscd.api", props.get("api_url", DEFAULT_API_URL));

      // The player's own choice, written by the Worlds screen. Deliberately
      // has no default: nothing is preselected on a fresh install, and the
      // primary server is one row in the list like everybody else's.
      DEFAULT_TARGET = System.getProperty("rscd.target", props.get("default_server", "")).trim();
      SERVER_NAME = props.get("default_server_name", "");
      WELCOME_LINE1 = props.get("default_server_welcome1", "");
      WELCOME_LINE2 = props.get("default_server_welcome2", "");

      // A remembered server carries its own asset host, so the client fetches
      // that server's content and not ours. Only applies once something has
      // been joined -- an empty default leaves CACHE_URL as configured above.
      String defaultCache = props.get("default_server_cache", "");
      if (DEFAULT_TARGET.length() > 0 && defaultCache.length() > 0) {
         CACHE_URL = System.getProperty("rscd.cacheurl", defaultCache);
      }

      // Same idea for the bridge, with one deliberate difference: this is read
      // back unconditionally, empty included. A stale CACHE_URL costs a wrong
      // download that the client notices; a stale WS_URL would silently connect
      // a player to the world they used to play on, which looks like success.
      WS_URL = DEFAULT_TARGET.length() > 0 ? props.get("default_server_ws", "") : "";

      // The remembered server wins over the file's static server/port keys --
      // it is the more recent statement of the same thing, made from inside
      // the client. An explicit -Drscd.server still beats both, so a launcher
      // can override without touching the player's file.
      if (System.getProperty("rscd.server") == null) {
         applyTarget(DEFAULT_TARGET);
      }
   }

   /*
    * Applies an rscd:// join link from the website's Play Game page:
    *
    *   rscd://host:port/?name=Server%20Name&world=1&protocol=10
    *
    * Only host and port decide anything; name is display text for the login
    * screen, world and protocol are informational (each world is its own
    * port, and nothing in the client gates on protocol yet). Returns false
    * on anything unparseable, leaving the configured server untouched --
    * a bad link degrades to a normal launch, never a broken one.
    */
   public static boolean applyJoinUri(String uri) {
      if (uri == null || !uri.regionMatches(true, 0, "rscd://", 0, 7)) {
         return false;
      }

      String rest = uri.substring(7);
      String query = "";
      int q = rest.indexOf('?');
      if (q >= 0) {
         query = rest.substring(q + 1);
         rest = rest.substring(0, q);
      }
      int slash = rest.indexOf('/');
      if (slash >= 0) {
         rest = rest.substring(0, slash);
      }
      if (!applyTarget(percentDecode(rest))) {
         return false;
      }

      for (String pair : query.split("&")) {
         int eq = pair.indexOf('=');
         if (eq > 0 && pair.substring(0, eq).equals("name")) {
            String name = percentDecode(pair.substring(eq + 1)).trim();
            if (name.length() > 0 && name.length() <= 48) {
               SERVER_NAME = name;
            }
         }
      }

      System.out.println("Join link: " + SERVER_IP + ":" + SERVER_PORT
            + (SERVER_NAME.length() > 0 ? " (" + SERVER_NAME + ")" : ""));
      return true;
   }

   /*
    * %xx and '+' decoding without java.net.URLDecoder's checked exception
    * and charset ceremony. Malformed escapes pass through literally.
    */
   private static String percentDecode(String text) {
      StringBuilder out = new StringBuilder(text.length());
      java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '%' && i + 2 < text.length()) {
            try {
               bytes.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
               i += 2;
               continue;
            } catch (NumberFormatException ignored) {
            }
         }
         if (bytes.size() > 0) {
            out.append(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
            bytes.reset();
         }
         out.append(c == '+' ? ' ' : c);
      }
      if (bytes.size() > 0) {
         out.append(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
      }
      return out.toString();
   }

   /*
    * Points SERVER_IP/SERVER_PORT at a "host" or "host:port" string, leaving
    * both alone if it is empty or unparseable. A missing port means the
    * default one, which is what a host typed by hand from a forum post looks
    * like.
    */
   public static boolean applyTarget(String target) {
      if (target == null) {
         return false;
      } else {
         target = target.trim();
         if (target.length() == 0) {
            return false;
         } else {
            String host = target;
            int port = 43594;
            int colon = target.lastIndexOf(58);
            if (colon == 0) {
               // ":43594" -- a port with no host names nothing.
               return false;
            }
            if (colon > 0) {
               host = target.substring(0, colon).trim();
               try {
                  port = Integer.parseInt(target.substring(colon + 1).trim());
               } catch (NumberFormatException var5) {
                  return false;
               }
            }

            if (host.length() != 0 && port > 0 && port <= 65535) {
               SERVER_IP = host;
               SERVER_PORT = port;
               return true;
            } else {
               return false;
            }
         }
      }
   }
}
