package org.rscdaemon.client;

/*
 * The handle an APOS script is handed at construction.
 *
 * Every APOS script ever written begins the same way:
 *
 *    public TPM_Position(Extension e) { super(e); }
 *
 * and then never mentions it again -- in APOS the Extension was the bot's own
 * view of the client, and the script was expected to reach the game through the
 * methods on Script rather than through this object. Sixteen scripts here, and
 * not one of them touches `e` after passing it up.
 *
 * So this is a handle and nothing more. Making it richer would invent an API
 * that no surviving script uses and that nobody can check against the original.
 * If a script one day needs something off it, it is added here and it is added
 * as a method, not by exposing mudclient: the client's fields are
 * package-private on purpose and a script compiled outside this package could
 * not read them anyway.
 */
public final class Extension {
   /* Package-private: Script needs it to call super(mudclient), a script does not. */
   final mudclient rs;

   public Extension(mudclient rs) {
      this.rs = rs;
   }

   /*
    * In APOS the Extension WAS an AWT component -- the bot's own panel -- so a
    * script could ask it for keyboard focus. Here it is a handle, so the
    * request is forwarded to the window that actually has the focus to give.
    *
    * This is the "if a script one day needs something off it" case noted
    * above, and it is added as a method rather than by exposing the client.
    */
   public boolean requestFocusInWindow() {
      return this.rs != null && this.rs.requestFocusForGame();
   }
}
