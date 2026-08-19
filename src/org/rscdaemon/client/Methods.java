package org.rscdaemon.client;

import org.rscdaemon.client.entityhandling.EntityHandler;
import org.rscdaemon.client.entityhandling.defs.NPCDef;
import org.rscdaemon.client.entityhandling.defs.SpellDef;
import org.rscdaemon.client.util.Config;
import org.rscdaemon.client.util.DataConversions;

/*
 * The scripting API.
 *
 * This is deliberately a re-implementation of the STS (SkullTorchaScriptable)
 * interface that Reines published in 2005, method for method, name for name.
 * Hundreds of scripts were written against it and they are the reason to keep
 * it: a script compiles here unchanged, because the class is still called
 * Methods, the constructor still takes a mudclient, and the compiler injects
 * "import org.rscdaemon.client.*;" so the missing package line does not matter.
 *
 * That compatibility is also why nothing is ever deleted from this surface.
 * Methods that guarded against Jagex staff (StartScanForMods) or checked a
 * reinet.co.uk subscription (IsSpecial) are meaningless on a server where
 * botting is a supported feature -- but a script merely mentioning one would
 * fail to compile if it went missing, so they stay as documented no-ops.
 *
 * Only the semantics were taken from STS, not its code; the bodies here read
 * this client's own fields. Where the one-line documentation was ambiguous,
 * the decompiled original settled it -- e.g. GetNpcById skips an NPC already
 * in combat and GetAllNpcById does not.
 */
public abstract class Methods {
   /** The client this script drives. Same field name STS used. */
   protected final mudclient rs;
   private final java.util.Random random = new java.util.Random();
   private volatile boolean running = true;

   public final int[] BANKERS = new int[]{95, 224, 268, 485, 540, 617};
   public final int[] BONES = new int[]{20, 413, 604, 814};
   public final String[] SPELLS = new String[]{
      "Wind strike", "Confuse", "Water strike", "Enchant lvl-1 amulet",
      "Earth strike", "Weaken", "Fire strike", "Bones to bananas",
      "Wind bolt", "Curse", "Low level alchemy", "Water bolt",
      "Varrock teleport", "Enchant lvl-2 amulet", "Earth bolt", "Lumbridge teleport",
      "Telekinetic grab", "Fire bolt", "Falador teleport", "Crumble undead",
      "Wind blast", "Superheat item", "Camelot teleport", "Water blast",
      "Enchant lvl-3 amulet", "Iban blast", "Ardougne teleport", "Earth blast",
      "High level alchemy", "Charge Water Orb", "Enchant lvl-4 amulet", "Watchtower teleport",
      "Fire blast", "Claws of Guthix", "Saradomin strike", "Flames of Zamorak",
      "Charge earth Orb", "Wind wave", "Charge Fire Orb", "Water wave",
      "Charge air Orb", "Vulnerability", "Enchant lvl-5 amulet", "Earth wave",
      "Enfeeble", "Fire wave", "Stun", "Charge"
   };

   public Methods(mudclient mc) {
      this.rs = mc;
   }

   /*
    * Stopping from outside the script -- the Stop button, or a second /start.
    * Running() is the only thing a well-behaved script loops on, so setting the
    * flag is enough to unwind it; ScriptRunner interrupts the thread as well so
    * a script parked in Wait() returns immediately rather than at the end of
    * its sleep.
    *
    * Named haltScript() and not stopScript() because APOS's public API has a
    * stopScript() of its own that a script calls on itself, and Script -- which
    * lives in this package -- cannot declare it while a final method of that
    * name is in scope. This one is package-private and no script has ever been
    * able to see it, so the name is free to move.
    */
   final void haltScript() {
      this.running = false;
   }

   final boolean isScriptRunning() {
      return this.running;
   }

   /* ---------------- IRC ---------------- */

   /** Returns true if IRC is enabled and connected */
   public final boolean UsingIRC() {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
      return false;
   }

   /** Sent an action to the desired channel */
   public final void IrcSendAction(String recipient, String message) {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
   }

   /** Sent a priv message to the desired channel/user */
   public final void IrcSendMessage(String recipient, String message) {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
   }

   /** Returns the name of the current active channel */
   public final String GetVisibleChannel() {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
      return "";
   }

   /** Returns a list of all channels you are in */
   public final String[] GetChanList() {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
      return new String[0];
   }

   /** Checks if your in the given channel */
   public final boolean InChannel(String channel) {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
      return false;
   }

   /** Returns your current IRC nickname */
   public final String GetNick() {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
      return "";
   }

   /** Joins the given channel */
   public final void JoinChannel(String channel) {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
   }

   /** Parts the given channel */
   public final void PartChannel(String channel) {
      /* No IRC client here. The hook stays so a 2005 script that mentions it
         still compiles; it reports "not connected" and does nothing. */
   }

   /* ---------------- Methods ---------------- */

   // Sleeps using sleeping bag if at given fatigue, returns false if there is an error,
   // otherwise returns true
   public final boolean SleepIfAt(int Fatigue) {
      /* This client has no sleep screen: fatigue is tracked and displayed, but
         there is no sleeping bag box and no captcha to answer, so there is
         nothing here to do. Kept as a no-op because scripts call it blind. */
      return false;
   }

   // Starts a new thread that scans for any mods anywhere near you, if any are detected the
   // bot closes
   public final void StartScanForMods() {
      /* STS watched for Jagex staff so it could stop the script before they
         saw it. Botting is a supported feature here and there is nobody to
         hide from, so this does nothing on purpose. */
   }

   /** Returns true if your in the given town */
   public final boolean InTown(String Town) {
      return Town != null && this.WhereIs(this.GetX(), this.GetY()).equalsIgnoreCase(Town);
   }

   // Returns the name of the town the given coords are in (has trouble if your upstairs etc,
   // only recognises main places)
   public final String WhereIs(int x, int y) {
      for (int i = 0; i < TOWNS.length; i++) {
         int[] box = TOWN_BOUNDS[i];
         if (x >= box[0] && x <= box[2] && y >= box[1] && y <= box[3]) {
            return TOWNS[i];
         }
      }

      return "Unknown";
   }

   /** This method is called when you start the script. Args[] contains any parameters provided */
   public void MainBody(String[] Args) {
   }

   /** This method is called when you receive a Chat (or NPC) message */
   public void OnChatMessage(String sender, String message) {
   }

   /** This method is called when you receive a private message */
   public void OnPrivateMessage(String sender, String message) {
   }

   /** This method is called when you receive a server message */
   public void OnServerMessage(String message) {
   }

   // Called when the user inputs a string starting with / that isn't a STS command. The / is
   // automatically stripped away
   public void OnInput(String input) {
   }

   /** Called when any key is pressed, that isnt a special hotkey that STS uses */
   public void KeyPressed(int key) {
   }

   /** Used to draw strings to the screen, please see the ExampleScript */
   public Stats ToShow() {
      return null;
   }

   /** Called when debug mode is enabled (F6) and a command is executed */
   public void Debug(String Command) {
   }

   /** Called if you get a chat message on IRC */
   public void OnIRCChatMessage(String channel, String senderName, String senderIdent, String senderHost, String message) {
   }

   /** Called if you get a Private message on IRC */
   public void OnIRCPrivateMessage(String senderName, String senderIdent, String senderHost, String message) {
   }

   /** Called if you see an action on IRC */
   public void OnIRCAction(String channel, String senderName, String senderIdent, String senderHost, String message) {
   }

   /* ---------------- Server Functions ---------------- */

   /** Changes to the given world */
   public final boolean ChangeWorld(int i) {
      /* login() drops the current socket itself, so there is no logout to
         sequence first. How many worlds exist is now a property of whichever
         server was chosen on the Worlds screen rather than a fact about ours,
         so only "not a world number at all" is refused here; a world the
         server does not run fails at the connection with a real message. */
      if (i < 1) {
         return false;
      }

      this.rs.login(this.rs.username, this.rs.password, i, false);
      return true;
   }

   /** Returns the world your currently on */
   public final int GetWorld() {
      return this.rs.theworld;
   }

   /** Returns true if your logged in */
   public final boolean LoggedIn() {
      return this.rs.loggedIn == 1;
   }

   /** Logs you out if possible (10sec rule still applies) */
   public final void LogOut() {
      /* The polite one: refused during combat and for ten seconds after,
         exactly as clicking the logout button is. */
      this.rs.logout();
   }

   /** Forces you to logout */
   public final void ForceLogOut() {
      /* Straight to the packet, skipping the combat check LogOut() honours.
         The server may still refuse; this only means the client will not. */
      this.rs.streamClass.createPacket(129);
      this.rs.streamClass.formatPacket();
      this.rs.logoutTimeout = 1000;
   }

   /** True to enable, false to disable */
   public final void AutoLogin(boolean on) {
      /* Reconnecting on a drop is what the client already does, so this is a
         script turning it off more often than on. */
      this.rs.autoLogin = on;
   }

   /** Return true is AutoLogin is enabled, false if it isn't */
   public final boolean IsAutoLogin() {
      return this.rs.autoLogin;
   }

   /** Tries to login with the given user and pass */
   public final void Login(String user, String pass) {
      this.rs.login(user, pass, this.rs.theworld == 0 ? 1 : this.rs.theworld, false);
   }

   /** Hops to the next server */
   public final void HopServer() {
      /* Two worlds, so hopping is a toggle. */
      this.HopServer(this.rs.theworld == 1 ? 2 : 1);
   }

   /** Hops to the given world */
   public final void HopServer(int world) {
      this.ChangeWorld(world);
   }

   /**
    * Connects to a server by address, where host may carry a ":port" suffix.
    * Not an STS method -- STS scripted a client with one server hardcoded in
    * it, so there was no address for a script to name. Added because this
    * client chooses its server, and a script that logs itself in has to be
    * able to say which one.
    */
   public final boolean ChangeServer(String host, int port) {
      if (host == null || host.trim().length() == 0) {
         return false;
      }

      host = host.trim();
      int colon = host.lastIndexOf(58);
      if (colon > 0) {
         try {
            port = Integer.parseInt(host.substring(colon + 1).trim());
         } catch (NumberFormatException e) {
            return false;
         }

         host = host.substring(0, colon).trim();
      }

      if (port <= 0 || port > 65535) {
         return false;
      }

      this.rs.setServer(host, port);
      this.rs.login(this.rs.username, this.rs.password, host, port, 1, false);
      return true;
   }

   /** The server currently connected to, as host:port. */
   public final String GetServer() {
      return (this.rs.serverHost != null ? this.rs.serverHost : Config.SERVER_IP) + ":" + Config.SERVER_PORT;
   }

   /* ---------------- Npc Functions ---------------- */

   /** Returns the amount of NPCs near you */
   public final int CountNpcs() {
      return this.rs.npcCount;
   }

   // Returns the npc's index, x-coord and y-coord as an array, ignores npc's that are in
   // combat
   public final int[] GetNpcById(int id) {
      return this.findNpc(new int[]{id}, true, false);
   }

   // Returns the npc's index, x-coord and y-coord as an array, ignores npc's that are in
   // combat
   public final int[] GetNpcById(int[] id) {
      return this.findNpc(id, true, false);
   }

   /** Returns the npc's index, x-coord and y-coord as an array, including ones in combat */
   public final int[] GetAllNpcById(int id) {
      return this.findNpc(new int[]{id}, false, false);
   }

   /** Returns the npc's index, x-coord and y-coord as an array, including ones in combat */
   public final int[] GetAllNpcById(int[] id) {
      return this.findNpc(id, false, false);
   }

   /** Returns the npc's index, x-cood and y-coord as an array, ignoring any talking */
   public final int[] GetNpcByIdNotTalk(int id) {
      return this.findNpc(new int[]{id}, true, true);
   }

   /** Returns the npc's index, x-cood and y-coord as an array, ignoring any talking */
   public final int[] GetNpcByIdNotTalk(int[] id) {
      return this.findNpc(id, true, true);
   }

   /** Returns true if the npc is attackable */
   public final boolean NpcAttackable(int id) {
      return EntityHandler.getNpcDef(id).isAttackable();
   }

   /** Returns true if the npc is in combat, false if it isnt */
   public final boolean NpcInCombat(int index) {
      /* Sprites 8 and 9 are the two combat stances; combatTimer stays set for
         a while afterwards, which is what makes a mob still "busy". */
      Mob npc = this.npc(index);
      return npc != null && (npc.currentSprite == 8 || npc.currentSprite == 9 || npc.combatTimer != 0);
   }

   /** Returns true if the npc's health bar is showing , false if it isnt */
   public final boolean NpcHealthBarShowing(int index) {
      /* Narrower than NpcInCombat: the bar is only drawn while combatTimer is
         still counting down. */
      Mob npc = this.npc(index);
      return npc != null && npc.combatTimer > 0;
   }

   /** Returns the name of the given npc */
   public final String NpcName(int id) {
      return EntityHandler.getNpcDef(id).getName();
   }

   /** Returns the npc's description */
   public final String NpcDesc(int id) {
      return EntityHandler.getNpcDef(id).getDescription();
   }

   /** Returns the maximum HP of the given npc */
   public final int NpcMaxHits(int id) {
      return EntityHandler.getNpcDef(id).getHits();
   }

   /**
    * Returns the npc's current HP, or -1 while it is unknown. The client only
    * hears a mob's hitpoints inside damage reports, so an npc nothing has hit
    * yet has no answer -- hitPointsBase is written nowhere else, which makes
    * it the test for "known" (see mudclient.healthCurrent).
    */
   public final int NpcHits(int index) {
      Mob npc = this.npc(index);
      if (npc == null || npc.hitPointsBase == 0) {
         return -1;
      }
      return npc.hitPointsCurrent;
   }

   /** Returns the combat level of the given npc */
   public final int NpcCombat(int id) {
      /* The definition carries the four stats; the level shown in-game is
         derived from them the same way a player's is. */
      NPCDef def = EntityHandler.getNpcDef(id);
      return combatLevel(def.getAtt(), def.getDef(), def.getStr(), def.getHits());
   }

   /** Attacks npc's based on their index */
   public final void AttackNpc(int index) {
      Mob npc = this.npc(index);
      if (npc != null) {
         this.rs.walkToTile(this.rs.sectionX, this.rs.sectionY, this.localX(npc), this.localY(npc), false);
         this.rs.streamClass.createPacket(73);
         this.rs.streamClass.add2ByteInt(npc.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Talks to npc's based on their index */
   public final void TalkToNpc(int index) {
      Mob npc = this.npc(index);
      if (npc != null) {
         this.rs.walkToTile(this.rs.sectionX, this.rs.sectionY, this.localX(npc), this.localY(npc), false);
         this.rs.streamClass.createPacket(177);
         this.rs.streamClass.add2ByteInt(npc.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Thieves to npc's based on their index */
   public final void ThieveNpc(int index) {
      /* The NPC's own command -- Pickpocket on a man, Steal-from on a stall.
         Packet 74 is what the second right-click option sends. */
      Mob npc = this.npc(index);
      if (npc != null) {
         this.rs.walkToTile(this.rs.sectionX, this.rs.sectionY, this.localX(npc), this.localY(npc), false);
         this.rs.streamClass.createPacket(74);
         this.rs.streamClass.add2ByteInt(npc.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns the id of the given npc */
   public final int NpcId(int index) {
      Mob npc = this.npc(index);
      return npc == null ? -1 : npc.type;
   }

   /** Returns the x-coord of the given npc */
   public final int NpcX(int index) {
      Mob npc = this.npc(index);
      return npc == null ? -1 : this.tileX(npc);
   }

   /** Returns the y-coord of the given npc */
   public final int NpcY(int index) {
      Mob npc = this.npc(index);
      return npc == null ? -1 : this.tileY(npc);
   }

   /** Uses item in position pos, on the given npc */
   public final void UseOnNpc(int pos, int index) {
      Mob npc = this.npc(index);
      if (npc != null && this.hasSlot(pos)) {
         this.rs.walkToTile(this.rs.sectionX, this.rs.sectionY, this.localX(npc), this.localY(npc), false);
         this.rs.streamClass.createPacket(142);
         this.rs.streamClass.add2ByteInt(npc.serverIndex);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /* ---------------- Player Functions ---------------- */

   /** Returns the index, x-coord and y-coord as an array */
   public final int[] GetPlayerByName(String name) {
      if (name != null) {
         for (int i = 0; i < this.rs.playerCount; i++) {
            Mob player = this.rs.playerArray[i];
            if (player != null && name.equalsIgnoreCase(player.name)) {
               return new int[]{i, this.tileX(player), this.tileY(player)};
            }
         }
      }

      return notFound();
   }

   /** Returns the amount of players near you */
   public final int CountPlayers() {
      return this.rs.playerCount;
   }

   /** Returns the given players x-coord */
   public final int PlayerX(int index) {
      Mob player = this.player(index);
      return player == null ? -1 : this.tileX(player);
   }

   /** Returns the given players y-coord */
   public final int PlayerY(int index) {
      Mob player = this.player(index);
      return player == null ? -1 : this.tileY(player);
   }

   /** Returns the given players name */
   public final String PlayerName(int index) {
      Mob player = this.player(index);
      return player == null || player.name == null ? "" : player.name;
   }

   /** Trade a player based on their index */
   public final void TradePlayer(int index) {
      Mob player = this.player(index);
      if (player != null) {
         this.rs.streamClass.createPacket(166);
         this.rs.streamClass.add2ByteInt(player.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true when in the first trade screen */
   public final boolean InTradeScreen1() {
      return this.rs.showTradeWindow;
   }

   /** Returns true when in the second trade screen */
   public final boolean InTradeScreen2() {
      return this.rs.showTradeConfirmWindow;
   }

   /** Accepts the first trade screen */
   public final void AcceptTrade1() {
      if (this.rs.showTradeWindow) {
         this.rs.streamClass.createPacket(211);
         this.rs.streamClass.formatPacket();
         this.rs.tradeWeAccepted = true;
      }
   }

   /** Accepts the second trade screen */
   public final void AcceptTrade2() {
      if (this.rs.showTradeConfirmWindow) {
         this.rs.streamClass.createPacket(53);
         this.rs.streamClass.formatPacket();
         this.rs.tradeConfirmAccepted = true;
      }
   }

   /** Returns true if you have accepted the first trade screen */
   public final boolean MyTrade1Accepted() {
      return this.rs.tradeWeAccepted;
   }

   /** Returns true if you have accepted the second trade screen */
   public final boolean MyTrade2Accepted() {
      return this.rs.tradeConfirmAccepted;
   }

   /** Returns true if your opponent has accepted the trade */
   public final boolean OpponentTradeAccepted() {
      return this.rs.tradeOtherAccepted;
   }

   /** Declines the trade */
   public final void DeclineTrade() {
      if (this.rs.showTradeWindow || this.rs.showTradeConfirmWindow) {
         this.rs.streamClass.createPacket(216);
         this.rs.streamClass.formatPacket();
         this.rs.showTradeWindow = false;
         this.rs.showTradeConfirmWindow = false;
      }
   }

   /** Set which items are in the trade window */
   public final void OfferItems(int[] items, int[] amount) {
      /* Replaces the whole offer -- packet 70 is the complete list, not a
         delta, so anything left out is withdrawn from the offer. */
      if (this.rs.showTradeWindow && items != null && amount != null) {
         int count = Math.min(items.length, Math.min(amount.length, this.rs.tradeMyItems.length));
         this.rs.tradeMyItemCount = count;

         for (int i = 0; i < count; i++) {
            this.rs.tradeMyItems[i] = items[i];
            this.rs.tradeMyItemsCount[i] = amount[i];
         }

         this.sendTradeOffer();
      }
   }

   /** Add items into the trade window */
   public final void AddToOffer(int item, int amount) {
      /* Adds to whatever is already offered, then re-sends the full list. */
      if (this.rs.showTradeWindow && this.rs.tradeMyItemCount < this.rs.tradeMyItems.length) {
         this.rs.tradeMyItems[this.rs.tradeMyItemCount] = item;
         this.rs.tradeMyItemsCount[this.rs.tradeMyItemCount] = amount;
         this.rs.tradeMyItemCount++;
         this.sendTradeOffer();
      }
   }

   /** Returns an array containing the ids of the items offered in trade */
   public final int[] GetOpponentTradeItems() {
      int[] items = new int[this.rs.tradeOtherItemCount];

      for (int i = 0; i < items.length; i++) {
         items[i] = this.rs.tradeOtherItems[i];
      }

      return items;
   }

   /** Returns an array containing the amounts of the items offered in trade */
   public final int[] GetOpponentTradeAmounts() {
      int[] amounts = new int[this.rs.tradeOtherItemCount];

      for (int i = 0; i < amounts.length; i++) {
         amounts[i] = this.rs.tradeOtherItemsCount[i];
      }

      return amounts;
   }

   /** Returns true if the amount (or more) of the given id is being offered in trade */
   public final boolean IsOffered(int id, int amount) {
      /* "or more", so a script can check the other side has put up at least
         what was agreed without caring how it was stacked. */
      if (!this.rs.showTradeWindow && !this.rs.showTradeConfirmWindow) {
         return false;
      }

      int total = 0;

      for (int i = 0; i < this.rs.tradeOtherItemCount; i++) {
         if (this.rs.tradeOtherItems[i] == id) {
            total += this.rs.tradeOtherItemsCount[i];
         }
      }

      return total >= amount;
   }

   /** Returns the name of the person your trading with */
   public final String GetTradeOpponentName() {
      return this.rs.tradeOtherPlayerName == null ? "" : this.rs.tradeOtherPlayerName;
   }

   /** Returns true if the given player is in combat */
   public final boolean PlayerInCombat(int index) {
      Mob player = this.player(index);
      return player != null && (player.currentSprite == 8 || player.currentSprite == 9 || player.combatTimer != 0);
   }

   /* ---------------- Communication Functions ---------------- */

   /** Say the given message */
   public final void Speak(String s) {
      if (s != null && s.length() > 0) {
         this.rs.sendChatString(s);
      }
   }

   /** Send a private message to the given player */
   public final void SendPM(String to, String message) {
      if (to != null && message != null && message.length() > 0) {
         byte[] encoded = DataConversions.stringToByteArray(message);
         this.rs.sendPrivateMessage(DataOperations.stringLength12ToLong(to), encoded, encoded.length);
      }
   }

   /** Add the specified player to your friend list */
   public final void AddToFriends(String player) {
      if (player != null) {
         this.rs.addToFriendsList(player);
      }
   }

   /** Add the specified player to your ignore list */
   public final void AddToIgnore(String player) {
      if (player != null) {
         this.rs.addToIgnoreList(player);
      }
   }

   /** Returns an array containing the names of everyone on your friend list */
   public final String[] GetFriendList() {
      String[] names = new String[this.rs.friendsCount];

      for (int i = 0; i < names.length; i++) {
         names[i] = DataOperations.longToString(this.rs.friendsListLongs[i]);
      }

      return names;
   }

   /** Returns an array containing the names of everyone on your ignore list */
   public final String[] GetIgnoreList() {
      String[] names = new String[this.rs.ignoreListCount];

      for (int i = 0; i < names.length; i++) {
         names[i] = DataOperations.longToString(this.rs.ignoreListLongs[i]);
      }

      return names;
   }

   /** Removes the given name from your friend list */
   public final void RemoveFromFriends(String player) {
      if (player != null) {
         this.rs.removeFromFriends(DataOperations.stringLength12ToLong(player));
      }
   }

   /** Removes the given name from your ignore list */
   public final void RemoveFromIgnore(String player) {
      if (player != null) {
         this.rs.removeFromIgnoreList(DataOperations.stringLength12ToLong(player));
      }
   }

   /** Returns true if the given player is on your friend list, otherwise false */
   public final boolean IsOnFriendList(String player) {
      if (player == null) {
         return false;
      }

      long hash = DataOperations.stringLength12ToLong(player);

      for (int i = 0; i < this.rs.friendsCount; i++) {
         if (this.rs.friendsListLongs[i] == hash) {
            return true;
         }
      }

      return false;
   }

   /** Returns true if the given player is on your ignore list, otherwise false */
   public final boolean IsOnIgnoreList(String player) {
      if (player == null) {
         return false;
      }

      long hash = DataOperations.stringLength12ToLong(player);

      for (int i = 0; i < this.rs.ignoreListCount; i++) {
         if (this.rs.ignoreListLongs[i] == hash) {
            return true;
         }
      }

      return false;
   }

   /* ---------------- Spell Functions ---------------- */

   /** Returns the name of the given spell */
   public final String SpellName(int spell) {
      return EntityHandler.getSpellDef(spell).getName();
   }

   /** Returns true if the spell can be cast on an npc */
   public final boolean IsCastableOnNpc(int spell) {
      return EntityHandler.getSpellDef(spell).getSpellType() == 2;
   }

   /** Casts the spell on an npc based on their index */
   public final void CastOnNpc(int spell, int index) {
      Mob npc = this.npc(index);
      if (npc != null) {
      /* stopShort true, the same walk fireAutocast uses: a cast stops next to
         its target, it does not stand on it -- and when the caster is already
         in melee with the target the walk resolves to no steps at all instead
         of a walk request the server reads as fleeing the fight. */
         this.rs.walkToTile(this.rs.sectionX, this.rs.sectionY, this.localX(npc), this.localY(npc), true);
      /* The spell goes FIRST. SpellHandler reads the spell index before it
         switches on the opcode to read the target, so sending the target first
         made the server read the target as the spell -- and any value outside the
         49-entry spell table is rejected outright as a cheat attempt, which is
         what a serverIndex or a tile coordinate almost always is. (That bound
         used to read 44, a stale count that also killed five real spells; see
         SpellHandler.handlePacket.) Every targeted cast in this class
         had the two the wrong way round, so no script has ever successfully cast
         a spell at anything; CastOnSelf worked only because it sends no target. */
         this.rs.streamClass.createPacket(71);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.add2ByteInt(npc.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Casts the spell on an npc without ever moving toward it -- for a
    *  second target while melee holds the first, where CastOnNpc's walk
    *  request would be read by the server as fleeing the fight. The server
    *  accepts a cast from where you stand within 5 tiles of the target;
    *  further away it queues a walk-to-range that a fighting player never
    *  completes, so callers should check the distance themselves. */
   public final void CastOnNpcStill(int spell, int index) {
      Mob npc = this.npc(index);
      if (npc != null) {
         this.rs.streamClass.createPacket(71);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.add2ByteInt(npc.serverIndex);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if the spell can be cast on an item */
   public final boolean IsCastableOnItem(int spell) {
      return EntityHandler.getSpellDef(spell).getSpellType() == 3;
   }

   /** Cast the spell on an item based on its position in your inventory */
   public final void CastOnItem(int spell, int pos) {
      if (this.hasSlot(pos)) {
         this.rs.streamClass.createPacket(49);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if the spell can be cast on an item on the ground */
   public final boolean IsCastableOnGItem(int spell) {
      return EntityHandler.getSpellDef(spell).getSpellType() == 3;
   }

   /** Cast the spell on an item on the ground */
   public final void CastOnGItem(int spell, int id, int x, int y) {
      this.rs.walkToGroundItem(this.rs.sectionX, this.rs.sectionY, this.local(x, this.rs.areaX), this.local(y, this.rs.areaY), true);
      this.rs.streamClass.createPacket(104);
      this.rs.streamClass.add2ByteInt(spell);
      this.rs.streamClass.add2ByteInt(x);
      this.rs.streamClass.add2ByteInt(y);
      this.rs.streamClass.add2ByteInt(id);
      this.rs.streamClass.formatPacket();
   }

   /** Returns true if the spell can be cast on yourself (teleport etc) */
   public final boolean IsCastableOnSelf(int spell) {
      int type = EntityHandler.getSpellDef(spell).getSpellType();
      return type <= 1 || type == 7;
   }

   /** Cast a spell on yourself */
   public final void CastOnSelf(int spell) {
      this.rs.streamClass.createPacket(206);
      this.rs.streamClass.add2ByteInt(spell);
      this.rs.streamClass.formatPacket();
   }

   /** Returns true if the spell can be cast on the ground (charge etc) */
   public final boolean IsCastableOnGround(int spell) {
      return EntityHandler.getSpellDef(spell).getSpellType() == 6;
   }

   /** Cast the spell on the ground */
   public final void CastOnGround(int spell) {
      /* Cast where you are standing -- there is no target to pick. */
      /* 232 carries the spell and nothing else: handleGroundCast works from
         where the player is standing, so the two coordinates this used to send
         were read as the spell and the cast was rejected. */
      this.rs.streamClass.createPacket(232);
      this.rs.streamClass.add2ByteInt(spell);
      this.rs.streamClass.formatPacket();
   }

   /** Returns true if the spell can be cast on a wall object */
   public final boolean IsCastableOnWallObject(int spell) {
      return EntityHandler.getSpellDef(spell).getSpellType() == 4;
   }

   /** Cast the spell on the wallobject at the given coords */
   public final void CastOnWallObject(int spell, int x, int y) {
      int index = this.wallObjectAt(x, y);
      if (index != -1) {
         this.rs.walkToAction(this.rs.doorX[index], this.rs.doorY[index], this.rs.doorDirection[index]);
         /* 67 likewise carries only the spell. The server has never done
            anything with it -- it answers "not yet implemented" -- but it should
            at least be a well-formed packet rather than one that trips the
            cheat check. */
         this.rs.streamClass.createPacket(67);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.addByte(this.rs.doorDirection[index]);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if the spell can be cast on an object */
   public final boolean IsCastableOnObject(int spell) {
      return EntityHandler.getSpellDef(spell).getSpellType() == 5;
   }

   /** Cast the spell on the object at the given coords */
   public final void CastOnObject(int spell, int x, int y) {
      int index = this.objectAt(x, y);
      if (index != -1) {
         this.rs.walkToObject(this.rs.objectX[index], this.rs.objectY[index], this.rs.objectID[index], this.rs.objectType[index]);
         this.rs.streamClass.createPacket(17);
         this.rs.streamClass.add2ByteInt(spell);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if you have all the runes for the given spell, false if you don't */
   public final boolean HasRunesForSpell(int spell) {
      /* Walks the spell's rune list against the inventory. hasRequiredRunes()
         is the client's own check, so staves that stand in for a rune are
         handled the same way they are when you click the spell. */
      SpellDef def = EntityHandler.getSpellDef(spell);
      java.util.Iterator<java.util.Map.Entry<Integer, Integer>> runes = def.getRunesRequired().iterator();

      while (runes.hasNext()) {
         java.util.Map.Entry<Integer, Integer> rune = runes.next();
         if (!this.rs.hasRequiredRunes(rune.getKey().intValue(), rune.getValue().intValue())) {
            return false;
         }
      }

      return true;
   }

   /* ---------------- Npc Interaction Functions ---------------- */

   /** Returns true if the quest menu is visible, false otherwise */
   public final boolean QuestMenu() {
      return this.rs.showQuestionMenu;
   }

   /** Returns the amount of options on the quest menu */
   public final int CountQuestMenu() {
      return this.rs.showQuestionMenu ? this.rs.questionMenuCount : 0;
   }

   /** Returns the given option */
   public final String GetQuestOption(int i) {
      return this.rs.showQuestionMenu && i >= 0 && i < this.rs.questionMenuCount ? this.rs.questionMenuAnswer[i] : "";
   }

   /** Answers the quest menu with the given option */
   public final void Answer(int i) {
      if (this.rs.showQuestionMenu && i >= 0 && i < this.rs.questionMenuCount) {
         this.rs.streamClass.createPacket(154);
         this.rs.streamClass.addByte(i);
         this.rs.streamClass.formatPacket();
         this.rs.showQuestionMenu = false;
      }
   }

   /** Returns true if the given npc is talking currently */
   public final boolean IsNpcTalking(int index) {
      /* lastMessageTimeout is how long the speech bubble has left. */
      Mob npc = this.npc(index);
      return npc != null && npc.lastMessageTimeout > 0;
   }

   /* ---------------- Bank & Shop Functions ---------------- */

   /** Returns the amount of items in your bank */
   public final int BankCount() {
      return this.rs.bankItemCount;
   }

   /** Gives the id of the bank item at the given position */
   public final int BankItemId(int index) {
      return index >= 0 && index < this.rs.bankItemCount ? this.rs.bankItems[index] : -1;
   }

   // Withdraws the given amount of items from your bank (can only withdraw 1 non-stackable at
   // a time)
   public final void Withdraw(int id, int amount) {
      if (this.rs.showBank) {
         this.rs.streamClass.createPacket(183);
         this.rs.streamClass.add2ByteInt(id);
         this.rs.streamClass.add4ByteInt(amount);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Deposit the given amount of items into your bank */
   public final void Deposit(int id, int amount) {
      if (this.rs.showBank) {
         this.rs.streamClass.createPacket(198);
         this.rs.streamClass.add2ByteInt(id);
         this.rs.streamClass.add4ByteInt(amount);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if the item is in your bank */
   public final boolean ItemInBank(int id) {
      return this.bankSlot(id) != -1;
   }

   /** Returns the amount of the given item in your bank */
   public final int CountInBank(int id) {
      int slot = this.bankSlot(id);
      return slot == -1 ? 0 : this.rs.bankItemsCount[slot];
   }

   /** Returns true if your bank is open, false is it isn't */
   public final boolean InBank() {
      return this.rs.showBank;
   }

   /** Closes the bank screen */
   public final void CloseBank() {
      if (this.rs.showBank) {
         this.rs.streamClass.createPacket(48);
         this.rs.streamClass.formatPacket();
         this.rs.showBank = false;
      }
   }

   /** Returns true if the shop screen is open */
   public final boolean InShop() {
      return this.rs.showShop;
   }

   /** Closes the shop screen */
   public final void CloseShop() {
      if (this.rs.showShop) {
         this.rs.streamClass.createPacket(253);
         this.rs.streamClass.formatPacket();
         this.rs.showShop = false;
      }
   }

   /** Buy the given item from the shop */
   public final void BuyShopItem(int id) {
      /* One at a time, at the price the shop is currently asking -- the server
         re-checks it, so a stale price is refused rather than overpaid. */
      if (this.rs.showShop && this.CountShop(id) > 0) {
         this.rs.streamClass.createPacket(128);
         this.rs.streamClass.add2ByteInt(id);
         this.rs.streamClass.add4ByteInt(this.ShopBuyPrice(id));
         this.rs.streamClass.addString("1");
         this.rs.streamClass.formatPacket();
      }
   }

   /** Sell the given item to the shop */
   public final void SellShopItem(int id) {
      if (this.rs.showShop && this.InInv(id)) {
         this.rs.streamClass.createPacket(255);
         this.rs.streamClass.add2ByteInt(id);
         this.rs.streamClass.add4ByteInt(this.ShopSellPrice(id));
         this.rs.streamClass.addString("1");
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns the price of the given item in the shop */
   public final int ShopBuyPrice(int id) {
      /* What the shop charges you: its buy modifier against the item's base
         price. Both shops' modifiers move with stock, so this is only good
         for as long as the window says it is. */
      return this.rs.shopItemBuyPriceModifier * EntityHandler.getItemDef(id).getBasePrice() / 100;
   }

   /** Returns the price the shop will give for the item */
   public final int ShopSellPrice(int id) {
      /* What the shop pays you. */
      return this.rs.shopItemSellPriceModifier * EntityHandler.getItemDef(id).getBasePrice() / 100;
   }

   /** Returns the amount of the given item in the shop */
   public final int CountShop(int id) {
      for (int i = 0; i < this.rs.shopItems.length; i++) {
         if (this.rs.shopItems[i] == id) {
            return this.rs.shopItemCount[i];
         }
      }

      return 0;
   }

   /* ---------------- Status Functions ---------------- */

   /** Gets your armour points */
   public final int GetArmourStats(int i) {
      /* Armour, WeaponAim, WeaponPower, Magic, Prayer, Ranged -- the six the
         stats panel lists, in the order it lists them. */
      return i >= 0 && i < this.rs.equipmentStatus.length ? this.rs.equipmentStatus[i] : 0;
   }

   /** Returns true if you can logout */
   public final boolean CanLogOut() {
      /* lastWalkTimeout is the client's own combat lock: above 450 you are
         fighting, above 0 you are inside the ten second cool-down. */
      return this.rs.loggedIn == 1 && this.rs.lastWalkTimeout == 0;
   }

   /** Returns true if you are currently sleeping */
   public final boolean Sleeping() {
      /* Always false, and deliberately.

         There is a sleep cycle -- using the bag locks you, and 1.5 seconds
         later fatigue is zero -- but the server never tells the client it is
         happening, and there is no sleep screen here to show if it did. On
         Jagex's servers the screen existed to make you type a distorted word,
         which was there to stop botting and was beaten by OCR inside months.
         Botting is a supported feature here, so the thing that screen was for
         does not apply and it is not coming back.

         1.5 seconds is short enough that a script testing this can only lose
         by firing the bag twice, which costs nothing: the second use zeroes a
         fatigue that is already zero. */
      return false;
   }

   /** Sets your fightmode */
   public final void SetMode(int i) {
      /* 0 controlled, 1 aggressive, 2 accurate, 3 defensive. */
      if (i >= 0 && i <= 3 && this.rs.lockedCombatStyle == -1) {
         this.rs.combatStyle = i;
         this.rs.streamClass.createPacket(42);
         this.rs.streamClass.addByte(i);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns your current fightmode */
   public final int GetMode() {
      return this.rs.combatStyle;
   }

   /**
    * A ground click on the tile you are standing on -- the classic way to run
    * from a fight, no movement involved. The server refuses retreat until the
    * opponent has made 3 hits, so keep calling until InCombat() goes false.
    * Does nothing useful outside combat.
    */
   public final void FleeCombat() {
      this.rs.sendFleeCommand();
   }

   /** Returns true if the built-in autocast (F2 settings) is switched on */
   public final boolean GetAutocast() {
      return this.rs.autocastEnabled;
   }

   /**
    * Switches the built-in autocast on or off. A script that wants melee to
    * land the killing blow has to be able to switch this off: autocast
    * re-casts the player's last spell at their combat target once a second,
    * and a spell that lands the kill collects no combat experience at all.
    */
   public final void SetAutocast(boolean on) {
      this.rs.autocastEnabled = on;
   }

   /** Returns your fatigue level */
   public final int GetFatigue() {
      return this.rs.fatigue;
   }

   /** Returns the amount of exp in the given stat */
   public final long GetExp(int i) {
      return (long)this.rs.playerStatExperience[i];
   }

   /** Returns the amount of exp for your next level */
   public final int GetExpForNextLvl(int i) {
      /* Remaining experience, not the threshold -- STS returned the gap and
         scripts print it straight to the screen. Zero at 99. */
      int next = this.rs.experienceArray[0];

      for (int i1 = 0; i1 < 98; i1++) {
         if (this.rs.playerStatExperience[i] >= this.rs.experienceArray[i1]) {
            next = this.rs.experienceArray[i1 + 1];
         }
      }

      int remaining = next - this.rs.playerStatExperience[i];
      return remaining < 0 ? 0 : remaining;
   }

   /** Returns the name of the given stat */
   public final String GetLvlName(int i) {
      return this.rs.skillArray[i];
   }

   /** Returns your current level in the given stat */
   public final int GetCurLvl(int i) {
      return this.rs.playerStatCurrent[i];
   }

   /** Returns your max level in the given stat */
   public final int GetMaxLvl(int i) {
      return this.rs.playerStatBase[i];
   }

   /** Returns your combat level */
   public final int GetCombatLvl() {
      return this.rs.ourPlayer == null ? -1 : this.rs.ourPlayer.level;
   }

   /** Returns your x-coord */
   public final int GetX() {
      return this.rs.sectionX + this.rs.areaX;
   }

   /** Returns your y-coord */
   public final int GetY() {
      return this.rs.sectionY + this.rs.areaY;
   }

   /** Returns true if your in combat, false if your not */
   public final boolean InCombat() {
      return this.rs.ourPlayer != null
         && (this.rs.ourPlayer.currentSprite == 8 || this.rs.ourPlayer.currentSprite == 9 || this.rs.ourPlayer.combatTimer != 0);
   }

   /**
    * True only while actually exchanging blows -- the combat stance sprite.
    * InCombat() also counts combatTimer, which is the health bar's linger
    * timer: it keeps running for seconds after the server has already freed
    * you, so a script waiting on InCombat() before eating stands hungry
    * through exactly the window it fled to use. This is the test that
    * matches the server's own fight lock.
    */
   public final boolean Fighting() {
      return this.rs.ourPlayer != null
         && (this.rs.ourPlayer.currentSprite == 8 || this.rs.ourPlayer.currentSprite == 9);
   }

   /** Returns true if your health bar is showing, false if it isn't */
   public final boolean HealthBarShowing() {
      return this.rs.ourPlayer != null && this.rs.ourPlayer.combatTimer > 0;
   }

   /** Returns your current hp as a percentage */
   public final int HitsPercent() {
      /* Current hits as a percentage of the base level, which is what the
         green bar above your head is showing. */
      int base = this.rs.playerStatBase[3];
      return base <= 0 ? 0 : this.rs.playerStatCurrent[3] * 100 / base;
   }

   /** Returns true if the given prayer is switched on */
   public final boolean IsPrayerOn(int i) {
      return this.rs.prayerOn[i];
   }

   /** Turn on the given prayer */
   public final void PrayerOn(int i) {
      if (i >= 0 && i < this.rs.prayerOn.length && !this.rs.prayerOn[i]) {
         this.rs.streamClass.createPacket(56);
         this.rs.streamClass.addByte(i);
         this.rs.streamClass.formatPacket();
         this.rs.prayerOn[i] = true;
      }
   }

   /** Turn off the given prayer */
   public final void PrayerOff(int i) {
      if (i >= 0 && i < this.rs.prayerOn.length && this.rs.prayerOn[i]) {
         this.rs.streamClass.createPacket(248);
         this.rs.streamClass.addByte(i);
         this.rs.streamClass.formatPacket();
         this.rs.prayerOn[i] = false;
      }
   }

   /** Returns the required level for the given prayer */
   public final int PrayerLvl(int i) {
      return EntityHandler.getPrayerDef(i).getReqLevel();
   }

   /** Returns true if the given quest is completed */
   public final boolean QuestDone(int i) {
      /* The id indexes mudclient.QUEST_NAMES, which is Jagex's own order, so
         the ids a 2005 script hardcoded still mean the same quests. Everything
         reads false until the server sends the completion packet.

         questProgress is 0/1/2 now, not a boolean -- 2 means complete. */
      return i >= 0 && i < this.rs.questProgress.length && this.rs.questProgress[i] == 2;
   }

   /** Returns the number of quest points you have */
   public final int QuestPoints() {
      return this.rs.questPoints;
   }

   /** True to force the statmenu to show over all others, false to return to normal */
   public final void ForceStatMenu(boolean show) {
      this.rs.forceStatMenu = show;
   }

   /** Returns true if you are currently walking */
   public final boolean IsWalking() {
      /* stepCount is how many waypoints are left in the walk the server sent
         us; anything above zero means we are still moving. */
      return this.rs.ourPlayer != null && this.rs.ourPlayer.stepCount > 0;
   }

   /** Returns true if your in combat with a player rather than npc */
   public final boolean FightingPlayerInWild() {
      /* Only interesting because it is the one fight you cannot walk away
         from without losing what you are carrying. */
      if (this.WildLvl() <= 0 || this.rs.ourPlayer == null || !this.InCombat()) {
         return false;
      }

      int opponent = this.rs.ourPlayer.attackingMobIndex;
      return opponent > 0;
   }

   /** Returns true if your in wilderness */
   public final boolean InWild() {
      return this.WildLvl() > 0;
   }

   /** Returns the wilderness level your in */
   public final int WildLvl() {
      /* The client's own formula, off the top of drawGame(): wilderness runs
         north from y 2203, and the strip east of x 2640 is the members'
         wilderness which reports as not-wilderness at all. */
      if (this.rs.notInWilderness) {
         return 0;
      }

      int depth = 2203 - (this.rs.sectionY + this.rs.wildY + this.rs.areaY);
      if (this.rs.sectionX + this.rs.wildX + this.rs.areaX >= 2640) {
         depth = -50;
      }

      return depth > 0 ? 1 + depth / 6 : 0;
   }

   /** Returns true if the current world is members */
   public final boolean MembersWorld() {
      /* Everything is open here; there is no free world to be on. */
      return true;
   }

   /** Deals with an incorrect sleep word */
   public final boolean FixSleeping() {
      /* There is no sleep word to get wrong -- see Sleeping(). Kept as a
         no-op because scripts call it blind. */
      return false;
   }

   /* ---------------- Inventory Functions ---------------- */

   /** Returns the amount of items in your inventory */
   public final int CountInv() {
      return this.rs.inventoryCount;
   }

   /** Returns the amount of the given item in your inventory */
   public final int CountInv(int id) {
      int total = 0;
            for (int slot = 0; slot < this.rs.inventoryCount; slot++) {
               if (this.rs.inventoryItems[slot] == id) { total += this.ItemStackable(id) ? this.rs.inventoryItemsCount[slot] : 1; }
            }
            return total;
   }

   /** Returns the amount of the given items in your inventory */
   public final int CountInv(int[] id) {
      int total = 0;
            for (int i = 0; i < id.length; i++) { total += this.CountInv(id[i]); }
            return total;
   }

   /** Returns true if the given item is in your inventory, false if it isn't */
   public final boolean InInv(int id) {
      return this.GetItemPos(id) != -1;
   }

   /** Returns the position of the given item in your inventory */
   public final int GetItemPos(int id) {
      for (int slot = 0; slot < this.rs.inventoryCount; slot++) {
               if (this.rs.inventoryItems[slot] == id) { return slot; }
            }
            return -1;
   }

   /** Use an item based on its position */
   public final void UseItem(int pos) {
      /* The item's own command -- Eat on food, Bury on bones, Light on logs. */
      if (this.hasSlot(pos)) {
         this.rs.streamClass.createPacket(89);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Use the second function of an item based on its position */
   public final void UseItem2(int pos) {
      /* RSC items carry exactly one inventory command, so the "second" one is
         the same one. Kept because scripts call it. */
      this.UseItem(pos);
   }

   /** Use the item in the first given position with the item in the second given position */
   public final void UseItemWithItem(int pos1, int pos2) {
      if (this.hasSlot(pos1) && this.hasSlot(pos2)) {
         this.rs.streamClass.createPacket(27);
         this.rs.streamClass.add2ByteInt(pos2);
         this.rs.streamClass.add2ByteInt(pos1);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Drop the item in the given position */
   public final void DropItem(int pos) {
      if (this.hasSlot(pos)) {
         this.rs.streamClass.createPacket(147);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if the given item is wearable */
   public final boolean IsItemWearable(int id) {
      return EntityHandler.getItemDef(id).isWieldable();
   }

   /** Returns true if your wearing the item in the given position */
   public final boolean WearingItem(int pos) {
      /* wearing[] is indexed by slot the same way the inventory is: 1 means
         that inventory slot is currently equipped. */
      return this.hasSlot(pos) && this.rs.wearing[pos] == 1;
   }

   /** Wears the item in the given position */
   public final void WearItem(int pos) {
      if (this.hasSlot(pos)) {
         this.rs.streamClass.createPacket(181);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Removes the item in the given position */
   public final void RemoveItem(int pos) {
      if (this.hasSlot(pos)) {
         this.rs.streamClass.createPacket(92);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns true if the given item can be eaten */
   public final boolean CanEat(int id) {
      /* Anything whose inventory command is Eat or Drink. Reading the
         definition rather than carrying a food list means new food works
         without touching this. */
      String command = EntityHandler.getItemDef(id).getCommand();
      return command != null && (command.equalsIgnoreCase("Eat") || command.equalsIgnoreCase("Drink"));
   }

   /** Uses an item in your iventory with an item on the ground */
   public final void UseItemOnGItem(int pos, int x, int y, int id) {
      if (this.hasSlot(pos)) {
         this.rs.walkToGroundItem(this.rs.sectionX, this.rs.sectionY, this.local(x, this.rs.areaX), this.local(y, this.rs.areaY), true);
         this.rs.streamClass.createPacket(34);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.add2ByteInt(id);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns the id of the item in the given position */
   public final int InvItemId(int pos) {
      return pos >= 0 && pos < this.rs.inventoryCount ? this.rs.inventoryItems[pos] : -1;
   }

   /* ---------------- Movement Functions ---------------- */

   /** Walk to the given coords */
   public final void WalkTo(int x, int y) {
      /* Sends the walk and returns immediately; the steps arrive over the next
         few ticks. WalkToWait() is the blocking one. */
      this.rs.sendWalkCommand(
         this.rs.sectionX, this.rs.sectionY, this.local(x, this.rs.areaX), this.local(y, this.rs.areaY),
         this.local(x, this.rs.areaX), this.local(y, this.rs.areaY), false, false
      );
   }

   /** Walk to the given coords and pause the script until you are at them */
   public final void WalkToWait(int x, int y) {
      this.WalkTo(x, y);
      this.waitForWalk(x, y);
   }

   /** Used to walk to the given coords even if a person is currently standing there */
   public final void ForceWalkTo(int x, int y) {
      /* Ignores the pathfinder's refusal and sends the step anyway. Useful for
         a tile the client thinks is blocked but the server will accept -- a
         door that has just opened, say. */
      this.rs.sendWalkCommandIgnoreCoordsEqual(
         this.rs.sectionX, this.rs.sectionY, this.local(x, this.rs.areaX), this.local(y, this.rs.areaY),
         this.local(x, this.rs.areaX), this.local(y, this.rs.areaY), false, true
      );
   }

   /** Used to walk to the given coords even if a person is currently standing there */
   public final void ForceWalkToWait(int x, int y) {
      this.ForceWalkTo(x, y);
      this.waitForWalk(x, y);
   }

   /** Returns true if you can reach the current coords */
   public final boolean IsReachable(int x, int y) {
      /* getStepCount() returns -1 when the pathfinder cannot get there, which
         is the only reachability answer the client has. */
      return this.rs.engineHandle.getStepCount(
            this.rs.sectionX, this.rs.sectionY,
            this.local(x, this.rs.areaX), this.local(y, this.rs.areaY),
            this.local(x, this.rs.areaX), this.local(y, this.rs.areaY),
            this.rs.sectionXArray, this.rs.sectionYArray, false
         )
         != -1;
   }

   /* ---------------- Wall Object Functions ---------------- */

   /** Returns the amount of wall objects near you */
   public final int CountWallObjects() {
      return this.rs.doorCount;
   }

   /** Returns the id, x-coord and y-coord of the nearest given wall object */
   public final int[] GetWallObjectById(int id) {
      return this.findWallObject(new int[]{id});
   }

   /** Returns the id, x-coord and y-coord of the nearest given wall object */
   public final int[] GetWallObjectById(int[] id) {
      return this.findWallObject(id);
   }

   /** Main action on the wall object at the given coords */
   public final void AtWallObject(int x, int y) {
      int index = this.wallObjectAt(x, y);
      if (index != -1) {
         this.rs.walkToAction(this.rs.doorX[index], this.rs.doorY[index], this.rs.doorDirection[index]);
         this.rs.streamClass.createPacket(126);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.addByte(this.rs.doorDirection[index]);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Secondary action on the wall object at the given coords */
   public final void AtWallObject2(int x, int y) {
      int index = this.wallObjectAt(x, y);
      if (index != -1) {
         this.rs.walkToAction(this.rs.doorX[index], this.rs.doorY[index], this.rs.doorDirection[index]);
         this.rs.streamClass.createPacket(235);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.addByte(this.rs.doorDirection[index]);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns the id of the wallobject at the given coords */
   public final int GetIdWallObject(int x, int y) {
      int index = this.wallObjectAt(x, y);
      return index == -1 ? -1 : this.rs.doorType[index];
   }

   /** Use the item in the given pos on the wallobject at the given coords */
   public final void UseOnWallObject(int pos, int x, int y) {
      int index = this.wallObjectAt(x, y);
      if (index != -1 && this.hasSlot(pos)) {
         this.rs.walkToAction(this.rs.doorX[index], this.rs.doorY[index], this.rs.doorDirection[index]);
         this.rs.streamClass.createPacket(36);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.addByte(this.rs.doorDirection[index]);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Returns the X coord of the given wallobject */
   public final int WallObjectX(int index) {
      return index >= 0 && index < this.rs.doorCount ? this.rs.doorX[index] + this.rs.areaX : -1;
   }

   /** Returns the Y coord of the given wallobject */
   public final int WallObjectY(int index) {
      return index >= 0 && index < this.rs.doorCount ? this.rs.doorY[index] + this.rs.areaY : -1;
   }

   /** Returns the Id of the given wallobject */
   public final int WallObjectId(int index) {
      return index >= 0 && index < this.rs.doorCount ? this.rs.doorType[index] : -1;
   }

   /** Returns the name of the given wallobject */
   public final String WallObjectName(int id) {
      return EntityHandler.getDoorDef(id).getName();
   }

   /** Returns the description of the given wallobject */
   public final String WallObjectDesc(int id) {
      return EntityHandler.getDoorDef(id).getDescription();
   }

   /* ---------------- Item Functions ---------------- */

   /** Returns the amount of items near you */
   public final int CountItems() {
      return this.rs.groundItemCount;
   }

   /** Returns the name of the given item */
   public final String ItemName(int id) {
      return EntityHandler.getItemDef(id).getName();
   }

   /** Returns the description of the given item */
   public final String ItemDesc(int id) {
      return EntityHandler.getItemDef(id).getDescription();
   }

   /** Returns the id, x-coord and y-coord of the nearest given item */
   public final int[] GetItemById(int id) {
      return this.findGroundItem(new int[]{id});
   }

   /** Returns the id, x-coord and y-coord of the nearest given item */
   public final int[] GetItemById(int[] id) {
      return this.findGroundItem(id);
   }

   /** Picks up the item at the specified coords */
   public final void PickupItem(int x, int y) {
      /* Whatever is on top of that tile. */
      int index = this.groundItemAt(x, y, null);
      if (index != -1) {
         this.PickupItem(x, y, this.rs.groundItemType[index]);
      }
   }

   /** Picks up an item based on its coords and id */
   public final void PickupItem(int x, int y, int id) {
      this.rs.walkToGroundItem(this.rs.sectionX, this.rs.sectionY, this.local(x, this.rs.areaX), this.local(y, this.rs.areaY), true);
      this.rs.streamClass.createPacket(245);
      this.rs.streamClass.add2ByteInt(x);
      this.rs.streamClass.add2ByteInt(y);
      this.rs.streamClass.add2ByteInt(id);
      this.rs.streamClass.add2ByteInt(0);
      this.rs.streamClass.formatPacket();
   }

   /** Picks up the closest item of the given id */
   public final void PickupItemById(int id) {
      this.PickupItemById(new int[]{id});
   }

   /** Picks up the closest item of the given ids */
   public final void PickupItemById(int[] id) {
      int[] found = this.findGroundItem(id);
      if (found[0] != -1) {
         this.PickupItem(found[1], found[2], found[0]);
      }
   }

   /** Returns true if the given item is stackable */
   public final boolean ItemStackable(int id) {
      return EntityHandler.getItemDef(id).isStackable();
   }

   /** Returns the X coord of the given item */
   public final int ItemX(int index) {
      return index >= 0 && index < this.rs.groundItemCount ? this.rs.groundItemX[index] + this.rs.areaX : -1;
   }

   /** Returns the Y coord of the given item */
   public final int ItemY(int index) {
      return index >= 0 && index < this.rs.groundItemCount ? this.rs.groundItemY[index] + this.rs.areaY : -1;
   }

   /** Returns the Id of the given item */
   public final int ItemId(int index) {
      return index >= 0 && index < this.rs.groundItemCount ? this.rs.groundItemType[index] : -1;
   }

   /** Returns true if the given id is at the given coords */
   public final boolean IsItemAt(int x, int y, int id) {
      return this.groundItemAt(x, y, new int[]{id}) != -1;
   }

   /* ---------------- Object Functions ---------------- */

   /** Returns the amount of objects near you */
   public final int CountObjects() {
      return this.rs.objectCount;
   }

   /** Returns the id, x-coord and y-coord of the nearest given object */
   public final int[] GetObjectById(int id) {
      return this.findObject(new int[]{id});
   }

   /** Returns the id, x-coord and y-coord of the nearest given object */
   public final int[] GetObjectById(int[] id) {
      return this.findObject(id);
   }

   /** Main action on the object at the given coords */
   public final void AtObject(int x, int y) {
      int index = this.objectAt(x, y);
      if (index != -1) {
         this.rs.walkToObject(this.rs.objectX[index], this.rs.objectY[index], this.rs.objectID[index], this.rs.objectType[index]);
         this.rs.streamClass.createPacket(51);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Secondary action on the object at the given coords */
   public final void AtObject2(int x, int y) {
      int index = this.objectAt(x, y);
      if (index != -1) {
         this.rs.walkToObject(this.rs.objectX[index], this.rs.objectY[index], this.rs.objectID[index], this.rs.objectType[index]);
         this.rs.streamClass.createPacket(40);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Use the item in the given position on the given object */
   public final void UseOnObject(int pos, int x, int y) {
      int index = this.objectAt(x, y);
      if (index != -1 && this.hasSlot(pos)) {
         this.rs.walkToObject(this.rs.objectX[index], this.rs.objectY[index], this.rs.objectID[index], this.rs.objectType[index]);
         this.rs.streamClass.createPacket(94);
         this.rs.streamClass.add2ByteInt(x);
         this.rs.streamClass.add2ByteInt(y);
         this.rs.streamClass.add2ByteInt(pos);
         this.rs.streamClass.formatPacket();
      }
   }

   /** Return the id of the object at the specified coords */
   public final int GetIdObject(int x, int y) {
      int index = this.objectAt(x, y);
      return index == -1 ? -1 : this.rs.objectType[index];
   }

   /** Returns the X coord of the given object */
   public final int ObjectX(int index) {
      return index >= 0 && index < this.rs.objectCount ? this.rs.objectX[index] + this.rs.areaX : -1;
   }

   /** Returns the Y coord of the given object */
   public final int ObjectY(int index) {
      return index >= 0 && index < this.rs.objectCount ? this.rs.objectY[index] + this.rs.areaY : -1;
   }

   /** Returns the Id of the given object */
   public final int ObjectId(int index) {
      return index >= 0 && index < this.rs.objectCount ? this.rs.objectType[index] : -1;
   }

   /** Returns the name of the given object */
   public final String ObjectName(int id) {
      return EntityHandler.getObjectDef(id).getName();
   }

   /** Returns the description of the given object */
   public final String ObjectDesc(int id) {
      return EntityHandler.getObjectDef(id).getDescription();
   }

   /** Returns true if an object on the given id is at the given coords */
   public final boolean IsObjectAt(int id, int x, int y) {
      int index = this.objectAt(x, y);
      return index != -1 && this.rs.objectType[index] == id;
   }

   /** Returns true if an object on the given ids is at the given coords */
   public final boolean IsObjectAt(int[] id, int x, int y) {
      int index = this.objectAt(x, y);
      return index != -1 && this.InArray(id, this.rs.objectType[index]);
   }

   /* ---------------- Setup Functions ---------------- */

   /** Enables/disables the gfx */
   public final void SetGfx(boolean on) {
      this.rs.drawGfx = on;
   }

   /** Returns if the users auth is "special" (eg. vet, mod, admin on reinet) */
   public final boolean IsSpecial() {
      /* Checked a subscription on Reines' site. There is nothing to subscribe
         to; every script gets the whole API. */
      return true;
   }

   /** Returns true if the mod scanner is currently active */
   public final boolean IsScanningForMods() {
      /* STS watched for Jagex staff so it could stop the script before they
         saw it. Botting is a supported feature here and there is nobody to
         hide from, so this does nothing on purpose. */
      return false;
   }

   /** Stops the mod scanner */
   public final void StopScanForMods() {
      /* STS watched for Jagex staff so it could stop the script before they
         saw it. Botting is a supported feature here and there is nobody to
         hide from, so this does nothing on purpose. */
   }

   /** Returns true if a popup is currently showing */
   public final boolean IsPopup() {
      /* The two boxes that swallow clicks until they are dismissed: the
         server message box and the welcome screen. */
      return this.rs.showServerMessageBox || this.rs.showWelcomeBox;
   }

   /** Closes any open popups */
   public final void ClosePopup() {
      this.rs.showServerMessageBox = false;
      this.rs.showWelcomeBox = false;
   }

   /**
    * Shows the given message and waits for the player to acknowledge it.
    *
    * In the game view, like GetInput -- STS's ShowMessage was mudclient.s(),
    * which set the text and blocked until dismissed. The Swing popup this
    * replaces was a SkullOrca-era workaround; see ScriptPrompt.
    */
   public final void ShowMessage(String message) {
      this.rs.prompt().show(message);
   }

   /**
    * Prompts the user for input and returns it as a string.
    *
    * Drawn in the game view, which is where it always was: STS's GetInput
    * called straight into the client (mudclient.t) and the client drew a box
    * over the scene. It became a Swing JOptionPane in the SkullOrca era only
    * because SkullOrca could not reach the graphics pane at all -- it loaded
    * Jagex's signed jar reflectively into a JPanel. This client owns its own
    * pane, so the original behaviour is back. See ScriptPrompt.
    *
    * Blocks the script's thread until the player answers, and returns "" if the
    * script is stopped while waiting -- the same thing the Swing version
    * returned when the dialog was dismissed, so no script sees a change.
    */
   public final String GetInput(String message) {
      /* STS called Load() here, which blocked until the loading screen was
         gone. Ours is Loading(), which only REPORTS whether you are logged
         out, so calling it would be a no-op statement rather than a wait --
         left out instead of carried across as noise. */
      return this.rs.prompt().ask(message);
   }

   /** Gives the user a list of options and returns the index of the selected one */
   public final int GetOption(String header, String[] options) {
      /* Returns the index chosen, or -1 if it was answered with anything else. */
      return this.rs.prompt().choose(header, options);
   }

   /** Returns the users auth name */
   public final String GetAuthName() {
      /* The name the account logged in with, before the server tells us how
         it is actually capitalised. */
      return this.rs.username == null ? "" : this.rs.username;
   }

   /** Returns the users username */
   public final String GetRsName() {
      return this.rs.ourPlayer == null || this.rs.ourPlayer.name == null ? "" : this.rs.ourPlayer.name;
   }

   /** Returns the distance between you and the given coords */
   public final int DistanceTo(int x, int y) {
      return this.DistanceTo(this.GetX(), this.GetY(), x, y);
   }

   /** Returns the distance between the given coords */
   public final int DistanceTo(int x1, int y1, int x2, int y2) {
      return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
   }

   /** Return the fightmode to normal */
   public final void UnlockMode() {
      this.rs.lockedCombatStyle = -1;
   }

   /** Lock the fightmode to the given mode */
   public final void LockMode(int i) {
      /* Pins the fight mode: the panel stops responding and SetMode() is
         refused until UnlockMode(), so a script cannot be knocked out of the
         style it needs by a stray click. */
      if (i >= 0 && i <= 3) {
         this.rs.lockedCombatStyle = -1;
         this.SetMode(i);
         this.rs.lockedCombatStyle = i;
      }
   }

   /** Toggle the chatfilter on or off */
   public final void ChatFilterOn(boolean on) {
      /* STS filtered swearing out of the chat box locally. This client has no
         such filter to switch, so the call is accepted and ignored. */
   }

   /** Stop the script and display the given message */
   public final void End(String s) {
      this.Display(s);
            this.running = false;
   }

   /** Stop the script */
   public final void End() {
      this.running = false;
   }

   /** Display the given message then exit the bot */
   public final void Die(String s) {
      /* Deliberately not System.exit: the end-user client runs several of
         these in one JVM, and killing the VM would take every other tab down
         with it. Logging out and stopping the script is as far as this goes. */
      this.Display(s);
      this.Die();
   }

   /** Totally exit the bot */
   public final void Die() {
      this.LogOut();
      this.running = false;
   }

   /** Display the given message */
   public final void Display(String s) {
      System.out.println(s);
   }

   /** Display the given message */
   public final void Display(int i) {
      System.out.println(i);
   }

   /** Returns true if logged out or on a loading screen */
   public final boolean Loading() {
      return this.rs.loggedIn != 1;
   }

   /** Returns a random number between the given boundaries */
   public final int Rand(int low, int high) {
      return low + this.random.nextInt(Math.max(1, high - low + 1));
   }

   /** Returns true if the script is still running */
   public final boolean Running() {
      return this.running;
   }

   /** Pause the script for the given length of milliseconds */
   public final void Wait(int ms) {
      try { Thread.sleep(ms); } catch (InterruptedException e) { this.running = false; }
   }

   /** Returns the system time */
   public final long GetMillis() {
      return System.currentTimeMillis();
   }

   /** Return the current time formatted at HH:mm */
   public final String Time() {
      return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
   }

   /** Saves a screenshot with the specified filename */
   public final void SaveScreenShot(String s) {
      /* The client names its own screenshots; the argument is accepted and
         ignored rather than dropped from the signature. */
      this.rs.takeScreenshot(false);
   }

   /** Converts an integer to a string */
   public final String IntToStr(int i) {
      return String.valueOf(i);
   }

   /** Converts a long to a string */
   public final String IntToStr(long i) {
      return String.valueOf(i);
   }

   /** Converts a string to an integer */
   public final int StrToInt(String s) {
      try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
   }

   /** Returns true if the given string is in the main string */
   public final boolean IsInStr(String string, String findme) {
      return string != null && findme != null && string.indexOf(findme) != -1;
   }

   /** Returns true if the given string is in the array */
   public final boolean InArray(String[] sarray, String s) {
      for (int i = 0; i < sarray.length; i++) { if (sarray[i] != null && sarray[i].equals(s)) { return true; } }
            return false;
   }

   /** Returns true if the given integer is in the array */
   public final boolean InArray(int[] iarray, int i) {
      for (int j = 0; j < iarray.length; j++) { if (iarray[j] == i) { return true; } }
            return false;
   }

   /** Replaces all occurances of toreplace with replacewith, in the string string */
   public final String Replace(String string, String toreplace, String replacewith) {
      return string.replace(toreplace, replacewith);
   }

   /** Returns the average of the given values */
   public final int GetAverage(int[] values) {
      if (values.length == 0) { return 0; }
            long total = 0L;
            for (int i = 0; i < values.length; i++) { total += values[i]; }
            return (int)(total / values.length);
   }

   /** Checks if x,y coords are within the given area */
   public final boolean InArea(int x, int y, int x1, int y1, int x2, int y2) {
      return x >= Math.min(x1, x2) && x <= Math.max(x1, x2) && y >= Math.min(y1, y2) && y <= Math.max(y1, y2);
   }

   /**
    * Plays the given sound file. Supports .wav, .au and .aiff (not mp3, and no
    * longer .mid).
    *
    * This was Applet.newAudioClip, which JDK 24 removed along with the rest of
    * the Applet API. javax.sound.sampled is the replacement and is a straight
    * swap for everything a script actually plays -- the one real loss is MIDI,
    * which newAudioClip handled and Clip does not, and which would need a
    * Sequencer to get back. Nothing in the script API has ever asked for it.
    *
    * Fire and forget, exactly as before: the clip closes itself when it
    * reaches the end, so a script does not have to know it owns a resource.
    */
   public final void PlaySound(String path) {
      /* A file next to the jar, not one of the client's packed sounds --
         those are PlaySound's job in the client, not the script's. */
      try {
         java.io.File file = new java.io.File(path);
         if (file.isFile()) {
            javax.sound.sampled.AudioInputStream stream =
               javax.sound.sampled.AudioSystem.getAudioInputStream(file);
            final javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            clip.addLineListener(new javax.sound.sampled.LineListener() {
               @Override
               public void update(javax.sound.sampled.LineEvent event) {
                  if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                     clip.close();
                  }
               }
            });
            clip.open(stream);
            clip.start();
         }
      } catch (Exception var3) {
         /* A script asking for a sound that will not play is not worth
            stopping the script over. */
      }
   }

   /* ---------------- helpers ---------------- */

   /*
    * The entity arrays hold section-local tiles. Everything a script sees is
    * absolute -- the same coordinates GetX()/GetY() return and the same ones
    * the packets carry -- so the two conversions live here rather than being
    * spelled out at forty call sites.
    */
   protected int tileX(Mob mob) {
      return (mob.currentX - 64) / this.rs.magicLoc + this.rs.areaX;
   }

   protected int tileY(Mob mob) {
      return (mob.currentY - 64) / this.rs.magicLoc + this.rs.areaY;
   }

   /*
    * Walk into range of a mob before acting on it. Every targeted action in
    * this class opens with this line; it is a method so the player-targeted
    * ones added for the APOS tier do not have to reach at localX/localY.
    */
   protected void walkToMob(Mob mob) {
      this.rs.walkToTile(this.rs.sectionX, this.rs.sectionY,
         this.localX(mob), this.localY(mob), false);
   }

   private int localX(Mob mob) {
      return (mob.currentX - 64) / this.rs.magicLoc;
   }

   private int localY(Mob mob) {
      return (mob.currentY - 64) / this.rs.magicLoc;
   }

   private int local(int absolute, int area) {
      return absolute - area;
   }

   protected Mob npc(int index) {
      return index >= 0 && index < this.rs.npcCount ? this.rs.npcArray[index] : null;
   }

   protected Mob player(int index) {
      return index >= 0 && index < this.rs.playerCount ? this.rs.playerArray[index] : null;
   }

   private boolean hasSlot(int pos) {
      return pos >= 0 && pos < this.rs.inventoryCount;
   }

   /* Fresh each time -- a script that writes into the result must not be able
      to poison the next caller's answer. */
   private static int[] notFound() {
      return new int[]{-1, -1, -1};
   }

   /*
    * The nearest matching NPC as {index, x, y}, or {-1,-1,-1}. skipCombat is
    * what separates GetNpcById from GetAllNpcById; skipTalking additionally
    * leaves alone anything mid-dialogue, which is what GetNpcByIdNotTalk is
    * for -- you do not want to interrupt the banker someone else is using.
    */
   private int[] findNpc(int[] ids, boolean skipCombat, boolean skipTalking) {
      int best = -1;
      int bestDistance = Integer.MAX_VALUE;

      for (int i = 0; i < this.rs.npcCount; i++) {
         Mob npc = this.rs.npcArray[i];
         if (npc == null || !this.InArray(ids, npc.type)) {
            continue;
         }

         if (skipCombat && this.NpcInCombat(i)) {
            continue;
         }

         if (skipTalking && this.IsNpcTalking(i)) {
            continue;
         }

         int distance = this.DistanceTo(this.tileX(npc), this.tileY(npc));
         if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
         }
      }

      if (best == -1) {
         return notFound();
      }

      Mob npc = this.rs.npcArray[best];
      return new int[]{best, this.tileX(npc), this.tileY(npc)};
   }

   /*
    * Objects, wall objects and ground items answer with the *id* first, not an
    * index -- only the NPC finders return an index. That asymmetry is STS's and
    * it is load-bearing: scripts written in 2006 do GetObjectById(t)[1] for x
    * and never touch [0], and NPCExists() is written as GetNpcById(id)[0] > -1.
    */
   private int[] findObject(int[] ids) {
      int best = -1;
      int bestDistance = Integer.MAX_VALUE;

      for (int i = 0; i < this.rs.objectCount; i++) {
         if (!this.InArray(ids, this.rs.objectType[i])) {
            continue;
         }

         int distance = this.DistanceTo(this.rs.objectX[i] + this.rs.areaX, this.rs.objectY[i] + this.rs.areaY);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
         }
      }

      return best == -1
         ? notFound()
         : new int[]{this.rs.objectType[best], this.rs.objectX[best] + this.rs.areaX, this.rs.objectY[best] + this.rs.areaY};
   }

   private int[] findWallObject(int[] ids) {
      int best = -1;
      int bestDistance = Integer.MAX_VALUE;

      for (int i = 0; i < this.rs.doorCount; i++) {
         if (!this.InArray(ids, this.rs.doorType[i])) {
            continue;
         }

         int distance = this.DistanceTo(this.rs.doorX[i] + this.rs.areaX, this.rs.doorY[i] + this.rs.areaY);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
         }
      }

      return best == -1
         ? notFound()
         : new int[]{this.rs.doorType[best], this.rs.doorX[best] + this.rs.areaX, this.rs.doorY[best] + this.rs.areaY};
   }

   private int[] findGroundItem(int[] ids) {
      int best = -1;
      int bestDistance = Integer.MAX_VALUE;

      for (int i = 0; i < this.rs.groundItemCount; i++) {
         if (!this.InArray(ids, this.rs.groundItemType[i])) {
            continue;
         }

         int distance = this.DistanceTo(this.rs.groundItemX[i] + this.rs.areaX, this.rs.groundItemY[i] + this.rs.areaY);
         if (distance < bestDistance) {
            bestDistance = distance;
            best = i;
         }
      }

      return best == -1
         ? notFound()
         : new int[]{this.rs.groundItemType[best], this.rs.groundItemX[best] + this.rs.areaX, this.rs.groundItemY[best] + this.rs.areaY};
   }

   /* Index into the client's arrays for the thing standing on an absolute tile. */
   private int objectAt(int x, int y) {
      int localX = this.local(x, this.rs.areaX);
      int localY = this.local(y, this.rs.areaY);

      for (int i = 0; i < this.rs.objectCount; i++) {
         if (this.rs.objectX[i] == localX && this.rs.objectY[i] == localY) {
            return i;
         }
      }

      return -1;
   }

   private int wallObjectAt(int x, int y) {
      int localX = this.local(x, this.rs.areaX);
      int localY = this.local(y, this.rs.areaY);

      for (int i = 0; i < this.rs.doorCount; i++) {
         if (this.rs.doorX[i] == localX && this.rs.doorY[i] == localY) {
            return i;
         }
      }

      return -1;
   }

   /* ids null means "anything". */
   private int groundItemAt(int x, int y, int[] ids) {
      int localX = this.local(x, this.rs.areaX);
      int localY = this.local(y, this.rs.areaY);

      for (int i = 0; i < this.rs.groundItemCount; i++) {
         if (this.rs.groundItemX[i] == localX
            && this.rs.groundItemY[i] == localY
            && (ids == null || this.InArray(ids, this.rs.groundItemType[i]))) {
            return i;
         }
      }

      return -1;
   }

   private int bankSlot(int id) {
      for (int i = 0; i < this.rs.bankItemCount; i++) {
         if (this.rs.bankItems[i] == id) {
            return i;
         }
      }

      return -1;
   }

   private void sendTradeOffer() {
      this.rs.streamClass.createPacket(70);
      this.rs.streamClass.addByte(this.rs.tradeMyItemCount);

      for (int i = 0; i < this.rs.tradeMyItemCount; i++) {
         this.rs.streamClass.add2ByteInt(this.rs.tradeMyItems[i]);
         this.rs.streamClass.add4ByteInt(this.rs.tradeMyItemsCount[i]);
      }

      this.rs.streamClass.formatPacket();
      this.rs.tradeWeAccepted = false;
      this.rs.tradeOtherAccepted = false;
   }

   /*
    * Blocks until the walk finishes or the client says it has stopped moving.
    * The timeout is there because a walk the server silently drops would
    * otherwise park the script forever.
    */
   private void waitForWalk(int x, int y) {
      long deadline = System.currentTimeMillis() + 30000L;

      while (this.running && System.currentTimeMillis() < deadline) {
         if (this.GetX() == x && this.GetY() == y) {
            return;
         }

         if (!this.IsWalking()) {
            return;
         }

         this.Wait(100);
      }
   }

   /* The in-game level from the four combat stats, for NPCs -- players are
      told their level by the server. */
   private static int combatLevel(int attack, int defence, int strength, int hits) {
      return (int)((attack + defence + strength + hits) / 4.0D);
   }

   /*
    * Town boxes, in absolute coordinates including the floor offset (each
    * storey is 944 higher than the one below, so Zanaris reads as y 3521 and
    * the Wizards' Guild above Yanille as y 1703).
    *
    * STS shipped its own table buried in obfuscated bytecode. This one is
    * ours, derived from the server's world data -- every box is drawn around
    * the shop bounds in conf/server/locs/Shops.xml and the banker spawns in
    * NpcLoc.xml, padded to cover the walkable town around them. WhereIs()
    * answers "Unknown" outside them rather than guessing.
    */
   private static final String[] TOWNS = new String[]{
      "Edgeville", "Varrock", "Seers", "Catherby", "Taverley", "Barbarian Village",
      "Falador", "Ardougne", "Draynor", "Port Sarim", "Lumbridge", "Rimmington",
      "Al Kharid", "Karamja", "Yanille", "Shilo Village", "Entrana", "Fishing Guild",
      "Legends Guild", "Legends Guild", "Gnome Stronghold", "Magic Guild",
      "Zanaris", "Dwarven Mines", "Mage Arena"
   };

   private static final int[][] TOWN_BOUNDS = new int[][]{
      {205, 435, 235, 460},
      {95, 490, 155, 535},
      {490, 440, 515, 465},
      {410, 480, 455, 500},
      {360, 495, 390, 515},
      {225, 500, 245, 520},
      {275, 525, 340, 585},
      {535, 565, 592, 620},
      {208, 625, 232, 645},
      {258, 620, 285, 665},
      {125, 635, 145, 665},
      {320, 650, 340, 675},
      {45, 665, 100, 705},
      {340, 705, 370, 720},
      {580, 745, 602, 765},
      {395, 828, 425, 858},
      {430, 555, 445, 572},
      {595, 512, 605, 522},
      {505, 1475, 520, 1490},
      {505, 2418, 520, 2432},
      {705, 1445, 725, 1460},
      {595, 1698, 605, 1710},
      {158, 3515, 180, 3530},
      {262, 3320, 300, 3348},
      {433, 3370, 447, 3382}
   };
}
