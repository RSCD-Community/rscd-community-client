package org.rscdaemon.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The counter half of a labourer script: announce, answer, trade, refuse
 * politely. A subclass says what it sells and what one costs, and fills in
 * {@link #work()} with how it gets the stuff.
 *
 * This lives in the client rather than in a script file because the script
 * tier compiles one file at a time: a base class sitting next to the scripts
 * would only ever be rebuilt by accident, which is exactly the stale-bytecode
 * trap that has bitten this project before. In the jar it is compiled with
 * everything else and imported by the preamble like any other client class.
 *
 * Three things are deliberate and worth keeping.
 *
 * <b>The trade window is the price list.</b> RSC has no player shops, and shop
 * prices cannot move without a protocol change -- but a script quoting its own
 * number in chat has no such limit. The whole pricing engine is
 * {@link #price()} and the arithmetic in the trade step.
 *
 * <b>A unit can be more than one item.</b> {@link #goods()} and
 * {@link #goodsPer()} describe what one sale hands over, so a potion shop can
 * sell a super set -- one attack, one strength, one defence -- as a single
 * priced thing, the way people actually bought them.
 *
 * <b>It does not pretend to be a person.</b> Asked what it is, it says so. The
 * point of a crew like this is that the world has somebody in it, not that
 * anyone is fooled about who.
 */
public abstract class MerchantScript extends Script {

   /** Coins. */
   protected static final int COINS = 10;

   /** How long a customer has to make sense before the window is declined. */
   protected static final long TRADE_MS = 20000L;

   /** Bad trades from one person before they stop being answered. */
   protected static final int BAD_TRADE_LIMIT = 3;

   /*
    * Unprompted chatter is deliberately jittered rather than fixed. Two desks
    * standing at the same bank on identical 90s timers speak in lockstep
    * forever, which reads as one machine talking twice rather than two
    * traders working -- and the window is kept narrow because the opposite
    * failure, a desk that goes properly quiet for ten minutes, reads as
    * broken to anyone waiting to buy from it.
    */
   private static final long ANNOUNCE_MIN = 75000L;
   private static final long ANNOUNCE_MAX = 165000L;
   private static final long ASIDE_MS = 240000L;

   /*
    * Floor between two public replies, whoever is asking. This is the
    * backstop that makes a chat loop impossible rather than merely unlikely:
    * the roster below is a list somebody has to remember to update, and this
    * is not. A bot that ships without its name on the roster can cost the
    * channel one line every few seconds, not hundreds.
    */
   private static final long REPLY_MS = 8000L;

   /** How far from where it last banked still counts as standing at the counter. */
   private static final int BANK_RADIUS = 6;

   private long announceTime;
   private long announceGap = ANNOUNCE_MIN;
   private long asideTime;
   private long replyTime;

   /** Where this desk last had a bank window open, or -1 before it ever has. */
   private int bankX = -1;
   private int bankY = -1;
   private long tradeTime = -1L;

   /** Who this trade window belongs to, or null when there is none. */
   private String partner;

   /** How many units are up on this side of the window. */
   private int offered;

   /*
    * Set once this side has clicked confirm. The confirm window stays open
    * until the customer clicks too, and without this flag the next pass would
    * come back round, see nothing offered any more, and decline the trade it
    * had just agreed to -- which is exactly what it did.
    */
   private boolean confirmed;

   /*
    * The last unit count this side explained itself over, so a customer who
    * asks for more than there is gets told once and not once a second until
    * the window times out.
    */
   private int toldShort = -1;

   /** Counts of bad offers, by name. */
   private final Map<String, Integer> badTrades = new HashMap<String, Integer>();

   public MerchantScript(Extension ex) {
      super(ex);
   }

   /* ------------------------------------------------ what a subclass says -- */

   /** The item ids that make up one unit for sale. */
   protected abstract int[] goods();

   /** How many of each of {@link #goods()} one unit contains. */
   protected abstract int[] goodsPer();

   /** Coins per unit. */
   protected abstract int price();

   /** What a customer would call the goods: "iron ore certs", "super sets". */
   protected abstract String goodsPlural();

   /** The gathering half: what this one does for a living between customers. */
   protected abstract int work();

   /** Name in the log. The class name unless a subclass wants otherwise. */
   protected String tag() {
      return getClass().getName();
   }

   /**
    * Crewmates this one will hand its whole stock to, free, on request.
    *
    * Empty by default, and it has to be: a name on this list can walk off
    * with everything the bot is carrying, so it is a deliberate act of
    * naming, never a default. The reason it exists is the way the crew is
    * meant to grow -- when a miner's level is high enough to be worth putting
    * on coal, the iron it has already certed belongs with whoever takes over
    * the iron, not in a bank nobody is selling from. Names are unique on the
    * server, so a name is a safe thing to trust here.
    */
   protected String[] crew() {
      return new String[0];
   }

   /**
    * Every name the project runs a bot under. Unlike {@link #crew()} this
    * grants nothing -- it is a do-not-talk-to list, so there is no harm in it
    * being generous and real harm in it being short. A desk that answers
    * another desk is the whole failure mode: the honest reply to "are you a
    * bot" itself contains the word "bot", so two unrostered desks in one bank
    * will answer each other as fast as the channel allows and never stop.
    *
    * Add a bot here the day it is named, not the day it first misbehaves.
    */
   protected String[] fleet() {
      return new String[] { "Fable" };
   }

   private boolean isFleet(String name) {
      if (isCrew(name)) {
         return true;
      }

      String[] names = fleet();

      for (int i = 0; i < names.length; i++) {
         if (names[i] != null && names[i].equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Whether a line of chat is one of ours coming back. Second line of
    * defence behind the roster, for the bot that got named and deployed
    * before anybody updated {@link #fleet()}. These fragments are chosen to
    * be things the desks say and people do not type; a subclass that rewrites
    * the speech should give this the same treatment.
    */
   private boolean ourPhrasing(String said) {
      return said.indexOf("one of fable's crew") != -1
         || said.indexOf("- trade me") != -1
         || said.indexOf("in stock at") != -1
         || said.indexOf("back once i've restocked") != -1
         || said.indexOf("if you need any") != -1;
   }

   private boolean isCrew(String name) {
      String[] names = crew();

      for (int i = 0; i < names.length; i++) {
         if (names[i] != null && names[i].equalsIgnoreCase(name)) {
            return true;
         }
      }

      return false;
   }

   /** Item slots in one side of a trade window. */
   protected static final int TRADE_SLOTS = 12;

   /**
    * Which of {@link #goods()} stack in the pack. All of them by default,
    * which is right for certificates and coins and wrong for almost anything
    * else worth selling.
    */
   protected boolean[] goodsStack() {
      boolean[] all = new boolean[goods().length];

      for (int i = 0; i < all.length; i++) {
         all[i] = true;
      }

      return all;
   }

   /**
    * Units that will physically fit in the window.
    *
    * A trade holds twelve item slots a side, and an item that does not stack
    * spends one slot each. Certificates never run into this -- five hundred
    * of them are one slot -- but a super set is three unstackable potions, so
    * four sets fill the window exactly and a fifth simply cannot be put up.
    * Finding that out by watching an offer silently come up short is no way
    * to run a counter, so the arithmetic is done here and the customer is
    * told the number before they pay for it.
    */
   protected int slotCap() {
      int[] per = goodsPer();
      boolean[] stacks = goodsStack();
      int fixed = 0;
      int each = 0;

      for (int i = 0; i < per.length; i++) {
         if (i < stacks.length && stacks[i]) {
            fixed++;
         } else {
            each += Math.max(1, per[i]);
         }
      }

      if (each < 1) {
         return Integer.MAX_VALUE;   // all stacking: the window is no limit
      }

      return Math.max(0, TRADE_SLOTS - fixed) / each;
   }

   protected String announcement() {
      return "@ran@Selling " + goodsPlural() + " - " + price() + "gp each - trade me";
   }

   protected String aside() {
      return "@ran@Hey - I've got " + goodsPlural() + " at " + price() + "gp if you need any";
   }

   protected String outOfStock() {
      return "Sorry m8, I'm out of stock - back once I've restocked";
   }

   protected String inStock() {
      return stock() + " " + goodsPlural() + " in stock at " + price() + "gp each";
   }

   /* ------------------------------------------------------------- the desk -- */

   /**
    * Everything it says goes through here, so the log is a full record of what
    * the desk did: what it heard, what it answered, what it sold. A merchant
    * nobody can audit is a merchant nobody should trust.
    */
   /**
    * What the game will carry in one line.
    *
    * mudclient builds exactly one chat input and gives it a limit of eighty
    * characters, and that same box is both public chat and private message --
    * so eighty is the length of anything this desk can say, not a style
    * preference. Past it the tail is simply gone, which is at its worst in
    * the one message that matters: the number a customer has to put up.
    */
   protected static final int CHAT_MAX = 80;

   /** Cut to what will actually arrive, on a word boundary where there is one. */
   protected String fit(String words) {
      if (words == null || words.length() <= CHAT_MAX) {
         return words;
      }

      String cut = words.substring(0, CHAT_MAX);
      int space = cut.lastIndexOf(' ');

      return space > CHAT_MAX / 2 ? cut.substring(0, space) : cut;
   }

   protected void say(String words) {
      words = fit(words);
      System.out.println("[" + tag() + "] say: " + words);
      Speak(words);
   }

   protected void tell(String words, String who) {
      words = fit(words);
      System.out.println("[" + tag() + "] pm " + who + ": " + words);
      sendPrivateMessage(words, who);
   }

   protected void note(String words) {
      System.out.println("[" + tag() + "] " + words);
   }

   /** Whole units in the pack: the goods it is short of decide the count. */
   protected int stock() {
      int[] ids = goods();
      int[] per = goodsPer();
      int units = Integer.MAX_VALUE;

      for (int i = 0; i < ids.length; i++) {
         int have = getInventoryCount(ids[i]) / Math.max(1, per[i]);

         if (have < units) {
            units = have;
         }
      }

      return units == Integer.MAX_VALUE ? 0 : units;
   }

   protected boolean trading() {
      return isInTradeOffer() || isInTradeConfirm();
   }

   protected int badCount(String name) {
      Integer n = badTrades.get(name);
      return n == null ? 0 : n.intValue();
   }

   private void countBad(String name) {
      int n = badCount(name) + 1;
      badTrades.put(name, Integer.valueOf(n));

      if (n >= BAD_TRADE_LIMIT) {
         note(name + " has had " + n + " bad trades - ignoring them.");
      }
   }

   private void clearTrade() {
      partner = null;
      offered = 0;
      confirmed = false;
      toldShort = -1;
      tradeTime = -1L;
   }

   /** How many coins the other side has actually put up. */
   private int offeredCoins() {
      int[] items = GetOpponentTradeItems();
      int[] amounts = GetOpponentTradeAmounts();
      int total = 0;

      for (int i = 0; i < items.length && i < amounts.length; i++) {
         if (items[i] == COINS) {
            total += amounts[i];
         }
      }

      return total;
   }

   /** True if they have put up anything that is not coins. */
   private boolean offeredJunk() {
      int[] items = GetOpponentTradeItems();

      for (int i = 0; i < items.length; i++) {
         if (items[i] != COINS) {
            return true;
         }
      }

      return false;
   }

   /**
    * One step of a live trade.
    *
    * The rule is simple enough to explain in a sentence, and that is
    * deliberate: put up coins in a multiple of the price, and that many units
    * appear on this side. Anything else -- an odd amount, an item instead of
    * coins, more than the stock -- is left alone until the timeout declines
    * it, so nobody's goods can vanish into a trade the script did not
    * understand.
    */
   private int tradeStep() {
      if (tradeTime != -1L && System.currentTimeMillis() > tradeTime + TRADE_MS) {
         if (partner != null) {
            tell("Trade timed out - " + price() + "gp each, coins only", partner);
            countBad(partner);
         }

         declineTrade();
         clearTrade();
         return random(1000, 1500);
      }

      /* A crewmate taking over the goods gets the lot, no coins asked. */
      if (partner != null && isCrew(partner)) {
         return handoverStep();
      }

      if (offeredJunk()) {
         return random(800, 1200);   // wait for them to take it back out
      }

      int coins = offeredCoins();
      int want = coins / price();

      if (want < 1 || coins % price() != 0) {
         return random(800, 1200);
      }

      int asked = want;
      String reason = null;

      if (want > stock()) {
         want = stock();
         reason = "only " + want + " left";
      }

      if (want > slotCap()) {
         want = slotCap();
         reason = "trade holds " + TRADE_SLOTS + " items, so " + want;
      }

      if (want < 1) {
         if (partner != null && toldShort != 0) {
            toldShort = 0;
            tell(outOfStock(), partner);
         }

         return random(800, 1200);
      }

      /*
       * They have paid for more than can be handed over. Put up everything
       * that can be, and say what the coins need to come down to -- the
       * confirm check below wants the two sides to match exactly, so a
       * customer left to guess would sit there watching a trade that never
       * completes and no explanation of why.
       *
       * Keep it short. A line this side sends is one line of somebody else's
       * chat window, and the game cuts a long one off mid-word, so the number
       * they need has to arrive before the sentence runs out of room.
       */
      if (reason != null && partner != null && toldShort != want) {
         toldShort = want;
         tell("Asked " + asked + ", " + reason + " - put up "
            + (want * price()) + "gp", partner);
      }

      int[] ids = goods();
      int[] per = goodsPer();

      if (isInTradeOffer()) {
         if (getLocalTradeItemCount() != ids.length || offered != want) {
            int[] amounts = new int[ids.length];

            for (int i = 0; i < ids.length; i++) {
               amounts[i] = per[i] * want;
            }

            offered = want;

            /* OfferItems replaces the whole offer rather than adding to it,
               so this both sets the count the first time and corrects it
               when they change their mind. */
            OfferItems(ids, amounts);
            return random(900, 1300);
         }

         acceptTrade();
         return random(900, 1300);
      }

      if (isInTradeConfirm()) {
         if (confirmed) {
            return random(900, 1300);   // waiting on their click
         }

         /* Last look before the goods move: their coins must still cover what
            is on this side. The confirm screen is the only place this can be
            checked against what both sides finally agreed. */
         if (offered > 0 && hasOtherTraded(COINS, offered * price()) && !offeredJunk()) {
            confirmTrade();
            confirmed = true;
            note("Sold " + offered + " to " + partner + " for " + (offered * price()) + "gp.");

            /* partner and offered are deliberately kept until the window
               closes: the confirm above is only this side's click, and
               clearing them here would make the next pass decline the trade
               it just agreed to. */
            tradeTime = -1L;
            return random(1200, 1800);
         }

         declineTrade();

         if (partner != null) {
            countBad(partner);
         }

         clearTrade();
      }

      return random(900, 1300);
   }

   /**
    * Handing the whole stock to a crewmate. Same window, no price: the units
    * go up, both sides accept, and the log says who took what.
    */
   private int handoverStep() {
      int units = stock();

      if (units < 1) {
         tell("Nothing left to hand over", partner);
         declineTrade();
         clearTrade();
         return random(1000, 1500);
      }

      int[] ids = goods();
      int[] per = goodsPer();

      if (isInTradeOffer()) {
         if (getLocalTradeItemCount() != ids.length || offered != units) {
            int[] amounts = new int[ids.length];

            for (int i = 0; i < ids.length; i++) {
               amounts[i] = per[i] * units;
            }

            offered = units;
            OfferItems(ids, amounts);
            return random(900, 1300);
         }

         acceptTrade();
         return random(900, 1300);
      }

      if (isInTradeConfirm()) {
         if (confirmed) {
            return random(900, 1300);
         }

         confirmTrade();
         confirmed = true;
         note("Handed " + offered + " " + goodsPlural() + " to crewmate " + partner + ".");
         tradeTime = -1L;
         return random(1200, 1800);
      }

      return random(900, 1300);
   }

   @Override
   public final int main() {
      if (getInventoryCount() == 0) {
         return 1000;   // inventory packet has not arrived yet
      }

      if (trading()) {
         return tradeStep();
      }

      if (partner != null) {
         clearTrade();   // window closed under us
      }

      long now = System.currentTimeMillis();

      /* The desk teaches itself where its counter is rather than being told:
         wherever it last had a bank window open is the spot customers can
         find it at. That keeps the sales patter where somebody might act on
         it -- shouting the price at an empty rock face is what makes a bot
         look like a bot -- and costs no configuration when a bot moves bank. */
      if (isBanking()) {
         bankX = getX();
         bankY = getY();
      }

      if (stock() > 0 && atCounter() && now > announceTime + announceGap) {
         announceTime = now;
         announceGap = random((int) ANNOUNCE_MIN, (int) ANNOUNCE_MAX);
         say(announcement());
         return random(1500, 2500);
      }

      return work();
   }

   /**
    * Whether it is standing at the bank it last used. False until it has
    * banked once, which is correct: a desk with nothing banked yet has
    * nothing to announce and nowhere to send anyone.
    */
   protected boolean atCounter() {
      return bankX >= 0
         && Math.abs(getX() - bankX) <= BANK_RADIUS
         && Math.abs(getY() - bankY) <= BANK_RADIUS;
   }

   /* -------------------------------------------------------- what it hears -- */

   @Override
   public void onTradeRequest(String player) {
      note("trade request from " + player);

      if (player == null || (badCount(player) >= BAD_TRADE_LIMIT && !isCrew(player))) {
         return;
      }

      if (trading()) {
         tell("One at a time - I'll be right with you", player);
         return;
      }

      if (stock() < 1 && !isCrew(player)) {
         tell(outOfStock(), player);
         return;
      }

      int[] found = getPlayerByName(player);

      if (found == null || found[0] == -1) {
         tell("I can't see you from here - come to me and trade", player);
         return;
      }

      partner = player;
      offered = 0;
      confirmed = false;
      tradeTime = System.currentTimeMillis();
      sendTradeRequest(getPlayerPID(found[0]));
   }

   /**
    * A private message gets the three things a customer actually needs: how
    * much there is, what it costs, and where to stand. No friend entry is
    * involved either way -- the server forwards a private message by name
    * without consulting anyone's list, so there is no list to fill up.
    *
    * The where-to-stand line is only sent when there is something to stand
    * for. Sending it every time reads as a contradiction -- "I'm out of
    * stock" followed immediately by "come and trade me" -- and it is worse
    * than rude: the desk wanders while it gathers, so the coordinates are a
    * few minutes stale at best, and a customer who walks to them for nothing
    * has been sent on an errand by a script that already knew better.
    */
   @Override
   public void onPrivateMessage(String message, String player, boolean moderator, boolean administrator) {
      if (player == null) {
         return;
      }

      boolean empty = stock() < 1;

      tell(empty ? outOfStock() : inStock(), player);

      /* Crew are told where it is regardless -- finding the desk is half of
         why anyone on the crew messages it. Customers are not. */
      if (!empty || isCrew(player)) {
         tell("I'm at (" + getX() + "," + getY() + ") - trade me when you get here", player);
      }
   }

   /**
    * Public chat. It answers a question about the goods, and otherwise chips
    * in occasionally so a passer-by knows there is a shop standing there.
    *
    * NPC speech arrives through this same hook with only a name to tell them
    * apart, so a desk near a bank will happily answer the banker's "Certainly
    * sir" with its sales patter unless it checks.
    */
   @Override
   public void onChatMessage(String message, String player, boolean moderator, boolean administrator) {
      if (message == null || player == null) {
         return;
      }

      if (player.equalsIgnoreCase(getPlayerName(0))) {
         return;   // its own voice coming back
      }

      int[] speaker = getPlayerByName(player);

      if (speaker == null || speaker[0] == -1) {
         return;   // an NPC, not a customer
      }

      String said = message.toLowerCase(Locale.ENGLISH);

      /* Never answer another desk, and never answer our own patter echoing
         back off one. Everything below this line assumes a person said it. */
      if (isFleet(player) || ourPhrasing(said)) {
         return;
      }

      note("heard " + player + ": " + message);

      long now = System.currentTimeMillis();
      boolean admits = said.indexOf("bot") != -1;

      if (admits || interested(said)) {
         if (now < replyTime + REPLY_MS) {
            return;   // already spoke; being asked twice is not a reason to
         }

         replyTime = now;
         say(admits ? admission() : (stock() > 0 ? "@ran@" + inStock() : outOfStock()));
         return;
      }

      /* Unprompted, and only where it can be acted on. Sharing the reply
         clock matters: a desk that has just answered somebody should not
         immediately follow it with an unrelated sales line. */
      if (stock() > 0 && atCounter() && now > asideTime + ASIDE_MS) {
         asideTime = now;
         replyTime = now;
         say(aside());
      }
   }

   /**
    * What it says when asked what it is. It tells the truth -- the point of a
    * crew like this is that the world has somebody in it, not that anyone is
    * fooled -- which is precisely why the answer contains the word that
    * provokes it, and why the roster and reply clock above are not optional.
    */
   protected String admission() {
      return "Aye, I'm a bot - one of Fable's crew";
   }

   /**
    * Whether a line of chat was about the goods -- the one place a bot decides
    * what it is listening for. The default reads as a shopkeeper with one ear
    * open; a subclass selling something else adds its own words, and a quiet
    * bot that should only ever answer its own trade can narrow it to nothing.
    *
    * Keep the words specific. Broad triggers are how a desk ends up answering
    * every conversation in a busy bank, which is indistinguishable from spam
    * to everyone standing in it.
    */
   protected boolean interested(String said) {
      return said.indexOf("price") != -1
         || said.indexOf("how much") != -1
         || said.indexOf("sell") != -1
         || said.indexOf("buy") != -1
         || said.indexOf("selling") != -1;
   }
}
