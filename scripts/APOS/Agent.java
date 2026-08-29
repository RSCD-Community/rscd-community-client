import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/**
 * A remote-control socket for the scripting API.
 *
 * Every other script in this folder decides for itself what to do. This one
 * decides nothing: it opens a loopback socket, takes one command per line,
 * calls the matching Script method, and hands back the answer as JSON. The
 * decisions happen wherever the other end of that socket lives -- a shell, a
 * program, a person typing into netcat.
 *
 * Why bother, when a script can already do all of this in Java? Because a
 * script has to be written, compiled and restarted for every change of mind.
 * Driving the same API from outside means you can look at the world, decide,
 * act, and look again, without the client ever restarting. That is the whole
 * point, and it is also why the interesting command here is "scene": one
 * request that describes everything nearby, so the far end can ask once
 * instead of polling twenty getters.
 *
 * Protocol, deliberately boring:
 *
 *   in    <id> <method> [arg ...]     args split on spaces, "quoted" allowed
 *   out   {"id":1,"ok":true,"result":...}
 *
 * Any public method on Script is callable by name -- this dispatches by
 * reflection rather than listing them, so the socket gains new verbs the
 * moment the API does. Three commands are synthesised on top:
 *
 *   scene [radius]   npcs, objects, ground items and players around you
 *   state            position, health, fatigue, levels, inventory
 *   ping             proof the script is still alive
 *
 * Threading is the one subtle part. The client is not thread-safe and every
 * API call has to land on the script's own thread, so the socket thread only
 * ever parks a request in a queue; main() runs it on the next tick and wakes
 * the waiter. Nothing touches the client from the socket thread. Nothing.
 */
public class Agent extends Script {

    private static final int DEFAULT_PORT = 43800;
    private static final int DEFAULT_RADIUS = 20;

    /* A request parked by the socket thread, executed by the script thread. */
    private static final class Call {
        String id, method;
        String[] args;
        String reply;       /* set by the script thread when done */
        boolean done;
    }

    private final LinkedList<Call> queue = new LinkedList<Call>();
    private ServerSocket listener;
    private int port = DEFAULT_PORT;

    public Agent(Extension e) {
        super(e);
    }

    public void init(String params) {
        if (params != null && params.trim().length() > 0) {
            try { port = Integer.parseInt(params.trim()); } catch (Exception ignored) { }
        }
        try {
            /* Loopback only. This is a remote control for the machine's owner,
             * not a service, and it must never be reachable from off-box. */
            listener = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
        } catch (IOException e) {
            System.out.println("Agent: could not listen on " + port + ": " + e);
            return;
        }
        System.out.println("Agent: listening on 127.0.0.1:" + port);
        Thread t = new Thread(new Runnable() {
            public void run() { accept(); }
        });
        t.setDaemon(true);
        t.start();
    }

    /* ---------------- socket side ---------------- */

    private void accept() {
        while (true) {
            Socket s = null;
            try {
                s = listener.accept();
                serve(s);
            } catch (IOException e) {
                /* A dropped client is routine; keep listening. */
            } finally {
                if (s != null) { try { s.close(); } catch (IOException ignored) { } }
            }
        }
    }

    private void serve(Socket s) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) { continue; }
            out.write(submit(line));
            out.write("\n");
            out.flush();
        }
    }

    /* Park a request and block this (socket) thread until main() runs it. */
    private String submit(String line) {
        String[] tok = tokenize(line);
        Call c = new Call();
        c.id = tok.length > 0 ? tok[0] : "0";
        c.method = tok.length > 1 ? tok[1] : "";
        c.args = new String[Math.max(0, tok.length - 2)];
        for (int i = 0; i < c.args.length; i++) { c.args[i] = tok[i + 2]; }

        synchronized (queue) { queue.addLast(c); }
        synchronized (c) {
            long deadline = System.currentTimeMillis() + 15000;
            while (!c.done && System.currentTimeMillis() < deadline) {
                try { c.wait(200); } catch (InterruptedException ignored) { }
            }
        }
        if (!c.done) {
            return "{\"id\":" + num(c.id) + ",\"ok\":false,\"error\":\"timeout\"}";
        }
        return c.reply;
    }

    /* Split on whitespace, honouring "double quoted" runs so chat and player
     * names survive the trip. */
    private static String[] tokenize(String line) {
        ArrayList<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false, any = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { quoted = !quoted; any = true; continue; }
            if (!quoted && (ch == ' ' || ch == '\t')) {
                if (any) { out.add(cur.toString()); cur.setLength(0); any = false; }
                continue;
            }
            cur.append(ch);
            any = true;
        }
        if (any) { out.add(cur.toString()); }
        return out.toArray(new String[out.size()]);
    }

    /* ---------------- script side ---------------- */

    public int main() {
        Call c;
        synchronized (queue) { c = queue.isEmpty() ? null : queue.removeFirst(); }
        if (c == null) { return 50; }
        String reply;
        try {
            reply = "{\"id\":" + num(c.id) + ",\"ok\":true,\"result\":" + run(c) + "}";
        } catch (InvocationTargetException e) {
            reply = err(c.id, e.getCause() == null ? e.toString() : e.getCause().toString());
        } catch (Throwable t) {
            reply = err(c.id, t.toString());
        }
        synchronized (c) { c.reply = reply; c.done = true; c.notifyAll(); }
        return 20;
    }

    private String err(String id, String msg) {
        return "{\"id\":" + num(id) + ",\"ok\":false,\"error\":" + str(msg) + "}";
    }

    private String run(Call c) throws Exception {
        if (c.method.equals("ping"))  { return "\"pong\""; }
        if (c.method.equals("state")) { return state(); }
        if (c.method.equals("scene")) {
            return scene(c.args.length > 0 ? Integer.parseInt(c.args[0]) : DEFAULT_RADIUS);
        }
        return json(invoke(c.method, c.args));
    }

    /* Find a public Script method of this name whose parameters the supplied
     * strings can actually fill, and call it. Varargs collapse to int[]. */
    private Object invoke(String name, String[] args) throws Exception {
        Method[] all = Script.class.getMethods();
        Method varargs = null;
        for (int i = 0; i < all.length; i++) {
            Method m = all[i];
            if (!m.getName().equals(name)) { continue; }
            Class<?>[] p = m.getParameterTypes();
            if (m.isVarArgs()) { varargs = m; continue; }
            if (p.length != args.length) { continue; }
            Object[] vals = new Object[p.length];
            boolean ok = true;
            for (int j = 0; j < p.length && ok; j++) {
                vals[j] = coerce(p[j], args[j]);
                ok = vals[j] != null;
            }
            if (ok) { return m.invoke(this, vals); }
        }
        if (varargs != null) {
            int[] ids = new int[args.length];
            for (int i = 0; i < args.length; i++) { ids[i] = Integer.parseInt(args[i]); }
            return varargs.invoke(this, new Object[] { ids });
        }
        throw new NoSuchMethodException(name + "/" + args.length);
    }

    private static Object coerce(Class<?> type, String s) {
        try {
            if (type == int.class)     { return Integer.valueOf(Integer.parseInt(s)); }
            if (type == long.class)    { return Long.valueOf(Long.parseLong(s)); }
            if (type == double.class)  { return Double.valueOf(Double.parseDouble(s)); }
            if (type == boolean.class) { return Boolean.valueOf(s.equals("1") || s.equalsIgnoreCase("true")); }
            if (type == String.class)  { return s; }
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    /* ---------------- JSON ---------------- */

    private static String num(String s) {
        try { return String.valueOf(Long.parseLong(s)); } catch (Exception e) { return "0"; }
    }

    private static String str(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; s != null && i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') { b.append('\\').append(ch); }
            else if (ch == '\n') { b.append("\\n"); }
            else if (ch == '\r') { b.append("\\r"); }
            else if (ch == '\t') { b.append("\\t"); }
            else if (ch < 0x20 || ch > 0x7e) { b.append(String.format("\\u%04x", (int) ch)); }
            else { b.append(ch); }
        }
        return b.append('"').toString();
    }

    private static String json(Object o) {
        if (o == null) { return "null"; }
        if (o instanceof String) { return str((String) o); }
        if (o instanceof Boolean || o instanceof Number) { return o.toString(); }
        if (o instanceof int[]) {
            int[] a = (int[]) o;
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) { b.append(','); } b.append(a[i]); }
            return b.append(']').toString();
        }
        if (o instanceof String[]) {
            String[] a = (String[]) o;
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < a.length; i++) { if (i > 0) { b.append(','); } b.append(str(a[i])); }
            return b.append(']').toString();
        }
        return str(o.toString());
    }

    /* ---------------- the two composite views ---------------- */

    /**
     * Everything about the character itself, in one round trip. Levels are
     * sent as two flat arrays rather than named fields: the far end already
     * knows the skill order, and this keeps the line short.
     */
    private String state() {
        StringBuilder b = new StringBuilder("{");
        b.append("\"x\":").append(getX());
        b.append(",\"y\":").append(getY());
        b.append(",\"hp\":").append(getHpPercent());
        b.append(",\"fatigue\":").append(getFatigue());
        b.append(",\"combat\":").append(inCombat());
        b.append(",\"walking\":").append(isWalking());
        b.append(",\"sleeping\":").append(isSleeping());
        b.append(",\"banking\":").append(isBanking());
        b.append(",\"shop\":").append(isShopOpen());
        b.append(",\"trade\":").append(isInTradeOffer() || isInTradeConfirm());
        b.append(",\"menu\":").append(isQuestMenu());
        b.append(",\"empty\":").append(getEmptySlots());
        b.append(",\"cur\":[");
        for (int i = 0; i < 18; i++) { if (i > 0) { b.append(','); } b.append(getCurrentLevel(i)); }
        b.append("],\"lvl\":[");
        for (int i = 0; i < 18; i++) { if (i > 0) { b.append(','); } b.append(getLevel(i)); }
        b.append("],\"inv\":[");
        int n = getInventoryCount();
        for (int i = 0; i < n; i++) {
            if (i > 0) { b.append(','); }
            b.append('[').append(i).append(',').append(getInventoryId(i))
             .append(',').append(getInventoryStack(i)).append(']');
        }
        return b.append("]}").toString();
    }

    /**
     * The world within `radius` tiles, as arrays of flat tuples.
     *
     * Tuples, not objects with keys, because the key names would be most of
     * the bytes. A scene of 180 entities is a couple of kilobytes this way
     * and comfortably inside the packet ceiling; spelled out with field names
     * it is four times that for no extra information.
     *
     *   npc    [index, id, x, y, combatLevel, inCombat]
     *   obj    [id, x, y]
     *   wall   [id, x, y]
     *   item   [id, x, y]
     *   player [index, pid, x, y, combatLevel, "name"]
     */
    private String scene(int radius) {
        StringBuilder b = new StringBuilder("{\"x\":");
        b.append(getX()).append(",\"y\":").append(getY()).append(",\"r\":").append(radius);

        b.append(",\"npc\":[");
        int n = countNpcs(), w = 0;
        for (int i = 0; i < n; i++) {
            if (distanceTo(getNpcX(i), getNpcY(i)) > radius) { continue; }
            if (w++ > 0) { b.append(','); }
            b.append('[').append(i).append(',').append(getNpcId(i)).append(',')
             .append(getNpcX(i)).append(',').append(getNpcY(i)).append(',')
             .append(getNpcCombatLevel(i)).append(',').append(isNpcInCombat(i)).append(']');
        }

        b.append("],\"obj\":[");
        n = getObjectCount(); w = 0;
        for (int i = 0; i < n; i++) {
            if (distanceTo(getObjectX(i), getObjectY(i)) > radius) { continue; }
            if (w++ > 0) { b.append(','); }
            b.append('[').append(getObjectId(i)).append(',').append(getObjectX(i))
             .append(',').append(getObjectY(i)).append(']');
        }

        b.append("],\"wall\":[");
        n = getWallObjectCount(); w = 0;
        for (int i = 0; i < n; i++) {
            if (distanceTo(getWallObjectX(i), getWallObjectY(i)) > radius) { continue; }
            if (w++ > 0) { b.append(','); }
            b.append('[').append(getWallObjectId(i)).append(',').append(getWallObjectX(i))
             .append(',').append(getWallObjectY(i)).append(']');
        }

        b.append("],\"item\":[");
        n = getGroundItemCount(); w = 0;
        for (int i = 0; i < n; i++) {
            if (distanceTo(getItemX(i), getItemY(i)) > radius) { continue; }
            if (w++ > 0) { b.append(','); }
            b.append('[').append(getGroundItemId(i)).append(',').append(getItemX(i))
             .append(',').append(getItemY(i)).append(']');
        }

        b.append("],\"player\":[");
        n = countPlayers(); w = 0;
        for (int i = 0; i < n; i++) {
            if (distanceTo(getPlayerX(i), getPlayerY(i)) > radius) { continue; }
            if (w++ > 0) { b.append(','); }
            b.append('[').append(i).append(',').append(getPlayerPID(i)).append(',')
             .append(getPlayerX(i)).append(',').append(getPlayerY(i)).append(',')
             .append(getPlayerCombatLevel(i)).append(',').append(str(getPlayerName(i))).append(']');
        }
        return b.append("]}").toString();
    }
}
