package org.rscdaemon.client.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * A JSON reader, because the world list is JSON and nothing in this client
 * could read it.
 *
 * Adding a JSON library instead would have meant a fourth jar on the classpath
 * for a document with five kinds of value in it, and one more dependency whose
 * licence has to be tracked. This is the whole of JSON's grammar in about a
 * hundred and fifty lines, which is small enough that anyone who wants to know
 * what the client does with what the registry sends it can just read it.
 *
 * It parses; it does not write. Nothing here sends JSON anywhere.
 *
 * Values come back as the obvious Java types -- Map for an object, List for an
 * array, String, Double, Boolean, null -- and the get* helpers below do the
 * casting, so callers never touch instanceof.
 */
public final class Json {
   private final String text;
   private int at;

   private Json(String text) {
      this.text = text;
   }

   /*
    * Parses a document. Returns null rather than throwing on malformed input:
    * every caller here is reading something off the network that it does not
    * control, and "the registry sent nonsense" is an expected state to be
    * shown on screen, not an exception to propagate through the game loop.
    */
   public static Object parse(String text) {
      if (text == null) {
         return null;
      }

      try {
         Json json = new Json(text);
         json.skipSpace();
         Object value = json.value();
         json.skipSpace();
         return json.at == json.text.length() ? value : null;
      } catch (RuntimeException e) {
         System.out.println("Malformed JSON: " + e.getMessage());
         return null;
      }
   }

   @SuppressWarnings("unchecked")
   public static Map<String, Object> asObject(Object value) {
      return value instanceof Map ? (Map<String, Object>)value : null;
   }

   @SuppressWarnings("unchecked")
   public static List<Object> asArray(Object value) {
      return value instanceof List ? (List<Object>)value : null;
   }

   public static String getString(Map<String, Object> object, String key, String fallback) {
      Object value = object == null ? null : object.get(key);
      return value instanceof String ? (String)value : fallback;
   }

   public static int getInt(Map<String, Object> object, String key, int fallback) {
      Object value = object == null ? null : object.get(key);
      if (value instanceof Double) {
         return (int)((Double)value).doubleValue();
      } else if (value instanceof String) {
         try {
            return Integer.parseInt(((String)value).trim());
         } catch (NumberFormatException e) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public static boolean getBoolean(Map<String, Object> object, String key, boolean fallback) {
      Object value = object == null ? null : object.get(key);
      return value instanceof Boolean ? ((Boolean)value).booleanValue() : fallback;
   }

   private Object value() {
      char c = this.peek();
      if (c == '{') {
         return this.object();
      } else if (c == '[') {
         return this.array();
      } else if (c == '"') {
         return this.string();
      } else if (this.text.startsWith("true", this.at)) {
         this.at += 4;
         return Boolean.TRUE;
      } else if (this.text.startsWith("false", this.at)) {
         this.at += 5;
         return Boolean.FALSE;
      } else if (this.text.startsWith("null", this.at)) {
         this.at += 4;
         return null;
      } else {
         return this.number();
      }
   }

   private Map<String, Object> object() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      this.expect('{');
      this.skipSpace();
      if (this.peek() == '}') {
         this.at++;
         return map;
      }

      while (true) {
         this.skipSpace();
         String key = this.string();
         this.skipSpace();
         this.expect(':');
         this.skipSpace();
         map.put(key, this.value());
         this.skipSpace();
         char c = this.next();
         if (c == '}') {
            return map;
         }

         if (c != ',') {
            throw new RuntimeException("expected , or } at " + this.at);
         }
      }
   }

   private List<Object> array() {
      List<Object> list = new ArrayList<Object>();
      this.expect('[');
      this.skipSpace();
      if (this.peek() == ']') {
         this.at++;
         return list;
      }

      while (true) {
         this.skipSpace();
         list.add(this.value());
         this.skipSpace();
         char c = this.next();
         if (c == ']') {
            return list;
         }

         if (c != ',') {
            throw new RuntimeException("expected , or ] at " + this.at);
         }
      }
   }

   private String string() {
      this.expect('"');
      StringBuilder out = new StringBuilder();

      while (true) {
         char c = this.next();
         if (c == '"') {
            return out.toString();
         }

         if (c != '\\') {
            out.append(c);
         } else {
            char escape = this.next();
            if (escape == 'n') {
               out.append('\n');
            } else if (escape == 't') {
               out.append('\t');
            } else if (escape == 'r') {
               out.append('\r');
            } else if (escape == 'b') {
               out.append('\b');
            } else if (escape == 'f') {
               out.append('\f');
            } else if (escape == 'u') {
               out.append((char)Integer.parseInt(this.text.substring(this.at, this.at + 4), 16));
               this.at += 4;
            } else {
               // Covers \" \\ \/ and anything a lenient writer emitted.
               out.append(escape);
            }
         }
      }
   }

   private Double number() {
      int start = this.at;
      if (this.peek() == '-') {
         this.at++;
      }

      while (this.at < this.text.length()) {
         char c = this.text.charAt(this.at);
         if ((c < '0' || c > '9') && c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-') {
            break;
         }

         this.at++;
      }

      if (start == this.at) {
         throw new RuntimeException("expected a value at " + start);
      }

      return Double.valueOf(this.text.substring(start, this.at));
   }

   private void skipSpace() {
      while (this.at < this.text.length() && this.text.charAt(this.at) <= ' ') {
         this.at++;
      }
   }

   private char peek() {
      if (this.at >= this.text.length()) {
         throw new RuntimeException("ended early");
      }

      return this.text.charAt(this.at);
   }

   private char next() {
      char c = this.peek();
      this.at++;
      return c;
   }

   private void expect(char c) {
      if (this.next() != c) {
         throw new RuntimeException("expected " + c + " at " + (this.at - 1));
      }
   }
}
