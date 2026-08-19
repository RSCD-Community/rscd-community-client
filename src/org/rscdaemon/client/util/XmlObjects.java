package org.rscdaemon.client.util;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Reads the entity-definition documents -- the XStream XML the cache ships --
 * without XStream.
 *
 * WHY. lib/xstream.jar is XStream 1.x, built in 2005. Its Sun14ReflectionProvider
 * reaches into java.base through sun.misc.Unsafe, which is why the client needs
 * the Add-Opens line in its manifest to run on a modern JDK at all. That is a
 * reprieve and not a fix: JEP 471 deprecated those memory-access methods, JEP 498
 * makes them warn at runtime, and they are slated to throw. When that lands, no
 * flag saves it and the client simply stops starting. A player should not need to
 * know any of this to run a game from 2001.
 *
 * WHAT IT SUPPORTS. Exactly the shapes the cache actually contains, established by
 * reading all ten documents rather than by inferring from the classes:
 *
 *   <ItemDef-array>          an array of aliased objects; every root is one of these
 *     <ItemDef>              an object: child element per public field
 *       <name>Iron Mace</name>            String  (<x></x> is the empty string)
 *       <basePrice>63</basePrice>         int
 *       <stackable>false</stackable>      boolean
 *   <sprites><int>130</int>...</sprites>  int[], twelve per NPC
 *   <drops><ItemDropDef>...</ItemDropDef></drops>   object array, and <drops /> for none
 *   <requiredRunes><entry><int>35</int><int>1</int></entry></requiredRunes>
 *                                         a Map, keyed and valued by element tag
 *
 * There are no class= attributes, no reference= ids, no nulls and no CDATA in any
 * of them, so none of XStream's machinery for those is reproduced. Four XML
 * entities occur across the whole cache (&amp; three times, &apos; once) and are
 * decoded; the rest are handled because leaving a half-done decoder around is how
 * you get a bug the first time somebody names an item "Rock & Roll <sword>".
 *
 * REFLECTION, DELIBERATELY NARROW. Fields are located with Class.getField and set
 * directly. Every field on every def class is public, so there is no setAccessible
 * call anywhere here -- which is the entire point. Reading your own public fields
 * is ordinary API use and no JDK has ever restricted it. Instantiation is
 * getDeclaredConstructor().newInstance() on the implicit no-arg constructor these
 * classes all have.
 *
 * An unknown element is skipped and reported once rather than throwing. A document
 * that gained a field should not stop the client booting, and one that lost a field
 * should say so out loud instead of silently leaving a zero.
 */
public final class XmlObjects {

   private XmlObjects() {
   }

   /* Element name -> class, the same table XStream's aliases held. */
   private static final Map<String, Class<?>> ALIASES = new HashMap<String, Class<?>>();
   /* Reported once each, so a schema drift is visible but not a log flood. */
   private static final Set<String> WARNED = new HashSet<String>();

   public static void alias(String name, String className) {
      try {
         ALIASES.put(name, Class.forName(className));
      } catch (ClassNotFoundException e) {
         System.out.println("XmlObjects: no class " + className + " for <" + name + ">");
      }
   }

   /**
    * Parses a whole document and returns what its root element describes --
    * an array for the {@code <X-array>} roots every def file uses.
    */
   public static Object read(String xml) {
      Node root = parse(xml);
      return root == null ? null : bind(root, typeOf(root));
   }

   /*
    * ---- the document tree ----
    *
    * A tree rather than a streaming parse: the largest document is NPCs at 1 MB
    * and about thirteen thousand elements, which costs a few hundred KB to hold
    * and makes the binder a plain recursive walk.
    */
   private static final class Node {
      String name;
      String text = "";
      List<Node> children;

      List<Node> kids() {
         return this.children == null ? EMPTY : this.children;
      }
   }

   private static final List<Node> EMPTY = new ArrayList<Node>(0);

   private static Node parse(String s) {
      List<Node> stack = new ArrayList<Node>();
      Node root = null;
      int i = 0;
      int length = s.length();

      while (i < length) {
         int open = s.indexOf('<', i);
         if (open < 0) {
            break;
         }

         /* Text between the previous tag and this one. Only elements with no
            children ever carry any, so it is attributed to the open element. */
         if (open > i && !stack.isEmpty()) {
            Node top = stack.get(stack.size() - 1);
            if (top.children == null) {
               top.text = decode(s.substring(i, open));
            }
         }

         int close = s.indexOf('>', open);
         if (close < 0) {
            break;
         }

         String tag = s.substring(open + 1, close);
         i = close + 1;

         // <?xml ...?>, <!-- ... -->, <!DOCTYPE ...>: skipped wholesale.
         if (tag.startsWith("?") || tag.startsWith("!")) {
            continue;
         }

         if (tag.startsWith("/")) {
            if (!stack.isEmpty()) {
               stack.remove(stack.size() - 1);
            }

            continue;
         }

         boolean selfClosing = tag.endsWith("/");
         if (selfClosing) {
            tag = tag.substring(0, tag.length() - 1);
         }

         /* Any attributes are dropped: the cache has none, and a name is all
            the binder ever asks for. */
         int space = tag.indexOf(' ');
         if (space > 0) {
            tag = tag.substring(0, space);
         }

         Node node = new Node();
         node.name = tag.trim();

         if (stack.isEmpty()) {
            if (root != null) {
               break; // a second root; malformed, keep the first
            }

            root = node;
         } else {
            Node parent = stack.get(stack.size() - 1);
            if (parent.children == null) {
               parent.children = new ArrayList<Node>();
               // An element with children has no text of its own.
               parent.text = "";
            }

            parent.children.add(node);
         }

         if (!selfClosing) {
            stack.add(node);
         }
      }

      return root;
   }

   /*
    * ---- binding ----
    */

   /** The class a root element describes: {@code <ItemDef-array>} is ItemDef[]. */
   private static Class<?> typeOf(Node root) {
      if (root.name.endsWith("-array")) {
         Class<?> component = ALIASES.get(root.name.substring(0, root.name.length() - 6));
         return component == null ? null : Array.newInstance(component, 0).getClass();
      }

      return ALIASES.get(root.name);
   }

   private static Object bind(Node node, Class<?> type) {
      if (type == null) {
         warn("no class registered for <" + node.name + ">");
         return null;
      }

      if (type.isArray()) {
         return bindArray(node, type.getComponentType());
      }

      if (Map.class.isAssignableFrom(type)) {
         return bindMap(node);
      }

      if (type == String.class || type.isPrimitive() || isBoxed(type)) {
         return scalar(node.text, type);
      }

      return bindObject(node, type);
   }

   private static Object bindArray(Node node, Class<?> component) {
      List<Node> kids = node.kids();
      Object array = Array.newInstance(component, kids.size());

      for (int i = 0; i < kids.size(); i++) {
         Node kid = kids.get(i);
         /* Element tag over declared type: an int[] holds <int>, and an object
            array holds elements named by their alias. They agree in this cache;
            preferring the tag is what would surface it if they ever did not. */
         Class<?> type = component.isPrimitive() || component == String.class
            ? component
            : ALIASES.containsKey(kid.name) ? ALIASES.get(kid.name) : component;
         Array.set(array, i, bind(kid, type));
      }

      return array;
   }

   private static Object bindMap(Node node) {
      HashMap<Object, Object> map = new HashMap<Object, Object>();

      for (Node entry : node.kids()) {
         List<Node> pair = entry.kids();
         if (pair.size() == 2) {
            map.put(byTag(pair.get(0)), byTag(pair.get(1)));
         } else {
            warn("<" + entry.name + "> has " + pair.size() + " children, expected 2");
         }
      }

      return map;
   }

   /** A value whose type is named by its own tag, as XStream writes map keys. */
   private static Object byTag(Node node) {
      if ("int".equals(node.name)) {
         return Integer.valueOf(parseInt(node.text));
      } else if ("long".equals(node.name)) {
         return Long.valueOf(parseLong(node.text));
      } else if ("boolean".equals(node.name)) {
         return Boolean.valueOf("true".equals(node.text));
      } else if ("string".equals(node.name)) {
         return node.text;
      } else {
         Class<?> type = ALIASES.get(node.name);
         if (type == null) {
            warn("no class registered for <" + node.name + ">");
            return node.text;
         }

         return bindObject(node, type);
      }
   }

   private static Object bindObject(Node node, Class<?> type) {
      Object instance;

      try {
         instance = type.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
         warn("cannot construct " + type.getName() + ": " + e);
         return null;
      }

      for (Node child : node.kids()) {
         Field field;

         try {
            // Public fields only, superclasses included -- EntityDef holds
            // name and description for every def that extends it.
            field = type.getField(child.name);
         } catch (NoSuchFieldException e) {
            warn(type.getSimpleName() + " has no field " + child.name);
            continue;
         }

         try {
            field.set(instance, bind(child, field.getType()));
         } catch (Exception e) {
            warn("cannot set " + type.getSimpleName() + "." + child.name + ": " + e);
         }
      }

      return instance;
   }

   private static boolean isBoxed(Class<?> type) {
      return type == Integer.class || type == Boolean.class || type == Long.class
         || type == Byte.class || type == Short.class || type == Double.class
         || type == Float.class || type == Character.class;
   }

   private static Object scalar(String text, Class<?> type) {
      if (type == String.class) {
         return text;
      } else if (type == boolean.class || type == Boolean.class) {
         return Boolean.valueOf("true".equals(text));
      } else if (type == int.class || type == Integer.class) {
         return Integer.valueOf(parseInt(text));
      } else if (type == long.class || type == Long.class) {
         return Long.valueOf(parseLong(text));
      } else if (type == byte.class || type == Byte.class) {
         return Byte.valueOf((byte)parseInt(text));
      } else if (type == short.class || type == Short.class) {
         return Short.valueOf((short)parseInt(text));
      } else if (type == double.class || type == Double.class) {
         return Double.valueOf(text.length() == 0 ? 0.0 : Double.parseDouble(text));
      } else if (type == float.class || type == Float.class) {
         return Float.valueOf(text.length() == 0 ? 0.0F : Float.parseFloat(text));
      } else if (type == char.class || type == Character.class) {
         return Character.valueOf(text.length() == 0 ? '\0' : text.charAt(0));
      } else {
         warn("no scalar conversion for " + type.getName());
         return null;
      }
   }

   /* An unparseable number is a zero and a line of output, not a dead client. */
   private static int parseInt(String text) {
      try {
         return text.length() == 0 ? 0 : Integer.parseInt(text.trim());
      } catch (NumberFormatException e) {
         warn("not a number: \"" + text + "\"");
         return 0;
      }
   }

   private static long parseLong(String text) {
      try {
         return text.length() == 0 ? 0L : Long.parseLong(text.trim());
      } catch (NumberFormatException e) {
         warn("not a number: \"" + text + "\"");
         return 0L;
      }
   }

   /*
    * XML entity decoding. Only &amp; and &apos; occur in the shipped cache, but
    * the full predefined set plus numeric references is barely more code than
    * the two, and the alternative is a bug that waits for someone to add an
    * item called "Rock & Roll".
    */
   private static String decode(String s) {
      int amp = s.indexOf('&');
      if (amp < 0) {
         return s;
      }

      StringBuilder out = new StringBuilder(s.length());
      out.append(s, 0, amp);

      for (int i = amp; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c != '&') {
            out.append(c);
            continue;
         }

         int end = s.indexOf(';', i);
         if (end < 0 || end - i > 10) {
            out.append(c);
            continue;
         }

         String name = s.substring(i + 1, end);
         if ("amp".equals(name)) {
            out.append('&');
         } else if ("lt".equals(name)) {
            out.append('<');
         } else if ("gt".equals(name)) {
            out.append('>');
         } else if ("quot".equals(name)) {
            out.append('"');
         } else if ("apos".equals(name)) {
            out.append('\'');
         } else if (name.startsWith("#")) {
            try {
               out.append((char)(name.startsWith("#x") || name.startsWith("#X")
                  ? Integer.parseInt(name.substring(2), 16)
                  : Integer.parseInt(name.substring(1))));
            } catch (NumberFormatException e) {
               out.append('&').append(name).append(';');
            }
         } else {
            out.append('&').append(name).append(';');
            i = end;
            continue;
         }

         i = end;
      }

      return out.toString();
   }

   private static void warn(String message) {
      if (WARNED.add(message)) {
         System.out.println("XmlObjects: " + message);
      }
   }
}
