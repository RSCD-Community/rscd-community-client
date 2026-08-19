package org.rscdaemon.client;

import java.awt.Graphics2D;

/*
 * The in-game box a script uses to ask the player something.
 *
 * WHY THIS EXISTS. GetInput and GetOption were java.swing.JOptionPane calls
 * with a null parent -- a desktop dialog over the game canvas, which can open
 * behind the window or on another monitor, and which cannot appear in an F12
 * screenshot or a recorded movie because both are composed from the client's
 * own pixel buffer.
 *
 * That was never how it worked. SkullOrca used Swing because it had no choice:
 * it loaded Jagex's signed jar reflectively into a JPanel and could not draw a
 * single pixel inside the game view -- the same constraint recorded at the top
 * of ScriptPanel. This client is ours, so the constraint is gone, and the
 * original behaviour can come back.
 *
 * THE ORIGINAL, recovered from STS.jar (Methods.class there is not obfuscated
 * and contains no JOptionPane at all). STS's GetInput was:
 *
 *     GetInput(String s) { Load(); return rs.t(s); }
 *
 * and mudclient.t(String), read out of the bytecode, was:
 *
 *     t(prompt):
 *         c.M = ""; c.N = "";        // clear the input buffers
 *         Ub = prompt;               // publish the pending prompt
 *         while (Vb == "") {         // block until an answer appears
 *             Thread.sleep(100);
 *             if (b.O == 0) break;   // give up if no longer running
 *         }
 *         answer = Vb; Vb = ""; return answer;
 *
 * with the render loop calling u(Ub) every frame while Ub was set, and u()
 * drawing a 500x70 box at (6,145) with the prompt centred at (256,165).
 *
 * Every part of that maps onto something already here, which is why this is a
 * restoration rather than an invention:
 *
 *     Ub, Vb      the pending prompt and the answer, below
 *     c.M, c.N    GameWindow.inputText and enteredText -- the client already
 *                 accumulates typed characters into inputText and copies it to
 *                 enteredText on Enter, so the answer arrives without this
 *                 class touching key handling at all
 *     b.O         the script still running
 *     u()         draw(), through Skin, so it matches the F2 menu and the
 *                 Worlds screen rather than being a third visual language
 *
 * THREADS. ask() is called on the script's thread and blocks there; draw() runs
 * on the client thread. The polling loop is STS's, kept deliberately: the
 * answer is delivered by AWT writing enteredText, not by any code of ours, so
 * there is nothing to signal a latch from. 50ms is imperceptible to someone
 * typing and costs nothing while no prompt is open.
 *
 * A script stopped while its prompt is open must not leave that thread parked
 * forever, so cancel() releases it -- otherwise /stop would hang on it.
 */
final class ScriptPrompt {

   /**
    * How an asynchronous prompt delivers its answer.
    *
    * The blocking ask() below is right for scripts, which run on their own
    * thread, and wrong for anything the client thread asks -- Withdraw X,
    * Deposit X, Buy X, Sell X are all handled inside processGame(), and a
    * thread that blocks there is the thread that would have drawn the box and
    * read the keystroke. Those four used a Swing JOptionPane precisely because
    * it spins its own event pump and so survives being called from there.
    *
    * So the client-thread path does not block at all: it publishes the prompt
    * and hands over a continuation, which poll() runs on a later frame once
    * the answer is in. Everything else -- drawing, Esc to cancel, swallowing
    * clicks while it is up -- is the same machinery, because all of it keys
    * off isOpen().
    */
   interface Answer {
      void got(String text);
   }

   /* STS drew 500x70 at (6,145) on a 512-wide view. Kept proportional instead
      of hardcoded so it survives the window being a different size. */
   private static final int MARGIN = 6;
   private static final int HEIGHT = 70;
   private static final int TOP = 145;

   private static final int POLL_MS = 50;

   private final mudclient rs;

   /* Volatile: written by the script thread, read by the client thread. */
   private volatile String prompt;
   private volatile String[] options;
   private volatile boolean cancelled;
   /* A message to acknowledge rather than a question to answer: same box, but
      Enter on its own is enough and there is no text field. */
   private volatile boolean acknowledgeOnly;

   /* Set by askAsync and cleared by poll, both on the client thread; also
      cleared by open() on the script thread, hence volatile. It is the flag
      that tells the two kinds of prompt apart: one with a callback is nobody's
      blocked thread, and closing it is poll's job rather than open's finally. */
   private volatile Answer callback;

   ScriptPrompt(mudclient rs) {
      this.rs = rs;
   }

   boolean isOpen() {
      return this.prompt != null;
   }

   /**
    * Asks for a line of text. Blocks the calling (script) thread until the
    * player presses Enter, or returns "" if the script is stopped first --
    * which is what the Swing version returned when the dialog was dismissed,
    * so scripts see no change in contract.
    */
   String ask(String message) {
      return open(message, null);
   }

   /**
    * Asks the player to pick one of several options. Returns the index, or -1.
    *
    * Presented as a numbered list the player answers by typing the number,
    * because that reuses the text path exactly -- no second input mechanism,
    * and it works with the client's own key handling untouched.
    */
   int choose(String header, String[] choices) {
      if (choices == null || choices.length == 0) {
         return -1;
      }

      String reply = open(header, choices);
      try {
         int n = Integer.parseInt(reply.trim());
         return n >= 1 && n <= choices.length ? n - 1 : -1;
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   /**
    * Shows a message and waits for the player to acknowledge it.
    *
    * STS's ShowMessage was mudclient.s(String), which is the same shape as the
    * input box -- set a flag and the text, then block until it is dismissed:
    *
    *     s(message):
    *         Kh = true; lj = message;
    *         while (Kh) { sleep(100); if (b.O == 0) break; }
    *
    * so it blocked the script as well. Enter dismisses it, which is the key the
    * client already reports through enteredText, so this needs no more
    * machinery than the question box does.
    */
   void show(String message) {
      open(message, null, true);
   }

   private String open(String message, String[] choices) {
      return open(message, choices, false);
   }

   private String open(String message, String[] choices, boolean acknowledgeOnly) {
      if (this.rs == null) {
         return "";
      }

      /* An automatic restart answers its own questions. The recorded setup
         answers feed the prompts in the order they were given, and a
         ShowMessage box does not wait for an Enter that nobody is there to
         press. A question beyond the end of the recording still asks for
         real -- there is nothing to answer it with. */
      ScriptRunner auto = this.rs.scripts();
      if (auto != null && auto.unattended()) {
         if (acknowledgeOnly) {
            return "";
         }
         String canned = auto.nextCannedAnswer();
         if (canned != null) {
            return canned;
         }
      }

      /* STS cleared both buffers before publishing the prompt; without that a
         character typed a moment earlier becomes the first character of the
         answer. */
      /* A script asking while a bank or shop prompt is up takes the box over,
         and the abandoned continuation must not then be handed the script's
         answer. Dropping it is what Esc would have done anyway. */
      this.callback = null;

      this.rs.inputText = "";
      this.rs.enteredText = "";
      this.cancelled = false;
      this.options = choices;
      this.acknowledgeOnly = acknowledgeOnly;
      this.prompt = message == null ? "" : message;

      try {
         while (!this.cancelled) {
            if (this.rs.enteredText.length() > 0) {
               String answer = this.rs.enteredText;
               /* A question really asked and really answered: remember the
                  answer, so an automatic restart can give it back. */
               if (!acknowledgeOnly && auto != null) {
                  auto.recordAnswer(answer);
               }
               return answer;
            }

            /* Enter on an empty line is a valid dismissal for a message box,
               and the client signals that by clearing inputText into
               enteredText -- both empty. Watch the key directly instead. */
            if (this.acknowledgeOnly && (this.rs.keyDown == 10 || this.rs.keyDown == 13)) {
               return "";
            }

            /* The script being stopped is the other way out, and the reason
               this is not an unbounded wait. */
            ScriptRunner runner = this.rs.scripts();
            if (runner != null && !runner.isRunning()) {
               return "";
            }

            try {
               Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               return "";
            }
         }

         return "";
      } finally {
         this.prompt = null;
         this.options = null;
         this.acknowledgeOnly = false;
         this.rs.inputText = "";
         this.rs.enteredText = "";
      }
   }

   /** Releases a script parked in ask(), so stopping one never hangs. */
   void cancel() {
      this.cancelled = true;
   }

   /**
    * Asks for a line of text without blocking. Returns at once; cb.got() runs
    * on the client thread, on a later frame, when Enter is pressed.
    *
    * Returns false, and does not ask, if a prompt is already up -- the box is a
    * single slot and there is one text buffer behind it, so a second question
    * would overwrite the first and steal its answer. The caller treats that the
    * same as a cancel: nothing happens.
    */
   boolean askAsync(String message, Answer cb) {
      if (this.rs == null || cb == null || isOpen()) {
         return false;
      }

      this.rs.inputText = "";
      this.rs.enteredText = "";
      this.cancelled = false;
      this.options = null;
      this.acknowledgeOnly = false;
      this.callback = cb;
      this.prompt = message == null ? "" : message;
      return true;
   }

   /**
    * Called once a frame from the client thread. Delivers the answer to an
    * askAsync prompt and closes it.
    *
    * Esc, which sets cancelled, closes the box with no callback at all. That
    * matches what dismissing the old JOptionPane did -- showInputDialog gave
    * back null, SalvageInput turned null into 0, and every one of the four call
    * sites returned on a zero.
    */
   void poll() {
      Answer cb = this.callback;
      if (cb == null) {
         return;
      }

      boolean answered = this.rs.enteredText.length() > 0;

      /* Enter on an empty line has to close the box too. The client signals a
         line by copying inputText into enteredText, so an empty one is
         indistinguishable from nothing having happened; the key itself is the
         only evidence. Reading it is safe because keyUp clears keyDown to 0, so
         it can only be Enter if Enter is being held right now -- and the click
         that opened this prompt held no key at all. */
      boolean dismissed = this.cancelled
         || (!answered && (this.rs.keyDown == 10 || this.rs.keyDown == 13));

      if (!answered && !dismissed) {
         return;
      }

      String reply = answered ? this.rs.enteredText : "";

      /* Closed before the callback runs, not after: the continuation sends a
         packet and can open a prompt of its own, and it would be refused by the
         guard in askAsync if this one were still standing. */
      this.callback = null;
      this.prompt = null;
      this.options = null;
      this.acknowledgeOnly = false;
      this.rs.inputText = "";
      this.rs.enteredText = "";

      if (answered) {
         cb.got(reply);
      }
   }

   /*
    * Drawn from the client thread, last, over everything -- including the F2
    * menu, because a script that is waiting for an answer is the most
    * immediately important thing on screen.
    */
   void draw(GameImageMiddleMan gg, int width, int height) {
      String message = this.prompt;
      if (message == null) {
         return;
      }

      String[] choices = this.options;
      int lines = choices == null ? 0 : choices.length;
      int boxHeight = HEIGHT + lines * 14;

      /* The box keeps its vanilla 512-wide shape and is centred in a bigger
         window with the same offsets the in-game dialogs use, so it sits over
         the bank/shop/trade window it belongs to instead of stretching across
         the top. Both are 0 at the minimum size. */
      int ox = (width - 512) / 2;
      int oy = (height - 346) / 2;
      int boxWidth = 512 - MARGIN * 2;

      Graphics2D g = Skin.open(gg, width, height);

      try {
         Skin.panel(g, MARGIN + ox, TOP + oy, boxWidth, boxHeight);
         Skin.textCentre(g, Skin.fit(g, message, Skin.FONT_HEAD, boxWidth - 24),
            ox + 256, TOP + oy + 22, Skin.FONT_HEAD, Skin.GOLD_HI);

         int y = TOP + oy + 40;
         for (int i = 0; i < lines; i++) {
            Skin.text(g, (i + 1) + ")  " + choices[i], MARGIN + ox + 18, y, Skin.FONT_BODY, Skin.TEXT);
            y += 14;
         }

         /* What they have typed so far, with a caret, in a sunken field -- the
            same treatment the Worlds screen gives its text boxes. */
         int fieldY = TOP + oy + boxHeight - 30;
         if (!this.acknowledgeOnly) {
            Skin.well(g, MARGIN + ox + 14, fieldY, boxWidth - 28, 20);
         }

         String typed = this.acknowledgeOnly || this.rs == null ? "" : this.rs.inputText;
         Skin.text(g, typed, MARGIN + ox + 21, fieldY + 14, Skin.FONT_BODY, Skin.TEXT);

         int caretX = MARGIN + ox + 21 + Skin.width(g, typed, Skin.FONT_BODY) + 1;
         if (!this.acknowledgeOnly && System.currentTimeMillis() % 1000L < 600L) {
            g.setColor(Skin.colour(Skin.GOLD_HI, 220));
            g.drawLine(caretX, fieldY + 4, caretX, fieldY + 16);
         }

         /* Esc is advertised because it is the only way out: while a prompt is
            up the client swallows clicks, so the menu's Stop button cannot be
            reached. */
         Skin.textRight(g, (this.acknowledgeOnly ? "Enter to continue" : "Enter to answer")
            + "   ·   Esc to cancel",
            MARGIN + ox + boxWidth - 16, fieldY + 14, Skin.FONT_SMALL, Skin.TEXT_DIM);
      } finally {
         g.dispose();
      }
   }
}
