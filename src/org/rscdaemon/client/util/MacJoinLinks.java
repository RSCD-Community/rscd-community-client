package org.rscdaemon.client.util;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/*
 * Receives rscd:// links on macOS, where they do not arrive as arguments.
 *
 * Every other platform hands a clicked link to a NEW process on the command
 * line -- `run.sh %u` on Linux, `"%1"` in the Windows registry -- and
 * SingleInstance is what stops that process becoming a second window. macOS
 * does not work that way. LaunchServices routes the link to the .app that
 * already claims the scheme and delivers it as a `kAEGetURL` Apple event; no
 * second process starts, and nothing appears in argv.
 *
 * The JDK turns that event into a Desktop open-URI callback, but ONLY for a
 * handler that has registered for it. Without one the event is dropped, so the
 * link does nothing at all -- on first launch as well as on a later click.
 * install-mac.sh's `--mac-url-scheme rscd` is necessary and not sufficient: it
 * declares the scheme in Info.plist so macOS routes the link to us, and this
 * is the half that catches what it routes.
 *
 * Reflection because the client is compiled with --release 8 for the players'
 * sake (see build.sh) and this API arrived in 9. Everything here is therefore
 * best-effort by construction: an older JRE, a headless JVM or a platform with
 * no such action all end in a quiet return, which is exactly right, because on
 * every one of those the command-line path is already doing the job.
 *
 * The rule is SingleInstance's rule, deliberately: a link is taken only when
 * nobody is signed in. Note what that means here -- on macOS there is no second
 * process to fall through to, so a player mid-game who clicks a link sees
 * nothing happen rather than getting a second window. Not interrupting a live
 * session is the behaviour we chose; this is the shape it takes on a platform
 * that reuses the app for us.
 */
public final class MacJoinLinks {

   private MacJoinLinks() {
   }

   /*
    * Start answering open-URI events.
    *
    * @param joiner    where an accepted link goes -- the running client
    * @param startupUri the link this launch was already started with, or null.
    *                   macOS may deliver the same link again through the event
    *                   as well; taking it twice would re-join a world we are
    *                   already joining, so the first event matching it is
    *                   ignored.
    */
   public static void register(final SingleInstance.Joiner joiner, final String startupUri) {
      if (joiner == null) {
         return;
      }

      try {
         Class<?> desktopType = Class.forName("java.awt.Desktop");
         Class<?> actionType = Class.forName("java.awt.Desktop$Action");
         Class<?> handlerType = Class.forName("java.awt.desktop.OpenURIHandler");

         if (!(Boolean) desktopType.getMethod("isDesktopSupported").invoke(null)) {
            return;
         }
         Object desktop = desktopType.getMethod("getDesktop").invoke(null);

         /* APP_OPEN_URI exists on every JDK 9+, but is only SUPPORTED on
            macOS. Asking is how we stay silent everywhere else. Read as a
            field rather than via Enum.valueOf, which cannot be spelled without
            a raw type here. */
         Object openUri = actionType.getField("APP_OPEN_URI").get(null);
         Method isSupported = desktopType.getMethod("isSupported", actionType);
         if (!(Boolean) isSupported.invoke(desktop, openUri)) {
            return;
         }

         Object handler = Proxy.newProxyInstance(
               MacJoinLinks.class.getClassLoader(),
               new Class<?>[]{handlerType},
               new Callback(joiner, startupUri));

         desktopType.getMethod("setOpenURIHandler", handlerType).invoke(desktop, handler);
      } catch (Throwable ignored) {
         /* No such class, no such action, headless, or a security manager that
            forbids it. A link then behaves as it did before this existed. */
      }
   }

   private static final class Callback implements InvocationHandler {

      private final SingleInstance.Joiner joiner;
      private final String startupUri;
      private boolean sawFirst;

      Callback(SingleInstance.Joiner joiner, String startupUri) {
         this.joiner = joiner;
         this.startupUri = startupUri;
      }

      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         String name = method.getName();

         /* A Proxy is handed Object's methods too, and answering them with
            "fall through to openURI" would be a bug waiting to happen. */
         if (args == null || args.length != 1) {
            if ("hashCode".equals(name)) {
               return System.identityHashCode(proxy);
            }
            if ("toString".equals(name)) {
               return "rscd-open-uri-handler";
            }
            return null;
         }
         if ("equals".equals(name)) {
            return proxy == args[0];
         }
         if (!"openURI".equals(name)) {
            return null;
         }

         try {
            Object uri = args[0].getClass().getMethod("getURI").invoke(args[0]);
            accept(uri == null ? null : uri.toString());
         } catch (Throwable ignored) {
            /* One malformed event must not take the handler down with it --
               it is registered for the life of the process. */
         }
         return null;
      }

      private void accept(String uri) {
         if (uri == null || !uri.regionMatches(true, 0, "rscd://", 0, 7)) {
            return;
         }

         /* The duplicate of the link we were launched with, and only that one,
            and only once. */
         if (!sawFirst) {
            sawFirst = true;
            if (uri.equals(startupUri)) {
               return;
            }
         }

         joiner.join(uri);
      }
   }
}
