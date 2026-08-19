package org.rscdaemon.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map.Entry;
import javax.imageio.ImageIO;
import org.rscdaemon.client.entityhandling.EntityHandler;
import org.rscdaemon.client.entityhandling.defs.ItemDef;
import org.rscdaemon.client.entityhandling.defs.NPCDef;
import org.rscdaemon.client.model.Sprite;
import org.rscdaemon.client.recorder.Recorder;
import org.rscdaemon.client.util.Assets;
import org.rscdaemon.client.util.Config;
import org.rscdaemon.client.util.DataConversions;
import org.rscdaemon.client.util.MacJoinLinks;
import org.rscdaemon.client.util.SingleInstance;
import org.rscdaemon.client.util.WorldList;

public final class mudclient extends GameWindowMiddleMan implements SingleInstance.Joiner {
   public static final int SPRITE_MEDIA_START = 2000;
   public static final int SPRITE_UTIL_START = 2100;
   public static final int SPRITE_ITEM_START = 2150;
   public static final int SPRITE_LOGO_START = 3150;
   public static final int SPRITE_PROJECTILE_START = 3160;

   /*
    * Login screen backdrop.
    *
    * The backdrop was never shipped as artwork -- vanilla rendered it. Once,
    * at startup, it loaded the Lumbridge region and photographed it from three
    * fixed camera positions, stashing each 512x200 capture in a sprite slot;
    * drawLoginScreen() then cross-fades between the three on a 3072-tick
    * cycle. That is the slow pan players remember behind the login boxes.
    *
    * Every number below is lifted from the original client
    * (rsclassic-1091943135.jar, decompiled to ../../rsclassic-src). The fork
    * had thrown the renderer away and blitted a static 512x140 RSCDaemon
    * banner from SPRITE_LOGO_START instead -- which is the very slot vanilla
    * captured its first frame into, so the banner is simply overwritten now.
    *
    * The third capture deliberately lands on LOGIN_LOGO_SPRITE, overwriting
    * the stone logo. That is not a clash: by then the logo has already been
    * composited into the framebuffer at (15, 15), which is why all three
    * frames carry it. LOGIN_LOGO_X only matters for the fallback below.
    */
   private static final int LOGIN_LOGO_SPRITE = 2010;
   private static final int LOGIN_LOGO_X = 14;
   private static final int LOGIN_LOGO_Y = 10;
   private static final int LOGIN_VIEW_WIDTH = 512;
   private static final int LOGIN_VIEW_HEIGHT = 200;
   private static final int LOGIN_VIEW_REGION_X = 2423;
   private static final int LOGIN_VIEW_REGION_Y = 2423;
   private static final int LOGIN_VIEW_CYCLE = 3072;
   /** Camera x, camera z, camera height, yaw -- one row per capture. */
   private static final int[][] LOGIN_VIEWS = new int[][]{{9728, 6400, 2200, 888}, {9216, 9216, 2200, 888}, {11136, 10368, 1000, 376}};
   private static final int[] LOGIN_VIEW_SPRITES = new int[]{3150, 3151, LOGIN_LOGO_SPRITE};
   private boolean loginBackdropReady;
   public static final int SPRITE_TEXTURE_START = 3220;
   private long startTime = 0L;
   private long serverStartTime = 0L;
   private String lastMessage = "";
   int fatigue;
   private String serverLocation = "US";
   private int prayerMenuIndex = 0;
   private int magicMenuIndex = 0;
   /* Package-private: the F2 settings page owns these three now. */
   boolean showRoof = true;
   boolean autoScreenshot = true;
   private long expGained = 0L;
   private boolean hasWorldInfo = false;
   /* Read by ScriptPanel to label its Record button. */
   boolean recording = false;
   /*
    * Frames waiting to be encoded. Bounded, and handed between the render
    * thread and the encoder thread, so it has to be a real concurrent queue --
    * the LinkedList this used to be was touched from both.
    *
    * 64 frames is about thirteen seconds at the default 5 fps, and roughly
    * 45 MB of BufferedImage if the encoder ever stalls completely. Past that
    * captureFrame() drops rather than grows.
    */
   private final java.util.concurrent.BlockingQueue<BufferedImage> frames =
      new java.util.concurrent.ArrayBlockingQueue<BufferedImage>(64);
   private Recorder recorder;
   private int captureTicks;
   private int framesDropped;
   private long lastFrame = 0L;
   public static String[] dB = new String[]{""};
   /*
    * The asset check now runs on every launch, desktop included, so an
    * unreachable host has to fail fast rather than sit on the OS default
    * connect timeout with a loading bar frozen at 1%.
    */
   private static final int CACHE_CONNECT_TIMEOUT = 8000;
   private static final int CACHE_READ_TIMEOUT = 30000;
   public String[] gamefiles = new String[]{
      "Loading.xml.data",
      "Landscape.xml.data",
      "Sprites.xml.data",
      "Animations.xml.data",
      "Doors.xml.data",
      "Elevation.xml.data",
      "ItemDef.xml.data",
      "NPCs.xml.data",
      "Objects.xml.data",
      "Prayers.xml.data",
      "SpellDef.xml.data",
      "Textures.xml.data",
      "Tiles.xml.data",
      "models36.jag",
      "sounds1.mem"
   };
   boolean combatWindow;
   private int lastLoggedInDays;
   private int subscriptionLeftDays;
   private int duelMyItemCount;
   private int[] duelMyItems;
   private int[] duelMyItemsCount;
   /*
    * The Offer/Stake quantity popup -- right-click an item in "Your
    * Inventory" on the trade or duel screen for "Offer/Stake 1/5/10/All/X",
    * restoring the menu the final official (2015) client added there. Left
    * click still adds one at a time (and repeats while held) exactly as
    * before; this is purely an added path onto the same offer arrays, not a
    * replacement. One set of fields serves both screens since a player can
    * only ever have one of them open at once -- offerMenuIsDuel says which
    * arrays/packet the click applies to.
    */
   private boolean showOfferMenu;
   private int offerMenuBoxX;
   private int offerMenuBoxY;
   private int offerMenuWidth;
   private int offerMenuHeight;
   private int offerMenuItem;
   private int offerMenuInvIndex;
   private boolean offerMenuIsDuel;
   private boolean configAutoCameraAngle;
   String[] questionMenuAnswer;
   private int spriteRotationMatchRun;
   private int handlePacketErrorCount;
   private Mob[] lastNpcArray;
   private int loginButtonNewUser;
   private int loginButtonExistingUser;
   String currentUser;
   String currentPass;
   int currentWorld;
   /*
    * The world the login screen uses, now that it has no field to type one in.
    * Only 1 and 2 exist -- GameWindowMiddleMan.login() rejects anything else --
    * and 2 has never been up, so there was nothing to choose between.
    */
   private static final int LOGIN_WORLD = 1;
   /*
    * AutoLogin's retry, in ticks -- updateGame() runs at 50 a second, so 100 is
    * two seconds for the first attempt and the wait grows by that much again
    * each time it fails, up to a minute. A minute is the number that matters:
    * it is how long the server holds "that username is already logged in".
    */
   private static final int AUTO_LOGIN_WAIT = 100;
   private static final int AUTO_LOGIN_WAIT_MAX = 3000;
   private int autoLoginTicks = AUTO_LOGIN_WAIT;
   private int autoLoginTries;
   int lastWalkTimeout;
   private String[] menuText1;
   private boolean duelOpponentAccepted;
   private boolean duelMyAccepted;
   private int tradeConfirmItemCount;
   private int[] tradeConfirmItems;
   private int[] tradeConfirmItemsCount;
   private int tradeConfirmOtherItemCount;
   private int[] tradeConfirmOtherItems;
   private int[] tradeConfirmOtherItemsCount;
   private String serverMessage;
   private String duelOpponentName;
   private int mouseOverBankPageText;
   int playerCount;
   private int lastPlayerCount;
   private int fightCount;
   int inventoryCount;
   int[] inventoryItems;
   int[] inventoryItemsCount;
   int[] wearing;
   private int mobMessageCount;
   String[] mobMessages;
   boolean showBank;
   private Model[] doorModel;
   private int[] mobMessagesX;
   private int[] mobMessagesY;
   private int[] mobMessagesWidth;
   private int[] mobMessagesHeight;
   Mob[] npcArray;
   int[] equipmentStatus;
   private final int[] characterTopBottomColours = new int[]{
      16711680, 16744448, 16769024, 10543104, 57344, 32768, 41088, 45311, 33023, 12528, 14680288, 3158064, 6307840, 8409088, 16777215
   };
   private int loginScreenNumber;
   private int bubbleCount;
   boolean[] prayerOn;
   boolean tradeOtherAccepted;
   boolean tradeWeAccepted;
   private Mob[] mobArray;
   private int[] npcCombatModelArray1 = new int[]{0, 1, 2, 1, 0, 0, 0, 0};
   private int[] bubbleScale;
   private int[] bubbleItemId;
   int npcCount;
   private int lastNpcCount;
   int wildX;
   int wildY;
   private int wildYMultiplier;
   private int lastWildYSubtract;
   private boolean memoryError;
   private int bankItemsMax;
   private int mouseOverMenu;
   private int[] walkModel = new int[]{0, 1, 2, 1};
   boolean showQuestionMenu;
   private int healthBarCount;
   int magicLoc;
   int loggedIn;
   private int cameraAutoAngle;
   private int cameraRotationBaseAddition;
   private Menu spellMenu;
   int spellMenuHandle;
   int menuMagicPrayersSelected;
   private int screenRotationX;
   private int screenRotationXStep;
   private int showAbuseWindow;
   private int duelCantRetreat;
   private int duelUseMagic;
   private int duelUsePrayer;
   private int duelUseWeapons;
   boolean showServerMessageBox;
   private boolean hasReceivedWelcomeBoxDetails;
   private String lastLoggedInAddress;
   private int loginTimer;
   int[] playerStatCurrent;
   int areaX;
   int areaY;
   private int wildYSubtract;
   private int lastModelFireLightningSpellNumber;
   private int lastModelTorchNumber;
   private int lastModelClawSpellNumber;
   int[] sectionXArray;
   int[] sectionYArray;
   int selectedItem;
   String selectedItemName;
   private int menuX;
   private int menuY;
   private int menuWidth;
   private int menuHeight;
   private int menuLength;
   private int duelOpponentItemCount;
   private int[] duelOpponentItems;
   private int[] duelOpponentItemsCount;
   private int[] teleBubbleY;
   private int[] menuID;
   private boolean showCharacterLookScreen;
   private int newBankItemCount;
   private int[] npcCombatModelArray2 = new int[]{0, 0, 0, 0, 0, 1, 2, 1};
   private Mob[] lastPlayerArray;
   private int inputBoxType;
   int combatStyle;
   private Model[] gameDataModels;
   private boolean configMouseButtons;
   private boolean duelNoRetreating;
   private boolean duelNoMagic;
   private boolean duelNoPrayer;
   private boolean duelNoWeapons;
   private int[] teleBubbleType;
   private int duelConfirmOpponentItemCount;
   private int[] duelConfirmOpponentItems;
   private int[] duelConfirmOpponentItemsCount;
   private int[] healthBarX;
   private int[] healthBarY;
   private int[] healthBarValue;
   private int loadedSectionMinX;
   private int loadedSectionMinY;
   private int loadedSectionMaxX;
   private int loadedSectionMaxY;
   private Menu menuLogin;
   private final int[] characterHairColours = new int[]{16760880, 16752704, 8409136, 6307872, 3158064, 16736288, 16728064, 16777215, 65280, 65535};
   private Model[] objectModelArray;
   private Menu menuWelcome;
   private int systemUpdate;
   private int cameraRotation;
   int logoutTimeout;
   private Menu gameMenu;
   int messagesHandleType2;
   int chatHandle;
   int messagesHandleType5;
   int messagesHandleType6;
   int messagesTab;
   boolean showWelcomeBox;
   private int characterHeadType;
   private int characterBodyGender;
   private int character2Colour;
   private int characterHairColour;
   private int characterTopColour;
   private int characterBottomColour;
   private int characterSkinColour;
   private int characterHeadGender;
   private int loginStatusText;
   private int loginUsernameTextBox;
   private int loginPasswordTextBox;
   private int loginOkButton;
   private int loginCancelButton;
   /*
    * The Worlds screen: a third button on the login panel, and the panel it
    * opens. loginScreenNumber 4.
    *
    * The client used to have one server in it, so there was nothing to choose
    * and no screen to choose it on. It now plays on whichever server the
    * player picks, which is the whole point of it being a community client
    * rather than ours -- so this is the screen that decides where you play,
    * and it is the first thing a fresh install shows.
    */
   private int loginWorldsButton;
   private int welcomeLine1Text;
   private int welcomeLine2Text;
   private final WorldList worldList = new WorldList();
   /* Draws and drives itself -- see WorldsPanel. It is not a Menu because it
      is not one of Jagex's screens. */
   private WorldsPanel worldsPanel;
   /*
    * ---- which world's content is loaded ----
    *
    * assetsLoaded is false until the first full boot finishes, which is how
    * the client knows whether the Worlds screen it is showing is the one
    * running before there is a game at all.
    *
    * loadedCacheUrl is where the loaded assets came from. Two servers with
    * different cache_url values are two different games -- different maps,
    * different item tables, different models -- so a join that crosses that
    * line has to go back through the loader instead of reusing what happens
    * to be in memory.
    */
   private boolean assetsLoaded;
   private String loadedCacheUrl = "";
   /* Set by joinWorld() while the boot-time chooser is up; it is the only way
      that loop knows the player has answered. */
   private boolean worldChosen;
   /* Set by joinWorld() when the chosen world's content is not the content
      already loaded. Acted on at the top of the next tick rather than in
      place: joinWorld() runs from the input handler, and rebuilding the
      framebuffer under the frame that is being drawn is not something to do
      halfway through reading a click. */
   private boolean reloadPending;
   /* The row that caused it, held only across that reload so the sign-in
      screen on the far side is the one that world's join would have shown. */
   private WorldList.Row joinedRow;
   /* An rscd:// link handed to us by a second launch that then exited. Set
      from the link listener's own thread and acted on at the top of a tick,
      for the same reason reloadPending is: joining rebuilds menus and can
      throw away the framebuffer, which must not happen under a draw. */
   private volatile String pendingJoinUri;
   private int selectedBankItem;
   private int selectedBankItemType;
   private String[] menuText2;
   int playerInfoTab;
   private Config theConfig;
   private boolean[] objectAlreadyInMenu;
   int[] playerStatBase;
   private int abuseSelectedType;
   private int actionPictureType;
   int actionPictureX;
   int actionPictureY;
   private int[] menuActionType;
   private int[] menuActionVariable;
   private int[] menuActionVariable2;
   int[] shopItems;
   int[] shopItemCount;
   private int[][] npcAnimationArray = new int[][]{
      {11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4},
      {11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4},
      {11, 3, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4},
      {3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
      {3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
      {4, 3, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5},
      {11, 4, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3},
      {11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4, 3}
   };
   int bankItemCount;
   private int characterDesignHeadButton1;
   private int characterDesignHeadButton2;
   private int characterDesignHairColourButton1;
   private int characterDesignHairColourButton2;
   private int characterDesignGenderButton1;
   private int characterDesignGenderButton2;
   private int characterDesignTopColourButton1;
   private int characterDesignTopColourButton2;
   private int characterDesignSkinColourButton1;
   private int characterDesignSkinColourButton2;
   private int characterDesignBottomColourButton1;
   private int characterDesignBottomColourButton2;
   private int characterDesignAcceptButton;
   private int[] bubbleX;
   private int[] bubbleY;
   private int[] newBankItems;
   private int[] newBankItemsCount;
   private int duelConfirmMyItemCount;
   private int[] duelConfirmMyItems;
   private int[] duelConfirmMyItemsCount;
   private int[] mobArrayIndexes;
   private Menu menuNewUser;
   private int[] messagesTimeout;
   private int lastAutoCameraRotatePlayerX;
   private int lastAutoCameraRotatePlayerY;
   int questionMenuCount;
   int[] objectX;
   int[] objectY;
   int[] objectType;
   int[] objectID;
   private int[] menuActionX;
   private int[] menuActionY;
   Mob ourPlayer;
   int sectionX;
   int sectionY;
   int serverIndex;
   private int inventoryMaxSlots;
   private int mouseDownTime;
   private int itemIncrement;
   int groundItemCount;
   private int modelFireLightningSpellNumber;
   private int modelTorchNumber;
   private int modelClawSpellNumber;
   boolean showTradeConfirmWindow;
   boolean tradeConfirmAccepted;
   private int teleBubbleCount;
   EngineHandle engineHandle;
   Mob[] playerArray;
   private boolean serverMessageBoxTop;
   private final String[] equipmentStatusName = new String[]{"Armour", "Weapon Aim", "Weapon Power", "Magic", "Prayer", "Range"};
   private int referId;
   /* Never assigned, so always control 0: status prints on the new-user screen
      overwrite the first text line of its menu rather than a dedicated one. */
   private int newUserStatusText;
   private int newUserOkButton;
   private int mouseButtonClick;
   private int cameraHeight;
   int[] bankItems;
   int[] bankItemsCount;
   boolean notInWilderness;
   int selectedSpell;
   private int screenRotationY;
   private int screenRotationYStep;
   int tradeOtherItemCount;
   int[] tradeOtherItems;
   int[] tradeOtherItemsCount;
   private int[] menuIndexes;
   private boolean zoomCamera;
   private AudioReader audioReader;
   int[] playerStatExperience;
   private boolean cameraAutoAngleDebug;
   private Mob[] npcRecordArray;
   final String[] skillArray = new String[]{
      "Attack",
      "Defense",
      "Strength",
      "Hits",
      "Ranged",
      "Prayer",
      "Magic",
      "Cooking",
      "Woodcut",
      "Fletching",
      "Fishing",
      "Firemaking",
      "Crafting",
      "Smithing",
      "Mining",
      "Herblaw",
      "Agility",
      "Thieving",
      "Runecraft"
   };
   boolean showDuelWindow;
   private int[] teleBubbleTime;
   private GameImageMiddleMan gameGraphics;
   private final String[] skillArrayLong = new String[]{
      "Attack",
      "Defense",
      "Strength",
      "Hits",
      "Ranged",
      "Prayer",
      "Magic",
      "Cooking",
      "Woodcutting",
      "Fletching",
      "Fishing",
      "Firemaking",
      "Crafting",
      "Smithing",
      "Mining",
      "Herblaw",
      "Agility",
      "Thieving",
      "Runecrafting"
   };
   private boolean lastLoadedNull;
   int[] experienceArray;
   private Camera gameCamera;
   boolean showShop;
   private int mouseClickArrayOffset;
   int[] mouseClickXArray;
   int[] mouseClickYArray;
   private boolean showDuelConfirmWindow;
   private boolean duelWeAccept;
   private Graphics aGraphics936;
   int[] doorX;
   int[] doorY;
   private int wildernessType;
   private boolean configSoundEffects;
   private boolean showRightClickMenu;
   private int screenRotationTimer;
   private int projectileFlightDuration;
   private int[] teleBubbleX;
   private Menu characterDesignMenu;
   int shopItemSellPriceModifier;
   int shopItemBuyPriceModifier;
   private int modelUpdatingTimer;
   int doorCount;
   int[] doorDirection;
   int[] doorType;
   private int allMessagesTabFlash;
   private int chatHistoryTabFlash;
   private int questHistoryTabFlash;
   private int privateHistoryTabFlash;
   int[] groundItemX;
   int[] groundItemY;
   int[] groundItemType;
   private int[] groundItemObjectVar;
   private int selectedShopItemIndex;
   private int selectedShopItemType;
   private String[] messagesArray;
   private long tradeConfirmOtherNameLong;
   boolean showTradeWindow;
   int playerAliveTimeout;
   private final int[] characterSkinColours = new int[]{15523536, 13415270, 11766848, 10056486, 9461792};
   private byte[] sounds;
   private boolean[] doorAlreadyInMenu;
   int objectCount;
   int tradeMyItemCount;
   int[] tradeMyItems;
   int[] tradeMyItemsCount;
   /* Package-private: ScriptPanel centres its alert box on these. */
   int windowWidth;
   int windowHeight;

   /*
    * The chat-tab strip sits below the world view, so the drawing buffer is
    * twelve rows taller than windowHeight -- 512x346 against a 512x334 view.
    *
    * That difference is why a full-screen overlay must ask for this rather than
    * windowHeight: the F2 script menu dimmed only the top 334 rows and left the
    * chat tabs at full brightness along the bottom edge, which read as the menu
    * failing to draw rather than as a deliberate cutout.
    */
   static final int CHAT_TABS_HEIGHT = 12;

   /** Full height of the drawing buffer, world view plus the chat tabs. */
   int surfaceHeight() {
      return this.windowHeight + CHAT_TABS_HEIGHT;
   }

   /*
    * Where the vanilla 512x334 layout begins once the window is bigger than
    * it. The pre-login screens and the client's own panels were all designed
    * against that surface; they sit centred rather than pinned to a corner.
    * Both are 0 at the minimum size, so the vanilla look is untouched.
    */
   int loginOffsetX() {
      return (this.windowWidth - 512) / 2;
   }

   int loginOffsetY() {
      return (this.windowHeight - 334) / 2;
   }

   /*
    * The chat-tab strip artwork (2022 on the login screens, 2023 in game) is
    * 512 wide, the only width the game ever had. In a wider window the strip
    * is finished by smearing the artwork's last column across the remainder --
    * the right edge is plain bar, so the colour match is exact and no tab
    * label gets duplicated. The in-game sprite is drawn 4 rows higher than
    * the login one, but those rows are only the raised tab tops (transparent
    * between tabs); the strip body occupies the same CHAT_TABS_HEIGHT rows
    * from windowHeight down on both, so one smear serves every caller.
    */
   private final void extendChatStrip() {
      if (this.windowWidth > 512) {
         int[] pixels = this.gameGraphics.imagePixelArray;
         int stride = this.gameGraphics.menuDefaultWidth;

         for (int row = 0; row < CHAT_TABS_HEIGHT; row++) {
            int base = (this.windowHeight + row) * stride;
            int edge = pixels[base + 511];

            for (int x = 512; x < this.windowWidth; x++) {
               pixels[base + x] = edge;
            }
         }
      }
   }

   /*
    * True while one of the client's own panels covers the game view.
    *
    * They are drawn over everything and take the mouse with it, so the things
    * underneath -- the world, the chat tabs, the message scrollbar -- are not
    * merely hidden, they are not there to be clicked on.
    */
   private boolean panelOwnsScreen() {
      return this.scriptPrompt.isOpen()
         || this.worldMapPanel != null && this.worldMapPanel.isOpen()
         || this.scriptPanel != null && this.scriptPanel.isOpen()
         || this.calculatorPanel != null && this.calculatorPanel.isOpen();
   }

   /*
    * See GameWindow.awaitingTextInput. Everything here already gates on a
    * click of its own -- scriptPrompt opens from Buy/Sell/Withdraw/Deposit X
    * and script ask()/choose(), inputBoxType from the friends panel's
    * add/PM/ignore boxes, showAbuseWindow==1 from Report Abuse's name field
    * -- so a level check is enough; the caller does not need an edge, and an
    * unsolicited focus() call this produces with no recent tap behind it is
    * simply ignored by the browser rather than doing anything wrong.
    *
    * gameMenu (general chat) is deliberately excluded: drawGameMenu() keeps
    * it focused at all times, so testing it here would summon the keyboard
    * on every ordinary click during play. Chat is reached by tapping its own
    * line instead -- see isChatEntryArea below.
    *
    * menuLogin is scoped to loginScreenNumber == 2 (the credentials screen)
    * as well as its own focus, since nothing clears currentFocusHandle back
    * to -1 once a field has been clicked, and the object outlives the
    * screen it belongs to.
    */
   @Override
   public boolean awaitingTextInput() {
      return this.scriptPrompt.isOpen()
         || this.inputBoxType != 0
         || this.showAbuseWindow == 1
         || this.loginScreenNumber == 2 && this.menuLogin != null && this.menuLogin.isEditingText();
   }

   /*
    * Open world -- somewhere a drag turns the camera instead of meaning
    * something to the interface. Everything that owns the screen is out, and so
    * are the two things that are always drawn over the world: the message strip
    * along the bottom, and the tab icons across the top right together with
    * whichever panel they have open under them.
    */
   @Override
   protected boolean isCameraDragArea(int x, int y) {
      if (this.loggedIn != 1
         || this.panelOwnsScreen()
         || this.showBank
         || this.showShop
         || this.showTradeWindow
         || this.showTradeConfirmWindow
         || this.showDuelWindow
         || this.showDuelConfirmWindow
         || this.showOfferMenu
         || this.showQuestionMenu
         || this.showRightClickMenu
         || this.showServerMessageBox
         || this.showWelcomeBox
         || this.showCharacterLookScreen
         || this.showAbuseWindow == 1) {
         return false;
      }

      if (y >= this.windowHeight - 66) {
         return false;
      }

      int panelLeft = this.gameGraphics.menuDefaultWidth - 200;
      return x < panelLeft || y >= 36 && this.mouseOverMenu == 0;
   }

   /*
    * The arrow keys' own effect on the camera, one step at a time, for a touch
    * screen that has no arrow keys.
    *
    * It cannot be done by sending the keys themselves: rotation is driven from
    * keyLeftDown/keyRightDown, which are held-key state read on the game's next
    * tick, and a synthetic press would be released again before that tick came
    * round. So the flags are set here for the auto-angle path -- which reads and
    * clears them itself, one 45 degree step per flag, and picks an angle the
    * player is actually visible from -- and the free path is turned directly,
    * since that one never clears the flag and would spin forever if handed one.
    */
   @Override
   protected void turnCameraStep(int dir) {
      if (this.loggedIn != 1) {
         return;
      }

      if (this.configAutoCameraAngle) {
         if (dir > 0) {
            super.keyLeftDown = true;
         } else {
            super.keyRightDown = true;
         }
      } else {
         /* Half an eighth-turn, so free mode moves at roughly the pace the
            eight fixed angles do rather than crawling by comparison. */
         this.cameraRotation = this.cameraRotation + dir * 16 & 0xFF;
      }
   }

   /* Zoom, in the same 25 unit steps and between the same bounds the up and
      down arrows keep it to. dir > 0 brings the camera closer. */
   @Override
   protected void zoomCameraStep(int dir) {
      if (this.loggedIn != 1) {
         return;
      }

      /* Twice the arrow keys' 25, which puts the whole 275..1525 range within
         about twenty-five steps -- a pinch a hand can actually make. */
      int height = this.cameraHeight - dir * 50;
      this.cameraHeight = Math.max(275, Math.min(1525, height));
   }

   /*
    * The world map is panned by dragging it, so on a touch screen a swipe
    * across it is that pan and nothing else. Without this the one finger did
    * both at once -- panning from the drag and zooming a notch every fifteen
    * pixels from the swipe -- which is the erratic map on a phone.
    *
    * Nothing is lost by it: the map carries its own zoom buttons (see
    * WorldMapPanel.zoomBy), and its legend scrolls by dragging as well as by
    * the wheel. The script panel is deliberately not here -- it scrolls, it
    * does not pan, so a swipe over it means exactly what it looks like.
    */
   @Override
   protected boolean isDragPanning() {
      return this.worldMapPanel != null && this.worldMapPanel.isOpen();
   }

   /*
    * The chat line -- the '*' cursor at the bottom left and whatever message
    * is part-typed after it. Chat is always the focused handle during play, so
    * on a desktop anything typed simply goes there and no click is involved at
    * all; a player with no keyboard has to be able to ask for one, and this is
    * the thing on screen that already means "your message goes here".
    *
    * Only the web client asks (see GameWindow.chatEntryTapped, and
    * rscweb.web.MobileKeyboard for what it does with the answer). Desktop
    * never calls it.
    *
    * drawGameMenu() builds the input at y = windowHeight - 10, height 14, but
    * the tab strip below claims every click past windowHeight - 4, which would
    * leave six pixels to hit -- about four on a phone, once rscd.css has zoomed
    * the page down to fit. So the box reaches further up instead, into the last
    * row of message text, which takes no clicks of its own.
    *
    * The right hand column is left out while a tab other than "All messages"
    * is up, because that is where its scrollbar stands.
    *
    * Every window that takes the screen for itself is excluded, because a tap
    * here is swallowed rather than passed on (see DomEvents) and swallowing one
    * meant for a bank, shop or trade would leave that window unusable along its
    * bottom edge. Chat is reachable again the moment the window is closed, and
    * none of them is a state anyone types a public message from.
    */
   @Override
   protected boolean isChatEntryArea(int x, int y) {
      if (this.loggedIn != 1
         || this.panelOwnsScreen()
         || this.showBank
         || this.showShop
         || this.showTradeWindow
         || this.showTradeConfirmWindow
         || this.showDuelWindow
         || this.showQuestionMenu
         || this.showRightClickMenu
         || this.showServerMessageBox
         || this.showWelcomeBox
         || this.showCharacterLookScreen) {
         return false;
      }
      int right = this.messagesTab > 0 ? this.windowWidth - 18 : this.windowWidth - 5;
      return x >= 5 && x < right && y >= this.windowHeight - 22 && y < this.windowHeight - 4;
   }

   /*
    * Hand keyboard focus back to the game view.
    *
    * For Extension.requestFocusInWindow(): an APOS script that opened a
    * settings window wants the game to have the keys again once it is done,
    * and in APOS it asked the Extension because the Extension was the panel.
    */
   final boolean requestFocusForGame() {
      return this.requestFocusInWindow();
   }
   private int cameraSizeInt;
   private Menu friendsMenu;
   int friendsMenuHandle;
   private Menu questMenu;
   int questMenuHandle;
   /*
    * Jagex's quest list, in the order the client held it. The index is the id:
    * it is what the server's quest-completion packet is keyed on and what a
    * script passes to Methods.QuestDone(), so nothing may be reordered or
    * removed from it. The "(members)" suffixes are theirs.
    */
   static final String[] QUEST_NAMES = new String[]{
      "Black knight's fortress",
      "Cook's assistant",
      "Demon slayer",
      "Doric's quest",
      "The restless ghost",
      "Goblin diplomacy",
      "Ernest the chicken",
      "Imp catcher",
      "Pirate's treasure",
      "Prince Ali rescue",
      "Romeo & Juliet",
      "Sheep shearer",
      "Shield of Arrav",
      "The knight's sword",
      "Vampire slayer",
      "Witch's potion",
      "Dragon slayer",
      "Witch's house (members)",
      "Lost city (members)",
      "Hero's quest (members)",
      "Druidic ritual (members)",
      "Merlin's crystal (members)",
      "Scorpion catcher (members)",
      "Family crest (members)",
      "Tribal totem (members)",
      "Fishing contest (members)",
      "Monk's friend (members)",
      "Temple of Ikov (members)",
      "Clock tower (members)",
      "The Holy Grail (members)",
      "Fight Arena (members)",
      "Tree Gnome Village (members)",
      "The Hazeel Cult (members)",
      "Sheep Herder (members)",
      "Plague City (members)",
      "Sea Slug (members)",
      "Waterfall quest (members)",
      "Biohazard (members)",
      "Jungle potion (members)",
      "Grand tree (members)",
      "Shilo village (members)",
      "Underground pass (members)",
      "Observatory quest (members)",
      "Tourist trap (members)",
      "Watchtower (members)",
      "Dwarf Cannon (members)",
      "Murder Mystery (members)",
      "Digsite (members)",
      "Gertrude's Cat (members)",
      "Legend's Quest (members)",
      /*
       * First entry past Jagex's list: Rune Mysteries, the gate quest for
       * this server's Runecrafting skill. Everything above is fixed
       * history; growth happens only by appending here, mirrored in the
       * server's Quests.NAMES.
       */
      "Rune mysteries"
   };
   /*
    * 0 not started, 1 started, 2 complete. Used to be a plain boolean --
    * RSC's own quest tab only ever painted red or green, never a third state
    * for "in progress" -- but the server now sends the middle value too, so
    * this client can add the distinction on top of the authentic tab without
    * changing what it authentically drew before. See drawGameScreen()'s quest
    * tab, and QuestManager.fillProgress on the server.
    */
   final byte[] questProgress = new byte[QUEST_NAMES.length];
   int questPoints;
   int friendsWindowTab;
   long privateMessageTarget;
   /* Built on first use by scripts(); null until then. */
   private ScriptRunner scriptRunner;
   /*
    * The in-game box a script asks questions through -- GetInput and
    * GetOption. Always present rather than lazy: it is polled from the script
    * thread, and a null check racing a lazy build is not worth the byte.
    */
   private final ScriptPrompt scriptPrompt = new ScriptPrompt(this);

   final ScriptPrompt prompt() {
      return this.scriptPrompt;
   }

   /* The F2 menu. Built on first use for the same reason as the runner. */
   private ScriptPanel scriptPanel;
   /*
    * settings.ini: set once the player has opened the script menu. It is the
    * absence of this that makes the welcome box mention F2, so a fresh install
    * is told and everyone else is not. Deleting the line brings the hint back,
    * which is the behaviour a plain-text settings file should have.
    */
   private static final String HINT_SETTING = "seen_script_menu";
   /* Whether the welcome box still carries the F2 line. Read once at startup
      rather than per frame: drawWelcomeBox runs every tick the box is up. */
   private boolean showScriptMenuHint = !Config.settings().getBoolean(HINT_SETTING, false);
   /* The world map, opened from that menu. Likewise. */
   private WorldMapPanel worldMapPanel;
   /* The calculators, opened from the same menu. Likewise -- and their own
      ScriptRunner, because they compile from calculators/ not scripts/. */
   private CalculatorPanel calculatorPanel;
   private ScriptRunner calculatorRunner;
   /* /debug -- mirrors every /command into the script's Debug() hook. */
   boolean scriptDebug;
   /* What /reload re-runs: the whole "name(arg,arg)" as it was typed. */
   private String lastScriptCommand = "";
   /* Reconnect on a dropped connection, and log back in from the login screen.
      Off by default: with it on, Logout is a round trip -- you log out and the
      client puts you straight back in, which is what a bot wants and not what
      a person clicking Logout means. Turn it on in the F2 menu, or with
      AutoLogin(true) from a script. */
   boolean autoLogin;
   /* Whether the login in flight was fired by the auto-login timer rather
      than a person. The welcome box is for a person arriving; a bot logged
      back in by the machinery has nobody to dismiss it, so packet 248 skips
      the box when this is set. */
   private boolean loginWasAutomatic;
   /* ForceStatMenu(true) -- pins the side panel open on the stats tab so a
      script can read levels without the mouse being anywhere near it. */
   boolean forceStatMenu;
   /* SetGfx(false) -- skips the 3D render. The 2D panels still draw, so the
      window stays readable; it is the scene that costs the CPU. */
   boolean drawGfx = true;
   /* LockMode(n) -- the fight mode a script has pinned, or -1 for unlocked.
      While it is set the combat-style panel stops responding to clicks. */
   int lockedCombatStyle = -1;
   /* STS's status menu: a few lines of text written over the top-left of the
      game view. Off by default, the way STS had it, and toggled from the F2
      settings page. It replaces the Info tab RSCD had bolted onto the stat
      panel -- the panel is Jagex's two tabs again, and this is the extra. */
   boolean statusOverlay;
   /* Built-in autocast: remembers the last spell the player cast on an npc or
      player (lastCastSpell) and the current combat target (autocastTargetType
      /autocastTargetIndex, updated by either a spell cast OR a melee attack
      click -- see rememberCastForAutocast/rememberCombatTarget), and while the
      player is in combat, casts that spell against that target once a second
      -- see processAutocast(). Off by default, toggled from the F2 settings
      page above Status overlay, whose line it extends when both are on.
      Session-only, same as Status overlay: nothing here is worth persisting
      to settings.ini. */
   boolean autocastEnabled;
   int lastCastSpell = -1;
   String lastCastSpellName;
   int autocastTargetType = -1;
   int autocastTargetIndex = -1;
   boolean autocastAcquireSuppressed = false;
   /* Player.castTimer() on the server (rscd-server model/Player.java) enforces
      1200ms between casts, not 1000 -- SPELL_COOLDOWN_MS matches that exactly.
      A client that fires at 1000ms is short by a flat 200ms on every single
      recast, no matter how good the connection is, which is why "you must
      wait" kept showing up even with the round-trip padding below.

      That padding still matters on top of the corrected base: castTimer()
      measures from when the SERVER receives the packet, not when the client
      sends it, so the client also needs to cover its own network trip.
      Every cast -- manual or autocast -- is timed on both ends: castSendTime
      when the packet goes out, and the next time any data comes back in
      (handleIncomingPacket) on a connection with no per-cast ack, which is
      as close to a round-trip sample as this protocol offers. That sample is
      noisy on its own -- RSC batches updates per server tick (~640ms), so
      "whatever arrives next" can land anywhere in the current tick almost
      independently of this specific cast's real transit time, and taking it
      at face value produced estimates too small to cover the actual trip.
      Smoothed into castRoundTripMs as a running MAXIMUM instead of the latest
      sample, so one lucky near-zero reading can't undo margin a slower one
      already established, plus a small fixed floor (MIN_ROUND_TRIP_MS) for
      the very first cast, before any sample exists at all. Biases toward
      slightly late rather than slightly early either way -- a rejected cast
      costs a full cycle; a few extra ms of margin costs nothing. */
   private static final long SPELL_COOLDOWN_MS = 1200L;
   private static final long MIN_ROUND_TRIP_MS = 150L;
   /* The running-maximum needs a ceiling as much as a floor: the "next packet
      in" proxy measures silence, not transit, when a cast goes out just
      before a quiet spell (nothing moving nearby, a lag spike, an interface
      pause). One multi-second sample would otherwise poison the recast
      margin for the rest of the session -- autocast fires its first cast
      and then sits out the whole fight. A real ack can't take longer than
      a full server tick plus generous transit. */
   private static final long MAX_ROUND_TRIP_MS = 1000L;
   long castSendTime;
   long castRoundTripMs = MIN_ROUND_TRIP_MS;
   boolean awaitingCastAck;
   /* STS's show-HP: keep drawing a mob's health bar for as long as the server
      has told us what its health is, rather than only while the combat timer
      is running. Off by default. */
   boolean showHealthBars;
   /* Whether the renderer's fog of war is drawn -- the fade to black past
      fogZDistance and the hard clip past clipFar3d/2. Vanilla's camera was pinned, so
      the black wall sat behind the fade and nobody ever saw either; this
      client can zoom out, which drags the wall straight into frame. Off
      pushes both past the whole loaded section, STS's disable-fog-of-war.
      The client's own rather than a script flag or a Game option, so it
      lives in settings.ini; the F2 settings page flips it. */
   boolean showFog = Config.settings().getBoolean("fog", true);
   /* When to write the window size to settings.ini -- a drag fires dozens of
      resize events, so the save waits until the size has sat still for a
      second. Zero while nothing is waiting. */
   private long resizeSaveDue;
   private long duelOpponentNameLong;
   String tradeOtherPlayerName;
   /*
    * Jagex's anti-macro jitter: every time the map tab comes up, the minimap is
    * drawn at a slightly random rotation (+/-6 of 256) and zoom (192 +/- 11),
    * so pixel positions on it never map to world tiles the same way twice.
    */
   private int minimapRandomRotation;
   private int minimapRandomZoom;

   /*
    * The entry point. There is only one now.
    *
    * There used to be two: this, and an applet started by the browser through
    * GameWindow.init(). The applet path survived long after it could be used --
    * browsers dropped plugin support between 2015 and 2017, Web Start went with
    * JDK 11, and JDK 24 removed the Applet API itself -- and was reachable here
    * only by setting -Drscd.applet=true, which configured the client for a page
    * that no longer exists: assets off the codebase host, server at loopback.
    *
    * All it really selected was Config.initConfig() over
    * Config.initConfig(String) -- asset delivery, not anything applet-bound;
    * createWindow() always opened a real AWT frame, even in the webclient. So
    * removing it removes a way to misconfigure the client and nothing else.
    *
    * Usage: java -jar rscd-client.jar [settings.ini] [rscd://host:port/...]
    *
    * An rscd:// argument is a join link -- the Play Game page on the website
    * renders one per online world, and the installed client is registered as
    * the OS handler for the scheme, which invokes this main with the link as
    * an argument. It wins over the remembered default server for this launch
    * only; nothing is written back to settings.ini.
    */
   public static final void main(String[] args) throws Exception {
      String joinUri = null;
      for (String arg : args) {
         if (arg != null && arg.regionMatches(true, 0, "rscd://", 0, 7)) {
            joinUri = arg;
         }
      }

      // May not exist -- initConfig(String) falls back to its own defaults.
      Config.initConfig(settingsFile(args).getPath());

      boolean joining = joinUri != null && Config.applyJoinUri(joinUri);
      if (joinUri != null && !joining) {
         System.out.println("Ignoring unparseable join link: " + joinUri);
      }

      /*
       * A link that a client already open can take is that client's to take.
       *
       * Only offered for a link we have already parsed, so a malformed one
       * still degrades to a normal launch here rather than being pushed at a
       * running instance that would only refuse it. And only accepted over
       * there if nobody is signed in -- someone mid-game keeps their session
       * and this launch opens its own window, which is the behaviour that
       * existed before any of this.
       */
      if (joining && SingleInstance.handoff(joinUri)) {
         System.out.println("Handed the link to the client already open.");
         return;
      }

      System.out.println("Assets: " + Config.CACHE_URL);
      System.out.println("Server: " + Config.SERVER_IP + ":" + Config.SERVER_PORT);

      // 1219: appearance packet's per-worn-slot field widened to a short
      // (matching PlayerUpdatePacketBuilder on the server) -- a 1218 client
      // misparses every appearance update from a widened server.
      GameWindowMiddleMan.clientVersion = 1219;
      mudclient mc = new mudclient();
      /* Open at whatever size the window was last dragged to, clamped to the
         same range the live resize accepts. The +11 is historical: the frame
         interior has always been one row shorter than the drawing surface. */
      mc.windowWidth = Math.max(512, Math.min(Config.settings().getInt("window_width", 512), 2560));
      mc.windowHeight = Math.max(334, Math.min(Config.settings().getInt("window_height", 334), 1440));
      /* Claim the link listener before the window exists rather than after:
         createWindow starts the game and does not come back, and a link that
         arrives during a slow first asset download should still be answered. */
      SingleInstance.listen(mc);
      /* And the macOS route to the same place. There a clicked link is an Apple
         event delivered to the app already running, not a second process with an
         argument, so SingleInstance never sees it -- see MacJoinLinks. No-ops
         everywhere else. */
      MacJoinLinks.register(mc, joinUri);

      // Nothing is on disk to show yet -- startGame() puts the splash up as
      // soon as it has been downloaded.
      mc.createWindow(mc.windowWidth, mc.windowHeight + 11, "RSCD Community Client", true);
   }

   /*
    * Where settings.ini lives.
    *
    * This used to be the bare name, resolved against the working directory,
    * which is fine from run.sh and wrong every other way a desktop client gets
    * started: double-clicking the jar leaves the working directory at the file
    * manager's idea of home, the file is not found, and the client silently
    * configures itself for an applet -- loopback asset host, nothing listening,
    * dead the instant it tries to fetch. So look next to the jar as well.
    */
   private static File settingsFile(String[] args) {
      // The settings file is the first argument that is not a join link --
      // when the OS scheme handler starts us, the link is all we get.
      String name = "settings.ini";
      for (String arg : args) {
         if (arg != null && !arg.regionMatches(true, 0, "rscd://", 0, 7)) {
            name = arg;
            break;
         }
      }
      File named = new File(name);
      if (named.isFile() || named.isAbsolute()) {
         return named;
      }

      File beside = new File(jarDirectory(), named.getName());
      return beside.isFile() ? beside : named;
   }

   /* Package-private rather than private: GameWindow resolves the baked fonts
      against it, and every other install-relative path in the client is
      already resolved through here. */
   static File jarDirectory() {
      try {
         File self = new File(mudclient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
         // A directory when running from build output rather than a jar.
         return self.isFile() ? self.getParentFile() : self;
      } catch (Exception var1) {
         return new File(".");
      }
   }

   /*
    * The script engine, built the first time anything asks for it. A player who
    * never types /start pays nothing for it -- no threads, no directory, no
    * compiler lookup.
    */
   final ScriptRunner scripts() {
      if (this.scriptRunner == null) {
         this.scriptRunner = new ScriptRunner(this, scriptDirectory());
      }

      return this.scriptRunner;
   }

   /*
    * The script menu. Costs nothing until F2 is pressed for the first time --
    * a player who never opens it never allocates it.
    */
   final ScriptPanel scriptPanel() {
      if (this.scriptPanel == null) {
         this.scriptPanel = new ScriptPanel(this);
      }

      return this.scriptPanel;
   }

   /*
    * Opens or closes the script menu, and remembers that the player has found
    * it. The only two ways in -- F2 and /menu -- both come through here so
    * that neither can mark it seen without the other.
    *
    * The welcome box advertises F2 until this has happened once. There is no
    * way to discover the menu from the game itself otherwise: nothing on
    * screen mentions it, and a key that does nothing visible is not something
    * anyone presses twice.
    */
   final void toggleScriptPanel() {
      if (this.showScriptMenuHint) {
         this.showScriptMenuHint = false;
         Config.settings().set(HINT_SETTING, true);
         Config.settings().save();
      }

      this.scriptPanel().toggle();
   }

   /*
    * The world map. Nothing is allocated and nothing is downloaded until it is
    * opened -- the map is a megabyte that most sessions never look at.
    */
   final WorldMapPanel worldMapPanel() {
      if (this.worldMapPanel == null) {
         this.worldMapPanel = new WorldMapPanel(this);
      }

      return this.worldMapPanel;
   }

   /*
    * The calculator loader: the same machinery as scripts() pointed at
    * calculators/. Its compiled output is named explicitly because the
    * two-argument constructor would default to the sibling "script-bin" and
    * a calculator called Miner would overwrite the script called Miner.
    */
   final ScriptRunner calculators() {
      if (this.calculatorRunner == null) {
         File dir = calculatorDirectory();
         this.calculatorRunner = new ScriptRunner(this, dir,
            new File(dir.getAbsoluteFile().getParentFile(), "calculators-bin"));
      }

      return this.calculatorRunner;
   }

   /* The calculator screen. Lazy for the same reason as the others. */
   final CalculatorPanel calculatorPanel() {
      if (this.calculatorPanel == null) {
         this.calculatorPanel = new CalculatorPanel(this);
      }

      return this.calculatorPanel;
   }

   /*
    * Scripts live next to the jar unless the path is absolute, for the same
    * reason settings.ini does: a double-clicked jar has no useful working
    * directory.
    */
   static File scriptDirectory() {
      File dir = new File(Config.SCRIPT_DIR == null ? "scripts" : Config.SCRIPT_DIR);
      return dir.isAbsolute() ? dir : new File(jarDirectory(), dir.getPath());
   }

   /* Calculators likewise. */
   static File calculatorDirectory() {
      File dir = new File(Config.CALCULATOR_DIR == null ? "calculators" : Config.CALCULATOR_DIR);
      return dir.isAbsolute() ? dir : new File(jarDirectory(), dir.getPath());
   }

   /*
    * And screenshots and movies, for the same reason again. media_dir is
    * relative by default, so it used to resolve against the working directory:
    * launched by hand from a home directory, the client scattered a media/
    * tree there instead of keeping it with the install. A player who
    * double-clicks the jar should not have to guess where their screenshots
    * went.
    */
   static File mediaDirectory() {
      File dir = new File(Config.MEDIA_DIR == null ? "media" : Config.MEDIA_DIR);
      return dir.isAbsolute() ? dir : new File(jarDirectory(), dir.getPath());
   }

   /*
    * The pre-rendered interface fonts -- see GameWindow.loadFonts.
    *
    * Deliberately NOT under mediaDirectory(). By default the two resolve to
    * the same place, but media_dir is a setting: it names where a player's
    * screenshots and recordings should be written, and someone who points it
    * at their Pictures folder is not asking for the fonts that shipped with
    * the client to go there too. Fonts are installed assets and stay with the
    * install.
    */
   static File fontDirectory() {
      File beside = new File(new File(jarDirectory(), "media"), "fonts");
      if (beside.isDirectory()) {
         return beside;
      }

      /* Running the compiled classes straight out of build/ rather than the
         packed jar, which is what build.sh leaves behind and what anyone
         working on the client does all day. jarDirectory() is build/ then, and
         the fonts are one level up beside the jar. Without this the fonts are
         the one asset that quietly stops applying in a dev checkout, which is
         the worst place to lose them -- it is where layout gets looked at. */
      File up = jarDirectory().getParentFile();
      return up == null ? beside : new File(new File(up, "media"), "fonts");
   }

   /*
    * requireReflectiveAccess() stood here.
    *
    * XStream 1.x deserialised the entity data by reflecting into JDK internals
    * -- java.util.Properties.defaults among them -- which JDK 16 refuses. The
    * fix was the Add-Opens line in the jar manifest, honoured for java -jar and
    * ignored for -cp, so this ran the exact setAccessible call XStream tripped
    * on and put a readable dialog in front of anyone who launched it the other
    * way.
    *
    * There is nothing left to test. The defs are read by util/XmlObjects now,
    * which reflects only over the public fields of our own classes -- ordinary
    * API use that no JDK restricts -- so the client needs no --add-opens, no
    * manifest entry, and no libraries at all. The guard outlived its cause by
    * about an hour and refused to start a client that had stopped needing it.
    */

   /*
    * Report something the client cannot start without. Goes to a dialog as well
    * as stderr, because the jar is meant to be double-clicked and stderr goes
    * nowhere when it is.
    */
   static void fatal(String message) {
      System.err.println(message);

      try {
         javax.swing.JOptionPane.showMessageDialog(null, message, "RSCD", javax.swing.JOptionPane.ERROR_MESSAGE);
      } catch (Throwable var2) {
         // Headless, or no display -- stderr was the best we could do.
      }

      System.exit(1);
   }

   /*
    * The script commands, on / with the rest of the user layer -- STS's
    * prefix, STS's names. :: is the admin plane, handed to the server
    * untouched.
    *
    *    /start name(arg,arg)   /stop   /reload   /scripts   /menu   /debug
    *
    * Anything else falls through to handleCommand, and an unrecognised /
    * line is offered to the running script through OnInput by the caller and
    * then dropped with a hint -- a / line is the client's, never chat.
    */
   private boolean handleScriptCommand(String s) {
      String cmd = s;
      String rest = "";
      int split = s.indexOf(' ');
      if (split != -1) {
         cmd = s.substring(0, split);
         rest = s.substring(split + 1).trim();
      }

      cmd = cmd.toLowerCase();
      if (cmd.equals("start")) {
         this.startScript(rest);
         return true;
      } else if (cmd.equals("stop")) {
         if (this.scriptRunner == null || !this.scriptRunner.isRunning()) {
            this.displayMessage("@gry@ Nothing is running", 3, 0);
         } else {
            String was = this.scriptRunner.getScriptName();
            this.scriptRunner.stop();
            this.displayMessage("@gry@ Stopped " + was, 3, 0);
         }

         return true;
      } else if (cmd.equals("reload")) {
         // Recompiles, because load() rebuilds whenever the source is newer.
         if (this.scriptRunner == null || this.lastScriptCommand.length() == 0) {
            this.displayMessage("@gry@ Nothing to reload", 3, 0);
         } else {
            this.startScript(this.lastScriptCommand);
         }

         return true;
      } else if (cmd.equals("scripts")) {
         // /macros, the STS name for the same listing, lands in handleCommand.
         this.listScripts();
         return true;
      } else if (cmd.equals("menu")) {
         this.toggleScriptPanel();
         return true;
      } else if (cmd.equals("debug")) {
         this.scriptDebug = !this.scriptDebug;
         this.displayMessage("@gry@ Debug " + (this.scriptDebug ? "on" : "off"), 3, 0);
         return true;
      } else {
         return false;
      }
   }

   /* One listing, two names: /scripts, and /macros as STS called it -- it
      listed its built-in Macro_ classes, and the scripts folder is the
      macros here. */
   private void listScripts() {
      String[] names = this.scripts().list();
      if (names.length == 0) {
         this.displayMessage("@gry@ No scripts in " + scriptDirectory().getPath(), 3, 0);
      } else {
         StringBuilder line = new StringBuilder("@gry@ ");

         for (int i = 0; i < names.length; i++) {
            line.append(i > 0 ? ", " : "").append(names[i]);
         }

         this.displayMessage(line.toString(), 3, 0);
      }
   }

   /*
    * "name(arg,arg)" or "name arg arg" -- the first is what every script from
    * 2006 documents, the second is what people type.
    */
   /*
    * The supervision tick, strictly for the script THIS session started and
    * has not stopped. It is brought back after every login (fresh start,
    * because a thread that lived through the outage is looping on a world
    * that vanished under it) and after a crash (with ScriptRunner's backoff,
    * so a script that dies on its first breath does not spin). Somebody who
    * starts a bot and comes back in a week finds it still going; /stop, the
    * panel's Stop, or the script ending itself are final -- and a freshly
    * launched client never starts anything on its own.
    */
   private void pumpScriptResume() {
      ScriptRunner runner = this.scripts();
      String name = runner.pump();
      if (name == null) {
         return;
      }
      try {
         Methods loaded = runner.load(name);
         this.displayMessage("@gry@ Resuming " + name, 3, 0);
         /* The recorded setup answers replay into the script's prompts, so
            it starts over without asking anybody anything. */
         runner.startAuto(loaded, name);
      } catch (ScriptRunner.ScriptException var3) {
         /* Source went bad since it last ran; retry on the crash backoff
            rather than every tick, and say so each time. */
         this.displayMessage("@red@ Could not resume " + name + " -- see /reload", 3, 0);
         runner.resumeFailed();
      }
   }

   private void startScript(String request) {
      String name = request.trim();
      String[] args = new String[0];

      int bracket = name.indexOf('(');
      if (bracket != -1) {
         int close = name.lastIndexOf(')');
         String inside = name.substring(bracket + 1, close == -1 ? name.length() : close).trim();
         name = name.substring(0, bracket).trim();
         if (inside.length() > 0) {
            args = inside.split("\\s*,\\s*");
         }
      } else {
         int space = name.indexOf(' ');
         if (space != -1) {
            args = name.substring(space + 1).trim().split("\\s+");
            name = name.substring(0, space);
         }
      }

      if (name.length() == 0) {
         this.displayMessage("@gry@ Usage: /start name(arg,arg)", 3, 0);
         return;
      }

      try {
         Methods loaded = this.scripts().load(name);
         this.scripts().start(loaded, name, args);
         // Only after it started, so /reload does not retry a typo.
         this.lastScriptCommand = request.trim();
         this.displayMessage("@gry@ Started " + name, 3, 0);
      } catch (ScriptRunner.ScriptException var7) {
         // Compiler output is many lines; the chat box shows one at a time.
         for (String line : var7.getMessage().split("\n")) {
            if (line.trim().length() > 0) {
               this.displayMessage("@red@ " + line, 3, 0);
            }
         }
      }
   }

   /*
    * The rest of STS's / layer -- the user-level convenience commands it grew
    * because the stock client made live RSC awkward, not admin commands. STS
    * kept /offer and /stake apart because it had a method per window; only
    * one of the two windows can be open, so here they are two names for the
    * same command. withdraw and deposit talk to the open bank, hop relogs
    * into another world of the same server (which is all /hop(n) ever did),
    * reset zeroes the exp counter the status overlay draws, and macros lists
    * what /scripts lists -- the scripts folder is the macros.
    */
   private boolean handleCommand(String s) {
      /* STS documented /offer(10,100); a decade of fingers types
         /offer 10 100. Both shapes land here as the same words. */
      String[] words = s.replace('(', ' ').replace(',', ' ').replace(')', ' ').trim().split("\\s+");
      String cmd = words[0].toLowerCase();
      String[] args = new String[words.length - 1];
      System.arraycopy(words, 1, args, 0, args.length);

      if (cmd.equals("macros")) {
         this.listScripts();
         return true;
      }

      if (cmd.equals("reset")) {
         this.expGained = 0L;
         this.displayMessage("@gry@ Exp counter reset", 3, 0);
         return true;
      }

      if (cmd.equals("hop")) {
         /* login() drops the current socket itself, and which worlds exist is
            the server's business -- a world it does not run fails at the
            connection with a real message (see Methods.ChangeWorld). */
         try {
            int world = Integer.parseInt(args[0]);
            if (world < 1) {
               this.displayMessage("@gry@ Invalid world", 3, 0);
            } else if (world == this.theworld) {
               this.displayMessage("@gry@ You are already on world " + world, 3, 0);
            } else {
               this.loginWasAutomatic = false;
               this.login(this.username, this.password, world, false);
            }
         } catch (Exception var10) {
            this.displayMessage("@gry@ Usage: /hop world", 3, 0);
         }

         return true;
      }

      if (cmd.equals("withdraw") || cmd.equals("deposit")) {
         /* The bank screen has no amount box, so typing the number was the
            only way to move a big stack without a hundred clicks. Checked
            against the client's own books first, the way offer is below, so a
            typo is a chat line rather than a silent nothing from the server. */
         if (!this.showBank) {
            this.displayMessage("@gry@ You aren't at a bank, there is nothing to " + cmd + " from.", 3, 0);
            return true;
         }

         try {
            int id = Integer.parseInt(args[0]);
            int amount = Integer.parseInt(args[1]);
            if (amount < 1) {
               this.displayMessage("@gry@ Invalid args!", 3, 0);
               return true;
            }

            if (cmd.equals("withdraw")) {
               int inBank = 0;

               for (int c = 0; c < this.bankItemCount; c++) {
                  if (this.bankItems[c] == id) {
                     inBank = this.bankItemsCount[c];
                     break;
                  }
               }

               if (inBank < amount) {
                  this.displayMessage("@gry@ You do not have that many " + EntityHandler.getItemDef(id).getName() + " in the bank", 3, 0);
                  return true;
               }

               super.streamClass.createPacket(183);
            } else {
               if (this.inventoryCount(id) < amount) {
                  this.displayMessage("@gry@ You do not have that many " + EntityHandler.getItemDef(id).getName() + " to deposit", 3, 0);
                  return true;
               }

               super.streamClass.createPacket(198);
            }

            super.streamClass.add2ByteInt(id);
            super.streamClass.add4ByteInt(amount);
            super.streamClass.formatPacket();
         } catch (Exception var11) {
            this.displayMessage("@gry@ Invalid args!", 3, 0);
         }

         return true;
      }

      if (!cmd.equals("offer") && !cmd.equals("stake")) {
         return false;
      } else {
         try {
            int id = Integer.parseInt(args[0]);
            int amount = Integer.parseInt(args[1]);
            boolean done = false;
            if (this.showTradeWindow) {
               if (this.tradeMyItemCount >= 12) {
                  this.displayMessage("@gry@ Your trade offer is currently full", 3, 0);
                  return true;
               }

               if (this.inventoryCount(id) < amount) {
                  this.displayMessage("@gry@ You do not have that many " + EntityHandler.getItemDef(id).getName() + " to offer", 3, 0);
                  return true;
               }

               if (!EntityHandler.getItemDef(id).isStackable() && amount > 1) {
                  this.displayMessage("@gry@ You can only offer 1 non stackable at a time", 3, 0);
                  return true;
               }

               for (int c = 0; c < this.tradeMyItemCount; c++) {
                  if (this.tradeMyItems[c] == id) {
                     if (EntityHandler.getItemDef(id).isStackable()) {
                        if (this.inventoryCount(id) < this.tradeMyItemsCount[c] + amount) {
                           this.displayMessage("@gry@ You do not have that many " + EntityHandler.getItemDef(id).getName() + " to offer", 3, 0);
                           return true;
                        }

                        this.tradeMyItemsCount[c] = this.tradeMyItemsCount[c] + amount;
                        done = true;
                     }
                     break;
                  }
               }

               if (!done) {
                  this.tradeMyItems[this.tradeMyItemCount] = id;
                  this.tradeMyItemsCount[this.tradeMyItemCount] = amount;
                  this.tradeMyItemCount++;
               }

               super.streamClass.createPacket(70);
               super.streamClass.addByte(this.tradeMyItemCount);

               for (int cx = 0; cx < this.tradeMyItemCount; cx++) {
                  super.streamClass.add2ByteInt(this.tradeMyItems[cx]);
                  super.streamClass.add4ByteInt(this.tradeMyItemsCount[cx]);
               }

               super.streamClass.formatPacket();
               this.tradeOtherAccepted = false;
               this.tradeWeAccepted = false;
            } else if (this.showDuelWindow) {
               if (this.duelMyItemCount >= 12) {
                  this.displayMessage("@gry@ Your duel offer is currently full", 3, 0);
                  return true;
               }

               if (this.inventoryCount(id) < amount) {
                  this.displayMessage("@gry@ You do not have that many " + EntityHandler.getItemDef(id).getName() + " to offer", 3, 0);
                  return true;
               }

               if (!EntityHandler.getItemDef(id).isStackable() && amount > 1) {
                  this.displayMessage("@gry@ You can only offer 1 non stackable at a time", 3, 0);
                  return true;
               }

               for (int cx = 0; cx < this.duelMyItemCount; cx++) {
                  if (this.duelMyItems[cx] == id) {
                     if (EntityHandler.getItemDef(id).isStackable()) {
                        if (this.inventoryCount(id) < this.duelMyItemsCount[cx] + amount) {
                           this.displayMessage("@gry@ You do not have that many " + EntityHandler.getItemDef(id).getName() + " to offer", 3, 0);
                           return true;
                        }

                        this.duelMyItemsCount[cx] = this.duelMyItemsCount[cx] + amount;
                        done = true;
                     }
                     break;
                  }
               }

               if (!done) {
                  this.duelMyItems[this.duelMyItemCount] = id;
                  this.duelMyItemsCount[this.duelMyItemCount] = amount;
                  this.duelMyItemCount++;
               }

               super.streamClass.createPacket(123);
               super.streamClass.addByte(this.duelMyItemCount);

               for (int cxx = 0; cxx < this.duelMyItemCount; cxx++) {
                  super.streamClass.add2ByteInt(this.duelMyItems[cxx]);
                  super.streamClass.add4ByteInt(this.duelMyItemsCount[cxx]);
               }

               super.streamClass.formatPacket();
               this.duelOpponentAccepted = false;
               this.duelMyAccepted = false;
            } else {
               this.displayMessage("@gry@ You aren't in a trade/stake, there is nothing to offer to.", 3, 0);
            }
         } catch (Exception var9) {
            this.displayMessage("@gry@ Invalid args!", 3, 0);
         }

         return true;
      }
   }

   private static String timeSince(long time) {
      int seconds = (int)((System.currentTimeMillis() - time) / 1000L);
      int minutes = seconds / 60;
      int hours = minutes / 60;
      int days = hours / 24;
      return days + " days " + hours % 24 + " hours " + minutes % 60 + " mins";
   }

   private BufferedImage getImage() throws IOException {
      BufferedImage bufferedImage = new BufferedImage(this.windowWidth, this.windowHeight + 11, 1);
      Graphics2D g2d = bufferedImage.createGraphics();
      g2d.drawImage(this.gameGraphics.image, 0, 0, this);
      g2d.dispose();
      return bufferedImage;
   }

   private File getEmptyFile(boolean movie) throws IOException {
      String charName = DataOperations.longToString(DataOperations.stringLength12ToLong(this.currentUser));
      File file = new File(mediaDirectory(), charName);
      if (!file.exists() || !file.isDirectory()) {
         // mkdirs, not mkdir: media/ itself may not exist yet either.
         file.mkdirs();
      }

      String folder = file.getPath() + File.separator;
      file = null;

      for (int suffix = 0; file == null || file.exists(); suffix++) {
         // .avi, not .mov: the recorder writes MJPG AVI now, not QuickTime.
         file = movie ? new File(folder + "movie" + suffix + ".avi") : new File(folder + "screenshot" + suffix + ".png");
      }

      return file;
   }

   boolean takeScreenshot(boolean verbose) {
      try {
         File file = this.getEmptyFile(false);
         ImageIO.write(this.getImage(), "png", file);
         if (verbose) {
            this.handleServerMessage("Screenshot saved as " + file.getName() + ".");
         }

         return true;
      } catch (IOException var3) {
         if (verbose) {
            this.handleServerMessage("Error saving screenshot.");
         }

         return false;
      }
   }

   /*
    * Start or stop the movie recorder. This was written inline under F11 and is
    * lifted out so the menu's Record button reaches the same code rather than a
    * second copy of it.
    */
   final void toggleRecording() {
      this.recording = !this.recording;
      if (this.recording) {
         try {
            this.frames.clear();
            this.framesDropped = 0;
            this.captureTicks = 0;
            File file = this.getEmptyFile(true);
            this.recorder = new Recorder(
               this.windowWidth, this.windowHeight + 11, (float)Config.MOVIE_FPS, this.frames, file.getAbsolutePath()
            );
            this.displayMessage("@gry@ Recording movie to " + file.getName(), 3, 0);
            new Thread(this.recorder, "recorder").start();
         } catch (Exception var4) {
            this.recording = false;
            this.displayMessage("@gry@ Could not start recording.", 3, 0);
         }
      } else {
         /* The encoder is still draining the queue, so the count it reports
            here is the frames handed to it, not necessarily the frames already
            on disk. Close the queue and let it finish on its own thread. */
         int captured = this.frames.size() + (this.recorder == null ? 0 : this.recorder.framesWritten());
         while (!this.frames.offer(Recorder.END)) {
            this.frames.poll();
         }

         this.recorder = null;
         String dropped = this.framesDropped > 0 ? ", " + this.framesDropped + " dropped" : "";
         this.displayMessage("@gry@ Movie saved (" + captured + " frames" + dropped + ").", 3, 0);
      }
   }

   /*
    * The half of the recorder that was missing: without this nothing ever went
    * into the queue, so every recording came out as a container with no video
    * in it.
    *
    * Sampled down to MOVIE_FPS rather than taken every tick -- the game runs at
    * 50 ticks a second and a frame is windowWidth x windowHeight of int, so
    * capturing every one would be about 35 MB/s into a queue the encoder
    * cannot drain that fast. If the queue is full anyway the frame is dropped
    * rather than blocking the render thread; a recording that stutters is
    * better than a client that does.
    */
   private final void captureFrame() {
      if (this.recording) {
         int every = Math.max(1, 50 / Math.max(1, Config.MOVIE_FPS));
         if (++this.captureTicks >= every) {
            this.captureTicks = 0;

            try {
               if (!this.frames.offer(this.getImage())) {
                  this.framesDropped++;
               }
            } catch (Exception var3) {
               this.framesDropped++;
            }
         }
      }
   }

   private static boolean isNumeric(String s) {
      char[] chars = s.toCharArray();

      for (int x = 0; x < chars.length; x++) {
         char c = chars[x];
         if (c < '0' || c > '9') {
            return false;
         }
      }

      return true;
   }

   private static int SalvageInput(String s) {
      if (s == null) {
         return 0;
      } else {
         String sl = s.replace(" ", "");
         sl = sl.trim();
         return isNumeric(sl) && sl.length() != 0 ? Integer.parseInt(sl) : 0;
      }
   }

   private static double roundTwoDecimals(double d) {
      DecimalFormat twoDForm = new DecimalFormat("#.##");
      return Double.valueOf(twoDForm.format(d));
   }

   public void loadCacheFromMirrors() {
      ArrayList<String> arraylist = new ArrayList<String>();
      arraylist.add(Config.CACHE_URL);
      dB = arraylist.toArray(new String[arraylist.size()]);
   }

   /*
    * Fetch one game asset into memory.
    *
    * The client used to keep a cache directory and only re-download what an
    * MD5 check said was stale. It keeps nothing now: every launch pulls the
    * full set from Config.CACHE_URL into Assets and drops it on exit. That
    * makes the web server the only copy that exists, so publishing content is
    * just replacing a file there -- no staleness, no checksum service, no
    * half-updated install to support, and nothing left on a player's disk.
    *
    * It also means there is no local copy to fall back on, so a failure here
    * is fatal. That is the trade and it is the point of the design.
    */
   public final void loadcache(String s1) {
      this.drawLoadingBarText(1, "Fetching " + s1 + "..");

      for (String mirror : dB) {
         String url = mirror + "/" + s1;

         try {
            HttpURLConnection httpurlconnection = (HttpURLConnection)new URL(url).openConnection();
            httpurlconnection.setConnectTimeout(CACHE_CONNECT_TIMEOUT);
            httpurlconnection.setReadTimeout(CACHE_READ_TIMEOUT);
            if (httpurlconnection.getResponseCode() != 200) {
               System.err.println(url + " -> HTTP " + httpurlconnection.getResponseCode());
               continue;
            }

            double total = (double)httpurlconnection.getContentLength();
            ByteArrayOutputStream out = new ByteArrayOutputStream(total > 0.0 ? (int)total : 65536);
            InputStream inputstream = httpurlconnection.getInputStream();

            try {
               byte[] abyte1 = new byte[8192];
               int k1;
               while ((k1 = inputstream.read(abyte1)) != -1) {
                  out.write(abyte1, 0, k1);
                  int percent = total > 0.0 ? (int)Math.min(100.0, (double)out.size() / total * 100.0) : 0;
                  this.drawLoadingBarText(percent, "Fetching " + s1 + ".. " + percent + "%");
               }
            } finally {
               inputstream.close();
            }

            Assets.put(s1, out.toByteArray());
            return;
         } catch (Exception var13) {
            System.err.println(url + " -> " + var13);
         }
      }

      this.drawLoadingBarText(0, "Could not fetch " + s1);
      fatal("Could not download the game data.\n\n" + Config.CACHE_URL + "/" + s1 + " could not be reached.\n\nCheck your connection and try again.");
   }

   final void drawNpc(int i, int j, int k, int l, int i1, int j1, int k1) {
      Mob mob = this.npcArray[i1];
      int l1 = mob.currentSprite + (this.cameraRotation + 16) / 32 & 7;
      boolean flag = false;
      int i2 = l1;
      if (l1 == 5) {
         i2 = 3;
         flag = true;
      } else if (l1 == 6) {
         i2 = 2;
         flag = true;
      } else if (l1 == 7) {
         i2 = 1;
         flag = true;
      }

      int j2 = i2 * 3 + this.walkModel[mob.stepCount / EntityHandler.getNpcDef(mob.type).getWalkModel() % 4];
      if (mob.currentSprite == 8) {
         i2 = 5;
         l1 = 2;
         flag = false;
         i -= EntityHandler.getNpcDef(mob.type).getCombatSprite() * k1 / 100;
         j2 = i2 * 3 + this.npcCombatModelArray1[this.loginTimer / (EntityHandler.getNpcDef(mob.type).getCombatModel() - 1) % 8];
      } else if (mob.currentSprite == 9) {
         i2 = 5;
         l1 = 2;
         flag = true;
         i += EntityHandler.getNpcDef(mob.type).getCombatSprite() * k1 / 100;
         j2 = i2 * 3 + this.npcCombatModelArray2[this.loginTimer / EntityHandler.getNpcDef(mob.type).getCombatModel() % 8];
      }

      for (int k2 = 0; k2 < 12; k2++) {
         int l2 = this.npcAnimationArray[l1][k2];
         int k3 = EntityHandler.getNpcDef(mob.type).getSprite(l2);
         if (k3 >= 0) {
            int i4 = 0;
            int j4 = 0;
            int k4 = j2;
            if (flag && i2 >= 1 && i2 <= 3 && EntityHandler.getAnimationDef(k3).hasF()) {
               k4 = j2 + 15;
            }

            if (i2 != 5 || EntityHandler.getAnimationDef(k3).hasA()) {
               int l4 = k4 + EntityHandler.getAnimationDef(k3).getNumber();
               i4 = i4 * k / this.gameGraphics.sprites[l4].getSomething1();
               j4 = j4 * l / this.gameGraphics.sprites[l4].getSomething2();
               int i5 = k
                  * this.gameGraphics.sprites[l4].getSomething1()
                  / this.gameGraphics.sprites[EntityHandler.getAnimationDef(k3).getNumber()].getSomething1();
               i4 -= (i5 - k) / 2;
               int colour = EntityHandler.getAnimationDef(k3).getCharColour();
               int skinColour = 0;
               if (colour == 1) {
                  colour = EntityHandler.getNpcDef(mob.type).getHairColour();
                  skinColour = EntityHandler.getNpcDef(mob.type).getSkinColour();
               } else if (colour == 2) {
                  colour = EntityHandler.getNpcDef(mob.type).getTopColour();
                  skinColour = EntityHandler.getNpcDef(mob.type).getSkinColour();
               } else if (colour == 3) {
                  colour = EntityHandler.getNpcDef(mob.type).getBottomColour();
                  skinColour = EntityHandler.getNpcDef(mob.type).getSkinColour();
               }

               this.gameGraphics.spriteClip4(i + i4, j + j4, i5, l, l4, colour, skinColour, j1, flag);
            }
         }
      }

      if (mob.lastMessageTimeout > 0) {
         this.mobMessagesWidth[this.mobMessageCount] = this.gameGraphics.textWidth(mob.lastMessage, 1) / 2;
         if (this.mobMessagesWidth[this.mobMessageCount] > 150) {
            this.mobMessagesWidth[this.mobMessageCount] = 150;
         }

         this.mobMessagesHeight[this.mobMessageCount] = this.gameGraphics.textWidth(mob.lastMessage, 1) / 300 * this.gameGraphics.messageFontHeight(1);
         this.mobMessagesX[this.mobMessageCount] = i + k / 2;
         this.mobMessagesY[this.mobMessageCount] = j;
         this.mobMessages[this.mobMessageCount++] = mob.lastMessage;
      }

      boolean showBar = this.healthBarShows(mob);
      if (mob.currentSprite == 8 || mob.currentSprite == 9 || mob.combatTimer != 0 || showBar) {
         if (showBar) {
            int i3 = i;
            if (mob.currentSprite == 8) {
               i3 = i - 20 * k1 / 100;
            } else if (mob.currentSprite == 9) {
               i3 = i + 20 * k1 / 100;
            }

            int l3 = this.healthBarWidth(mob);
            this.healthBarX[this.healthBarCount] = i3 + k / 2;
            this.healthBarY[this.healthBarCount] = j;
            this.healthBarValue[this.healthBarCount++] = l3;
         }

         if (mob.combatTimer > 150) {
            int j3 = i;
            if (mob.currentSprite == 8) {
               j3 = i - 10 * k1 / 100;
            } else if (mob.currentSprite == 9) {
               j3 = i + 10 * k1 / 100;
            }

            this.gameGraphics.drawPicture(j3 + k / 2 - 12, j + l / 2 - 12, 2012);
            this.gameGraphics.drawText(String.valueOf(mob.damageTaken), j3 + k / 2 - 1, j + l / 2 + 5, 3, 16777215);
         }
      }
   }

   /*
    * STS's show-HP.
    *
    * Vanilla draws the bar only while combatTimer is running -- 200 ticks from
    * the last damage update -- so a mob you are not fighting has no bar even
    * though the server has already told you its health. With the setting on the
    * bar stays for as long as that health is known.
    *
    * hitPointsBase is the test for "known": the client sets it nowhere but the
    * damage update, and it is also the divisor, so a mob the server has said
    * nothing about is both unrevealed and unsafe to divide by.
    */
   final boolean healthBarShows(Mob mob) {
      return this.healthBase(mob) > 0 && (mob.combatTimer > 0 || this.showHealthBars);
   }

   /*
    * Our own health does NOT come from hitPointsCurrent, even though every
    * other mob's does.
    *
    * hitPointsCurrent is written in exactly one place -- the type-2 mob update,
    * which is a *damage* report. That is fine for someone else: the only health
    * we are ever told about them arrives that way, so the last damage report is
    * by definition the newest thing we know. It is wrong for us. The server
    * keeps our real hits in the stat table and sends them through the stat
    * packets instead, which is what the Stats panel reads and what actually
    * tracks eating, praying, regenerating and levelling up. Nothing in those
    * paths touches hitPointsCurrent.
    *
    * So a player's own bar froze at whatever the last hit landed them on and
    * stayed there: eat back to full and the numbers read 49/49 while the bar
    * still showed the damage. Fresh from login it was worse -- hitPointsBase is
    * 0 until something hits you, so the bar did not draw at all no matter what
    * the setting said. Vanilla never noticed because vanilla only draws the bar
    * while combatTimer is running, which is precisely when the damage report is
    * the current truth; showing it the rest of the time is ours, and it is the
    * rest of the time that this was wrong.
    *
    * Reading the stat table at the point of use rather than copying it into
    * hitPointsCurrent on every stat update keeps one owner for the value. A
    * copy is another thing to keep in step, and the whole bug is a copy that
    * fell out of step.
    */
   private int healthCurrent(Mob mob) {
      return mob == this.ourPlayer ? this.playerStatCurrent[3] : mob.hitPointsCurrent;
   }

   private int healthBase(Mob mob) {
      return mob == this.ourPlayer ? this.playerStatBase[3] : mob.hitPointsBase;
   }

   /**
    * Width in pixels of the green part of a 30-pixel bar. Clamped: the red part
    * is drawn as the 30 minus this, so anything out of range would be drawn as
    * a negative-width box rather than simply looking wrong.
    */
   private int healthBarWidth(Mob mob) {
      int width = this.healthCurrent(mob) * 30 / this.healthBase(mob);
      if (width < 0) {
         return 0;
      }
      return width > 30 ? 30 : width;
   }

   private final void drawCharacterLookScreen() {
      this.characterDesignMenu.updateActions(super.mouseX, super.mouseY, super.lastMouseDownButton, super.mouseDownButton);
      if (this.characterDesignMenu.hasActivated(this.characterDesignHeadButton1)) {
         do {
            this.characterHeadType = (this.characterHeadType - 1 + EntityHandler.animationCount()) % EntityHandler.animationCount();
         } while (
            (EntityHandler.getAnimationDef(this.characterHeadType).getGenderModel() & 3) != 1
               || (EntityHandler.getAnimationDef(this.characterHeadType).getGenderModel() & 4 * this.characterHeadGender) == 0
         );
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignHeadButton2)) {
         do {
            this.characterHeadType = (this.characterHeadType + 1) % EntityHandler.animationCount();
         } while (
            (EntityHandler.getAnimationDef(this.characterHeadType).getGenderModel() & 3) != 1
               || (EntityHandler.getAnimationDef(this.characterHeadType).getGenderModel() & 4 * this.characterHeadGender) == 0
         );
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignHairColourButton1)) {
         this.characterHairColour = (this.characterHairColour - 1 + this.characterHairColours.length) % this.characterHairColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignHairColourButton2)) {
         this.characterHairColour = (this.characterHairColour + 1) % this.characterHairColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignGenderButton1) || this.characterDesignMenu.hasActivated(this.characterDesignGenderButton2)) {
         this.characterHeadGender = 3 - this.characterHeadGender;

         while (
            (EntityHandler.getAnimationDef(this.characterHeadType).getGenderModel() & 3) != 1
               || (EntityHandler.getAnimationDef(this.characterHeadType).getGenderModel() & 4 * this.characterHeadGender) == 0
         ) {
            this.characterHeadType = (this.characterHeadType + 1) % EntityHandler.animationCount();
         }

         while (
            (EntityHandler.getAnimationDef(this.characterBodyGender).getGenderModel() & 3) != 2
               || (EntityHandler.getAnimationDef(this.characterBodyGender).getGenderModel() & 4 * this.characterHeadGender) == 0
         ) {
            this.characterBodyGender = (this.characterBodyGender + 1) % EntityHandler.animationCount();
         }
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignTopColourButton1)) {
         this.characterTopColour = (this.characterTopColour - 1 + this.characterTopBottomColours.length) % this.characterTopBottomColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignTopColourButton2)) {
         this.characterTopColour = (this.characterTopColour + 1) % this.characterTopBottomColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignSkinColourButton1)) {
         this.characterSkinColour = (this.characterSkinColour - 1 + this.characterSkinColours.length) % this.characterSkinColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignSkinColourButton2)) {
         this.characterSkinColour = (this.characterSkinColour + 1) % this.characterSkinColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignBottomColourButton1)) {
         this.characterBottomColour = (this.characterBottomColour - 1 + this.characterTopBottomColours.length) % this.characterTopBottomColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignBottomColourButton2)) {
         this.characterBottomColour = (this.characterBottomColour + 1) % this.characterTopBottomColours.length;
      }

      if (this.characterDesignMenu.hasActivated(this.characterDesignAcceptButton)) {
         super.streamClass.createPacket(218);
         super.streamClass.addByte(this.characterHeadGender);
         super.streamClass.addByte(this.characterHeadType);
         super.streamClass.addByte(this.characterBodyGender);
         super.streamClass.addByte(this.character2Colour);
         super.streamClass.addByte(this.characterHairColour);
         super.streamClass.addByte(this.characterTopColour);
         super.streamClass.addByte(this.characterBottomColour);
         super.streamClass.addByte(this.characterSkinColour);
         super.streamClass.formatPacket();
         this.gameGraphics.clearScreen();
         this.showCharacterLookScreen = false;
      }
   }

   final int inventoryCount(int reqID) {
      int amount = 0;

      for (int index = 0; index < this.inventoryCount; index++) {
         if (this.inventoryItems[index] == reqID) {
            if (!EntityHandler.getItemDef(reqID).isStackable()) {
               amount++;
            } else {
               amount += this.inventoryItemsCount[index];
            }
         }
      }

      return amount;
   }

   private final void updateLoginScreen() {
      if (super.socketTimeout > 0) {
         super.socketTimeout--;
      }

      /*
       * STS's AutoLogin. The client already reconnects a socket that dies
       * under you -- that is original behaviour and is not gated by this.
       * What this adds is the other case: sitting at the login screen, after
       * a logout, after a kick, after a failed reconnect, which is the one a
       * bot actually has to survive. STS handled it from here, by logging in
       * again with the credentials it already had.
       *
       * Off by default, because with it on the Logout button becomes a round
       * trip and a person who clicks Logout means it.
       *
       * The wait grows with each failed try, up to a minute: "that username is
       * already logged in" clears after 60 seconds and a client that retries
       * every two seconds spends that minute hammering the login server.
       *
       * With this on, the Logout button is a round trip -- you log out and come
       * straight back in. That is what it did in STS and what makes it useful;
       * turn it off in the F2 menu, or AutoLogin(false) from a script, when you
       * mean to stay logged out.
       */
      /* Not while the Worlds screen is up: a player browsing for a different
         server is not waiting to be logged back into the old one. */
      if (this.autoLogin && this.loginScreenNumber != 4 && this.currentUser.length() > 0 && this.currentPass.length() > 0) {
         if (this.autoLoginTicks > 0) {
            this.autoLoginTicks--;
         } else {
            this.autoLoginTries++;
            this.autoLoginTicks = Math.min(AUTO_LOGIN_WAIT * this.autoLoginTries, AUTO_LOGIN_WAIT_MAX);
            /* Onto the login panel first, so the status line has somewhere to
               print: loginScreenPrint() is a no-op on the welcome screen and
               every failure would be silent. */
            this.loginScreenNumber = 2;
            this.menuLogin.updateText(this.loginUsernameTextBox, this.currentUser);
            this.menuLogin.updateText(this.loginPasswordTextBox, this.currentPass);
            this.loginWasAutomatic = true;
            this.login(this.currentUser, this.currentPass, this.currentWorld, false);
            return;
         }
      }

      if (this.loginScreenNumber == 0) {
         this.menuWelcome.updateActions(super.mouseX, super.mouseY, super.lastMouseDownButton, super.mouseDownButton);
         if (this.menuWelcome.hasActivated(this.loginButtonNewUser)) {
            this.loginScreenNumber = 1;
         }

         if (this.menuWelcome.hasActivated(this.loginButtonExistingUser)) {
            this.loginScreenNumber = 2;
            this.menuLogin.updateText(this.loginStatusText, "Please enter your username and password");
            this.menuLogin.updateText(this.loginUsernameTextBox, this.currentUser);
            this.menuLogin.updateText(this.loginPasswordTextBox, this.currentPass);
            this.menuLogin.setFocus(this.loginUsernameTextBox);
            return;
         }
      } else if (this.loginScreenNumber == 1) {
         this.menuNewUser.updateActions(super.mouseX, super.mouseY, super.lastMouseDownButton, super.mouseDownButton);
         if (this.menuNewUser.hasActivated(this.newUserOkButton)) {
            this.loginScreenNumber = 0;
            return;
         }
      } else if (this.loginScreenNumber == 2) {
         this.menuLogin.updateActions(super.mouseX, super.mouseY, super.lastMouseDownButton, super.mouseDownButton);
         if (this.menuLogin.hasActivated(this.loginCancelButton)) {
            this.loginScreenNumber = 0;
         }

         if (this.menuLogin.hasActivated(this.loginUsernameTextBox)) {
            this.menuLogin.setFocus(this.loginPasswordTextBox);
         }

         // Enter in the password box logs in, which is where the third row
         // used to take the focus instead.
         if (this.menuLogin.hasActivated(this.loginWorldsButton)) {
            this.showWorldsScreen();
            return;
         }

         if (this.menuLogin.hasActivated(this.loginPasswordTextBox) || this.menuLogin.hasActivated(this.loginOkButton)) {
            this.currentUser = this.menuLogin.getText(this.loginUsernameTextBox);
            this.currentPass = this.menuLogin.getText(this.loginPasswordTextBox);
            if (this.currentWorld < 1) {
               this.currentWorld = LOGIN_WORLD;
            }

            this.loginWasAutomatic = false;
            this.login(this.currentUser, this.currentPass, this.currentWorld, false);
         }
      } else if (this.loginScreenNumber == 4) {
         this.handleWorldsScreenInput();
      }
   }

   /*
    * Worlds screen input. Runs before the frame is drawn, so the row it acts
    * on is the one the list highlighted last frame -- which is the row under
    * the pointer, the same way the friends list decides what a click meant.
    */
   private final void handleWorldsScreenInput() {
      // The screen is drawn centred (WorldsPanel.draw); shift the mouse to match.
      this.worldsPanel().update(super.mouseX - this.loginOffsetX(), super.mouseY - this.loginOffsetY(), super.mouseDownButton);
   }

   /*
    * Back. Only somewhere to go back to once a server has been chosen: on a
    * fresh install this screen is the start of everything, so Back leaves you
    * here rather than on a sign-in box for nowhere.
    */
   final void leaveWorldsScreen() {
      this.loginScreenNumber = Config.DEFAULT_TARGET.length() > 0 ? 0 : 4;
   }

   private final void drawWorldsScreen() {
      this.worldsPanel().draw(this.gameGraphics, super.mouseX, super.mouseY);
   }

   private final void drawLoginScreen() {
      this.hasReceivedWelcomeBoxDetails = false;
      this.gameGraphics.f1Toggle = false;
      this.gameGraphics.clearScreen();
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      if (this.loginScreenNumber >= 0 && this.loginScreenNumber <= 4) {
         if (this.loginBackdropReady) {
            /*
             * Cross-fade the three captures. Each holds for 768 ticks then
             * spends 256 dissolving into the next, which at one tick a frame
             * is the pace the original panned at.
             */
            int cycle = this.loginTimer * 2 % LOGIN_VIEW_CYCLE;
            if (cycle < 1024) {
               this.gameGraphics.drawPicture(ox, LOGIN_LOGO_Y + oy, LOGIN_VIEW_SPRITES[0]);
               if (cycle > 768) {
                  this.gameGraphics.drawSpriteAlpha(ox, LOGIN_LOGO_Y + oy, LOGIN_VIEW_SPRITES[1], cycle - 768);
               }
            } else if (cycle < 2048) {
               this.gameGraphics.drawPicture(ox, LOGIN_LOGO_Y + oy, LOGIN_VIEW_SPRITES[1]);
               if (cycle > 1792) {
                  this.gameGraphics.drawSpriteAlpha(ox, LOGIN_LOGO_Y + oy, LOGIN_VIEW_SPRITES[2], cycle - 1792);
               }
            } else {
               this.gameGraphics.drawPicture(ox, LOGIN_LOGO_Y + oy, LOGIN_VIEW_SPRITES[2]);
               if (cycle > 2816) {
                  this.gameGraphics.drawSpriteAlpha(ox, LOGIN_LOGO_Y + oy, LOGIN_VIEW_SPRITES[0], cycle - 2816);
               }
            }
         } else {
            this.gameGraphics.drawPicture(LOGIN_LOGO_X + ox, LOGIN_LOGO_Y + oy, LOGIN_LOGO_SPRITE);
         }
      }

      if (this.loginScreenNumber == 0) {
         this.menuWelcome.drawMenu();
      }

      if (this.loginScreenNumber == 1) {
         this.menuNewUser.drawMenu();
      }

      if (this.loginScreenNumber == 2) {
         this.menuLogin.drawMenu();
      }

      if (this.loginScreenNumber == 4) {
         this.drawWorldsScreen();
      }

      this.gameGraphics.drawPicture(0, this.windowHeight, 2022);
      this.extendChatStrip();
      this.gameGraphics.drawImage(this.aGraphics936, 0, 0);
   }

   private final void drawAbuseWindow1() {
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      this.abuseSelectedType = 0;
      int i = 135 + oy;

      for (int j = 0; j < 12; j++) {
         if (super.mouseX > 66 + ox && super.mouseX < 446 + ox && super.mouseY >= i - 12 && super.mouseY < i + 3) {
            this.abuseSelectedType = j + 1;
         }

         i += 14;
      }

      if (this.mouseButtonClick != 0 && this.abuseSelectedType != 0) {
         this.mouseButtonClick = 0;
         this.showAbuseWindow = 2;
         super.inputText = "";
         super.enteredText = "";
      } else {
         i += 15;
         if (this.mouseButtonClick != 0) {
            this.mouseButtonClick = 0;
            if (super.mouseX < 56 + ox || super.mouseY < 35 + oy || super.mouseX > 456 + ox || super.mouseY > 325 + oy) {
               this.showAbuseWindow = 0;
               return;
            }

            if (super.mouseX > 66 + ox && super.mouseX < 446 + ox && super.mouseY >= i - 15 && super.mouseY < i + 5) {
               this.showAbuseWindow = 0;
               return;
            }
         }

         this.gameGraphics.drawBox(56 + ox, 35 + oy, 400, 290, 0);
         this.gameGraphics.drawBoxEdge(56 + ox, 35 + oy, 400, 290, 16777215);
         int var4 = 50 + oy;
         this.gameGraphics.drawText("This form is for reporting players who are breaking our rules", 256 + ox, var4, 1, 16777215);
         var4 += 15;
         this.gameGraphics.drawText("Using it sends a snapshot of the last 60 secs of activity to us", 256 + ox, var4, 1, 16777215);
         var4 += 15;
         this.gameGraphics.drawText("If you misuse this form, you will be banned.", 256 + ox, var4, 1, 16744448);
         var4 += 15;
         var4 += 10;
         this.gameGraphics.drawText("First indicate which of our 12 rules is being broken. For a detailed", 256 + ox, var4, 1, 16776960);
         var4 += 15;
         this.gameGraphics.drawText("explanation of each rule please read the manual on our website.", 256 + ox, var4, 1, 16776960);
         var4 += 15;
         int k;
         if (this.abuseSelectedType == 1) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("1: Offensive language", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 2) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("2: Item scamming", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 3) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("3: Password scamming", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 4) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("4: Bug abuse", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 5) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("5: RSCD Community Client Staff impersonation", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 6) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("6: Account sharing/trading", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 7) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("7: Macroing", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 8) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("8: Mutiple logging in", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 9) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("9: Encouraging others to break rules", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 10) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("10: Misuse of customer support", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 11) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("11: Advertising / website", 256 + ox, var4, 1, k);
         var4 += 14;
         if (this.abuseSelectedType == 12) {
            this.gameGraphics.drawBoxEdge(66 + ox, var4 - 12, 380, 15, 16777215);
            k = 16744448;
         } else {
            k = 16777215;
         }

         this.gameGraphics.drawText("12: Real world item trading", 256 + ox, var4, 1, k);
         var4 += 14;
         var4 += 15;
         k = 16777215;
         if (super.mouseX > 196 + ox && super.mouseX < 316 + ox && super.mouseY > var4 - 15 && super.mouseY < var4 + 5) {
            k = 16776960;
         }

         this.gameGraphics.drawText("Click here to cancel", 256 + ox, var4, 1, k);
      }
   }

   private final void autoRotateCamera() {
      if ((this.cameraAutoAngle & 1) != 1 || !this.enginePlayerVisible(this.cameraAutoAngle)) {
         if ((this.cameraAutoAngle & 1) == 0 && this.enginePlayerVisible(this.cameraAutoAngle)) {
            if (this.enginePlayerVisible(this.cameraAutoAngle + 1 & 7)) {
               this.cameraAutoAngle = this.cameraAutoAngle + 1 & 7;
            } else {
               if (this.enginePlayerVisible(this.cameraAutoAngle + 7 & 7)) {
                  this.cameraAutoAngle = this.cameraAutoAngle + 7 & 7;
               }
            }
         } else {
            int[] ai = new int[]{1, -1, 2, -2, 3, -3, 4};

            for (int i = 0; i < 7; i++) {
               if (this.enginePlayerVisible(this.cameraAutoAngle + ai[i] + 8 & 7)) {
                  this.cameraAutoAngle = this.cameraAutoAngle + ai[i] + 8 & 7;
                  break;
               }
            }

            if ((this.cameraAutoAngle & 1) == 0 && this.enginePlayerVisible(this.cameraAutoAngle)) {
               if (this.enginePlayerVisible(this.cameraAutoAngle + 1 & 7)) {
                  this.cameraAutoAngle = this.cameraAutoAngle + 1 & 7;
                  return;
               }

               if (this.enginePlayerVisible(this.cameraAutoAngle + 7 & 7)) {
                  this.cameraAutoAngle = this.cameraAutoAngle + 7 & 7;
               }
            }
         }
      }
   }

   @Override
   public final Graphics getGraphics() {
      return GameWindow.gameFrame != null ? GameWindow.gameFrame.getGraphics() : super.getGraphics();
   }

   final void drawPlayer(int i, int j, int k, int l, int i1, int j1, int k1) {
      Mob mob = this.playerArray[i1];
      if (mob.colourBottomType != 255) {
         int l1 = mob.currentSprite + (this.cameraRotation + 16) / 32 & 7;
         boolean flag = false;
         int i2 = l1;
         if (l1 == 5) {
            i2 = 3;
            flag = true;
         } else if (l1 == 6) {
            i2 = 2;
            flag = true;
         } else if (l1 == 7) {
            i2 = 1;
            flag = true;
         }

         int j2 = i2 * 3 + this.walkModel[mob.stepCount / 6 % 4];
         if (mob.currentSprite == 8) {
            i2 = 5;
            l1 = 2;
            flag = false;
            i -= 5 * k1 / 100;
            j2 = i2 * 3 + this.npcCombatModelArray1[this.loginTimer / 5 % 8];
         } else if (mob.currentSprite == 9) {
            i2 = 5;
            l1 = 2;
            flag = true;
            i += 5 * k1 / 100;
            j2 = i2 * 3 + this.npcCombatModelArray2[this.loginTimer / 6 % 8];
         }

         for (int k2 = 0; k2 < 12; k2++) {
            int l2 = this.npcAnimationArray[l1][k2];
            int l3 = mob.animationCount[l2] - 1;
            if (l3 >= 0) {
               int k4 = 0;
               int i5 = 0;
               int j5 = j2;
               if (flag && i2 >= 1 && i2 <= 3) {
                  if (EntityHandler.getAnimationDef(l3).hasF()) {
                     j5 = j2 + 15;
                  } else if (l2 == 4 && i2 == 1) {
                     k4 = -22;
                     i5 = -3;
                     j5 = i2 * 3 + this.walkModel[(2 + mob.stepCount / 6) % 4];
                  } else if (l2 == 4 && i2 == 2) {
                     k4 = 0;
                     i5 = -8;
                     j5 = i2 * 3 + this.walkModel[(2 + mob.stepCount / 6) % 4];
                  } else if (l2 == 4 && i2 == 3) {
                     k4 = 26;
                     i5 = -5;
                     j5 = i2 * 3 + this.walkModel[(2 + mob.stepCount / 6) % 4];
                  } else if (l2 == 3 && i2 == 1) {
                     k4 = 22;
                     i5 = 3;
                     j5 = i2 * 3 + this.walkModel[(2 + mob.stepCount / 6) % 4];
                  } else if (l2 == 3 && i2 == 2) {
                     k4 = 0;
                     i5 = 8;
                     j5 = i2 * 3 + this.walkModel[(2 + mob.stepCount / 6) % 4];
                  } else if (l2 == 3 && i2 == 3) {
                     k4 = -26;
                     i5 = 5;
                     j5 = i2 * 3 + this.walkModel[(2 + mob.stepCount / 6) % 4];
                  }
               }

               if (i2 != 5 || EntityHandler.getAnimationDef(l3).hasA()) {
                  int k5 = j5 + EntityHandler.getAnimationDef(l3).getNumber();
                  k4 = k4 * k / this.gameGraphics.sprites[k5].getSomething1();
                  i5 = i5 * l / this.gameGraphics.sprites[k5].getSomething2();
                  int l5 = k
                     * this.gameGraphics.sprites[k5].getSomething1()
                     / this.gameGraphics.sprites[EntityHandler.getAnimationDef(l3).getNumber()].getSomething1();
                  k4 -= (l5 - k) / 2;
                  int colour = EntityHandler.getAnimationDef(l3).getCharColour();
                  int skinColour = this.characterSkinColours[mob.colourSkinType];
                  if (colour == 1) {
                     colour = this.characterHairColours[mob.colourHairType];
                  } else if (colour == 2) {
                     colour = this.characterTopBottomColours[mob.colourTopType];
                  } else if (colour == 3) {
                     colour = this.characterTopBottomColours[mob.colourBottomType];
                  }

                  this.gameGraphics.spriteClip4(i + k4, j + i5, l5, l, k5, colour, skinColour, j1, flag);
               }
            }
         }

         if (mob.lastMessageTimeout > 0) {
            this.mobMessagesWidth[this.mobMessageCount] = this.gameGraphics.textWidth(mob.lastMessage, 1) / 2;
            if (this.mobMessagesWidth[this.mobMessageCount] > 150) {
               this.mobMessagesWidth[this.mobMessageCount] = 150;
            }

            this.mobMessagesHeight[this.mobMessageCount] = this.gameGraphics.textWidth(mob.lastMessage, 1) / 300 * this.gameGraphics.messageFontHeight(1);
            this.mobMessagesX[this.mobMessageCount] = i + k / 2;
            this.mobMessagesY[this.mobMessageCount] = j;
            this.mobMessages[this.mobMessageCount++] = mob.lastMessage;
         }

         if (mob.bubbleTimeout > 0) {
            this.bubbleX[this.bubbleCount] = i + k / 2;
            this.bubbleY[this.bubbleCount] = j;
            this.bubbleScale[this.bubbleCount] = k1;
            this.bubbleItemId[this.bubbleCount++] = mob.bubbleItem;
         }

         boolean showBar = this.healthBarShows(mob);
         if (mob.currentSprite == 8 || mob.currentSprite == 9 || mob.combatTimer != 0 || showBar) {
            if (showBar) {
               int i3 = i;
               if (mob.currentSprite == 8) {
                  i3 = i - 20 * k1 / 100;
               } else if (mob.currentSprite == 9) {
                  i3 = i + 20 * k1 / 100;
               }

               int i4 = this.healthBarWidth(mob);
               this.healthBarX[this.healthBarCount] = i3 + k / 2;
               this.healthBarY[this.healthBarCount] = j;
               this.healthBarValue[this.healthBarCount++] = i4;
            }

            if (mob.combatTimer > 150) {
               int j3 = i;
               if (mob.currentSprite == 8) {
                  j3 = i - 10 * k1 / 100;
               } else if (mob.currentSprite == 9) {
                  j3 = i + 10 * k1 / 100;
               }

               this.gameGraphics.drawPicture(j3 + k / 2 - 12, j + l / 2 - 12, 2011);
               this.gameGraphics.drawText(String.valueOf(mob.damageTaken), j3 + k / 2 - 1, j + l / 2 + 5, 3, 16777215);
            }
         }

         if (mob.skullVisible == 1 && mob.bubbleTimeout == 0) {
            int k3 = j1 + i + k / 2;
            if (mob.currentSprite == 8) {
               k3 -= 20 * k1 / 100;
            } else if (mob.currentSprite == 9) {
               k3 += 20 * k1 / 100;
            }

            int j4 = 16 * k1 / 100;
            int l4 = 16 * k1 / 100;
            this.gameGraphics.spriteClip1(k3 - j4 / 2, j - l4 / 2 - 10 * k1 / 100, j4, l4, 2013);
         }
      }
   }

   private final void loadConfigFilter() {
      EntityHandler.load();
   }

   private final void loadModels() {
      this.drawLoadingBarText(68, "Unpacking Models...");
      String[] modelNames = new String[]{
         "torcha2",
         "torcha3",
         "torcha4",
         "skulltorcha2",
         "skulltorcha3",
         "skulltorcha4",
         "firea2",
         "firea3",
         "fireplacea2",
         "fireplacea3",
         "firespell2",
         "firespell3",
         "lightning2",
         "lightning3",
         "clawspell2",
         "clawspell3",
         "clawspell4",
         "clawspell5",
         "spellcharge2",
         "spellcharge3",
         "essence portal2",
         "essence portal3"
      };

      for (String name : modelNames) {
         EntityHandler.storeModel(name);
      }

      byte[] models = this.load("models36.jag");
      if (models == null) {
         this.lastLoadedNull = true;
      } else {
         for (int j = 0; j < EntityHandler.getModelCount(); j++) {
            int k = DataOperations.getEntryOffset(EntityHandler.getModelName(j) + ".ob3", models);
            if (k == 0) {
               this.gameDataModels[j] = new Model(1, 1);
            } else {
               this.gameDataModels[j] = new Model(models, k, true);
            }

            // isGiantCrystal is really "draw this whole model 50/50 translucent";
            // the essence mine portal borrows it so its light show blends with
            // whatever stands behind it instead of rendering as solid geometry.
            this.gameDataModels[j].isGiantCrystal = EntityHandler.getModelName(j).equals("giantcrystal")
               || EntityHandler.getModelName(j).startsWith("essence portal");
         }
      }
   }

   @Override
   protected final void handleMouseDown(int button, int x, int y) {
      this.mouseClickXArray[this.mouseClickArrayOffset] = x;
      this.mouseClickYArray[this.mouseClickArrayOffset] = y;
      this.mouseClickArrayOffset = this.mouseClickArrayOffset + 1 & 8191;

      for (int l = 10; l < 4000; l++) {
         int i1 = this.mouseClickArrayOffset - l & 8191;
         if (this.mouseClickXArray[i1] == x && this.mouseClickYArray[i1] == y) {
            boolean flag = false;

            for (int j1 = 1; j1 < l; j1++) {
               int k1 = this.mouseClickArrayOffset - j1 & 8191;
               int l1 = i1 - j1 & 8191;
               if (this.mouseClickXArray[l1] != x || this.mouseClickYArray[l1] != y) {
                  flag = true;
               }

               if (this.mouseClickXArray[k1] != this.mouseClickXArray[l1] || this.mouseClickYArray[k1] != this.mouseClickYArray[l1]) {
                  break;
               }

               if (j1 == l - 1 && flag && this.lastWalkTimeout == 0 && this.logoutTimeout == 0) {
                  this.logout();
                  return;
               }
            }
         }
      }
   }

   /*
    * The wheel, dispatched to whichever scrollable the pointer is currently
    * over. Vanilla had no wheel to give any of these -- see GameFrame's own
    * note on why one exists at all -- so every target below is this client's,
    * not Jagex's, and each is tried in the same order its own input already
    * takes the mouse away from whatever is behind it.
    */
   @Override
   protected final void handleMouseWheel(int rotation, int x, int y) {
      if (this.worldMapPanel != null && this.worldMapPanel.isOpen()) {
         this.worldMapPanel.wheel(rotation, x, y);
         return;
      }

      if (this.scriptPanel != null && this.scriptPanel.isOpen()) {
         this.scriptPanel.wheel(rotation, x, y);
         return;
      }

      if (this.calculatorPanel != null && this.calculatorPanel.isOpen()) {
         this.calculatorPanel.wheel(rotation, x, y);
         return;
      }

      if (this.loginScreenNumber == 4) {
         this.worldsPanel().wheel(rotation, x, y);
         return;
      }

      if (this.loggedIn != 1) {
         return;
      }

      /* The message tabs' scrollback -- the same box the click-guard just
         above drawGameMenu protects, tried under the same panelOwnsScreen()
         condition it uses. Tab 0 has no scrollbar; see the comment at its
         click-guard for why. */
      if (this.messagesTab > 0 && !this.panelOwnsScreen()) {
         int handle = this.messagesTab == 1
            ? this.messagesHandleType2
            : this.messagesTab == 2 ? this.messagesHandleType5 : this.messagesHandleType6;
         if (this.scrollMenuList(this.gameMenu, handle, x, y, rotation)) {
            return;
         }
      }

      /* The three sidebar lists that share Menu's scrollable type-9 widget:
         friends/ignore, the quest list, and the magic/prayer book. Only one
         can be showing at a time (mouseOverMenu), so there is no ambiguity
         about which the wheel belongs to. */
      if (this.mouseOverMenu == 5) {
         this.scrollMenuList(this.friendsMenu, this.friendsMenuHandle, x, y, rotation);
      } else if (this.mouseOverMenu == 3) {
         this.scrollMenuList(this.questMenu, this.questMenuHandle, x, y, rotation);
      } else if (this.mouseOverMenu == 4) {
         this.scrollMenuList(this.spellMenu, this.spellMenuHandle, x, y, rotation);
      }
   }

   /*
    * Scrolls a Menu list by three rows per notch if (x, y) lands on it,
    * through Menu.scrollList -- which clamps, unlike writing menuListScrollOffset
    * directly would (see its own comment for why that matters for a type-9
    * list). Returns whether the point was actually on it, so callers trying
    * more than one list in sequence know to stop.
    */
   private final boolean scrollMenuList(Menu menu, int handle, int x, int y, int rotation) {
      if (menu == null
         || x < menu.menuObjectX[handle]
         || x > menu.menuObjectX[handle] + menu.menuObjectWidth[handle]
         || y < menu.menuObjectY[handle]
         || y > menu.menuObjectY[handle] + menu.menuObjectHeight[handle]) {
         return false;
      }

      menu.scrollList(handle, rotation * 3);
      return true;
   }

   @Override
   protected final void renderFrame() {
      if (this.lastLoadedNull) {
         Graphics g = this.getGraphics();
         g.setColor(Color.black);
         g.fillRect(0, 0, GameWindow.canvasWidth(), GameWindow.canvasHeight());
         g.setFont(GameImage.helvetica(1, 16));
         g.setColor(Color.yellow);
         int i = 35;
         /* The only two places the name is abbreviated, because these are the
            only two that run out of the window. Two lines where vanilla had
            one: even as "RSCD Comm. Client" this is 551px at bold 16 from
            x=30, against 482px of room, measured in Helvetica. */
         g.drawString("Sorry, an error has occured", 30, i);
         i += 25;
         g.drawString("whilst loading RSCD Comm. Client", 30, i);
         i += 50;
         g.setColor(Color.white);
         g.drawString("To fix this try the following (in order):", 30, i);
         i += 50;
         g.setColor(Color.white);
         g.setFont(GameImage.helvetica(1, 12));
         g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, i);
         i += 30;
         g.drawString("2: Try clearing your web-browsers cache from tools->internet options", 30, i);
         i += 30;
         g.drawString("3: Try using a different game-world", 30, i);
         i += 30;
         g.drawString("4: Try rebooting your computer", 30, i);
         i += 30;
         g.drawString("5: Try selecting a different version of Java from the play-game menu", 30, i);
         this.changeThreadSleepModifier(1);
      } else if (this.memoryError) {
         Graphics g2 = this.getGraphics();
         g2.setColor(Color.black);
         g2.fillRect(0, 0, GameWindow.canvasWidth(), GameWindow.canvasHeight());
         g2.setFont(GameImage.helvetica(1, 20));
         g2.setColor(Color.white);
         g2.drawString("Error - out of memory!", 50, 50);
         g2.drawString("Close ALL unnecessary programs", 50, 100);
         g2.drawString("and windows before loading the game", 50, 150);
         /* Likewise abbreviated and likewise split: 611px at bold 20 against
            462px of room from x=50 as vanilla's single line. */
         g2.drawString("RSCD Comm. Client", 50, 200);
         g2.drawString("needs about 100mb of spare RAM", 50, 230);
         this.changeThreadSleepModifier(1);
      } else {
         try {
            if (this.loggedIn == 1) {
               this.gameGraphics.drawStringShadows = true;
               this.drawGame();
            } else {
               this.gameGraphics.drawStringShadows = false;
               this.drawLoginScreen();
            }

            // After the frame is complete, so a recording shows what was on screen.
            this.captureFrame();
         } catch (OutOfMemoryError var3) {
            this.garbageCollect();
            this.memoryError = true;
         }
      }
   }

   final void walkToObject(int x, int y, int id, int type) {
      int i1;
      int j1;
      if (id != 0 && id != 4) {
         j1 = EntityHandler.getObjectDef(type).getWidth();
         i1 = EntityHandler.getObjectDef(type).getHeight();
      } else {
         i1 = EntityHandler.getObjectDef(type).getWidth();
         j1 = EntityHandler.getObjectDef(type).getHeight();
      }

      if (EntityHandler.getObjectDef(type).getType() != 2 && EntityHandler.getObjectDef(type).getType() != 3) {
         this.sendWalkCommand(this.sectionX, this.sectionY, x, y, x + i1 - 1, y + j1 - 1, true, true);
      } else {
         if (id == 0) {
            x--;
            i1++;
         }

         if (id == 2) {
            j1++;
         }

         if (id == 4) {
            i1++;
         }

         if (id == 6) {
            y--;
            j1++;
         }

         this.sendWalkCommand(this.sectionX, this.sectionY, x, y, x + i1 - 1, y + j1 - 1, false, true);
      }
   }

   private final void drawBankBox() {
      char c = 408;
      char c1 = 334;
      if (this.mouseOverBankPageText > 0 && this.bankItemCount <= 48) {
         this.mouseOverBankPageText = 0;
      }

      if (this.mouseOverBankPageText > 1 && this.bankItemCount <= 96) {
         this.mouseOverBankPageText = 1;
      }

      if (this.mouseOverBankPageText > 2 && this.bankItemCount <= 144) {
         this.mouseOverBankPageText = 2;
      }

      if (this.selectedBankItem >= this.bankItemCount || this.selectedBankItem < 0) {
         this.selectedBankItem = -1;
      }

      if (this.selectedBankItem != -1 && this.bankItems[this.selectedBankItem] != this.selectedBankItemType) {
         this.selectedBankItem = -1;
         this.selectedBankItemType = -2;
      }

      if (this.mouseButtonClick != 0) {
         this.mouseButtonClick = 0;
         int i = super.mouseX - (256 + this.loginOffsetX() - c / 2);
         int k = super.mouseY - (170 + this.loginOffsetY() - c1 / 2);
         if (i >= 0 && k >= 12 && i < 408 && k < 280) {
            int i1 = this.mouseOverBankPageText * 48;

            for (int l1 = 0; l1 < 6; l1++) {
               for (int j2 = 0; j2 < 8; j2++) {
                  int l6 = 7 + j2 * 49;
                  int j7 = 28 + l1 * 34;
                  if (i > l6 && i < l6 + 49 && k > j7 && k < j7 + 34 && i1 < this.bankItemCount && this.bankItems[i1] != -1) {
                     this.selectedBankItemType = this.bankItems[i1];
                     this.selectedBankItem = i1;
                  }

                  i1++;
               }
            }

            i = 256 + this.loginOffsetX() - c / 2;
            k = 170 + this.loginOffsetY() - c1 / 2;
            int k2;
            if (this.selectedBankItem < 0) {
               k2 = -1;
            } else {
               k2 = this.bankItems[this.selectedBankItem];
            }

            if (k2 != -1) {
               int j1 = this.bankItemsCount[this.selectedBankItem];
               if (j1 >= 1 && super.mouseX >= i + 220 && super.mouseY >= k + 238 && super.mouseX < i + 250 && super.mouseY <= k + 249) {
                  super.streamClass.createPacket(183);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(1);
                  super.streamClass.formatPacket();
               }

               if (j1 >= 10 && super.mouseX >= i + 250 && super.mouseY >= k + 238 && super.mouseX < i + 280 && super.mouseY <= k + 249) {
                  super.streamClass.createPacket(183);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(10);
                  super.streamClass.formatPacket();
               }

               if (j1 >= 100 && super.mouseX >= i + 280 && super.mouseY >= k + 238 && super.mouseX < i + 305 && super.mouseY <= k + 249) {
                  super.streamClass.createPacket(183);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(100);
                  super.streamClass.formatPacket();
               }

               if (j1 >= 1000 && super.mouseX >= i + 305 && super.mouseY >= k + 238 && super.mouseX < i + 335 && super.mouseY <= k + 249) {
                  super.streamClass.createPacket(183);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(1000);
                  super.streamClass.formatPacket();
               }

               if (j1 >= 10000 && super.mouseX >= i + 335 && super.mouseY >= k + 238 && super.mouseX < i + 368 && super.mouseY <= k + 249) {
                  /* Withdraw X. This asked with a Swing JOptionPane, which is
                     the one way you can ask a question from the client thread
                     and get an answer back on the same call -- the dialog runs
                     its own event pump. The cost was a desktop window over the
                     game: it can open behind it or on another monitor, and it
                     cannot appear in an F12 screenshot, because that is drawn
                     from the client's own pixel buffer.

                     ScriptPrompt draws in the game view instead, so the answer
                     cannot arrive on this call and everything that followed the
                     dialog moves into the continuation. SalvageInput and the
                     "0 means do nothing" contract are unchanged: Esc gives no
                     callback at all, which is what dismissing the dialog did
                     when showInputDialog returned null.

                     The ceiling is the count as it stood when the button was
                     clicked, because there is no lookup from item id back to a
                     bank slot -- and it only ever trims the request, which the
                     server does again on its own side. */
                  final int withdrawItem = k2;
                  final int withdrawHeld = j1;
                  this.scriptPrompt.askAsync("Withdraw how many?", new ScriptPrompt.Answer() {
                     public void got(String text) {
                        int amount = SalvageInput(text);
                        if (amount <= 0) {
                           return;
                        }

                        if (amount > withdrawHeld) {
                           amount = withdrawHeld;
                        }

                        mudclient.this.streamClass.createPacket(183);
                        mudclient.this.streamClass.add2ByteInt(withdrawItem);
                        mudclient.this.streamClass.add4ByteInt(amount);
                        mudclient.this.streamClass.formatPacket();
                     }
                  });
                  return;
               }

               if (super.mouseX >= i + 370 && super.mouseY >= k + 238 && super.mouseX < i + 400 && super.mouseY <= k + 249) {
                  super.streamClass.createPacket(183);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(j1);
                  super.streamClass.formatPacket();
               }

               if (this.inventoryCount(k2) >= 1 && super.mouseX >= i + 220 && super.mouseY >= k + 263 && super.mouseX < i + 250 && super.mouseY <= k + 274) {
                  super.streamClass.createPacket(198);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(1);
                  super.streamClass.formatPacket();
               }

               if (this.inventoryCount(k2) >= 10 && super.mouseX >= i + 250 && super.mouseY >= k + 263 && super.mouseX < i + 280 && super.mouseY <= k + 274) {
                  super.streamClass.createPacket(198);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(10);
                  super.streamClass.formatPacket();
               }

               if (this.inventoryCount(k2) >= 100 && super.mouseX >= i + 280 && super.mouseY >= k + 263 && super.mouseX < i + 305 && super.mouseY <= k + 274) {
                  super.streamClass.createPacket(198);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(100);
                  super.streamClass.formatPacket();
               }

               if (this.inventoryCount(k2) >= 1000 && super.mouseX >= i + 305 && super.mouseY >= k + 263 && super.mouseX < i + 335 && super.mouseY <= k + 274) {
                  super.streamClass.createPacket(198);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(1000);
                  super.streamClass.formatPacket();
               }

               if (this.inventoryCount(k2) >= 1 && super.mouseX >= i + 335 && super.mouseY >= k + 263 && super.mouseX < i + 368 && super.mouseY <= k + 274) {
                  /* Deposit X -- see Withdraw X above. The clamp reads the
                     inventory again inside the continuation rather than
                     capturing the count now, because frames pass between the
                     question and the answer and what is carried can change. */
                  final int depositItem = k2;
                  this.scriptPrompt.askAsync("Deposit how many?", new ScriptPrompt.Answer() {
                     public void got(String text) {
                        int amount = SalvageInput(text);
                        if (amount <= 0) {
                           return;
                        }

                        int held = mudclient.this.inventoryCount(depositItem);
                        if (amount > held) {
                           amount = held;
                        }

                        if (amount <= 0) {
                           return;
                        }

                        mudclient.this.streamClass.createPacket(198);
                        mudclient.this.streamClass.add2ByteInt(depositItem);
                        mudclient.this.streamClass.add4ByteInt(amount);
                        mudclient.this.streamClass.formatPacket();
                     }
                  });
                  return;
               }

               if (super.mouseX >= i + 370 && super.mouseY >= k + 263 && super.mouseX < i + 400 && super.mouseY <= k + 274) {
                  super.streamClass.createPacket(198);
                  super.streamClass.add2ByteInt(k2);
                  super.streamClass.add4ByteInt(this.inventoryCount(k2));
                  super.streamClass.formatPacket();
               }
            }
         } else if (this.bankItemCount > 48 && i >= 50 && i <= 115 && k <= 12) {
            this.mouseOverBankPageText = 0;
         } else if (this.bankItemCount > 48 && i >= 115 && i <= 180 && k <= 12) {
            this.mouseOverBankPageText = 1;
         } else if (this.bankItemCount > 96 && i >= 180 && i <= 245 && k <= 12) {
            this.mouseOverBankPageText = 2;
         } else {
            if (this.bankItemCount <= 144 || i < 245 || i > 310 || k > 12) {
               super.streamClass.createPacket(48);
               super.streamClass.formatPacket();
               this.showBank = false;
               return;
            }

            this.mouseOverBankPageText = 3;
         }
      }

      int j = 256 + this.loginOffsetX() - c / 2;
      int l = 170 + this.loginOffsetY() - c1 / 2;
      this.gameGraphics.drawBox(j, l, 408, 12, 192);
      int k1 = 10000536;
      this.gameGraphics.drawBoxAlpha(j, l + 12, 408, 17, k1, 160);
      this.gameGraphics.drawBoxAlpha(j, l + 29, 8, 204, k1, 160);
      this.gameGraphics.drawBoxAlpha(j + 399, l + 29, 9, 204, k1, 160);
      this.gameGraphics.drawBoxAlpha(j, l + 233, 408, 47, k1, 160);
      this.gameGraphics.drawString("Bank", j + 1, l + 10, 1, 16777215);
      int i2 = 50;
      if (this.bankItemCount > 48) {
         int l2 = 16777215;
         if (this.mouseOverBankPageText == 0) {
            l2 = 16711680;
         } else if (super.mouseX > j + i2 && super.mouseY >= l && super.mouseX < j + i2 + 65 && super.mouseY < l + 12) {
            l2 = 16776960;
         }

         this.gameGraphics.drawString("<page 1>", j + i2, l + 10, 1, l2);
         i2 += 65;
         l2 = 16777215;
         if (this.mouseOverBankPageText == 1) {
            l2 = 16711680;
         } else if (super.mouseX > j + i2 && super.mouseY >= l && super.mouseX < j + i2 + 65 && super.mouseY < l + 12) {
            l2 = 16776960;
         }

         this.gameGraphics.drawString("<page 2>", j + i2, l + 10, 1, l2);
         i2 += 65;
      }

      if (this.bankItemCount > 96) {
         int i3 = 16777215;
         if (this.mouseOverBankPageText == 2) {
            i3 = 16711680;
         } else if (super.mouseX > j + i2 && super.mouseY >= l && super.mouseX < j + i2 + 65 && super.mouseY < l + 12) {
            i3 = 16776960;
         }

         this.gameGraphics.drawString("<page 3>", j + i2, l + 10, 1, i3);
         i2 += 65;
      }

      if (this.bankItemCount > 144) {
         int j3 = 16777215;
         if (this.mouseOverBankPageText == 3) {
            j3 = 16711680;
         } else if (super.mouseX > j + i2 && super.mouseY >= l && super.mouseX < j + i2 + 65 && super.mouseY < l + 12) {
            j3 = 16776960;
         }

         this.gameGraphics.drawString("<page 4>", j + i2, l + 10, 1, j3);
         i2 += 65;
      }

      int k3 = 16777215;
      if (super.mouseX > j + 320 && super.mouseY >= l && super.mouseX < j + 408 && super.mouseY < l + 12) {
         k3 = 16711680;
      }

      this.gameGraphics.drawBoxTextRight("Close window", j + 406, l + 10, 1, k3);
      this.gameGraphics.drawString("Number in bank in green", j + 7, l + 24, 1, 65280);
      this.gameGraphics.drawString("Number held in blue", j + 289, l + 24, 1, 65535);
      int i7 = 13684944;
      int k7 = this.mouseOverBankPageText * 48;

      for (int i8 = 0; i8 < 6; i8++) {
         for (int j8 = 0; j8 < 8; j8++) {
            int l8 = j + 7 + j8 * 49;
            int i9 = l + 28 + i8 * 34;
            if (this.selectedBankItem == k7) {
               this.gameGraphics.drawBoxAlpha(l8, i9, 49, 34, 16711680, 160);
            } else {
               this.gameGraphics.drawBoxAlpha(l8, i9, 49, 34, i7, 160);
            }

            this.gameGraphics.drawBoxEdge(l8, i9, 50, 35, 0);
            if (k7 < this.bankItemCount && this.bankItems[k7] != -1) {
               this.gameGraphics
                  .spriteClip4(
                     l8,
                     i9,
                     48,
                     32,
                     2150 + EntityHandler.getItemDef(this.bankItems[k7]).getSprite(),
                     EntityHandler.getItemDef(this.bankItems[k7]).getPictureMask(),
                     0,
                     0,
                     false
                  );
               this.gameGraphics.drawString(String.valueOf(this.bankItemsCount[k7]), l8 + 1, i9 + 10, 1, 65280);
               this.gameGraphics.drawBoxTextRight(String.valueOf(this.inventoryCount(this.bankItems[k7])), l8 + 47, i9 + 29, 1, 65535);
            }

            k7++;
         }
      }

      this.gameGraphics.drawLineX(j + 5, l + 256, 398, 0);
      if (this.selectedBankItem == -1) {
         this.gameGraphics.drawText("Select an object to withdraw or deposit", j + 204, l + 248, 3, 16776960);
      } else {
         int k8;
         if (this.selectedBankItem < 0) {
            k8 = -1;
         } else {
            k8 = this.bankItems[this.selectedBankItem];
         }

         if (k8 != -1) {
            int l7 = this.bankItemsCount[this.selectedBankItem];
            if (l7 > 0) {
               this.gameGraphics.drawString("Withdraw " + EntityHandler.getItemDef(k8).getName(), j + 2, l + 248, 1, 16777215);
               int l3 = 16777215;
               if (super.mouseX >= j + 220 && super.mouseY >= l + 238 && super.mouseX < j + 250 && super.mouseY <= l + 249) {
                  l3 = 16711680;
               }

               this.gameGraphics.drawString("One", j + 222, l + 248, 1, l3);
               if (l7 >= 10) {
                  int i4 = 16777215;
                  if (super.mouseX >= j + 250 && super.mouseY >= l + 238 && super.mouseX < j + 280 && super.mouseY <= l + 249) {
                     i4 = 16711680;
                  }

                  this.gameGraphics.drawString("10", j + 252, l + 248, 1, i4);
               }

               if (l7 >= 100) {
                  int j4 = 16777215;
                  if (super.mouseX >= j + 280 && super.mouseY >= l + 238 && super.mouseX < j + 305 && super.mouseY <= l + 249) {
                     j4 = 16711680;
                  }

                  this.gameGraphics.drawString(" 100", j + 282, l + 248, 1, j4);
               }

               if (l7 >= 1000) {
                  int k4 = 16777215;
                  if (super.mouseX >= j + 305 && super.mouseY >= l + 238 && super.mouseX < j + 335 && super.mouseY <= l + 249) {
                     k4 = 16711680;
                  }

                  this.gameGraphics.drawString("1k", j + 307, l + 248, 1, k4);
               }

               if (l7 >= 1) {
                  int l4 = 16777215;
                  if (super.mouseX >= j + 335 && super.mouseY >= l + 238 && super.mouseX < j + 368 && super.mouseY <= l + 249) {
                     l4 = 16711680;
                  }

                  this.gameGraphics.drawString("  X", j + 337, l + 248, 1, l4);
               }

               int i5 = 16777215;
               if (super.mouseX >= j + 370 && super.mouseY >= l + 238 && super.mouseX < j + 400 && super.mouseY <= l + 249) {
                  i5 = 16711680;
               }

               this.gameGraphics.drawString("All", j + 370, l + 248, 1, i5);
            }

            if (this.inventoryCount(k8) > 0) {
               this.gameGraphics.drawString("Deposit " + EntityHandler.getItemDef(k8).getName(), j + 2, l + 273, 1, 16777215);
               int j5 = 16777215;
               if (super.mouseX >= j + 220 && super.mouseY >= l + 263 && super.mouseX < j + 250 && super.mouseY <= l + 274) {
                  j5 = 16711680;
               }

               this.gameGraphics.drawString("One", j + 222, l + 273, 1, j5);
               if (this.inventoryCount(k8) >= 10) {
                  int k5 = 16777215;
                  if (super.mouseX >= j + 250 && super.mouseY >= l + 263 && super.mouseX < j + 280 && super.mouseY <= l + 274) {
                     k5 = 16711680;
                  }

                  this.gameGraphics.drawString("10", j + 252, l + 273, 1, k5);
               }

               if (this.inventoryCount(k8) >= 100) {
                  int l5 = 16777215;
                  if (super.mouseX >= j + 280 && super.mouseY >= l + 263 && super.mouseX < j + 305 && super.mouseY <= l + 274) {
                     l5 = 16711680;
                  }

                  this.gameGraphics.drawString(" 100", j + 282, l + 273, 1, l5);
               }

               if (this.inventoryCount(k8) >= 1000) {
                  int i6 = 16777215;
                  if (super.mouseX >= j + 305 && super.mouseY >= l + 263 && super.mouseX < j + 335 && super.mouseY <= l + 274) {
                     i6 = 16711680;
                  }

                  this.gameGraphics.drawString("1k", j + 307, l + 273, 1, i6);
               }

               if (this.inventoryCount(k8) >= 1) {
                  int j6 = 16777215;
                  if (super.mouseX >= j + 335 && super.mouseY >= l + 263 && super.mouseX < j + 368 && super.mouseY <= l + 274) {
                     j6 = 16711680;
                  }

                  this.gameGraphics.drawString("  X", j + 337, l + 273, 1, j6);
               }

               int k6 = 16777215;
               if (super.mouseX >= j + 370 && super.mouseY >= l + 263 && super.mouseX < j + 400 && super.mouseY <= l + 274) {
                  k6 = 16711680;
               }

               this.gameGraphics.drawString("All", j + 370, l + 273, 1, k6);
            }
         }
      }
   }

   private final void drawLoggingOutBox() {
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      this.gameGraphics.drawBox(126 + ox, 137 + oy, 260, 60, 0);
      this.gameGraphics.drawBoxEdge(126 + ox, 137 + oy, 260, 60, 16777215);
      this.gameGraphics.drawText("Logging out...", 256 + ox, 173 + oy, 5, 16777215);
   }

   private final void drawInventoryMenu(boolean flag) {
      int i = this.gameGraphics.menuDefaultWidth - 248;
      this.gameGraphics.drawPicture(i, 3, 2001);

      for (int j = 0; j < this.inventoryMaxSlots; j++) {
         int k = i + j % 5 * 49;
         int i1 = 36 + j / 5 * 34;
         if (j < this.inventoryCount && this.wearing[j] == 1) {
            this.gameGraphics.drawBoxAlpha(k, i1, 49, 34, 16711680, 128);
         } else {
            this.gameGraphics.drawBoxAlpha(k, i1, 49, 34, GameImage.convertRGBToLong(181, 181, 181), 128);
         }

         if (j < this.inventoryCount) {
            this.gameGraphics
               .spriteClip4(
                  k,
                  i1,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.inventoryItems[j]).getSprite(),
                  EntityHandler.getItemDef(this.inventoryItems[j]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.inventoryItems[j]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.inventoryItemsCount[j]), k + 1, i1 + 10, 1, 16776960);
            }
         }
      }

      for (int l = 1; l <= 4; l++) {
         this.gameGraphics.drawLineY(i + l * 49, 36, this.inventoryMaxSlots / 5 * 34, 0);
      }

      for (int j1 = 1; j1 <= this.inventoryMaxSlots / 5 - 1; j1++) {
         this.gameGraphics.drawLineX(i, 36 + j1 * 34, 245, 0);
      }

      if (flag) {
         i = super.mouseX - (this.gameGraphics.menuDefaultWidth - 248);
         int k1 = super.mouseY - 36;
         if (i >= 0 && k1 >= 0 && i < 248 && k1 < this.inventoryMaxSlots / 5 * 34) {
            int currentInventorySlot = i / 49 + k1 / 34 * 5;
            if (currentInventorySlot < this.inventoryCount) {
               int i2 = this.inventoryItems[currentInventorySlot];
               ItemDef itemDef = EntityHandler.getItemDef(i2);
               if (this.selectedSpell >= 0) {
                  if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 3) {
                     this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on";
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 600;
                     this.menuActionType[this.menuLength] = currentInventorySlot;
                     this.menuActionVariable[this.menuLength] = this.selectedSpell;
                     this.menuLength++;
                     return;
                  }
               } else {
                  if (this.selectedItem >= 0) {
                     this.menuText1[this.menuLength] = "Use " + this.selectedItemName + " with";
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 610;
                     this.menuActionType[this.menuLength] = currentInventorySlot;
                     this.menuActionVariable[this.menuLength] = this.selectedItem;
                     this.menuLength++;
                     return;
                  }

                  if (this.wearing[currentInventorySlot] == 1) {
                     this.menuText1[this.menuLength] = "Remove";
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 620;
                     this.menuActionType[this.menuLength] = currentInventorySlot;
                     this.menuLength++;
                  } else if (EntityHandler.getItemDef(i2).isWieldable()) {
                     this.menuText1[this.menuLength] = "Wear";
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 630;
                     this.menuActionType[this.menuLength] = currentInventorySlot;
                     this.menuLength++;
                  }

                  if (!itemDef.getCommand().equals("")) {
                     this.menuText1[this.menuLength] = itemDef.getCommand();
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 640;
                     this.menuActionType[this.menuLength] = currentInventorySlot;
                     this.menuLength++;
                  }

                  this.menuText1[this.menuLength] = "Use";
                  this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                  this.menuID[this.menuLength] = 650;
                  this.menuActionType[this.menuLength] = currentInventorySlot;
                  this.menuLength++;
                  this.menuText1[this.menuLength] = "Drop";
                  this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                  this.menuID[this.menuLength] = 660;
                  this.menuActionType[this.menuLength] = currentInventorySlot;
                  this.menuLength++;
                  this.menuText1[this.menuLength] = "Examine";
                  this.menuText2[this.menuLength] = "@lre@" + itemDef.getName() + (this.ourPlayer.admin >= 2 ? " @or1@(" + i2 + ")" : "");
                  this.menuID[this.menuLength] = 3600;
                  this.menuActionType[this.menuLength] = i2;
                  this.menuLength++;
               }
            }
         }
      }
   }

   private final void drawChatMessageTabs() {
      this.gameGraphics.drawPicture(0, this.windowHeight - 4, 2023);
      this.extendChatStrip();
      int i = GameImage.convertRGBToLong(200, 200, 255);
      if (this.messagesTab == 0) {
         i = GameImage.convertRGBToLong(255, 200, 50);
      }

      if (this.allMessagesTabFlash % 30 > 15) {
         i = GameImage.convertRGBToLong(255, 50, 50);
      }

      this.gameGraphics.drawText("All messages", 54, this.windowHeight + 6, 0, i);
      i = GameImage.convertRGBToLong(200, 200, 255);
      if (this.messagesTab == 1) {
         i = GameImage.convertRGBToLong(255, 200, 50);
      }

      if (this.chatHistoryTabFlash % 30 > 15) {
         i = GameImage.convertRGBToLong(255, 50, 50);
      }

      this.gameGraphics.drawText("Chat history", 155, this.windowHeight + 6, 0, i);
      i = GameImage.convertRGBToLong(200, 200, 255);
      if (this.messagesTab == 2) {
         i = GameImage.convertRGBToLong(255, 200, 50);
      }

      if (this.questHistoryTabFlash % 30 > 15) {
         i = GameImage.convertRGBToLong(255, 50, 50);
      }

      this.gameGraphics.drawText("Quest history", 255, this.windowHeight + 6, 0, i);
      i = GameImage.convertRGBToLong(200, 200, 255);
      if (this.messagesTab == 3) {
         i = GameImage.convertRGBToLong(255, 200, 50);
      }

      if (this.privateHistoryTabFlash % 30 > 15) {
         i = GameImage.convertRGBToLong(255, 50, 50);
      }

      this.gameGraphics.drawText("Private history", 355, this.windowHeight + 6, 0, i);
      this.gameGraphics.drawText("Report abuse", 457, this.windowHeight + 6, 0, 16777215);
   }

   private final void drawCharacterDesignScreen() {
      this.gameGraphics.f1Toggle = false;
      this.gameGraphics.clearScreen();
      this.characterDesignMenu.drawMenu();
      int i = 140 + this.loginOffsetX();
      int j = 50 + this.loginOffsetY();
      i += 116;
      j -= 25;
      this.gameGraphics
         .spriteClip3(
            i - 32 - 55,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.character2Colour).getNumber(),
            this.characterTopBottomColours[this.characterBottomColour]
         );
      this.gameGraphics
         .spriteClip4(
            i - 32 - 55,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.characterBodyGender).getNumber(),
            this.characterTopBottomColours[this.characterTopColour],
            this.characterSkinColours[this.characterSkinColour],
            0,
            false
         );
      this.gameGraphics
         .spriteClip4(
            i - 32 - 55,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.characterHeadType).getNumber(),
            this.characterHairColours[this.characterHairColour],
            this.characterSkinColours[this.characterSkinColour],
            0,
            false
         );
      this.gameGraphics
         .spriteClip3(
            i - 32,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.character2Colour).getNumber() + 6,
            this.characterTopBottomColours[this.characterBottomColour]
         );
      this.gameGraphics
         .spriteClip4(
            i - 32,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.characterBodyGender).getNumber() + 6,
            this.characterTopBottomColours[this.characterTopColour],
            this.characterSkinColours[this.characterSkinColour],
            0,
            false
         );
      this.gameGraphics
         .spriteClip4(
            i - 32,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.characterHeadType).getNumber() + 6,
            this.characterHairColours[this.characterHairColour],
            this.characterSkinColours[this.characterSkinColour],
            0,
            false
         );
      this.gameGraphics
         .spriteClip3(
            i - 32 + 55,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.character2Colour).getNumber() + 12,
            this.characterTopBottomColours[this.characterBottomColour]
         );
      this.gameGraphics
         .spriteClip4(
            i - 32 + 55,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.characterBodyGender).getNumber() + 12,
            this.characterTopBottomColours[this.characterTopColour],
            this.characterSkinColours[this.characterSkinColour],
            0,
            false
         );
      this.gameGraphics
         .spriteClip4(
            i - 32 + 55,
            j,
            64,
            102,
            EntityHandler.getAnimationDef(this.characterHeadType).getNumber() + 12,
            this.characterHairColours[this.characterHairColour],
            this.characterSkinColours[this.characterSkinColour],
            0,
            false
         );
      this.gameGraphics.drawPicture(0, this.windowHeight, 2022);
      this.extendChatStrip();
      this.gameGraphics.drawImage(this.aGraphics936, 0, 0);
   }

   private final Mob makePlayer(int mobArrayIndex, int x, int y, int sprite) {
      if (this.mobArray[mobArrayIndex] == null) {
         this.mobArray[mobArrayIndex] = new Mob();
         this.mobArray[mobArrayIndex].serverIndex = mobArrayIndex;
         this.mobArray[mobArrayIndex].mobIntUnknown = 0;
      }

      Mob mob = this.mobArray[mobArrayIndex];
      boolean flag = false;

      for (int i1 = 0; i1 < this.lastPlayerCount; i1++) {
         if (this.lastPlayerArray[i1].serverIndex == mobArrayIndex) {
            flag = true;
            break;
         }
      }

      if (flag) {
         mob.nextSprite = sprite;
         int j1 = mob.waypointCurrent;
         if (x != mob.waypointsX[j1] || y != mob.waypointsY[j1]) {
            int var9;
            mob.waypointCurrent = var9 = (j1 + 1) % 10;
            mob.waypointsX[var9] = x;
            mob.waypointsY[var9] = y;
         }
      } else {
         mob.serverIndex = mobArrayIndex;
         mob.waypointEndSprite = 0;
         mob.waypointCurrent = 0;
         mob.waypointsX[0] = mob.currentX = x;
         mob.waypointsY[0] = mob.currentY = y;
         mob.nextSprite = mob.currentSprite = sprite;
         mob.stepCount = 0;
      }

      this.playerArray[this.playerCount++] = mob;
      return mob;
   }

   private final void drawWelcomeBox() {
      int i = 65;
      if (!this.lastLoggedInAddress.equals("0.0.0.0")) {
         i += 30;
      }

      /* The one place the script menu is advertised. This box is already ours
         rather than Jagex's -- it has said "RSCD Community Client" since the
         client was renamed -- so the extra line costs nothing in authenticity,
         and it is the only text every player is guaranteed to read, on a
         screen where nothing else is competing for their attention. It stops
         appearing the moment they open the menu; see toggleScriptPanel. */
      if (this.showScriptMenuHint) {
         i += 20;
      }

      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      int j = 167 - i / 2 + oy;
      this.gameGraphics.drawBox(56 + ox, 167 - i / 2 + oy, 400, i, 0);
      this.gameGraphics.drawBoxEdge(56 + ox, 167 - i / 2 + oy, 400, i, 16777215);
      j += 20;
      this.gameGraphics.drawText("Welcome to RSCD Community Client " + this.currentUser, 256 + ox, j, 4, 16776960);
      j += 30;
      if (this.showScriptMenuHint) {
         this.gameGraphics.drawText("Press F2 for scripts, the world map and settings", 256 + ox, j, 1, 65535);
         j += 20;
      }

      String s;
      if (this.lastLoggedInDays == 0) {
         s = "earlier today";
      } else if (this.lastLoggedInDays == 1) {
         s = "yesterday";
      } else {
         s = this.lastLoggedInDays + " days ago";
      }

      if (!this.lastLoggedInAddress.equals("0.0.0.0")) {
         this.gameGraphics.drawText("You last logged in " + s, 256 + ox, j, 1, 16777215);
         j += 15;
         this.gameGraphics.drawText("from: " + this.lastLoggedInAddress, 256 + ox, j, 1, 16777215);
         j += 15;
      }

      if (this.subscriptionLeftDays > 0) {
         this.gameGraphics.drawText("Subscription Left: " + this.subscriptionLeftDays + " days", 256 + ox, j, 1, 16777215);
         j += 15;
      }

      int l = 16777215;
      if (super.mouseY > j - 12 && super.mouseY <= j && super.mouseX > 106 + ox && super.mouseX < 406 + ox) {
         l = 16711680;
      }

      this.gameGraphics.drawText("Click here to close window", 256 + ox, j, 1, l);
      if (this.mouseButtonClick == 1) {
         if (l == 16711680) {
            this.showWelcomeBox = false;
         }

         if ((super.mouseX < 86 + ox || super.mouseX > 426 + ox) && (super.mouseY < 167 - i / 2 + oy || super.mouseY > 167 + i / 2 + oy)) {
            this.showWelcomeBox = false;
         }
      }

      this.mouseButtonClick = 0;
   }

   final void logout() {
      if (this.loggedIn != 0) {
         if (this.lastWalkTimeout > 450) {
            this.displayMessage("@gry@ You can't logout during combat!", 3, 0);
         } else if (this.lastWalkTimeout > 0) {
            this.displayMessage("@gry@ You can't logout for 10 seconds after combat", 3, 0);
         } else {
            super.streamClass.createPacket(129);
            super.streamClass.formatPacket();
            this.logoutTimeout = 1000;
         }
      }
   }

   private final void drawPlayerInfoMenu(boolean flag) {
      int i = this.gameGraphics.menuDefaultWidth - 199;
      int j = 36;
      this.gameGraphics.drawPicture(i - 49, 3, 2003);
      char c = 196;
      char c1 = 275;
      /* Two tabs, Jagex's. */
      int l;
      int k = l = GameImage.convertRGBToLong(160, 160, 160);
      if (this.playerInfoTab == 0) {
         k = GameImage.convertRGBToLong(220, 220, 220);
      } else {
         l = GameImage.convertRGBToLong(220, 220, 220);
      }

      this.gameGraphics.drawBoxAlpha(i, j, c / 2, 24, k, 128);
      this.gameGraphics.drawBoxAlpha(i + c / 2, j, c / 2, 24, l, 128);
      this.gameGraphics.drawBoxAlpha(i, j + 24, c, c1 - 24, GameImage.convertRGBToLong(220, 220, 220), 128);
      this.gameGraphics.drawLineX(i, j + 24, c, 0);
      this.gameGraphics.drawLineY(i + c / 2, j, 24, 0);
      this.gameGraphics.drawText("Stats", i + c / 4, j + 16, 4, 0);
      this.gameGraphics.drawText("Quests", i + c / 4 + c / 2, j + 16, 4, 0);
      if (this.playerInfoTab == 0) {
         int i1 = 72;
         int k1 = -1;
         this.gameGraphics.drawString("Skills", i + 5, i1, 3, 16776960);
         i1 += 13;

         /*
          * Jagex's layout, which is not the symmetrical one it looks like: the
          * right-hand column is drawn a row higher than the left, at i1 - 13,
          * so it starts level with the Skills header and finishes a row early.
          * That is what leaves the gap the Quest Points line sits in. RSCD had
          * levelled the two columns up and moved fatigue into the space, which
          * is why everything below read a row out.
          */
         for (int l1 = 0; l1 < 9; l1++) {
            int i2 = 16777215;
            if (super.mouseX > i + 3 && super.mouseY >= i1 - 11 && super.mouseY < i1 + 2 && super.mouseX < i + 90) {
               i2 = 16711680;
               k1 = l1;
            }

            this.gameGraphics.drawString(this.skillArray[l1] + ":@yel@" + this.playerStatCurrent[l1] + "/" + this.playerStatBase[l1], i + 5, i1, 1, i2);
            i2 = 16777215;
            if (super.mouseX >= i + 90 && super.mouseY >= i1 - 13 - 11 && super.mouseY < i1 - 13 + 2 && super.mouseX < i + 196) {
               i2 = 16711680;
               k1 = l1 + 9;
            }

            this.gameGraphics
               .drawString(
                  this.skillArray[l1 + 9] + ":@yel@" + this.playerStatCurrent[l1 + 9] + "/" + this.playerStatBase[l1 + 9], i + c / 2 - 5, i1 - 13, 1, i2
               );
            i1 += 13;
         }

         /*
          * The row the Quest Points line used to sit in is the right-hand
          * column's early finish -- which makes it the one place a 19th
          * skill fits without pushing everything below off the panel edge.
          * Runecrafting takes that row and Quest Points shares the fatigue
          * line instead, which cost no height at all.
          */
         int rcColor = 16777215;
         if (super.mouseX >= i + 90 && super.mouseY >= i1 - 13 - 11 && super.mouseY < i1 - 13 + 2 && super.mouseX < i + 196) {
            rcColor = 16711680;
            k1 = 18;
         }
         this.gameGraphics
            .drawString(this.skillArray[18] + ":@yel@" + this.playerStatCurrent[18] + "/" + this.playerStatBase[18], i + c / 2 - 5, i1 - 13, 1, rcColor);
         i1 += 12;
         /* Jagex sent fatigue as 0..750 and divided here; rscd-server sends it
            already as a percentage, so the number is used as it arrives. */
         this.gameGraphics.drawString("Fatigue: @yel@" + this.fatigue + "%", i + 5, i1 - 13, 1, 16777215);
         this.gameGraphics.drawString("Quest Points:@yel@" + this.questPoints, i + c / 2 - 5, i1 - 13, 1, 16777215);
         i1 += 8;
         this.gameGraphics.drawString("Equipment Status", i + 5, i1, 3, 16776960);
         i1 += 12;

         for (int j2 = 0; j2 < 3; j2++) {
            this.gameGraphics.drawString(this.equipmentStatusName[j2] + ":@yel@" + this.equipmentStatus[j2], i + 5, i1, 1, 16777215);
            this.gameGraphics.drawString(this.equipmentStatusName[j2 + 3] + ":@yel@" + this.equipmentStatus[j2 + 3], i + c / 2 + 25, i1, 1, 16777215);
            i1 += 13;
         }

         i1 += 6;
         this.gameGraphics.drawLineX(i, i1 - 15, c, 0);
         if (k1 != -1) {
            this.gameGraphics.drawString(this.skillArrayLong[k1] + " skill", i + 5, i1, 1, 16776960);
            i1 += 12;
            int k2 = this.experienceArray[0];

            for (int i3 = 0; i3 < 98; i3++) {
               if (this.playerStatExperience[k1] >= this.experienceArray[i3]) {
                  k2 = this.experienceArray[i3 + 1];
               }
            }

            this.gameGraphics.drawString("Total xp: " + this.playerStatExperience[k1], i + 5, i1, 1, 16777215);
            i1 += 12;
            this.gameGraphics.drawString("Next level at: " + k2, i + 5, i1, 1, 16777215);
         } else {
            /* Three lines, which is all the panel has room for below the
               divider. RSCD had a fourth in each branch -- Required xp here
               and Total xp below -- and the fourth hung off the bottom edge. */
            this.gameGraphics.drawString("Overall levels", i + 5, i1, 1, 16776960);
            i1 += 12;
            int skillTotal = 0;

            for (int j3 = 0; j3 < 19; j3++) {
               skillTotal += this.playerStatBase[j3];
            }

            this.gameGraphics.drawString("Skill total: " + skillTotal, i + 5, i1, 1, 16777215);
            i1 += 12;
            this.gameGraphics.drawString("Combat level: " + this.ourPlayer.level, i + 5, i1, 1, 16777215);
         }
      }

      if (this.playerInfoTab == 1) {
         /*
          * Jagex's quest tab, entry for entry: the header occupies index 0 of
          * the list and the fifty quests follow it, green when finished and red
          * when not. The list scrolls, which is why it is a Menu control rather
          * than fifty drawString calls -- fifty rows at 12px each is more than
          * twice the height of the panel.
          *
          * The middle colour is not Jagex's -- RSC's own tab was always red or
          * green, nothing else, confirmed against classic.runescape.wiki. RS2
          * added a yellow "started" state in 2004; this is that, added on top
          * rather than a change to the authentic red/green pair, which is why
          * the header still calls out only green.
          */
         this.questMenu.resetListTextCount(this.questMenuHandle);
         this.questMenu.drawMenuListText(this.questMenuHandle, 0, "@whi@Quest-list (green=completed)");

         for (int q = 0; q < QUEST_NAMES.length; q++) {
            String colour = this.questProgress[q] == 2 ? "@gre@" : this.questProgress[q] == 1 ? "@yel@" : "@red@";
            this.questMenu.drawMenuListText(this.questMenuHandle, q + 1, colour + QUEST_NAMES[q]);
         }

         this.questMenu.drawMenu();
      }

      if (flag) {
         i = super.mouseX - (this.gameGraphics.menuDefaultWidth - 199);
         j = super.mouseY - 36;
         if (i >= 0 && j >= 0 && i < c && j < c1 && this.playerInfoTab == 1) {
            /* Only while the quest tab is up, the way Jagex did it -- otherwise
               the list would swallow drags meant for the stats panel. */
            this.questMenu.updateActions(i + (this.gameGraphics.menuDefaultWidth - 199), j + 36, super.lastMouseDownButton, super.mouseDownButton);
         }

         if (i >= 0 && j >= 0 && i < c && j < c1 && j <= 24 && this.mouseButtonClick == 1) {
            /* 98 is half of the 196 the panel is wide, and it is Jagex's own
               split -- neither tab takes the pixel column on the line. */
            if (i < 98) {
               this.playerInfoTab = 0;
            }

            if (i > 98) {
               this.playerInfoTab = 1;
            }
         }
      }
   }

   private final void drawWildernessWarningBox() {
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      int i = 97 + oy;
      this.gameGraphics.drawBox(86 + ox, 77 + oy, 340, 180, 0);
      this.gameGraphics.drawBoxEdge(86 + ox, 77 + oy, 340, 180, 16777215);
      this.gameGraphics.drawText("Warning! Proceed with caution", 256 + ox, i, 4, 16711680);
      i += 26;
      this.gameGraphics.drawText("If you go much further north you will enter the", 256 + ox, i, 1, 16777215);
      i += 13;
      this.gameGraphics.drawText("wilderness. This a very dangerous area where", 256 + ox, i, 1, 16777215);
      i += 13;
      this.gameGraphics.drawText("other players can attack you!", 256 + ox, i, 1, 16777215);
      i += 22;
      this.gameGraphics.drawText("The further north you go the more dangerous it", 256 + ox, i, 1, 16777215);
      i += 13;
      this.gameGraphics.drawText("becomes, but the more treasure you will find.", 256 + ox, i, 1, 16777215);
      i += 22;
      this.gameGraphics.drawText("In the wilderness an indicator at the bottom-right", 256 + ox, i, 1, 16777215);
      i += 13;
      this.gameGraphics.drawText("of the screen will show the current level of danger", 256 + ox, i, 1, 16777215);
      i += 22;
      int j = 16777215;
      if (super.mouseY > i - 12 && super.mouseY <= i && super.mouseX > 181 + ox && super.mouseX < 331 + ox) {
         j = 16711680;
      }

      this.gameGraphics.drawText("Click here to close window", 256 + ox, i, 1, j);
      if (this.mouseButtonClick != 0) {
         if (super.mouseY > i - 12 && super.mouseY <= i && super.mouseX > 181 + ox && super.mouseX < 331 + ox) {
            this.wildernessType = 2;
         }

         if (super.mouseX < 86 + ox || super.mouseX > 426 + ox || super.mouseY < 77 + oy || super.mouseY > 257 + oy) {
            this.wildernessType = 2;
         }

         this.mouseButtonClick = 0;
      }
   }

   final void drawGroundItem(int i, int j, int k, int l, int i1, int j1, int k1) {
      int l1 = EntityHandler.getItemDef(i1).getSprite() + 2150;
      int i2 = EntityHandler.getItemDef(i1).getPictureMask();
      this.gameGraphics.spriteClip4(i, j, k, l, l1, i2, 0, 0, false);
   }

   @Override
   protected final void handleServerMessage(String s) {
      if (s.startsWith("@bor@")) {
         this.displayMessage(s, 4, 0);
      } else if (s.startsWith("@que@")) {
         this.displayMessage("@whi@" + s, 5, 0);
      } else if (s.startsWith("@pri@")) {
         this.displayMessage(s, 6, 0);
      } else {
         this.displayMessage(s, 3, 0);
      }

      this.dispatchServerMessage(s);
   }

   /*
    * Hand a server line to the running script.
    *
    * An incoming private message arrives as a server message too -- the server
    * sends it as one, and GameWindowMiddleMan composes "@pri@<who> tells you:
    * <what>" before it gets here. Splitting it back apart is what lets a script
    * see OnPrivateMessage at all, and is why that one shape does not also fire
    * OnServerMessage. The outgoing echo is "@pri@You tell <who>: <what>", which
    * does not contain " tells you: " and so stays a server message, exactly as
    * a script would expect.
    */
   private final void dispatchServerMessage(String s) {
      if (this.scriptRunner == null) {
         return;
      }

      if (s.startsWith("@pri@")) {
         int said = s.indexOf(" tells you: ");
         if (said != -1) {
            this.scriptRunner.firePrivateMessage(s.substring(5, said), s.substring(said + 12));
            return;
         }
      }

      this.scriptRunner.fireServerMessage(s);
   }

   private final void checkMouseOverMenus() {
      /*
       * ForceStatMenu(true) holds the stats panel open regardless of where the
       * mouse is. Levels are only drawn while that panel is up, so a script
       * that wants to read them off the screen -- which is how the old ones
       * did it -- otherwise has to fight the player for the cursor.
       */
      if (this.forceStatMenu) {
         this.mouseOverMenu = 3;
         return;
      }

      if (this.mouseOverMenu == 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3
         && super.mouseY < 35) {
         this.mouseOverMenu = 1;
      }

      if (this.mouseOverMenu == 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 33
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 33
         && super.mouseY < 35) {
         this.mouseOverMenu = 2;
         this.minimapRandomRotation = (int)(Math.random() * 13.0) - 6;
         this.minimapRandomZoom = (int)(Math.random() * 23.0) - 11;
      }

      if (this.mouseOverMenu == 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 66
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 66
         && super.mouseY < 35) {
         this.mouseOverMenu = 3;
      }

      if (this.mouseOverMenu == 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 99
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 99
         && super.mouseY < 35) {
         this.mouseOverMenu = 4;
      }

      if (this.mouseOverMenu == 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 132
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 132
         && super.mouseY < 35) {
         this.mouseOverMenu = 5;
      }

      if (this.mouseOverMenu == 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 165
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 165
         && super.mouseY < 35) {
         this.mouseOverMenu = 6;
      }

      if (this.mouseOverMenu != 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3
         && super.mouseY < 26) {
         this.mouseOverMenu = 1;
      }

      if (this.mouseOverMenu != 0
         && this.mouseOverMenu != 2
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 33
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 33
         && super.mouseY < 26) {
         this.mouseOverMenu = 2;
         this.minimapRandomRotation = (int)(Math.random() * 13.0) - 6;
         this.minimapRandomZoom = (int)(Math.random() * 23.0) - 11;
      }

      if (this.mouseOverMenu != 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 66
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 66
         && super.mouseY < 26) {
         this.mouseOverMenu = 3;
      }

      if (this.mouseOverMenu != 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 99
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 99
         && super.mouseY < 26) {
         this.mouseOverMenu = 4;
      }

      if (this.mouseOverMenu != 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 132
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 132
         && super.mouseY < 26) {
         this.mouseOverMenu = 5;
      }

      if (this.mouseOverMenu != 0
         && super.mouseX >= this.gameGraphics.menuDefaultWidth - 35 - 165
         && super.mouseY >= 3
         && super.mouseX < this.gameGraphics.menuDefaultWidth - 3 - 165
         && super.mouseY < 26) {
         this.mouseOverMenu = 6;
      }

      if (this.mouseOverMenu == 1 && (super.mouseX < this.gameGraphics.menuDefaultWidth - 248 || super.mouseY > 36 + this.inventoryMaxSlots / 5 * 34)) {
         this.mouseOverMenu = 0;
      }

      if (this.mouseOverMenu == 3 && (super.mouseX < this.gameGraphics.menuDefaultWidth - 199 || super.mouseY > 316)) {
         this.mouseOverMenu = 0;
      }

      if ((this.mouseOverMenu == 2 || this.mouseOverMenu == 4 || this.mouseOverMenu == 5)
         && (super.mouseX < this.gameGraphics.menuDefaultWidth - 199 || super.mouseY > 240)) {
         this.mouseOverMenu = 0;
      }

      if (this.mouseOverMenu == 6 && (super.mouseX < this.gameGraphics.menuDefaultWidth - 199 || super.mouseY > 311)) {
         this.mouseOverMenu = 0;
      }
   }

   private final void menuClick(int index) {
      int actionX = this.menuActionX[index];
      int actionY = this.menuActionY[index];
      int actionType = this.menuActionType[index];
      int actionVariable = this.menuActionVariable[index];
      int actionVariable2 = this.menuActionVariable2[index];
      int currentMenuID = this.menuID[index];
      if (currentMenuID == 200) {
         this.walkToGroundItem(this.sectionX, this.sectionY, actionX, actionY, true);
         super.streamClass.createPacket(104);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberSpellCast(actionVariable);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 210) {
         this.walkToGroundItem(this.sectionX, this.sectionY, actionX, actionY, true);
         super.streamClass.createPacket(34);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
      }

      if (currentMenuID == 220) {
         this.walkToGroundItem(this.sectionX, this.sectionY, actionX, actionY, true);
         super.streamClass.createPacket(245);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 3200) {
         this.displayMessage("@gry@ " + EntityHandler.getItemDef(actionType).getDescription(), 3, 0);
      }

      if (currentMenuID == 300) {
         this.walkToAction(actionX, actionY, actionType);
         super.streamClass.createPacket(67);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.addByte(actionType);
         super.streamClass.formatPacket();
         this.rememberSpellCast(actionVariable);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 310) {
         this.walkToAction(actionX, actionY, actionType);
         super.streamClass.createPacket(36);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.addByte(actionType);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
      }

      if (currentMenuID == 320) {
         this.walkToAction(actionX, actionY, actionType);
         super.streamClass.createPacket(126);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.addByte(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 2300) {
         this.walkToAction(actionX, actionY, actionType);
         super.streamClass.createPacket(235);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.addByte(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 3300) {
         this.displayMessage("@gry@ " + EntityHandler.getDoorDef(actionType).getDescription(), 3, 0);
      }

      if (currentMenuID == 400) {
         this.walkToObject(actionX, actionY, actionType, actionVariable);
         super.streamClass.createPacket(17);
         super.streamClass.add2ByteInt(actionVariable2);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.formatPacket();
         this.rememberSpellCast(actionVariable2);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 410) {
         this.walkToObject(actionX, actionY, actionType, actionVariable);
         super.streamClass.createPacket(94);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.add2ByteInt(actionVariable2);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
      }

      if (currentMenuID == 420) {
         this.walkToObject(actionX, actionY, actionType, actionVariable);
         super.streamClass.createPacket(51);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 2400) {
         this.walkToObject(actionX, actionY, actionType, actionVariable);
         super.streamClass.createPacket(40);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 3400) {
         this.displayMessage("@gry@ " + EntityHandler.getObjectDef(actionType).getDescription(), 3, 0);
      }

      if (currentMenuID == 600) {
         super.streamClass.createPacket(49);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberSpellCast(actionVariable);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 610) {
         super.streamClass.createPacket(27);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
      }

      if (currentMenuID == 620) {
         super.streamClass.createPacket(92);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 630) {
         super.streamClass.createPacket(181);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 640) {
         super.streamClass.createPacket(89);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 650) {
         this.selectedItem = actionType;
         this.mouseOverMenu = 0;
         this.selectedItemName = EntityHandler.getItemDef(this.inventoryItems[this.selectedItem]).getName();
      }

      if (currentMenuID == 660) {
         super.streamClass.createPacket(147);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
         this.mouseOverMenu = 0;
         this.displayMessage("@pnk@ Dropping " + EntityHandler.getItemDef(this.inventoryItems[actionType]).getName(), 4, 0);
      }

      if (currentMenuID == 3600) {
         this.displayMessage("@gry@ " + EntityHandler.getItemDef(actionType).getDescription(), 3, 0);
      }

      if (currentMenuID == 700) {
         int l1 = (actionX - 64) / this.magicLoc;
         int l3 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, l1, l3, true);
         super.streamClass.createPacket(71);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberCastForAutocast(actionVariable, 0, actionType);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 710) {
         int i2 = (actionX - 64) / this.magicLoc;
         int i4 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, i2, i4, true);
         super.streamClass.createPacket(142);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
      }

      if (currentMenuID == 720) {
         int j2 = (actionX - 64) / this.magicLoc;
         int j4 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, j2, j4, true);
         super.streamClass.createPacket(177);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 725) {
         int k2 = (actionX - 64) / this.magicLoc;
         int k4 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, k2, k4, true);
         super.streamClass.createPacket(74);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 715 || currentMenuID == 2715) {
         int l2 = (actionX - 64) / this.magicLoc;
         int l4 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, l2, l4, true);
         super.streamClass.createPacket(73);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberCombatTarget(0, actionType);
      }

      if (currentMenuID == 3700) {
         this.displayMessage("@gry@ " + EntityHandler.getNpcDef(actionType).getDescription(), 3, 0);
      }

      if (currentMenuID == 800) {
         int i3 = (actionX - 64) / this.magicLoc;
         int i5 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, i3, i5, true);
         super.streamClass.createPacket(55);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberCastForAutocast(actionVariable, 1, actionType);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 810) {
         int j3 = (actionX - 64) / this.magicLoc;
         int j5 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, j3, j5, true);
         super.streamClass.createPacket(16);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.add2ByteInt(actionVariable);
         super.streamClass.formatPacket();
         this.selectedItem = -1;
      }

      if (currentMenuID == 805 || currentMenuID == 2805) {
         int k3 = (actionX - 64) / this.magicLoc;
         int k5 = (actionY - 64) / this.magicLoc;
         this.walkToTile(this.sectionX, this.sectionY, k3, k5, true);
         super.streamClass.createPacket(57);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberCombatTarget(1, actionType);
      }

      if (currentMenuID == 2806) {
         super.streamClass.createPacket(222);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 2810) {
         super.streamClass.createPacket(166);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 2820) {
         super.streamClass.createPacket(68);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
      }

      if (currentMenuID == 900) {
         this.walkToTile(this.sectionX, this.sectionY, actionX, actionY, true);
         super.streamClass.createPacket(232);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.add2ByteInt(actionX + this.areaX);
         super.streamClass.add2ByteInt(actionY + this.areaY);
         super.streamClass.formatPacket();
         this.rememberSpellCast(actionType);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 920) {
         /* Walking away is the player saying the fight is over: a ground
            click drops the autocast target, or the tick loop would keep
            walking us straight back to the mob to cast again. The spell
            stays remembered -- the next Attack click resumes autocast. */
         this.autocastTargetType = -1;
         this.autocastTargetIndex = -1;
         this.autocastAcquireSuppressed = true;
         this.walkToTile(this.sectionX, this.sectionY, actionX, actionY, false);
         if (this.actionPictureType == -24) {
            this.actionPictureType = 24;
         }
      }

      if (currentMenuID == 1000) {
         super.streamClass.createPacket(206);
         super.streamClass.add2ByteInt(actionType);
         super.streamClass.formatPacket();
         this.rememberSpellCast(actionType);
         this.selectedSpell = -1;
      }

      if (currentMenuID == 4000) {
         this.selectedItem = -1;
         this.selectedSpell = -1;
      }
   }

   final void drawTeleportBubble(int i, int j, int k, int l, int i1, int j1, int k1) {
      int l1 = this.teleBubbleType[i1];
      int i2 = this.teleBubbleTime[i1];
      if (l1 == 0) {
         int j2 = 255 + i2 * 5 * 256;
         this.gameGraphics.drawCircle(i + k / 2, j + l / 2, 20 + i2 * 2, j2, 255 - i2 * 5);
      }

      if (l1 == 1) {
         int k2 = 16711680 + i2 * 5 * 256;
         this.gameGraphics.drawCircle(i + k / 2, j + l / 2, 10 + i2, k2, 255 - i2 * 5);
      }
   }

   @Override
   protected final void updateGame() {
      if (!this.memoryError) {
         if (!this.lastLoadedNull) {
            /* Before anything else this tick: the world that was picked last
               tick needs different content, and nothing below should run
               against the framebuffer and menus that are about to be replaced. */
            if (this.reloadPending) {
               this.reloadForChosenWorld();
               return;
            }

            /* A join link handed over by a second launch. Same safe point,
               and before the resize below, because joining may set
               reloadPending and there is no sense resizing a framebuffer that
               is about to be thrown away. */
            if (this.pendingJoinUri != null) {
               this.applyPendingJoin();
               return;
            }

            /* A window resize is applied at the same safe point, for the same
               reason: between ticks, nothing is mid-draw against the
               framebuffer that is about to be replaced. */
            this.applyPendingResize();
            if (this.resizeSaveDue != 0L && System.currentTimeMillis() >= this.resizeSaveDue) {
               this.resizeSaveDue = 0L;
               Config.settings().set("window_width", this.windowWidth);
               Config.settings().set("window_height", this.windowHeight);
               Config.settings().save();
            }

            try {
               this.loginTimer++;
               if (this.loggedIn == 0) {
                  super.lastActionTimeout = 0;
                  this.updateLoginScreen();
               }

               if (this.loggedIn == 1) {
                  super.lastActionTimeout++;
                  this.pumpScriptResume();
                  this.processGame();
               }

               super.lastMouseDownButton = 0;
               super.keyDown2 = 0;
               this.screenRotationTimer++;
               if (this.screenRotationTimer > 500) {
                  this.screenRotationTimer = 0;
                  int i = (int)(Math.random() * 4.0);
                  if ((i & 1) == 1) {
                     this.screenRotationX = this.screenRotationX + this.screenRotationXStep;
                  }

                  if ((i & 2) == 2) {
                     this.screenRotationY = this.screenRotationY + this.screenRotationYStep;
                  }
               }

               if (this.screenRotationX < -50) {
                  this.screenRotationXStep = 2;
               }

               if (this.screenRotationX > 50) {
                  this.screenRotationXStep = -2;
               }

               if (this.screenRotationY < -50) {
                  this.screenRotationYStep = 2;
               }

               if (this.screenRotationY > 50) {
                  this.screenRotationYStep = -2;
               }

               if (this.allMessagesTabFlash > 0) {
                  this.allMessagesTabFlash--;
               }

               if (this.chatHistoryTabFlash > 0) {
                  this.chatHistoryTabFlash--;
               }

               if (this.questHistoryTabFlash > 0) {
                  this.questHistoryTabFlash--;
               }

               if (this.privateHistoryTabFlash > 0) {
                  this.privateHistoryTabFlash--;
                  return;
               }
            } catch (OutOfMemoryError var2) {
               this.garbageCollect();
               this.memoryError = true;
            }
         }
      }
   }

   private final Model makeModel(int x, int y, int k, int l, int i1) {
      int modelX = x;
      int modelX1 = x;
      int modelX2 = y;
      int j2 = EntityHandler.getDoorDef(l).getModelVar2();
      int k2 = EntityHandler.getDoorDef(l).getModelVar3();
      int l2 = EntityHandler.getDoorDef(l).getModelVar1();
      Model model = new Model(4, 1);
      if (k == 0) {
         modelX1 = x + 1;
      }

      if (k == 1) {
         modelX2 = y + 1;
      }

      if (k == 2) {
         modelX = x + 1;
         modelX2 = y + 1;
      }

      if (k == 3) {
         modelX1 = x + 1;
         modelX2 = y + 1;
      }

      modelX *= this.magicLoc;
      int modelY = y * this.magicLoc;
      modelX1 *= this.magicLoc;
      modelX2 *= this.magicLoc;
      int i3 = model.getOrAddVertex(modelX, -this.engineHandle.getAveragedElevation(modelX, modelY), modelY);
      int j3 = model.getOrAddVertex(modelX, -this.engineHandle.getAveragedElevation(modelX, modelY) - l2, modelY);
      int k3 = model.getOrAddVertex(modelX1, -this.engineHandle.getAveragedElevation(modelX1, modelX2) - l2, modelX2);
      int l3 = model.getOrAddVertex(modelX1, -this.engineHandle.getAveragedElevation(modelX1, modelX2), modelX2);
      int[] ai = new int[]{i3, j3, k3, l3};
      model.addFace(4, ai, j2, k2);
      model.setLight(false, 60, 24, -50, -10, -50);
      if (x >= 0 && y >= 0 && x < 96 && y < 96) {
         this.gameCamera.addModel(model);
      }

      model.key = i1 + 10000;
      return model;
   }

   private final void resetLoginVars() {
      /* Leaving the world leaves the map, which is the client's largest single
         allocation by a wide margin -- close() lets the raster go. Nothing
         draws it at the login screen anyway, so it would have been 55MB held
         for a screen nobody can see it on. */
      if (this.worldMapPanel != null) {
         this.worldMapPanel.close();
      }

      this.loggedIn = 0;
      this.currentUser = "";
      this.currentPass = "";
      this.currentWorld = LOGIN_WORLD;
      this.playerCount = 0;
      this.npcCount = 0;

      /*
       * Where a fresh install starts: the Worlds screen, not a welcome panel
       * and not a sign-in box.
       *
       * An empty default_server in settings.ini is the whole of the "has this
       * player ever joined anything" test -- there is no first-run flag, and
       * no server is preselected, including ours. Being asked to sign in
       * before being asked where is the wrong order when the answer is not
       * already known, and a client that quietly defaulted to our server would
       * be handing itself the one advantage this whole design is about giving
       * away.
       */
      if (Config.DEFAULT_TARGET.length() > 0) {
         this.loginScreenNumber = 0;
         this.setServer(Config.SERVER_IP, Config.SERVER_PORT);
         this.applyServerWelcome();
      } else {
         /* Unconditional now. This used to be guarded on the Worlds menu
            having been built already, and fell back to the welcome panel if it
            had not -- so whether a fresh install saw the screen it is supposed
            to start on depended on which ran first. The panel builds itself on
            first use, so there is nothing left to be too early for. */
         this.showWorldsScreen();
      }
   }

   private static final String formatItemCount(int i) {
      String s = String.valueOf(i);

      for (int j = s.length() - 3; j > 0; j -= 3) {
         s = s.substring(0, j) + "," + s.substring(j);
      }

      if (s.length() > 8) {
         s = "@gre@" + s.substring(0, s.length() - 8) + " million @whi@(" + s + ")";
      } else if (s.length() > 4) {
         s = "@cya@" + s.substring(0, s.length() - 4) + "K @whi@(" + s + ")";
      }

      return s;
   }

   private final void drawGame() {
      if (this.playerAliveTimeout != 0) {
         this.gameGraphics.fadePixels();
         this.gameGraphics.drawText("Oh dear! You are dead...", this.windowWidth / 2, this.windowHeight / 2, 7, 16711680);
         this.drawChatMessageTabs();
         this.gameGraphics.drawImage(this.aGraphics936, 0, 0);
      } else if (this.showCharacterLookScreen) {
         this.drawCharacterDesignScreen();
      } else if (this.engineHandle.playerIsAlive) {
         for (int i = 0; i < 64; i++) {
            this.gameCamera.removeModel(this.engineHandle.roofModels[this.lastWildYSubtract][i]);
            if (this.lastWildYSubtract == 0) {
               this.gameCamera.removeModel(this.engineHandle.wallModels[1][i]);
               this.gameCamera.removeModel(this.engineHandle.roofModels[1][i]);
               this.gameCamera.removeModel(this.engineHandle.wallModels[2][i]);
               this.gameCamera.removeModel(this.engineHandle.roofModels[2][i]);
            }

            this.zoomCamera = true;
            if (this.lastWildYSubtract == 0 && (this.engineHandle.walkableValue[this.ourPlayer.currentX / 128][this.ourPlayer.currentY / 128] & 128) == 0) {
               if (this.showRoof) {
                  this.gameCamera.addModel(this.engineHandle.roofModels[this.lastWildYSubtract][i]);
                  if (this.lastWildYSubtract == 0) {
                     this.gameCamera.addModel(this.engineHandle.wallModels[1][i]);
                     this.gameCamera.addModel(this.engineHandle.roofModels[1][i]);
                     this.gameCamera.addModel(this.engineHandle.wallModels[2][i]);
                     this.gameCamera.addModel(this.engineHandle.roofModels[2][i]);
                  }
               }

               this.zoomCamera = false;
            }
         }

         if (this.modelFireLightningSpellNumber != this.lastModelFireLightningSpellNumber) {
            this.lastModelFireLightningSpellNumber = this.modelFireLightningSpellNumber;

            for (int j = 0; j < this.objectCount; j++) {
               if (this.objectType[j] == 97) {
                  this.replaceObjectModel(j, "firea" + (this.modelFireLightningSpellNumber + 1));
               }

               if (this.objectType[j] == 274) {
                  this.replaceObjectModel(j, "fireplacea" + (this.modelFireLightningSpellNumber + 1));
               }

               if (this.objectType[j] == 1031) {
                  this.replaceObjectModel(j, "lightning" + (this.modelFireLightningSpellNumber + 1));
               }

               if (this.objectType[j] == 1036) {
                  this.replaceObjectModel(j, "firespell" + (this.modelFireLightningSpellNumber + 1));
               }

               if (this.objectType[j] == 1147) {
                  this.replaceObjectModel(j, "spellcharge" + (this.modelFireLightningSpellNumber + 1));
               }

               // Essence mine portal: three crown frames, each the same
               // geometry rotated 15 degrees. Three steps of 15 close the
               // 45-degree segment of its 8-fold ring, so the cycle loops
               // seamlessly and the flames appear to swirl.
               if (this.objectType[j] == 1207) {
                  this.replaceObjectModel(j, this.modelFireLightningSpellNumber == 0
                     ? "essence portal"
                     : "essence portal" + (this.modelFireLightningSpellNumber + 1));
               }
            }
         }

         if (this.modelTorchNumber != this.lastModelTorchNumber) {
            this.lastModelTorchNumber = this.modelTorchNumber;

            for (int k = 0; k < this.objectCount; k++) {
               if (this.objectType[k] == 51) {
                  this.replaceObjectModel(k, "torcha" + (this.modelTorchNumber + 1));
               }

               if (this.objectType[k] == 143) {
                  this.replaceObjectModel(k, "skulltorcha" + (this.modelTorchNumber + 1));
               }
            }
         }

         if (this.modelClawSpellNumber != this.lastModelClawSpellNumber) {
            this.lastModelClawSpellNumber = this.modelClawSpellNumber;

            for (int l = 0; l < this.objectCount; l++) {
               if (this.objectType[l] == 1142) {
                  this.replaceObjectModel(l, "clawspell" + (this.modelClawSpellNumber + 1));
               }
            }
         }

         this.gameCamera.reduceSprites(this.fightCount);
         this.fightCount = 0;

         for (int i1 = 0; i1 < this.playerCount; i1++) {
            Mob mob = this.playerArray[i1];
            if (mob.colourBottomType != 255) {
               int k1 = mob.currentX;
               int i2 = mob.currentY;
               int k2 = -this.engineHandle.getAveragedElevation(k1, i2);
               int l3 = this.gameCamera.addSprite(5000 + i1, k1, k2, i2, 145, 220, i1 + 10000);
               this.fightCount++;
               if (mob == this.ourPlayer) {
                  this.gameCamera.setOurPlayer(l3);
               }

               if (mob.currentSprite == 8) {
                  this.gameCamera.setSpriteTranslateX(l3, -30);
               }

               if (mob.currentSprite == 9) {
                  this.gameCamera.setSpriteTranslateX(l3, 30);
               }
            }
         }

         for (int j1 = 0; j1 < this.playerCount; j1++) {
            Mob player = this.playerArray[j1];
            if (player.projectileCountdown > 0) {
               Mob npc = null;
               if (player.attackingNpcIndex != -1) {
                  npc = this.npcRecordArray[player.attackingNpcIndex];
               } else if (player.attackingMobIndex != -1) {
                  npc = this.mobArray[player.attackingMobIndex];
               }

               if (npc != null) {
                  int px = player.currentX;
                  int py = player.currentY;
                  int pi = -this.engineHandle.getAveragedElevation(px, py) - 110;
                  int nx = npc.currentX;
                  int ny = npc.currentY;
                  int ni = -this.engineHandle.getAveragedElevation(nx, ny) - EntityHandler.getNpcDef(npc.type).getCamera2() / 2;
                  int i10 = (px * player.projectileCountdown + nx * (this.projectileFlightDuration - player.projectileCountdown)) / this.projectileFlightDuration;
                  int j10 = (pi * player.projectileCountdown + ni * (this.projectileFlightDuration - player.projectileCountdown)) / this.projectileFlightDuration;
                  int k10 = (py * player.projectileCountdown + ny * (this.projectileFlightDuration - player.projectileCountdown)) / this.projectileFlightDuration;
                  this.gameCamera.addSprite(3160 + player.attackingCameraInt, i10, j10, k10, 32, 32, 0);
                  this.fightCount++;
               }
            }
         }

         // The same loop again for shots fired by npcs, which used to have no
         // draw path at all: the server could describe one perfectly well and
         // nothing would ever appear. A projectile is drawn from whoever fired
         // it, and npcs fire too.
         for (int j1 = 0; j1 < this.npcCount; j1++) {
            Mob caster = this.npcArray[j1];
            if (caster.projectileCountdown > 0) {
               Mob target = null;
               if (caster.attackingNpcIndex != -1) {
                  target = this.npcRecordArray[caster.attackingNpcIndex];
               } else if (caster.attackingMobIndex != -1) {
                  target = this.mobArray[caster.attackingMobIndex];
               }

               if (target != null) {
                  int px = caster.currentX;
                  int py = caster.currentY;
                  int pi = -this.engineHandle.getAveragedElevation(px, py) - 110;
                  int nx = target.currentX;
                  int ny = target.currentY;
                  int ni = -this.engineHandle.getAveragedElevation(nx, ny) - EntityHandler.getNpcDef(target.type).getCamera2() / 2;
                  int i10 = (px * caster.projectileCountdown + nx * (this.projectileFlightDuration - caster.projectileCountdown)) / this.projectileFlightDuration;
                  int j10 = (pi * caster.projectileCountdown + ni * (this.projectileFlightDuration - caster.projectileCountdown)) / this.projectileFlightDuration;
                  int k10 = (py * caster.projectileCountdown + ny * (this.projectileFlightDuration - caster.projectileCountdown)) / this.projectileFlightDuration;
                  this.gameCamera.addSprite(3160 + caster.attackingCameraInt, i10, j10, k10, 32, 32, 0);
                  this.fightCount++;
               }
            }
         }

         for (int l1 = 0; l1 < this.npcCount; l1++) {
            Mob npcx = this.npcArray[l1];
            int mobx = npcx.currentX;
            int moby = npcx.currentY;
            int i7 = -this.engineHandle.getAveragedElevation(mobx, moby);
            int i9 = this.gameCamera
               .addSprite(
                  20000 + l1, mobx, i7, moby, EntityHandler.getNpcDef(npcx.type).getCamera1(), EntityHandler.getNpcDef(npcx.type).getCamera2(), l1 + 30000
               );
            this.fightCount++;
            if (npcx.currentSprite == 8) {
               this.gameCamera.setSpriteTranslateX(i9, -30);
            }

            if (npcx.currentSprite == 9) {
               this.gameCamera.setSpriteTranslateX(i9, 30);
            }
         }

         for (int j2 = 0; j2 < this.groundItemCount; j2++) {
            int j3 = this.groundItemX[j2] * this.magicLoc + 64;
            int k4 = this.groundItemY[j2] * this.magicLoc + 64;
            this.gameCamera
               .addSprite(
                  40000 + this.groundItemType[j2], j3, -this.engineHandle.getAveragedElevation(j3, k4) - this.groundItemObjectVar[j2], k4, 96, 64, j2 + 20000
               );
            this.fightCount++;
         }

         for (int k3 = 0; k3 < this.teleBubbleCount; k3++) {
            int l4 = this.teleBubbleX[k3] * this.magicLoc + 64;
            int j7 = this.teleBubbleY[k3] * this.magicLoc + 64;
            int j9 = this.teleBubbleType[k3];
            if (j9 == 0) {
               this.gameCamera.addSprite(50000 + k3, l4, -this.engineHandle.getAveragedElevation(l4, j7), j7, 128, 256, k3 + 50000);
               this.fightCount++;
            }

            if (j9 == 1) {
               this.gameCamera.addSprite(50000 + k3, l4, -this.engineHandle.getAveragedElevation(l4, j7), j7, 128, 64, k3 + 50000);
               this.fightCount++;
            }
         }

         this.gameGraphics.f1Toggle = false;
         this.gameGraphics.clearScreen();
         this.gameGraphics.f1Toggle = super.keyF1Toggle;
         if (this.lastWildYSubtract == 3) {
            int i5 = 40 + (int)(Math.random() * 3.0);
            int k7 = 40 + (int)(Math.random() * 7.0);
            this.gameCamera.setLight(i5, k7, -50, -10, -50);
         }

         this.bubbleCount = 0;
         this.mobMessageCount = 0;
         this.healthBarCount = 0;
         if (this.cameraAutoAngleDebug) {
            if (this.configAutoCameraAngle && !this.zoomCamera) {
               int lastCameraAutoAngle = this.cameraAutoAngle;
               this.autoRotateCamera();
               if (this.cameraAutoAngle != lastCameraAutoAngle) {
                  this.lastAutoCameraRotatePlayerX = this.ourPlayer.currentX;
                  this.lastAutoCameraRotatePlayerY = this.ourPlayer.currentY;
               }
            }

            this.gameCamera.clipFar3d = 3000;
            this.gameCamera.clipFar2d = 3000;
            this.gameCamera.fogZFalloff = 1;
            this.gameCamera.fogZDistance = 2800;
            this.cameraRotation = this.cameraAutoAngle * 32;
            int k5 = this.lastAutoCameraRotatePlayerX + this.screenRotationX;
            int l7 = this.lastAutoCameraRotatePlayerY + this.screenRotationY;
            this.gameCamera.setCamera(k5, -this.engineHandle.getAveragedElevation(k5, l7), l7, 912, this.cameraRotation * 4, 0, 2000);
         } else {
            if (this.configAutoCameraAngle && !this.zoomCamera) {
               this.autoRotateCamera();
            }

            /* fogZDistance is where the fog starts fading things to black, clipFar3d/2
               the far clip -- the black wall itself. Fog off is STS's
               disable-fog-of-war: clip and fog both pushed past the whole
               loaded section (96x96 tiles is ~8700 units corner to corner),
               so everything the client has loaded is drawn and none of it
               fades. The section boundary is still black -- there is nothing
               beyond it to draw -- but it walks with you the way the loaded
               world does. */
            if (!this.showFog) {
               this.gameCamera.clipFar3d = 12000;
               this.gameCamera.clipFar2d = 12000;
               this.gameCamera.fogZFalloff = 1;
               this.gameCamera.fogZDistance = 12000;
            } else if (!super.keyF1Toggle) {
               this.gameCamera.clipFar3d = 2400;
               this.gameCamera.clipFar2d = 2400;
               this.gameCamera.fogZFalloff = 1;
               this.gameCamera.fogZDistance = 2300;
            } else {
               this.gameCamera.clipFar3d = 2200;
               this.gameCamera.clipFar2d = 2200;
               this.gameCamera.fogZFalloff = 1;
               this.gameCamera.fogZDistance = 2100;
            }

            int l5 = this.lastAutoCameraRotatePlayerX + this.screenRotationX;
            int i8 = this.lastAutoCameraRotatePlayerY + this.screenRotationY;
            this.gameCamera.setCamera(l5, -this.engineHandle.getAveragedElevation(l5, i8), i8, 912, this.cameraRotation * 4, 0, this.cameraHeight * 2);
         }

         /*
          * finishCamera() is the 3D render, and on a botting box it is nearly
          * all of the frame cost. SetGfx(false) drops it and paints the
          * viewport flat instead, so the panels, chat and overlay still read
          * normally -- the world just isn't drawn.
          */
         if (this.drawGfx) {
            this.gameCamera.finishCamera();
         } else {
            this.gameGraphics.drawBox(0, 0, this.windowWidth, this.windowHeight, 0);
         }

         this.drawOverheads();
         if (this.actionPictureType > 0) {
            this.gameGraphics.drawPicture(this.actionPictureX - 8, this.actionPictureY - 8, 2014 + (24 - this.actionPictureType) / 6);
         }

         if (this.actionPictureType < 0) {
            this.gameGraphics.drawPicture(this.actionPictureX - 8, this.actionPictureY - 8, 2018 + (24 + this.actionPictureType) / 6);
         }

         if (this.systemUpdate != 0) {
            int i6 = this.systemUpdate / 50;
            int j8 = i6 / 60;
            i6 %= 60;
            if (i6 < 10) {
               this.gameGraphics.drawText("System update in: " + j8 + ":0" + i6, this.windowWidth / 2, this.windowHeight - 7, 1, 16776960);
            } else {
               this.gameGraphics.drawText("System update in: " + j8 + ":" + i6, this.windowWidth / 2, this.windowHeight - 7, 1, 16776960);
            }
         }

         /* 453/465 were the vanilla 512-wide view's right margin for this icon
            (59px) and its centred text (47px); kept as offsets from
            windowWidth so the indicator stays pinned to the corner instead of
            drifting into the middle of a widened viewport. */
         if (Config.SERVER_WORLD == 2) {
            this.gameGraphics.drawPicture(this.windowWidth - 59, this.windowHeight - 56, 2013);
            this.gameGraphics.drawText("Wilderness", this.windowWidth - 47, this.windowHeight - 20, 1, 16776960);
            this.gameGraphics.drawText("Level: 123", this.windowWidth - 47, this.windowHeight - 7, 1, 16776960);
            if (this.wildernessType == 0) {
               this.wildernessType = 2;
            }
         } else if (!this.notInWilderness) {
            int j6 = 2203 - (this.sectionY + this.wildY + this.areaY);
            if (this.sectionX + this.wildX + this.areaX >= 2640) {
               j6 = -50;
            }

            if (j6 > 0) {
               int k8 = 1 + j6 / 6;
               this.gameGraphics.drawPicture(this.windowWidth - 59, this.windowHeight - 56, 2013);
               this.gameGraphics.drawText("Wilderness", this.windowWidth - 47, this.windowHeight - 20, 1, 16776960);
               this.gameGraphics.drawText("Level: " + k8, this.windowWidth - 47, this.windowHeight - 7, 1, 16776960);
               if (this.wildernessType == 0) {
                  this.wildernessType = 2;
               }
            } else if (this.sectionX + this.areaX >= 395
               && this.sectionX + this.areaX <= 438
               && this.sectionY + this.areaY >= 3560
               && this.sectionY + this.areaY <= 3600) {
               this.gameGraphics.drawPicture(this.windowWidth - 59, this.windowHeight - 56, 2013);
               this.gameGraphics.drawText("Wilderness", this.windowWidth - 47, this.windowHeight - 20, 1, 16776960);
               this.gameGraphics.drawText("Level: 123", this.windowWidth - 47, this.windowHeight - 7, 1, 16776960);
               if (this.wildernessType == 0) {
                  this.wildernessType = 2;
               }
            }

            if (this.wildernessType == 0 && j6 > -10 && j6 <= 0) {
               this.wildernessType = 1;
            }
         }

         if (this.messagesTab == 0) {
            for (int k6 = 0; k6 < 5; k6++) {
               if (this.messagesTimeout[k6] > 0) {
                  String s = this.messagesArray[k6];
                  this.gameGraphics.drawString(s, 7, this.windowHeight - 18 - k6 * 12, 1, 16776960);
               }
            }
         }

         this.gameMenu.hide(this.messagesHandleType2);
         this.gameMenu.hide(this.messagesHandleType5);
         this.gameMenu.hide(this.messagesHandleType6);
         if (this.messagesTab == 1) {
            this.gameMenu.show(this.messagesHandleType2);
         } else if (this.messagesTab == 2) {
            this.gameMenu.show(this.messagesHandleType5);
         } else if (this.messagesTab == 3) {
            this.gameMenu.show(this.messagesHandleType6);
         }

         Menu.listLineHeightReduction = 2;
         this.gameMenu.drawMenu();
         Menu.listLineHeightReduction = 0;
         this.gameGraphics.drawSpriteAlpha(this.gameGraphics.menuDefaultWidth - 3 - 197, 3, 2000, 128);
         this.drawGameWindowsMenus();
         this.drawStatusOverlay();
         this.drawScriptOverlay();
         this.gameGraphics.drawStringShadows = false;
         this.drawChatMessageTabs();
         if (this.scriptPanel != null) {
            this.scriptPanel.draw(this.gameGraphics, super.mouseX, super.mouseY);
         }

         /* Over the F2 menu for the same reason as the map below it. */
         if (this.calculatorPanel != null) {
            this.calculatorPanel.draw(this.gameGraphics, super.mouseX, super.mouseY);
         }

         /* Over the F2 menu, which is what opened it. */
         if (this.worldMapPanel != null) {
            this.worldMapPanel.draw(this.gameGraphics, super.mouseX, super.mouseY);
         }

         /* After the F2 menu on purpose: a script waiting for an answer is the
            most immediately important thing on the screen. */
         this.scriptPrompt.draw(this.gameGraphics, this.windowWidth, this.surfaceHeight());

         this.gameGraphics.drawImage(this.aGraphics936, 0, 0);
      }
   }

   /*
    * STS's status menu, put back.
    *
    * It was never a panel: STS wrote plain text straight over the game view at
    * x = 10, one line every 12 pixels in the small font, white with @red@
    * markers, and that is all this is. Everything on it is something the client
    * already knows -- the same set the Info tab was showing, which is why that
    * tab is gone and this is here instead.
    *
    * Drawn before the script's own ToShow() so a script that wants the corner
    * for itself gets it.
    */
   private final void drawStatusOverlay() {
      if (this.statusOverlay && this.loggedIn == 1 && this.ourPlayer != null) {
         int y = 150;
         this.gameGraphics.drawString(this.ourPlayer.name + " @red@(@whi@" + this.ourPlayer.level + "@red@)", 10, y, 1, 16777215);
         y += 12;
         this.gameGraphics
            .drawString("Coords: @red@(@whi@" + (this.sectionX + this.areaX) + "@red@,@whi@" + (this.sectionY + this.areaY) + "@red@)", 10, y, 1, 16777215);
         y += 12;
         this.gameGraphics.drawString("Pid: @red@(@whi@" + this.ourPlayer.serverIndex + "@red@)", 10, y, 1, 16777215);
         y += 12;
         this.gameGraphics.drawString("Fatigue: @red@" + this.fatigue + "%", 10, y, 1, 16777215);
         y += 12;
         this.gameGraphics
            .drawString("Exp gained: @red@" + (this.expGained > 1000L ? this.expGained / 1000L + "k" : String.valueOf(this.expGained)), 10, y, 1, 16777215);
         if (this.autocastEnabled) {
            y += 12;
            this.gameGraphics.drawString("Autocast: @red@"
                  + (this.lastCastSpellName != null ? this.lastCastSpellName : "Cast a spell to set"), 10, y, 1, 16777215);
         }
      }
   }

   /* Records the spell just cast and starts the round-trip latency sample,
      for every cast menu branch -- not just the two that target an npc or
      player. The status overlay is meant to show what autocast will actually
      repeat, so it has to track every cast, not just combat ones; clearing
      the autocast target here is what keeps that honest -- a self/item/door
      /object/ground cast has nothing sensible to repeat, and without this a
      mid-fight utility cast (Bones to Bananas, say) would leave the OLD
      combat target set while lastCastSpell pointed at the new, uncombat
      spell, and processAutocast would start firing that at whoever the
      target used to be. rememberCastForAutocast below re-sets a real target
      right after, for the two branches that have one. */
   private void rememberSpellCast(int spellId) {
      this.lastCastSpell = spellId;
      this.lastCastSpellName = EntityHandler.getSpellDef(spellId).getName();
      this.autocastTargetType = -1;
      this.autocastTargetIndex = -1;
      this.noteCastSent();
   }

   /* Records the spell+target a "Cast X on <npc/player>" click just sent, so
      processAutocast() knows what to repeat. targetType 0 = npc, 1 = player,
      matching the two menuClick branches (700/800) this is called from. */
   private void rememberCastForAutocast(int spellId, int targetType, int targetServerIndex) {
      this.rememberSpellCast(spellId);
      this.autocastTargetType = targetType;
      this.autocastTargetIndex = targetServerIndex;
   }

   /* Records who an "Attack" click just went after -- left-click's default
      action or the right-click menu's, menuID 715/2715/805/2805, melee or
      not -- so autocast can fire on it immediately. Deliberately leaves
      lastCastSpell alone: attacking someone does not change which spell
      autocast is set to, only who it is aimed at.

      Firing here rather than only waiting for the tick loop is the point:
      ranged and magic both start doing real damage the moment this click
      lands, from well outside melee range, while the target is still
      walking over -- an Attack click already IS the start of the fight for
      them, same as it is for melee, so autocast has no reason to wait on
      anything more before taking its first swing at a target the player
      just declared. Reported as two symptoms of the one gap: "doesn't start
      autocasting immediately", and for a target killed entirely at range,
      not at all -- see fireAutocast for the second half of that fix. */
   private void rememberCombatTarget(int targetType, int targetServerIndex) {
      this.autocastAcquireSuppressed = false;
      this.autocastTargetType = targetType;
      this.autocastTargetIndex = targetServerIndex;
      this.fireAutocast();
   }

   /* Starts the round-trip clock for the cast packet that was just written to
      the stream (manual click or autocast's own resend). See the field
      comment on castSendTime for why this exists. */
   private void noteCastSent() {
      this.castSendTime = System.currentTimeMillis();
      this.awaitingCastAck = true;
   }

   /* Called from the top of handleIncomingPacket for every packet, not just
      ones related to casting -- on a connection with no per-cast ack, the
      next thing the server sends is the closest proxy this protocol offers.
      Folded into castRoundTripMs as a running maximum (never lets a small
      sample erase a larger one already seen) rather than the latest sample
      taken at face value -- see the field comment for why the raw sample is
      too noisy to trust on its own. */
   private void noteIncomingForAutocast() {
      if (this.awaitingCastAck) {
         this.awaitingCastAck = false;
         long rtt = System.currentTimeMillis() - this.castSendTime;
         if (rtt > MAX_ROUND_TRIP_MS) {
            rtt = MAX_ROUND_TRIP_MS;
         }
         if (rtt > this.castRoundTripMs) {
            this.castRoundTripMs = rtt;
         }
      }
   }

   /* Built-in autocast, ticked every frame: keeps fireAutocast's resend
      going for as long as there is still a live target set (autocastTargetType
      /Index -- set by either a spell cast or an attack click, see
      rememberCastForAutocast/rememberCombatTarget). */
   private void processAutocast() {
      this.acquireAutocastTargetFromCombat();
      this.fireAutocast();
   }

   /* Combat entered without an Attack click -- an aggressive mob walked up
      and engaged us -- never goes through rememberCombatTarget, so autocast
      had a spell but no one to cast it at. Being in combat is itself the
      declaration of a target: the engaged pair stand on the same tile drawn
      in sprites 8 and 9, so the one mob sharing our tile in a combat sprite
      is our opponent and nothing else can match. Only runs while the current
      target is unset or no longer findable (dead mobs leave the arrays), so
      it never steals aim from a fight the player chose. */
   private void acquireAutocastTargetFromCombat() {
      if (!this.autocastEnabled || this.lastCastSpell < 0 || this.ourPlayer == null) {
         return;
      }
      /* A ground click cancelled autocast mid-fight. While the combat sprite
         is still on us (fleeing takes a tick or two, or the mob re-engages),
         re-acquiring here would walk us straight back in -- so hold off until
         we have actually been out of combat once. */
      if (this.autocastAcquireSuppressed) {
         if (this.ourPlayer.currentSprite != 8 && this.ourPlayer.currentSprite != 9) {
            this.autocastAcquireSuppressed = false;
         }
         return;
      }
      if (this.autocastTargetType >= 0) {
         Mob current = this.autocastTargetType == 0
               ? this.findNpcByServerIndex(this.autocastTargetIndex)
               : this.findPlayerByServerIndex(this.autocastTargetIndex);
         if (current != null) {
            return;
         }
      }
      Mob us = this.ourPlayer;
      if (us.currentSprite != 8 && us.currentSprite != 9) {
         return;
      }
      for (int i = 0; i < this.npcCount; i++) {
         Mob npc = this.npcArray[i];
         if ((npc.currentSprite == 8 || npc.currentSprite == 9)
               && npc.currentX == us.currentX && npc.currentY == us.currentY) {
            this.rememberCombatTarget(0, npc.serverIndex);
            return;
         }
      }
      for (int i = 0; i < this.playerCount; i++) {
         Mob player = this.playerArray[i];
         if (player != us && (player.currentSprite == 8 || player.currentSprite == 9)
               && player.currentX == us.currentX && player.currentY == us.currentY) {
            this.rememberCombatTarget(1, player.serverIndex);
            return;
         }
      }
   }

   /* Casts the last spell picked (lastCastSpell) at the current combat
      target, timed against the server's real cooldown plus network margin
      -- see SPELL_COOLDOWN_MS and castRoundTripMs's field comments. Called
      both from the tick loop, to keep resending, and directly from
      rememberCombatTarget, so the first cast goes out the instant the
      player declares a target rather than waiting for the next tick.

      Used to also require currentSprite 8/9 or combatTimer != 0 -- the same
      "in combat" signal Script.inCombat() uses -- before it would fire at
      all. That flag only goes true once melee actually starts, i.e. once
      the target has walked all the way to the player, so it was wrong for
      ranged and magic: both do real damage from well outside melee range,
      and a target killed entirely at that range meant the signal never
      went true and autocast never repeated even once. The target itself,
      not the server's melee state, is what decides whether this keeps
      firing -- silently stops once it leaves view (dies, walks off, logs
      out); the player has to attack or cast again to give it a new one,
      same as the spec asked for rather than guessing a replacement. */
   private void fireAutocast() {
      if (!this.autocastEnabled || this.lastCastSpell < 0 || this.autocastTargetType < 0 || this.ourPlayer == null) {
         return;
      }

      /* A dialogue question menu is the server waiting on an answer -- a cast
         sent now would make the server tear the menu down mid-question
         (reported live against Demon Slayer's incantation menu). Hold fire
         until the player has answered; the target and spell stay set, so
         autocast resumes on its own once the menu closes. */
      if (this.showQuestionMenu) {
         return;
      }

      long now = System.currentTimeMillis();
      if (now - this.castSendTime < SPELL_COOLDOWN_MS + this.castRoundTripMs) {
         return;
      }

      Mob target = this.autocastTargetType == 0 ? this.findNpcByServerIndex(this.autocastTargetIndex) : this.findPlayerByServerIndex(this.autocastTargetIndex);
      if (target == null) {
         return;
      }

      int tileX = (target.currentX - 64) / this.magicLoc;
      int tileY = (target.currentY - 64) / this.magicLoc;
      this.walkToTile(this.sectionX, this.sectionY, tileX, tileY, true);
      super.streamClass.createPacket(this.autocastTargetType == 0 ? 71 : 55);
      super.streamClass.add2ByteInt(this.lastCastSpell);
      super.streamClass.add2ByteInt(this.autocastTargetIndex);
      super.streamClass.formatPacket();
      this.noteCastSent();
   }

   private Mob findNpcByServerIndex(int serverIndex) {
      for (int i = 0; i < this.npcCount; i++) {
         if (this.npcArray[i].serverIndex == serverIndex) {
            return this.npcArray[i];
         }
      }

      return null;
   }

   private Mob findPlayerByServerIndex(int serverIndex) {
      for (int i = 0; i < this.playerCount; i++) {
         if (this.playerArray[i].serverIndex == serverIndex) {
            return this.playerArray[i];
         }
      }

      return null;
   }

   /*
    * Whatever the script's ToShow() asks for, drawn last so it sits over the
    * panels -- a bot HUD is no use underneath the inventory.
    *
    * Every array is checked before it is indexed. Stats leaves its fields null
    * when a script hands it mismatched lengths, and the whole point of that is
    * that a script bug costs its overlay rather than the frame.
    *
    * Font and colour are per line when the script gave them -- APOS scripts do,
    * through drawString(text, x, y, font, colour) -- and fall back to font 1 in
    * white, which is the only thing an STS script's three-array Stats can mean.
    */
   /*
    * Replays the shape commands a script queued in paint().
    *
    * Every APOS drawing call has a GameImage primitive behind it, so this is a
    * dispatch table rather than a renderer. Malformed commands are skipped
    * instead of throwing: the overlay belongs to the script, and a script bug
    * should cost the script its overlay and nothing else.
    */
   private final void drawScriptShapes(int[][] ops) {
      if (ops == null) {
         return;
      }

      for (int i = 0; i < ops.length; i++) {
         int[] op = ops[i];

         if (op == null || op.length == 0) {
            continue;
         }

         switch (op[0]) {
            case Script.OP_HLINE:
               if (op.length == 5) {
                  this.gameGraphics.drawLineX(op[1], op[2], op[3], op[4]);
               }
               break;
            case Script.OP_VLINE:
               if (op.length == 5) {
                  this.gameGraphics.drawLineY(op[1], op[2], op[3], op[4]);
               }
               break;
            case Script.OP_BOX_OUTLINE:
               /* drawBoxEdge takes a width and a height, not a second corner,
                  despite its x2/y2 parameter names -- see its body. */
               if (op.length == 6) {
                  this.gameGraphics.drawBoxEdge(op[1], op[2], op[3], op[4], op[5]);
               }
               break;
            case Script.OP_BOX_FILL:
               if (op.length == 6) {
                  this.gameGraphics.drawBox(op[1], op[2], op[3], op[4], op[5]);
               }
               break;
            case Script.OP_BOX_ALPHA:
               if (op.length == 7) {
                  this.gameGraphics.drawBoxAlpha(op[1], op[2], op[3], op[4], op[6], op[5]);
               }
               break;
            case Script.OP_CIRCLE:
               if (op.length == 6) {
                  this.drawScriptCircle(op[1], op[2], op[3], op[4], op[5]);
               }
               break;
            case Script.OP_PIXEL:
               if (op.length == 4) {
                  this.gameGraphics.setPixelColour(op[1], op[2], op[3]);
               }
               break;
         }
      }
   }

   /*
    * A filled circle, as horizontal spans. GameImage has no circle primitive
    * and drawBoxAlpha is the only thing here that understands transparency, so
    * each span goes through it.
    */
   private final void drawScriptCircle(int cx, int cy, int radius, int colour, int trans) {
      if (radius <= 0) {
         return;
      }

      for (int dy = -radius; dy <= radius; dy++) {
         int half = (int) Math.sqrt((double) (radius * radius - dy * dy));

         if (half > 0) {
            this.gameGraphics.drawBoxAlpha(cx - half, cy + dy, half * 2, 1, colour, trans);
         }
      }
   }

   private final void drawScriptOverlay() {
      if (this.scriptRunner == null) {
         return;
      }

      Stats show = this.scriptRunner.stats();

      /*
       * Shapes first, text over them, because a script that draws a box and
       * then labels it means the label to be readable.
       *
       * Read after stats(), which is what runs paint() and fills the buffer,
       * and independently of whether paint() produced any text -- an overlay
       * that is all boxes and no strings is a perfectly ordinary progress bar.
       */
      this.drawScriptShapes(this.scriptRunner.shapes());

      if (show == null || show.a == null || show.b == null || show.c == null) {
         return;
      }

      int lines = Math.min(show.a.length, Math.min(show.b.length, show.c.length));

      for (int i = 0; i < lines; i++) {
         if (show.a[i] != null) {
            int font = show.fonts == null ? 1 : show.fonts[i];
            int colour = show.colours == null ? 16777215 : show.colours[i];
            this.gameGraphics.drawString(show.a[i], show.b[i], show.c[i], font, colour);
         }
      }
   }

   /*
    * Keys reach a script through GameWindow's hook rather than an override,
    * because keyDown() is final. Only while logged in: a script has no business
    * seeing what is typed into the password box.
    */
   @Override
   protected final void handleKeyPressed(int key) {
      if (this.scriptRunner != null && this.loggedIn == 1) {
         this.scriptRunner.fireKeyPressed(key);
      }
   }

   private final void drawRightClickMenu() {
      if (this.mouseButtonClick == 0) {
         if (super.mouseX >= this.menuX - 10
            && super.mouseY >= this.menuY - 10
            && super.mouseX <= this.menuX + this.menuWidth + 10
            && super.mouseY <= this.menuY + this.menuHeight + 10) {
            this.gameGraphics.drawBoxAlpha(this.menuX, this.menuY, this.menuWidth, this.menuHeight, 13684944, 160);
            this.gameGraphics.drawString("Choose option", this.menuX + 2, this.menuY + 12, 1, 65535);

            for (int j = 0; j < this.menuLength; j++) {
               int l = this.menuX + 2;
               int j1 = this.menuY + 27 + j * 15;
               int k1 = 16777215;
               if (super.mouseX > l - 2 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && super.mouseX < l - 3 + this.menuWidth) {
                  k1 = 16776960;
               }

               this.gameGraphics.drawString(this.menuText1[this.menuIndexes[j]] + " " + this.menuText2[this.menuIndexes[j]], l, j1, 1, k1);
            }
         } else {
            this.showRightClickMenu = false;
         }
      } else {
         for (int i = 0; i < this.menuLength; i++) {
            int k = this.menuX + 2;
            int i1 = this.menuY + 27 + i * 15;
            if (super.mouseX > k - 2 && super.mouseY > i1 - 12 && super.mouseY < i1 + 4 && super.mouseX < k - 3 + this.menuWidth) {
               this.menuClick(this.menuIndexes[i]);
               break;
            }
         }

         this.mouseButtonClick = 0;
         this.showRightClickMenu = false;
      }
   }

   @Override
   protected final void resetIntVars() {
      this.systemUpdate = 0;
      this.loginScreenNumber = 0;
      this.loggedIn = 0;
      this.logoutTimeout = 0;
   }

   private final void drawQuestionMenu() {
      if (this.mouseButtonClick == 0) {
         for (int j = 0; j < this.questionMenuCount; j++) {
            int k = 65535;
            if (super.mouseX < this.gameGraphics.textWidth(this.questionMenuAnswer[j], 1) && super.mouseY > j * 12 && super.mouseY < 12 + j * 12) {
               k = 16711680;
            }

            this.gameGraphics.drawString(this.questionMenuAnswer[j], 6, 12 + j * 12, 1, k);
         }
      } else {
         for (int i = 0; i < this.questionMenuCount; i++) {
            if (super.mouseX < this.gameGraphics.textWidth(this.questionMenuAnswer[i], 1) && super.mouseY > i * 12 && super.mouseY < 12 + i * 12) {
               super.streamClass.createPacket(154);
               super.streamClass.addByte(i);
               super.streamClass.formatPacket();
               break;
            }
         }

         this.mouseButtonClick = 0;
         this.showQuestionMenu = false;
      }
   }

   final void walkToAction(int actionX, int actionY, int actionType) {
      if (actionType == 0) {
         this.sendWalkCommand(this.sectionX, this.sectionY, actionX, actionY - 1, actionX, actionY, false, true);
      } else if (actionType == 1) {
         this.sendWalkCommand(this.sectionX, this.sectionY, actionX - 1, actionY, actionX, actionY, false, true);
      } else {
         this.sendWalkCommand(this.sectionX, this.sectionY, actionX, actionY, actionX, actionY, true, true);
      }
   }

   private final void garbageCollect() {
      try {
         if (this.gameGraphics != null) {
            this.gameGraphics.cleanupSprites();
            this.gameGraphics.imagePixelArray = null;
            this.gameGraphics = null;
         }

         if (this.gameCamera != null) {
            this.gameCamera.cleanupModels();
            this.gameCamera = null;
         }

         this.gameDataModels = null;
         this.objectModelArray = null;
         this.doorModel = null;
         this.mobArray = null;
         this.playerArray = null;
         this.npcRecordArray = null;
         this.npcArray = null;
         this.ourPlayer = null;
         if (this.engineHandle != null) {
            this.engineHandle.terrainModels = null;
            this.engineHandle.wallModels = (Model[][])null;
            this.engineHandle.roofModels = (Model[][])null;
            this.engineHandle.landscapeModel = null;
            this.engineHandle = null;
         }

         System.gc();
      } catch (Exception var2) {
      }
   }

   @Override
   protected final void loginScreenPrint(String s, String s1) {
      if (this.loginScreenNumber == 1) {
         this.menuNewUser.updateText(this.newUserStatusText, s + " " + s1);
      }

      if (this.loginScreenNumber == 2) {
         this.menuLogin.updateText(this.loginStatusText, s + " " + s1);
      }

      this.drawLoginScreen();
      this.resetCurrentTimeArray();
   }

   private final void drawInventoryRightClickMenu() {
      int i = 2203 - (this.sectionY + this.wildY + this.areaY);
      if (this.sectionX + this.wildX + this.areaX >= 2640) {
         i = -50;
      }

      int j = -1;

      for (int k = 0; k < this.objectCount; k++) {
         this.objectAlreadyInMenu[k] = false;
      }

      for (int l = 0; l < this.doorCount; l++) {
         this.doorAlreadyInMenu[l] = false;
      }

      int i1 = this.gameCamera.getMousePickedCount();
      Model[] models = this.gameCamera.getMousePickedModels();
      int[] ai = this.gameCamera.getMousePickedFaces();

      for (int j1 = 0; j1 < i1 && this.menuLength <= 200; j1++) {
         int k1 = ai[j1];
         Model model = models[j1];
         if (model.faceTag[k1] <= 65535 || model.faceTag[k1] >= 200000 && model.faceTag[k1] <= 300000) {
            if (model == this.gameCamera.spriteModel) {
               int i2 = model.faceTag[k1] % 10000;
               int l2 = model.faceTag[k1] / 10000;
               if (l2 == 1) {
                  String s = "";
                  int k3 = 0;
                  if (this.ourPlayer.level > 0 && this.playerArray[i2].level > 0) {
                     k3 = this.ourPlayer.level - this.playerArray[i2].level;
                  }

                  if (k3 < 0) {
                     s = "@or1@";
                  }

                  if (k3 < -3) {
                     s = "@or2@";
                  }

                  if (k3 < -6) {
                     s = "@or3@";
                  }

                  if (k3 < -9) {
                     s = "@red@";
                  }

                  if (k3 > 0) {
                     s = "@gr1@";
                  }

                  if (k3 > 3) {
                     s = "@gr2@";
                  }

                  if (k3 > 6) {
                     s = "@gr3@";
                  }

                  if (k3 > 9) {
                     s = "@gre@";
                  }

                  s = " " + s + "(level-" + this.playerArray[i2].level + ")";
                  if (this.selectedSpell >= 0) {
                     if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 1
                        || EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 2
                        || EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 8
                        || EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 9) {
                        this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on";
                        this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                        this.menuID[this.menuLength] = 800;
                        this.menuActionX[this.menuLength] = this.playerArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.playerArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                        this.menuActionVariable[this.menuLength] = this.selectedSpell;
                        this.menuLength++;
                     }
                  } else if (this.selectedItem >= 0) {
                     this.menuText1[this.menuLength] = "Use " + this.selectedItemName + " with";
                     this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                     this.menuID[this.menuLength] = 810;
                     this.menuActionX[this.menuLength] = this.playerArray[i2].currentX;
                     this.menuActionY[this.menuLength] = this.playerArray[i2].currentY;
                     this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                     this.menuActionVariable[this.menuLength] = this.selectedItem;
                     this.menuLength++;
                  } else {
                     if (i > 0 && (this.playerArray[i2].currentY - 64) / this.magicLoc + this.wildY + this.areaY < 2203) {
                        this.menuText1[this.menuLength] = "Attack";
                        this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                        if (k3 >= 0 && k3 < 5) {
                           this.menuID[this.menuLength] = 805;
                        } else {
                           this.menuID[this.menuLength] = 2805;
                        }

                        this.menuActionX[this.menuLength] = this.playerArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.playerArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                        this.menuLength++;
                     } else if (this.sectionX + this.areaX >= 395
                        && this.sectionX + this.areaX <= 438
                        && this.sectionY + this.areaY >= 3560
                        && this.sectionY + this.areaY <= 3600) {
                        this.menuText1[this.menuLength] = "Attack";
                        this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                        if (k3 >= 0 && k3 < 5) {
                           this.menuID[this.menuLength] = 805;
                        } else {
                           this.menuID[this.menuLength] = 2805;
                        }

                        this.menuActionX[this.menuLength] = this.playerArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.playerArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                        this.menuLength++;
                     } else if (Config.SERVER_WORLD != 2) {
                        this.menuText1[this.menuLength] = "Duel with";
                        this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                        this.menuActionX[this.menuLength] = this.playerArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.playerArray[i2].currentY;
                        this.menuID[this.menuLength] = 2806;
                        this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                        this.menuLength++;
                     } else {
                        this.menuText1[this.menuLength] = "Attack";
                        this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                        if (k3 >= 0 && k3 < 5) {
                           this.menuID[this.menuLength] = 805;
                        } else {
                           this.menuID[this.menuLength] = 2805;
                        }

                        this.menuActionX[this.menuLength] = this.playerArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.playerArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                        this.menuLength++;
                     }

                     this.menuText1[this.menuLength] = "Trade with";
                     this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                     this.menuID[this.menuLength] = 2810;
                     this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                     this.menuLength++;
                     this.menuText1[this.menuLength] = "Follow";
                     this.menuText2[this.menuLength] = "@whi@" + this.playerArray[i2].name + s;
                     this.menuID[this.menuLength] = 2820;
                     this.menuActionType[this.menuLength] = this.playerArray[i2].serverIndex;
                     this.menuLength++;
                  }
               } else if (l2 == 2) {
                  ItemDef itemDef = EntityHandler.getItemDef(this.groundItemType[i2]);
                  if (this.selectedSpell >= 0) {
                     if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 3) {
                        this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on";
                        this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                        this.menuID[this.menuLength] = 200;
                        this.menuActionX[this.menuLength] = this.groundItemX[i2];
                        this.menuActionY[this.menuLength] = this.groundItemY[i2];
                        this.menuActionType[this.menuLength] = this.groundItemType[i2];
                        this.menuActionVariable[this.menuLength] = this.selectedSpell;
                        this.menuLength++;
                     }
                  } else if (this.selectedItem >= 0) {
                     this.menuText1[this.menuLength] = "Use " + this.selectedItemName + " with";
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 210;
                     this.menuActionX[this.menuLength] = this.groundItemX[i2];
                     this.menuActionY[this.menuLength] = this.groundItemY[i2];
                     this.menuActionType[this.menuLength] = this.groundItemType[i2];
                     this.menuActionVariable[this.menuLength] = this.selectedItem;
                     this.menuLength++;
                  } else {
                     this.menuText1[this.menuLength] = "Take";
                     this.menuText2[this.menuLength] = "@lre@" + itemDef.getName();
                     this.menuID[this.menuLength] = 220;
                     this.menuActionX[this.menuLength] = this.groundItemX[i2];
                     this.menuActionY[this.menuLength] = this.groundItemY[i2];
                     this.menuActionType[this.menuLength] = this.groundItemType[i2];
                     this.menuLength++;
                     this.menuText1[this.menuLength] = "Examine";
                     this.menuText2[this.menuLength] = "@lre@"
                        + itemDef.getName()
                        + (
                           this.ourPlayer.admin >= 2
                              ? " @or1@("
                                 + this.groundItemType[i2]
                                 + ":"
                                 + (this.groundItemX[i2] + this.areaX)
                                 + ","
                                 + (this.groundItemY[i2] + this.areaY)
                                 + ")"
                              : ""
                        );
                     this.menuID[this.menuLength] = 3200;
                     this.menuActionType[this.menuLength] = this.groundItemType[i2];
                     this.menuLength++;
                  }
               } else if (l2 == 3) {
                  String s1 = "";
                  int l3 = -1;
                  NPCDef npcDef = EntityHandler.getNpcDef(this.npcArray[i2].type);
                  if (npcDef.isAttackable()) {
                     int j4 = (npcDef.getAtt() + npcDef.getDef() + npcDef.getStr() + npcDef.getHits()) / 4;
                     int k4 = (this.playerStatBase[0] + this.playerStatBase[1] + this.playerStatBase[2] + this.playerStatBase[3] + 27) / 4;
                     l3 = k4 - j4;
                     s1 = "@yel@";
                     if (l3 < 0) {
                        s1 = "@or1@";
                     }

                     if (l3 < -3) {
                        s1 = "@or2@";
                     }

                     if (l3 < -6) {
                        s1 = "@or3@";
                     }

                     if (l3 < -9) {
                        s1 = "@red@";
                     }

                     if (l3 > 0) {
                        s1 = "@gr1@";
                     }

                     if (l3 > 3) {
                        s1 = "@gr2@";
                     }

                     if (l3 > 6) {
                        s1 = "@gr3@";
                     }

                     if (l3 > 9) {
                        s1 = "@gre@";
                     }

                     s1 = " " + s1 + "(level-" + j4 + ")";
                  }

                  if (this.selectedSpell >= 0) {
                     if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 2) {
                        this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on";
                        this.menuText2[this.menuLength] = "@yel@" + npcDef.getName();
                        this.menuID[this.menuLength] = 700;
                        this.menuActionX[this.menuLength] = this.npcArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.npcArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.npcArray[i2].serverIndex;
                        this.menuActionVariable[this.menuLength] = this.selectedSpell;
                        this.menuLength++;
                     }
                  } else if (this.selectedItem >= 0) {
                     this.menuText1[this.menuLength] = "Use " + this.selectedItemName + " with";
                     this.menuText2[this.menuLength] = "@yel@" + npcDef.getName();
                     this.menuID[this.menuLength] = 710;
                     this.menuActionX[this.menuLength] = this.npcArray[i2].currentX;
                     this.menuActionY[this.menuLength] = this.npcArray[i2].currentY;
                     this.menuActionType[this.menuLength] = this.npcArray[i2].serverIndex;
                     this.menuActionVariable[this.menuLength] = this.selectedItem;
                     this.menuLength++;
                  } else {
                     if (npcDef.isAttackable()) {
                        this.menuText1[this.menuLength] = "Attack";
                        this.menuText2[this.menuLength] = "@yel@" + npcDef.getName() + s1;
                        if (l3 >= 0) {
                           this.menuID[this.menuLength] = 715;
                        } else {
                           this.menuID[this.menuLength] = 2715;
                        }

                        this.menuActionX[this.menuLength] = this.npcArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.npcArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.npcArray[i2].serverIndex;
                        this.menuLength++;
                     }

                     this.menuText1[this.menuLength] = "Talk-to";
                     this.menuText2[this.menuLength] = "@yel@" + npcDef.getName();
                     this.menuID[this.menuLength] = 720;
                     this.menuActionX[this.menuLength] = this.npcArray[i2].currentX;
                     this.menuActionY[this.menuLength] = this.npcArray[i2].currentY;
                     this.menuActionType[this.menuLength] = this.npcArray[i2].serverIndex;
                     this.menuLength++;
                     if (!npcDef.getCommand().equals("")) {
                        this.menuText1[this.menuLength] = npcDef.getCommand();
                        this.menuText2[this.menuLength] = "@yel@" + npcDef.getName();
                        this.menuID[this.menuLength] = 725;
                        this.menuActionX[this.menuLength] = this.npcArray[i2].currentX;
                        this.menuActionY[this.menuLength] = this.npcArray[i2].currentY;
                        this.menuActionType[this.menuLength] = this.npcArray[i2].serverIndex;
                        this.menuLength++;
                     }

                     this.menuText1[this.menuLength] = "Examine";
                     this.menuText2[this.menuLength] = "@yel@" + npcDef.getName() + (this.ourPlayer.admin >= 2 ? " @or1@(" + this.npcArray[i2].type + ")" : "");
                     this.menuID[this.menuLength] = 3700;
                     this.menuActionType[this.menuLength] = this.npcArray[i2].type;
                     this.menuLength++;
                  }
               }
            } else if (model != null && model.key >= 10000) {
               int j2 = model.key - 10000;
               int i3 = this.doorType[j2];
               if (!this.doorAlreadyInMenu[j2]) {
                  if (this.selectedSpell >= 0) {
                     if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 4) {
                        this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on";
                        this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getDoorDef(i3).getName();
                        this.menuID[this.menuLength] = 300;
                        this.menuActionX[this.menuLength] = this.doorX[j2];
                        this.menuActionY[this.menuLength] = this.doorY[j2];
                        this.menuActionType[this.menuLength] = this.doorDirection[j2];
                        this.menuActionVariable[this.menuLength] = this.selectedSpell;
                        this.menuLength++;
                     }
                  } else if (this.selectedItem >= 0) {
                     this.menuText1[this.menuLength] = "Use " + this.selectedItemName + " with";
                     this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getDoorDef(i3).getName();
                     this.menuID[this.menuLength] = 310;
                     this.menuActionX[this.menuLength] = this.doorX[j2];
                     this.menuActionY[this.menuLength] = this.doorY[j2];
                     this.menuActionType[this.menuLength] = this.doorDirection[j2];
                     this.menuActionVariable[this.menuLength] = this.selectedItem;
                     this.menuLength++;
                  } else {
                     if (!EntityHandler.getDoorDef(i3).getCommand1().equalsIgnoreCase("WalkTo")) {
                        this.menuText1[this.menuLength] = EntityHandler.getDoorDef(i3).getCommand1();
                        this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getDoorDef(i3).getName();
                        this.menuID[this.menuLength] = 320;
                        this.menuActionX[this.menuLength] = this.doorX[j2];
                        this.menuActionY[this.menuLength] = this.doorY[j2];
                        this.menuActionType[this.menuLength] = this.doorDirection[j2];
                        this.menuLength++;
                     }

                     if (!EntityHandler.getDoorDef(i3).getCommand2().equalsIgnoreCase("Examine")) {
                        this.menuText1[this.menuLength] = EntityHandler.getDoorDef(i3).getCommand2();
                        this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getDoorDef(i3).getName();
                        this.menuID[this.menuLength] = 2300;
                        this.menuActionX[this.menuLength] = this.doorX[j2];
                        this.menuActionY[this.menuLength] = this.doorY[j2];
                        this.menuActionType[this.menuLength] = this.doorDirection[j2];
                        this.menuLength++;
                     }

                     this.menuText1[this.menuLength] = "Examine";
                     this.menuText2[this.menuLength] = "@cya@"
                        + EntityHandler.getDoorDef(i3).getName()
                        + (this.ourPlayer.admin >= 2 ? " @or1@(" + i3 + ":" + (this.doorX[j2] + this.areaX) + "," + (this.doorY[j2] + this.areaY) + ")" : "");
                     this.menuID[this.menuLength] = 3300;
                     this.menuActionType[this.menuLength] = i3;
                     this.menuLength++;
                  }

                  this.doorAlreadyInMenu[j2] = true;
               }
            } else if (model != null && model.key >= 0) {
               int k2 = model.key;
               int j3 = this.objectType[k2];
               if (!this.objectAlreadyInMenu[k2]) {
                  if (this.selectedSpell >= 0) {
                     if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 5) {
                        this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on";
                        this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getObjectDef(j3).getName();
                        this.menuID[this.menuLength] = 400;
                        this.menuActionX[this.menuLength] = this.objectX[k2];
                        this.menuActionY[this.menuLength] = this.objectY[k2];
                        this.menuActionType[this.menuLength] = this.objectID[k2];
                        this.menuActionVariable[this.menuLength] = this.objectType[k2];
                        this.menuActionVariable2[this.menuLength] = this.selectedSpell;
                        this.menuLength++;
                     }
                  } else if (this.selectedItem >= 0) {
                     this.menuText1[this.menuLength] = "Use " + this.selectedItemName + " with";
                     this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getObjectDef(j3).getName();
                     this.menuID[this.menuLength] = 410;
                     this.menuActionX[this.menuLength] = this.objectX[k2];
                     this.menuActionY[this.menuLength] = this.objectY[k2];
                     this.menuActionType[this.menuLength] = this.objectID[k2];
                     this.menuActionVariable[this.menuLength] = this.objectType[k2];
                     this.menuActionVariable2[this.menuLength] = this.selectedItem;
                     this.menuLength++;
                  } else {
                     if (!EntityHandler.getObjectDef(j3).getCommand1().equalsIgnoreCase("WalkTo")) {
                        this.menuText1[this.menuLength] = EntityHandler.getObjectDef(j3).getCommand1();
                        this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getObjectDef(j3).getName();
                        this.menuID[this.menuLength] = 420;
                        this.menuActionX[this.menuLength] = this.objectX[k2];
                        this.menuActionY[this.menuLength] = this.objectY[k2];
                        this.menuActionType[this.menuLength] = this.objectID[k2];
                        this.menuActionVariable[this.menuLength] = this.objectType[k2];
                        this.menuLength++;
                     }

                     if (!EntityHandler.getObjectDef(j3).getCommand2().equalsIgnoreCase("Examine")) {
                        this.menuText1[this.menuLength] = EntityHandler.getObjectDef(j3).getCommand2();
                        this.menuText2[this.menuLength] = "@cya@" + EntityHandler.getObjectDef(j3).getName();
                        this.menuID[this.menuLength] = 2400;
                        this.menuActionX[this.menuLength] = this.objectX[k2];
                        this.menuActionY[this.menuLength] = this.objectY[k2];
                        this.menuActionType[this.menuLength] = this.objectID[k2];
                        this.menuActionVariable[this.menuLength] = this.objectType[k2];
                        this.menuLength++;
                     }

                     this.menuText1[this.menuLength] = "Examine";
                     this.menuText2[this.menuLength] = "@cya@"
                        + EntityHandler.getObjectDef(j3).getName()
                        + (
                           this.ourPlayer.admin >= 2
                              ? " @or1@(" + j3 + ":" + (this.objectX[k2] + this.areaX) + "," + (this.objectY[k2] + this.areaY) + ")"
                              : ""
                        );
                     this.menuID[this.menuLength] = 3400;
                     this.menuActionType[this.menuLength] = j3;
                     this.menuLength++;
                  }

                  this.objectAlreadyInMenu[k2] = true;
               }
            } else {
               if (k1 >= 0) {
                  k1 = model.faceTag[k1] - 200000;
               }

               if (k1 >= 0) {
                  j = k1;
               }
            }
         }
      }

      if (this.selectedSpell >= 0
         && (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() <= 1 || EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 7)) {
         this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on self";
         this.menuText2[this.menuLength] = "";
         this.menuID[this.menuLength] = 1000;
         this.menuActionType[this.menuLength] = this.selectedSpell;
         this.menuLength++;
      }

      if (j != -1) {
         if (this.selectedSpell >= 0) {
            if (EntityHandler.getSpellDef(this.selectedSpell).getSpellType() == 6) {
               this.menuText1[this.menuLength] = "Cast " + EntityHandler.getSpellDef(this.selectedSpell).getName() + " on ground";
               this.menuText2[this.menuLength] = "";
               this.menuID[this.menuLength] = 900;
               this.menuActionX[this.menuLength] = this.engineHandle.selectedX[j];
               this.menuActionY[this.menuLength] = this.engineHandle.selectedY[j];
               this.menuActionType[this.menuLength] = this.selectedSpell;
               this.menuLength++;
               return;
            }
         } else if (this.selectedItem < 0) {
            this.menuText1[this.menuLength] = "Walk here";
            this.menuText2[this.menuLength] = "";
            this.menuID[this.menuLength] = 920;
            this.menuActionX[this.menuLength] = this.engineHandle.selectedX[j];
            this.menuActionY[this.menuLength] = this.engineHandle.selectedY[j];
            this.menuLength++;
         }
      }
   }

   /*
    * Boot, in the order the design actually requires: choose a server, THEN
    * download that server's content.
    *
    * It used to run the other way round -- the whole 4.7 MB came down before
    * the Worlds screen existed -- and that was wrong twice over. A fresh
    * install has no default_server, so CACHE_URL fell back to the cache_url
    * line in settings.ini, which is ours: the client pulled our maps, item
    * names and models onto a machine belonging to somebody who had not been
    * asked yet, and who might have been about to pick another server
    * entirely. And once loaded, they stayed loaded, so joining a second world
    * later in the same session ran that world against the first one's content.
    * Definitions that disagree do not throw; they misbehave quietly, which is
    * the walls-that-block-but-do-not-render class of bug.
    *
    * So: nothing is fetched until a world is named, and every world is
    * fetched from its own cache_url. The first file in the list is
    * Loading.xml.data, which means the splash on screen while a server's
    * content downloads is that server's own splash -- the answer to "which
    * one did I just click" arrives before anything else does.
    */
   @Override
   protected final void startGame() {
      if (Config.DEFAULT_TARGET.length() == 0) {
         this.chooseWorldBeforeBoot();
      }

      this.loadGameData();
   }

   private final void loadGameData() {
      System.out.println("Starting game...");
      // Both modes stream assets from Config.CACHE_URL, as the webclient
      // always did; desktop mode's read-straight-off-disk shortcut is gone.
      this.loadCacheFromMirrors();
      int i = 0;

      for (int j = 0; j < 99; j++) {
         int k = j + 1;
         int i1 = (int)((double)k + 300.0 * Math.pow(2.0, (double)k / 7.0));
         i += i1;
         this.experienceArray[j] = (i & 268435452) / 4;
      }

      System.out.println("Loaded experience array...");
      super.yOffset = 0;

      /*
       * Loading.xml.data is listed first so the splash can go up the moment it
       * lands rather than after the whole 4.7 MB -- otherwise the download runs
       * against a black rectangle with a bar floating in it. Its bytes are
       * dropped straight after: 2.3 MB of the download, needed exactly once.
       */
      for (String s : this.gamefiles) {
         this.loadcache(s);
         if (s.equals("Loading.xml.data")) {
            this.setLogo(Toolkit.getDefaultToolkit().createImage(Assets.get(s)));
            Assets.drop(s);
            System.out.println("Set loading logo...");
         }
      }

      System.out.println("Fetched " + this.gamefiles.length + " assets from " + Config.CACHE_URL);
      GameWindowMiddleMan.maxPacketReadCount = 1000;
      System.out.println("Loading Config Filter...");
      this.loadConfigFilter();
      if (!this.lastLoadedNull) {
         this.drawLoadingBarText(100, "Synchronization Finished...");
         this.aGraphics936 = this.getGraphics();
         this.changeThreadSleepModifier(50);
         this.gameGraphics = new GameImageMiddleMan(this.windowWidth, this.surfaceHeight(), 4000, this);
         this.gameGraphics._mudclient = this;
         this.gameGraphics.setDimensions(0, 0, this.windowWidth, this.surfaceHeight());
         Menu.drawBackgroundTexture = false;
         this.spellMenu = new Menu(this.gameGraphics, 5);
         int l = this.gameGraphics.menuDefaultWidth - 199;
         byte byte0 = 36;
         this.spellMenuHandle = this.spellMenu.makeInteractiveTextList(l, byte0 + 24, 196, 90, 1, 500, true);
         this.friendsMenu = new Menu(this.gameGraphics, 5);
         this.friendsMenuHandle = this.friendsMenu.makeInteractiveTextList(l, byte0 + 40, 196, 126, 1, 500, true);
         /* The quest list fills the whole panel body below the tabs, 251 of the
            275 it is tall. Jagex's numbers. */
         this.questMenu = new Menu(this.gameGraphics, 5);
         this.questMenuHandle = this.questMenu.makeInteractiveTextList(l, byte0 + 24, 196, 251, 1, 500, true);
         System.out.println("Loading media...");
         this.loadMedia();
         if (!this.lastLoadedNull) {
            System.out.println("Loading entities...");
            this.loadEntity();
            if (!this.lastLoadedNull) {
               this.gameCamera = new Camera(this.gameGraphics, 15000, 15000, 1000);
               this.gameCamera
                  .setCameraSize(this.windowWidth / 2, this.windowHeight / 2, this.windowWidth / 2, this.windowHeight / 2, this.windowWidth, this.cameraSizeInt);
               this.gameCamera.clipFar3d = 2400;
               this.gameCamera.clipFar2d = 2400;
               this.gameCamera.fogZFalloff = 1;
               this.gameCamera.fogZDistance = 2300;
               this.gameCamera.setLight(-50, -10, -50);
               this.engineHandle = new EngineHandle(this.gameCamera, this.gameGraphics);
               System.out.println("Loading textures...");
               this.loadTextures();
               if (!this.lastLoadedNull) {
                  System.out.println("Loading models...");
                  this.loadModels();
                  if (!this.lastLoadedNull) {
                     System.out.println("Loading sounds...");
                     this.loadSounds();
                     if (!this.lastLoadedNull) {
                        this.drawLoadingBarText(100, "Starting RSCD Community Client...");
                        this.drawGameMenu();
                        this.makeLoginMenus();
                        this.makeCharacterDesignMenu();
                        this.renderLoginScreenViewports();
                        /* Recorded before resetLoginVars(), which is what puts
                           the login or Worlds screen up: from here on, "which
                           content is loaded" is a question with an answer, and
                           joinWorld() compares against it. */
                        this.assetsLoaded = true;
                        this.loadedCacheUrl = Config.CACHE_URL;
                        this.resetLoginVars();
                     }
                  }
               }
            }
         }
      }
   }

   /*
    * Render the three login-screen backdrops -- see the LOGIN_VIEW_* constants.
    *
    * Called once, from startGame(), after loadModels(): everything here needs
    * the object models and the landscape archive. The region is dropped again
    * afterwards, so the login screen holds nothing but three sprites; the
    * client reloads whatever region it actually needs when the player logs in.
    *
    * If any of it fails the login screen falls back to the bare stone logo
    * rather than taking the client down with it.
    */
   private final void renderLoginScreenViewports() {
      try {
         this.engineHandle.loadArea(LOGIN_VIEW_REGION_X, LOGIN_VIEW_REGION_Y, 0);
         this.engineHandle.placeMapObjects(this.gameDataModels);

         for (int view = 0; view < LOGIN_VIEWS.length; view++) {
            /*
             * The last shot looks down over the town, so the roofs and the two
             * upper storeys come off first or they hide the streets. Ground
             * level is left alone.
             */
            if (view == 2) {
               for (int i = 0; i < 64; i++) {
                  this.gameCamera.removeModel(this.engineHandle.roofModels[0][i]);
                  this.gameCamera.removeModel(this.engineHandle.wallModels[1][i]);
                  this.gameCamera.removeModel(this.engineHandle.roofModels[1][i]);
                  this.gameCamera.removeModel(this.engineHandle.wallModels[2][i]);
                  this.gameCamera.removeModel(this.engineHandle.roofModels[2][i]);
               }
            }

            int cameraX = LOGIN_VIEWS[view][0];
            int cameraZ = LOGIN_VIEWS[view][1];
            // Far clip and fog are pushed well past the in-game 2400/2300 --
            // these are wide establishing shots, not a player's-eye view.
            this.gameCamera.clipFar3d = 4100;
            this.gameCamera.clipFar2d = 4100;
            this.gameCamera.fogZFalloff = 1;
            this.gameCamera.fogZDistance = 4000;
            this.gameCamera
               .setCamera(
                  cameraX,
                  -this.engineHandle.getAveragedElevation(cameraX, cameraZ),
                  cameraZ,
                  912,
                  LOGIN_VIEWS[view][3],
                  0,
                  LOGIN_VIEWS[view][2]
               );
            this.gameGraphics.f1Toggle = false;
            this.gameGraphics.clearScreen();
            this.gameCamera.finishCamera();
            // Twice: the backdrop sits behind white menu text, so it is
            // knocked well down before anything is drawn over it.
            this.gameGraphics.fadePixels();
            this.gameGraphics.fadePixels();
            // Black bands top and bottom, then a box blur that feathers each
            // band into the scene so the capture fades out instead of cutting.
            this.gameGraphics.drawBox(0, 0, LOGIN_VIEW_WIDTH, 6, 0);

            for (int edge = 6; edge >= 1; edge--) {
               this.gameGraphics.blurRegion(0, edge, 0, edge, LOGIN_VIEW_WIDTH, 8);
            }

            this.gameGraphics.drawBox(0, 194, LOGIN_VIEW_WIDTH, 20, 0);

            for (int edge = 6; edge >= 1; edge--) {
               this.gameGraphics.blurRegion(0, edge, 0, 194 - edge, LOGIN_VIEW_WIDTH, 8);
            }

            this.gameGraphics.drawPicture(15, 15, LOGIN_LOGO_SPRITE);
            this.gameGraphics.storeSpriteVert(LOGIN_VIEW_SPRITES[view], 0, 0, LOGIN_VIEW_WIDTH, LOGIN_VIEW_HEIGHT);
         }

         this.loginBackdropReady = true;
      } catch (Throwable var5) {
         System.err.println("Could not render the login screen backdrop, falling back to the logo:");
         var5.printStackTrace();
         this.loginBackdropReady = false;
      }

      this.gameCamera.clipFar3d = 2400;
      this.gameCamera.clipFar2d = 2400;
      this.gameCamera.fogZFalloff = 1;
      this.gameCamera.fogZDistance = 2300;
      this.engineHandle.garbageCollect();
   }

   private final void loadSprite(int id, String packageName, int amount) {
      for (int i = id; i < id + amount; i++) {
         if (!this.gameGraphics.loadSprite(i, packageName)) {
            this.lastLoadedNull = true;
            return;
         }
      }
   }

   private final void loadMedia() {
      this.drawLoadingBarText(20, "Unpacking Media");
      this.loadSprite(2000, "media", 1);
      this.loadSprite(2001, "media", 6);
      this.loadSprite(2009, "media", 1);
      this.loadSprite(2010, "media", 1);
      this.loadSprite(2011, "media", 3);
      this.loadSprite(2014, "media", 8);
      this.loadSprite(2022, "media", 1);
      this.loadSprite(2023, "media", 1);
      this.loadSprite(2024, "media", 1);
      this.loadSprite(2025, "media", 2);
      this.loadSprite(2100, "media", 2);
      this.loadSprite(2102, "media", 4);
      this.loadSprite(2106, "media", 2);
      this.loadSprite(3160, "media", 7);
      this.loadSprite(3150, "media", 1);
      int i = EntityHandler.invPictureCount();

      for (int j = 1; i > 0; j++) {
         int k = i;
         i -= 30;
         if (k > 30) {
            k = 30;
         }

         this.loadSprite(2150 + (j - 1) * 30, "media.object", k);
      }
   }

   private final void loadEntity() {
      this.drawLoadingBarText(36, "Unpacking Entities");
      int animationNumber = 0;

      label33:
      for (int animationIndex = 0; animationIndex < EntityHandler.animationCount(); animationIndex++) {
         String s = EntityHandler.getAnimationDef(animationIndex).getName();

         for (int nextAnimationIndex = 0; nextAnimationIndex < animationIndex; nextAnimationIndex++) {
            if (EntityHandler.getAnimationDef(nextAnimationIndex).getName().equalsIgnoreCase(s)) {
               EntityHandler.getAnimationDef(animationIndex).number = EntityHandler.getAnimationDef(nextAnimationIndex).getNumber();
               continue label33;
            }
         }

         this.loadSprite(animationNumber, "entity", 15);
         if (EntityHandler.getAnimationDef(animationIndex).hasA()) {
            this.loadSprite(animationNumber + 15, "entity", 3);
         }

         if (EntityHandler.getAnimationDef(animationIndex).hasF()) {
            this.loadSprite(animationNumber + 18, "entity", 9);
         }

         EntityHandler.getAnimationDef(animationIndex).number = animationNumber;
         animationNumber += 27;
      }
   }

   private final void loadTextures() {
      this.drawLoadingBarText(52, "Unpacking Textures");
      this.gameCamera.allocateTextures(EntityHandler.textureCount(), 7, 11);

      for (int i = 0; i < EntityHandler.textureCount(); i++) {
         this.loadSprite(3220 + i, "texture", 1);
         Sprite sprite = this.gameGraphics.sprites[3220 + i];
         int length = sprite.getWidth() * sprite.getHeight();
         int[] pixels = sprite.getPixels();
         int[] ai1 = new int[32768];

         for (int k = 0; k < length; k++) {
            ai1[((pixels[k] & 16252928) >> 9) + ((pixels[k] & 63488) >> 6) + ((pixels[k] & 248) >> 3)]++;
         }

         int[] dictionary = new int[256];
         dictionary[0] = 16711935;
         int[] temp = new int[256];

         for (int i1 = 0; i1 < ai1.length; i1++) {
            int j1 = ai1[i1];
            if (j1 > temp[255]) {
               for (int k1 = 1; k1 < 256; k1++) {
                  if (j1 > temp[k1]) {
                     for (int i2 = 255; i2 > k1; i2--) {
                        dictionary[i2] = dictionary[i2 - 1];
                        temp[i2] = temp[i2 - 1];
                     }

                     dictionary[k1] = ((i1 & 31744) << 9) + ((i1 & 992) << 6) + ((i1 & 31) << 3) + 263172;
                     temp[k1] = j1;
                     break;
                  }
               }
            }

            ai1[i1] = -1;
         }

         byte[] indices = new byte[length];

         for (int l1 = 0; l1 < length; l1++) {
            int j2 = pixels[l1];
            int k2 = ((j2 & 16252928) >> 9) + ((j2 & 63488) >> 6) + ((j2 & 248) >> 3);
            int l2 = ai1[k2];
            if (l2 == -1) {
               int i3 = 999999999;
               int j3 = j2 >> 16 & 0xFF;
               int k3 = j2 >> 8 & 0xFF;
               int l3 = j2 & 0xFF;

               for (int i4 = 0; i4 < 256; i4++) {
                  int j4 = dictionary[i4];
                  int k4 = j4 >> 16 & 0xFF;
                  int l4 = j4 >> 8 & 0xFF;
                  int i5 = j4 & 0xFF;
                  int j5 = (j3 - k4) * (j3 - k4) + (k3 - l4) * (k3 - l4) + (l3 - i5) * (l3 - i5);
                  if (j5 < i3) {
                     i3 = j5;
                     l2 = i4;
                  }
               }

               ai1[k2] = l2;
            }

            indices[l1] = (byte)l2;
         }

         this.gameCamera.defineTexture(i, indices, dictionary, sprite.getSomething1() / 64 - 1);
      }
   }

   private final void checkMouseStatus() {
      if (this.selectedSpell >= 0 || this.selectedItem >= 0) {
         this.menuText1[this.menuLength] = "Cancel";
         this.menuText2[this.menuLength] = "";
         this.menuID[this.menuLength] = 4000;
         this.menuLength++;
      }

      int i = 0;

      while (i < this.menuLength) {
         this.menuIndexes[i] = i++;
      }

      // The bytecode reused one int slot here for two unrelated purposes -- a
      // boolean sort sentinel and, further down, an int index -- so the
      // decompiler merged them into a single mistyped "flag". Split back apart.
      boolean sorted = false;

      while (!sorted) {
         sorted = true;

         for (int j = 0; j < this.menuLength - 1; j++) {
            int l = this.menuIndexes[j];
            int j1 = this.menuIndexes[j + 1];
            if (this.menuID[l] > this.menuID[j1]) {
               this.menuIndexes[j] = j1;
               this.menuIndexes[j + 1] = l;
               sorted = false;
            }
         }
      }

      if (this.menuLength > 20) {
         this.menuLength = 20;
      }

      if (this.menuLength > 0) {
         int labelIndex = -1;

         for (int i1 = 0; i1 < this.menuLength; i1++) {
            if (this.menuText2[this.menuIndexes[i1]] != null && this.menuText2[this.menuIndexes[i1]].length() > 0) {
               labelIndex = i1;
               break;
            }
         }

         String s = null;
         if ((this.selectedItem >= 0 || this.selectedSpell >= 0) && this.menuLength == 1) {
            s = "Choose a target";
         } else if ((this.selectedItem >= 0 || this.selectedSpell >= 0) && this.menuLength > 1) {
            s = "@whi@" + this.menuText1[this.menuIndexes[0]] + " " + this.menuText2[this.menuIndexes[0]];
         } else if (labelIndex != -1) {
            s = this.menuText2[this.menuIndexes[labelIndex]] + ": @whi@" + this.menuText1[this.menuIndexes[0]];
         }

         if (this.menuLength == 2 && s != null) {
            s = s + "@whi@ / 1 more option";
         }

         if (this.menuLength > 2 && s != null) {
            s = s + "@whi@ / " + (this.menuLength - 1) + " more options";
         }

         if (s != null) {
            this.gameGraphics.drawString(s, 6, 14, 1, 16776960);
         }

         if (!this.configMouseButtons && this.mouseButtonClick == 1 || this.configMouseButtons && this.mouseButtonClick == 1 && this.menuLength == 1) {
            this.menuClick(this.menuIndexes[0]);
            this.mouseButtonClick = 0;
            return;
         }

         if (!this.configMouseButtons && this.mouseButtonClick == 2 || this.configMouseButtons && this.mouseButtonClick == 1) {
            this.menuHeight = (this.menuLength + 1) * 15;
            this.menuWidth = this.gameGraphics.textWidth("Choose option", 1) + 5;

            for (int k1 = 0; k1 < this.menuLength; k1++) {
               int l1 = this.gameGraphics.textWidth(this.menuText1[k1] + " " + this.menuText2[k1], 1) + 5;
               if (l1 > this.menuWidth) {
                  this.menuWidth = l1;
               }
            }

            this.menuX = super.mouseX - this.menuWidth / 2;
            this.menuY = super.mouseY - 7;
            this.showRightClickMenu = true;
            if (this.menuX < 0) {
               this.menuX = 0;
            }

            if (this.menuY < 0) {
               this.menuY = 0;
            }

            /* These were 510 and 315 -- windowWidth - 2 and windowHeight - 19
               as literals. In a resized window the literals clamped the menu
               back into the vanilla 512x334 corner, up to a thousand pixels
               from the cursor, and drawRightClickMenu's mouse-left-the-menu
               check closed it the same frame it opened: right-click looked
               simply dead over the inventory and the far map. */
            if (this.menuX + this.menuWidth > this.windowWidth - 2) {
               this.menuX = this.windowWidth - 2 - this.menuWidth;
            }

            if (this.menuY + this.menuHeight > this.windowHeight - 19) {
               this.menuY = this.windowHeight - 19 - this.menuHeight;
            }

            this.mouseButtonClick = 0;
         }
      }
   }

   @Override
   protected final void cantLogout() {
      this.logoutTimeout = 0;
      this.displayMessage("@gry@ Sorry, you can't logout at the moment", 3, 0);
   }

   private final void drawFriendsWindow(boolean flag) {
      int i = this.gameGraphics.menuDefaultWidth - 199;
      int j = 36;
      this.gameGraphics.drawPicture(i - 49, 3, 2005);
      char c = 196;
      char c1 = 182;
      int l;
      int k = l = GameImage.convertRGBToLong(160, 160, 160);
      if (this.friendsWindowTab == 0) {
         k = GameImage.convertRGBToLong(220, 220, 220);
      } else {
         l = GameImage.convertRGBToLong(220, 220, 220);
      }

      this.gameGraphics.drawBoxAlpha(i, j, c / 2, 24, k, 128);
      this.gameGraphics.drawBoxAlpha(i + c / 2, j, c / 2, 24, l, 128);
      this.gameGraphics.drawBoxAlpha(i, j + 24, c, c1 - 24, GameImage.convertRGBToLong(220, 220, 220), 128);
      this.gameGraphics.drawLineX(i, j + 24, c, 0);
      this.gameGraphics.drawLineY(i + c / 2, j, 24, 0);
      this.gameGraphics.drawLineX(i, j + c1 - 16, c, 0);
      this.gameGraphics.drawText("Friends", i + c / 4, j + 16, 4, 0);
      this.gameGraphics.drawText("Ignore", i + c / 4 + c / 2, j + 16, 4, 0);
      this.friendsMenu.resetListTextCount(this.friendsMenuHandle);
      /* "Remove" sits 126px into the panel -- x=439 in Jagex's 512-wide
         client, where it was hardcoded. The trailing run of Ws is theirs
         too: invisible click-width padding that hangs off the screen edge. */
      String removeTail = "~" + (i + 126) + "~@whi@Remove         WWWWWWWWWW";
      if (this.friendsWindowTab == 0) {
         for (int i1 = 0; i1 < super.friendsCount; i1++) {
            String s;
            if (super.friendsListOnlineStatus[i1] == 99) {
               s = "@gre@";
            } else if (super.friendsListOnlineStatus[i1] > 0) {
               s = "@yel@";
            } else {
               s = "@red@";
            }

            this.friendsMenu
               .drawMenuListText(
                  this.friendsMenuHandle, i1, s + DataOperations.longToString(super.friendsListLongs[i1]) + removeTail
               );
         }
      }

      if (this.friendsWindowTab == 1) {
         for (int j1 = 0; j1 < super.ignoreListCount; j1++) {
            this.friendsMenu
               .drawMenuListText(
                  this.friendsMenuHandle, j1, "@yel@" + DataOperations.longToString(super.ignoreListLongs[j1]) + removeTail
               );
         }
      }

      this.friendsMenu.drawMenu();
      if (this.friendsWindowTab == 0) {
         int k1 = this.friendsMenu.selectedListIndex(this.friendsMenuHandle);
         if (k1 < 0 || super.mouseX >= i + 176) {
            this.gameGraphics.drawText("Click a name to send a message", i + c / 2, j + 35, 1, 16777215);
         } else if (super.mouseX > i + 116) {
            this.gameGraphics.drawText("Click to remove " + DataOperations.longToString(super.friendsListLongs[k1]), i + c / 2, j + 35, 1, 16777215);
         } else if (super.friendsListOnlineStatus[k1] == 99) {
            this.gameGraphics.drawText("Click to message " + DataOperations.longToString(super.friendsListLongs[k1]), i + c / 2, j + 35, 1, 16777215);
         } else if (super.friendsListOnlineStatus[k1] > 0) {
            this.gameGraphics
               .drawText(
                  DataOperations.longToString(super.friendsListLongs[k1]) + " is on world " + super.friendsListOnlineStatus[k1], i + c / 2, j + 35, 1, 16777215
               );
         } else {
            this.gameGraphics.drawText(DataOperations.longToString(super.friendsListLongs[k1]) + " is offline", i + c / 2, j + 35, 1, 16777215);
         }

         int k2;
         if (super.mouseX > i && super.mouseX < i + c && super.mouseY > j + c1 - 16 && super.mouseY < j + c1) {
            k2 = 16776960;
         } else {
            k2 = 16777215;
         }

         this.gameGraphics.drawText("Click here to add a friend", i + c / 2, j + c1 - 3, 1, k2);
      }

      if (this.friendsWindowTab == 1) {
         int l1 = this.friendsMenu.selectedListIndex(this.friendsMenuHandle);
         if (l1 < 0 || super.mouseX >= i + 176 || super.mouseX <= i + 116) {
            this.gameGraphics.drawText("Blocking messages from:", i + c / 2, j + 35, 1, 16777215);
         } else if (super.mouseX > i + 116) {
            this.gameGraphics.drawText("Click to remove " + DataOperations.longToString(super.ignoreListLongs[l1]), i + c / 2, j + 35, 1, 16777215);
         }

         int l2;
         if (super.mouseX > i && super.mouseX < i + c && super.mouseY > j + c1 - 16 && super.mouseY < j + c1) {
            l2 = 16776960;
         } else {
            l2 = 16777215;
         }

         this.gameGraphics.drawText("Click here to add a name", i + c / 2, j + c1 - 3, 1, l2);
      }

      if (flag) {
         i = super.mouseX - (this.gameGraphics.menuDefaultWidth - 199);
         j = super.mouseY - 36;
         if (i >= 0 && j >= 0 && i < 196 && j < 182) {
            this.friendsMenu.updateActions(i + (this.gameGraphics.menuDefaultWidth - 199), j + 36, super.lastMouseDownButton, super.mouseDownButton);
            if (j <= 24 && this.mouseButtonClick == 1) {
               if (i < 98 && this.friendsWindowTab == 1) {
                  this.friendsWindowTab = 0;
                  this.friendsMenu.setListScroll(this.friendsMenuHandle, 0);
               } else if (i > 98 && this.friendsWindowTab == 0) {
                  this.friendsWindowTab = 1;
                  this.friendsMenu.setListScroll(this.friendsMenuHandle, 0);
               }
            }

            if (this.mouseButtonClick == 1 && this.friendsWindowTab == 0) {
               int i2 = this.friendsMenu.selectedListIndex(this.friendsMenuHandle);
               // i is the panel-relative mouse x by this point.
               if (i2 >= 0 && i < 176) {
                  if (i > 116) {
                     this.removeFromFriends(super.friendsListLongs[i2]);
                  } else if (super.friendsListOnlineStatus[i2] != 0) {
                     this.inputBoxType = 2;
                     this.privateMessageTarget = super.friendsListLongs[i2];
                     super.inputMessage = "";
                     super.enteredMessage = "";
                  }
               }
            }

            if (this.mouseButtonClick == 1 && this.friendsWindowTab == 1) {
               int j2 = this.friendsMenu.selectedListIndex(this.friendsMenuHandle);
               if (j2 >= 0 && i < 176 && i > 116) {
                  this.removeFromIgnoreList(super.ignoreListLongs[j2]);
               }
            }

            if (j > 166 && this.mouseButtonClick == 1 && this.friendsWindowTab == 0) {
               this.inputBoxType = 1;
               super.inputText = "";
               super.enteredText = "";
            }

            if (j > 166 && this.mouseButtonClick == 1 && this.friendsWindowTab == 1) {
               this.inputBoxType = 3;
               super.inputText = "";
               super.enteredText = "";
            }

            this.mouseButtonClick = 0;
         }
      }
   }

   private final boolean loadSection(int i, int j) {
      if (this.playerAliveTimeout != 0) {
         this.engineHandle.playerIsAlive = false;
         return false;
      } else {
         this.notInWilderness = false;
         i += this.wildX;
         j += this.wildY;
         if (this.lastWildYSubtract == this.wildYSubtract && i > this.loadedSectionMinX && i < this.loadedSectionMaxX && j > this.loadedSectionMinY && j < this.loadedSectionMaxY) {
            this.engineHandle.playerIsAlive = true;
            return false;
         } else {
            this.gameGraphics.drawText("Loading... Please wait", 256, 192, 1, 16777215);
            this.drawChatMessageTabs();
            this.gameGraphics.drawImage(this.aGraphics936, 0, 0);
            int k = this.areaX;
            int l = this.areaY;
            int i1 = (i + 24) / 48;
            int j1 = (j + 24) / 48;
            this.lastWildYSubtract = this.wildYSubtract;
            this.areaX = i1 * 48 - 48;
            this.areaY = j1 * 48 - 48;
            /*
             * The loaded area is 96x96 tiles centred on a 48-tile section
             * corner; the +/-32 rectangle is the inner two thirds of it. Walk
             * outside that and this method reloads around the new centre, so
             * there are always at least 16 loaded tiles beyond the player in
             * every direction.
             */
            this.loadedSectionMinX = i1 * 48 - 32;
            this.loadedSectionMinY = j1 * 48 - 32;
            this.loadedSectionMaxX = i1 * 48 + 32;
            this.loadedSectionMaxY = j1 * 48 + 32;
            this.engineHandle.loadArea(i, j, this.lastWildYSubtract);
            this.areaX = this.areaX - this.wildX;
            this.areaY = this.areaY - this.wildY;
            int k1 = this.areaX - k;
            int l1 = this.areaY - l;

            for (int i2 = 0; i2 < this.objectCount; i2++) {
               this.objectX[i2] = this.objectX[i2] - k1;
               this.objectY[i2] = this.objectY[i2] - l1;
               int j2 = this.objectX[i2];
               int l2 = this.objectY[i2];
               int k3 = this.objectType[i2];
               int m4 = this.objectID[i2];
               Model model = this.objectModelArray[i2];

               try {
                  int l4 = this.objectID[i2];
                  int k5;
                  int i6;
                  if (l4 != 0 && l4 != 4) {
                     i6 = EntityHandler.getObjectDef(k3).getWidth();
                     k5 = EntityHandler.getObjectDef(k3).getHeight();
                  } else {
                     k5 = EntityHandler.getObjectDef(k3).getWidth();
                     i6 = EntityHandler.getObjectDef(k3).getHeight();
                  }

                  int j6 = (j2 + j2 + k5) * this.magicLoc / 2;
                  int k6 = (l2 + l2 + i6) * this.magicLoc / 2;
                  if (j2 >= 0 && l2 >= 0 && j2 < 96 && l2 < 96) {
                     this.gameCamera.addModel(model);
                     model.setTranslation(j6, -this.engineHandle.getAveragedElevation(j6, k6), k6);
                     this.engineHandle.registerObjectCollision(j2, l2, k3, m4);
                     if (k3 == 74) {
                        model.translateBy(0, -480, 0);
                     }
                  }
               } catch (RuntimeException var21) {
                  System.out.println("Loc Error: " + var21.getMessage());
                  System.out.println("i:" + i2 + " obj:" + model);
                  var21.printStackTrace();
               }
            }

            for (int k2 = 0; k2 < this.doorCount; k2++) {
               this.doorX[k2] = this.doorX[k2] - k1;
               this.doorY[k2] = this.doorY[k2] - l1;
               int i3 = this.doorX[k2];
               int l3 = this.doorY[k2];
               int j4 = this.doorType[k2];
               int i5 = this.doorDirection[k2];

               try {
                  this.engineHandle.registerDoorCollision(i3, l3, i5, j4);
                  Model model_1 = this.makeModel(i3, l3, i5, j4, k2);
                  this.doorModel[k2] = model_1;
               } catch (RuntimeException var20) {
                  System.out.println("Bound Error: " + var20.getMessage());
                  var20.printStackTrace();
               }
            }

            for (int j3 = 0; j3 < this.groundItemCount; j3++) {
               this.groundItemX[j3] = this.groundItemX[j3] - k1;
               this.groundItemY[j3] = this.groundItemY[j3] - l1;
            }

            for (int i4 = 0; i4 < this.playerCount; i4++) {
               Mob mob = this.playerArray[i4];
               mob.currentX = mob.currentX - k1 * this.magicLoc;
               mob.currentY = mob.currentY - l1 * this.magicLoc;

               for (int j5 = 0; j5 <= mob.waypointCurrent; j5++) {
                  mob.waypointsX[j5] = mob.waypointsX[j5] - k1 * this.magicLoc;
                  mob.waypointsY[j5] = mob.waypointsY[j5] - l1 * this.magicLoc;
               }
            }

            for (int k4 = 0; k4 < this.npcCount; k4++) {
               Mob mob_1 = this.npcArray[k4];
               mob_1.currentX = mob_1.currentX - k1 * this.magicLoc;
               mob_1.currentY = mob_1.currentY - l1 * this.magicLoc;

               for (int l5 = 0; l5 <= mob_1.waypointCurrent; l5++) {
                  mob_1.waypointsX[l5] = mob_1.waypointsX[l5] - k1 * this.magicLoc;
                  mob_1.waypointsY[l5] = mob_1.waypointsY[l5] - l1 * this.magicLoc;
               }
            }

            this.engineHandle.playerIsAlive = true;
            return true;
         }
      }
   }

   private final void drawMagicWindow(boolean flag) {
      int i = this.gameGraphics.menuDefaultWidth - 199;
      int j = 36;
      this.gameGraphics.drawPicture(i - 49, 3, 2004);
      char c = 196;
      char c1 = 182;
      int l;
      int k = l = GameImage.convertRGBToLong(160, 160, 160);
      if (this.menuMagicPrayersSelected == 0) {
         k = GameImage.convertRGBToLong(220, 220, 220);
      } else {
         l = GameImage.convertRGBToLong(220, 220, 220);
      }

      this.gameGraphics.drawBoxAlpha(i, j, c / 2, 24, k, 128);
      this.gameGraphics.drawBoxAlpha(i + c / 2, j, c / 2, 24, l, 128);
      this.gameGraphics.drawBoxAlpha(i, j + 24, c, 90, GameImage.convertRGBToLong(220, 220, 220), 128);
      this.gameGraphics.drawBoxAlpha(i, j + 24 + 90, c, c1 - 'Z' - 24, GameImage.convertRGBToLong(160, 160, 160), 128);
      this.gameGraphics.drawLineX(i, j + 24, c, 0);
      this.gameGraphics.drawLineY(i + c / 2, j, 24, 0);
      this.gameGraphics.drawLineX(i, j + 113, c, 0);
      this.gameGraphics.drawText("Magic", i + c / 4, j + 16, 4, 0);
      this.gameGraphics.drawText("Prayers", i + c / 4 + c / 2, j + 16, 4, 0);
      if (this.menuMagicPrayersSelected == 0) {
         this.spellMenu.resetListTextCount(this.spellMenuHandle);
         int i1 = 0;

         // List position -> def id via the level-sorted order; the raw def
         // order is append-only and no longer matches the level ladder.
         for (int spellIndex : EntityHandler.spellDisplayOrder()) {
            String s = "@yel@";

            for (Entry e : EntityHandler.getSpellDef(spellIndex).getRunesRequired()) {
               if (!this.hasRequiredRunes((Integer)e.getKey(), (Integer)e.getValue())) {
                  s = "@whi@";
                  break;
               }
            }

            int spellLevel = this.playerStatCurrent[6];
            if (EntityHandler.getSpellDef(spellIndex).getReqLevel() > spellLevel) {
               s = "@bla@";
            }

            this.spellMenu
               .drawMenuListText(
                  this.spellMenuHandle,
                  i1++,
                  s + "Level " + EntityHandler.getSpellDef(spellIndex).getReqLevel() + ": " + EntityHandler.getSpellDef(spellIndex).getName()
               );
         }

         this.spellMenu.drawMenu();
         int selectedSpellIndex = this.spellMenu.selectedListIndex(this.spellMenuHandle);
         if (selectedSpellIndex != -1) {
            selectedSpellIndex = EntityHandler.spellDisplayOrder()[selectedSpellIndex];
            this.gameGraphics
               .drawString(
                  "Level " + EntityHandler.getSpellDef(selectedSpellIndex).getReqLevel() + ": " + EntityHandler.getSpellDef(selectedSpellIndex).getName(),
                  i + 2,
                  j + 124,
                  1,
                  16776960
               );
            this.gameGraphics.drawString(EntityHandler.getSpellDef(selectedSpellIndex).getDescription(), i + 2, j + 136, 0, 16777215);
            int i4 = 0;

            for (Entry<Integer, Integer> ex : EntityHandler.getSpellDef(selectedSpellIndex).getRunesRequired()) {
               int runeID = ex.getKey();
               this.gameGraphics.drawPicture(i + 2 + i4 * 44, j + 150, 2150 + EntityHandler.getItemDef(runeID).getSprite());
               int runeInvCount = this.inventoryCount(runeID);
               int runeCount = ex.getValue();
               String s2 = "@red@";
               if (this.hasRequiredRunes(runeID, runeCount)) {
                  s2 = "@gre@";
               }

               this.gameGraphics.drawString(s2 + runeInvCount + "/" + runeCount, i + 2 + i4 * 44, j + 150, 1, 16777215);
               i4++;
            }
         } else {
            this.gameGraphics.drawString("Point at a spell for a description", i + 2, j + 124, 1, 0);
         }
      }

      if (this.menuMagicPrayersSelected == 1) {
         this.spellMenu.resetListTextCount(this.spellMenuHandle);
         int j1 = 0;

         // Same level-sorted walk as the spellbook above.
         for (int j2 : EntityHandler.prayerDisplayOrder()) {
            String s1 = "@whi@";
            if (EntityHandler.getPrayerDef(j2).getReqLevel() > this.playerStatBase[5]) {
               s1 = "@bla@";
            }

            if (this.prayerOn[j2]) {
               s1 = "@gre@";
            }

            this.spellMenu
               .drawMenuListText(
                  this.spellMenuHandle, j1++, s1 + "Level " + EntityHandler.getPrayerDef(j2).getReqLevel() + ": " + EntityHandler.getPrayerDef(j2).getName()
               );
         }

         this.spellMenu.drawMenu();
         int j3 = this.spellMenu.selectedListIndex(this.spellMenuHandle);
         if (j3 != -1) {
            j3 = EntityHandler.prayerDisplayOrder()[j3];
            this.gameGraphics
               .drawText(
                  "Level " + EntityHandler.getPrayerDef(j3).getReqLevel() + ": " + EntityHandler.getPrayerDef(j3).getName(), i + c / 2, j + 130, 1, 16776960
               );
            this.gameGraphics.drawText(EntityHandler.getPrayerDef(j3).getDescription(), i + c / 2, j + 145, 0, 16777215);
            this.gameGraphics.drawText("Drain rate: " + EntityHandler.getPrayerDef(j3).getDrainRate(), i + c / 2, j + 160, 1, 0);
         } else {
            this.gameGraphics.drawString("Point at a prayer for a description", i + 2, j + 124, 1, 0);
         }
      }

      if (flag) {
         i = super.mouseX - (this.gameGraphics.menuDefaultWidth - 199);
         j = super.mouseY - 36;
         if (i >= 0 && j >= 0 && i < 196 && j < 182) {
            this.spellMenu.updateActions(i + (this.gameGraphics.menuDefaultWidth - 199), j + 36, super.lastMouseDownButton, super.mouseDownButton);
            if (j <= 24 && this.mouseButtonClick == 1) {
               if (i < 98 && this.menuMagicPrayersSelected == 1) {
                  this.menuMagicPrayersSelected = 0;
                  this.prayerMenuIndex = this.spellMenu.getMenuIndex(this.spellMenuHandle);
                  this.spellMenu.setListScroll(this.spellMenuHandle, this.magicMenuIndex);
               } else if (i > 98 && this.menuMagicPrayersSelected == 0) {
                  this.menuMagicPrayersSelected = 1;
                  this.magicMenuIndex = this.spellMenu.getMenuIndex(this.spellMenuHandle);
                  this.spellMenu.setListScroll(this.spellMenuHandle, this.prayerMenuIndex);
               }
            }

            if (this.mouseButtonClick == 1 && this.menuMagicPrayersSelected == 0) {
               int k1 = this.spellMenu.selectedListIndex(this.spellMenuHandle);
               if (k1 != -1) {
                  // Clicks come back as list positions; everything below --
                  // including selectedSpell, which goes on the wire -- needs
                  // the def id.
                  k1 = EntityHandler.spellDisplayOrder()[k1];
                  int k2 = this.playerStatCurrent[6];
                  if (EntityHandler.getSpellDef(k1).getReqLevel() > k2) {
                     this.displayMessage("@gry@ Your magic ability is not high enough for this spell", 3, 0);
                  } else {
                     int k3 = 0;

                     for (Entry<Integer, Integer> ex : EntityHandler.getSpellDef(k1).getRunesRequired()) {
                        if (!this.hasRequiredRunes(ex.getKey(), ex.getValue())) {
                           this.displayMessage("@gry@ You don't have all the reagents you need for this spell", 3, 0);
                           k3 = -1;
                           break;
                        }

                        k3++;
                     }

                     if (k3 == EntityHandler.getSpellDef(k1).getRuneCount()) {
                        this.selectedSpell = k1;
                        this.selectedItem = -1;
                     }
                  }
               }
            }

            if (this.mouseButtonClick == 1 && this.menuMagicPrayersSelected == 1) {
               int l1 = this.spellMenu.selectedListIndex(this.spellMenuHandle);
               if (l1 != -1) {
                  // As with spells: prayerOn and the toggle packets are
                  // indexed by def id, the click by list position.
                  l1 = EntityHandler.prayerDisplayOrder()[l1];
                  int l2 = this.playerStatBase[5];
                  if (EntityHandler.getPrayerDef(l1).getReqLevel() > l2) {
                     this.displayMessage("@gry@ Your prayer ability is not high enough for this prayer", 3, 0);
                  } else if (this.playerStatCurrent[5] == 0) {
                     this.displayMessage("@gry@ You have run out of prayer points. Return to a church to recharge", 3, 0);
                  } else if (this.prayerOn[l1]) {
                     super.streamClass.createPacket(248);
                     super.streamClass.addByte(l1);
                     super.streamClass.formatPacket();
                     this.prayerOn[l1] = false;
                     this.playSound("prayeroff");
                  } else {
                     super.streamClass.createPacket(56);
                     super.streamClass.addByte(l1);
                     super.streamClass.formatPacket();
                     this.prayerOn[l1] = true;
                     this.playSound("prayeron");
                  }
               }
            }

            this.mouseButtonClick = 0;
         }
      }
   }

   @Override
   protected final void handleMenuKeyDown(int key) {
      /*
       * The world map takes the whole keyboard while it is up. It pans with the
       * arrow keys, and those are the camera's -- 1004/1005 raise and lower it
       * right below here, 1006/1007 swing it round -- so sharing them would
       * have meant reading the map while the world spun underneath it. Nothing
       * else on the keyboard has anywhere useful to go either: the chat box is
       * behind a full-screen panel.
       */
      if (this.loggedIn == 1 && this.worldMapPanel != null && this.worldMapPanel.isOpen()) {
         this.worldMapPanel.handleKey(key);
         return;
      }

      /*
       * The calculators take the whole keyboard too, and this is the reason
       * they can have text fields where the script menu cannot: a level typed
       * into a field never reaches the game menu, so it never lands in the
       * chat line as well. The panel handles F2 and Escape itself.
       */
      if (this.loggedIn == 1 && this.calculatorPanel != null && this.calculatorPanel.isOpen()) {
         this.calculatorPanel.handleKey(key);
         return;
      }

      switch (key) {
         case 1004:
            if (this.cameraHeight < 300) {
               this.cameraHeight += 25;
            } else {
               this.cameraHeight -= 25;
            }
            break;
         case 1005:
            if (this.cameraHeight > 1500) {
               this.cameraHeight -= 25;
            } else {
               this.cameraHeight += 25;
            }
            break;
         case 1018:
            this.toggleRecording();
            break;
         case 1019:
            this.takeScreenshot(true);
            break;
         /*
          * F2 opens the script menu, following F11 and F12 above rather than
          * inventing a second convention. Only in game: at the login screen
          * the panel has nothing to show and the keyboard belongs to the
          * username box.
          */
         case 1009:
            if (this.loggedIn == 1) {
               this.toggleScriptPanel();
            }
            break;
         case 27:
            /*
             * Escape dismisses a prompt before it closes the menu, and this is
             * the only way out of one. Swallowing clicks to stop them falling
             * through to the menu would otherwise put the player in a corner:
             * the menu's Stop button is reached by clicking, so a script parked
             * on a question could be neither answered nor stopped.
             *
             * Cancelling returns "" to the script, which is what the old Swing
             * dialog returned when it was closed, so scripts see nothing new.
             */
            if (this.scriptPrompt.isOpen()) {
               this.scriptPrompt.cancel();
            } else if (this.scriptPanel != null) {
               this.scriptPanel.handleKey(key);
            }
      }

      /*
       * The menus do not exist until makeLoginMenus() has run, and the client
       * is on screen and taking keystrokes well before that -- the loading
       * bar is drawn from the same window. A keypress there used to throw a
       * NullPointerException out of keyDown() and kill the AWT event thread,
       * so the window stopped responding to input for the rest of the session
       * and the only clue was one stack trace. Nothing is lost by ignoring a
       * key aimed at a menu that has not been built yet.
       */
      if (this.loggedIn == 0) {
         if (this.loginScreenNumber == 0 && this.menuWelcome != null) {
            this.menuWelcome.keyDown(key);
         }

         if (this.loginScreenNumber == 1 && this.menuNewUser != null) {
            this.menuNewUser.keyDown(key);
         }

         if (this.loginScreenNumber == 2 && this.menuLogin != null) {
            this.menuLogin.keyDown(key);
         }

         if (this.loginScreenNumber == 4) {
            this.worldsPanel().keyDown(key);
         }
      }

      if (this.loggedIn == 1) {
         if (this.showCharacterLookScreen) {
            if (this.characterDesignMenu != null) {
               this.characterDesignMenu.keyDown(key);
            }

            return;
         }

         /*
          * A prompt takes the keyboard the same way it takes the mouse. The
          * client accumulates every typed character into inputText no matter
          * what is on screen -- that is how the answer reaches ScriptPrompt at
          * all -- but the game menu would ALSO see it, so answering "50" to
          * Withdraw X typed 50 into the chat box and Enter said it out loud.
          */
         if (this.inputBoxType == 0 && this.showAbuseWindow == 0 && this.gameMenu != null
               && !this.scriptPrompt.isOpen()) {
            this.gameMenu.keyDown(key);
         }
      }
   }

   /* One amount button on a shop transaction row: centred, white, red while
      the cursor sits inside its click band on that row. The bands overhang
      the glyphs a little on purpose -- that is how the official client made
      small labels easy to hit. */
   private final void drawShopAmount(String label, int x, int y, boolean inRow, int lo, int hi) {
      int colour = 16777215;
      if (inRow && super.mouseX > lo && super.mouseX < hi) {
         colour = 16711680;
      }

      this.gameGraphics.drawText(label, x, y, 3, colour);
   }

   private final void drawShopBox() {
      if (this.mouseButtonClick != 0) {
         this.mouseButtonClick = 0;
         int i = super.mouseX - (52 + this.loginOffsetX());
         int j = super.mouseY - (44 + this.loginOffsetY());
         if (i < 0 || j < 12 || i >= 408 || j >= 246) {
            super.streamClass.createPacket(253);
            super.streamClass.formatPacket();
            this.showShop = false;
            return;
         }

         int k = 0;

         for (int i1 = 0; i1 < 5; i1++) {
            for (int i2 = 0; i2 < 8; i2++) {
               int l2 = 7 + i2 * 49;
               int l3 = 28 + i1 * 34;
               if (i > l2 && i < l2 + 49 && j > l3 && j < l3 + 34 && this.shopItems[k] != -1) {
                  this.selectedShopItemIndex = k;
                  this.selectedShopItemType = this.shopItems[k];
               }

               k++;
            }
         }

         if (this.selectedShopItemIndex >= 0) {
            int j2 = this.shopItems[this.selectedShopItemIndex];
            if (j2 != -1) {
               /* The transaction rows are the final official client's (Feb
                  2015): Buy:/Sell: followed by 1 5 10 50 X. The fixed amounts
                  send at once and only X asks a question -- the same contract
                  the bank's row keeps. The click bands and the rule that
                  5/10/50 only exist when that many are there to move are
                  lifted from the signed jar. */
               int stockNow = this.shopItemCount[this.selectedShopItemIndex];
               if (stockNow > 0 && j >= 204 && j <= 215) {
                  int amount = 0;
                  if (i > 318 && i < 330) {
                     amount = 1;
                  }

                  if (stockNow >= 5 && i > 333 && i < 345) {
                     amount = 5;
                  }

                  if (stockNow >= 10 && i > 348 && i < 365) {
                     amount = 10;
                  }

                  if (stockNow >= 50 && i > 368 && i < 385) {
                     amount = 50;
                  }

                  if (amount > 0) {
                     int priceNow = this.shopItemBuyPriceModifier * EntityHandler.getItemDef(j2).getBasePrice() / 100;
                     /* Same trim the Buy X continuation applies: what can be
                        paid for, with the free-item divide guarded. */
                     if (priceNow > 0 && priceNow * amount > this.inventoryCount(10)) {
                        amount = this.inventoryCount(10) / priceNow;
                     }

                     if (amount > 0) {
                        super.streamClass.createPacket(128);
                        super.streamClass.add2ByteInt(j2);
                        super.streamClass.add4ByteInt(priceNow);
                        super.streamClass.addString(Integer.toString(amount));
                        super.streamClass.formatPacket();
                     }

                     return;
                  }
               }

               if (stockNow > 0 && i > 388 && i < 400 && j >= 204 && j <= 215) {
                  /* Buy X -- see Withdraw X in drawBankBox for why this is a
                     continuation rather than a straight line. The item, its
                     price and the stock are captured here because the shop
                     selection can move while the question is on screen; the
                     coin count is read inside, because that is what the clamp
                     is actually about. */
                  final int buyItem = j2;
                  final int buyPrice = this.shopItemBuyPriceModifier * EntityHandler.getItemDef(j2).getBasePrice() / 100;
                  final int buyStock = this.shopItemCount[this.selectedShopItemIndex];
                  this.scriptPrompt.askAsync("Buy how many?", new ScriptPrompt.Answer() {
                     public void got(String text) {
                        int amount = SalvageInput(text);
                        if (amount <= 0) {
                           return;
                        }

                        if (amount > buyStock) {
                           amount = buyStock;
                        }

                        /* Trim to what can be paid for. A free item costs
                           nothing to want any number of, and dividing by its
                           price would throw. */
                        if (buyPrice > 0 && buyPrice * amount > mudclient.this.inventoryCount(10)) {
                           amount = mudclient.this.inventoryCount(10) / buyPrice;
                        }

                        if (amount <= 0) {
                           return;
                        }

                        mudclient.this.streamClass.createPacket(128);
                        mudclient.this.streamClass.add2ByteInt(buyItem);
                        mudclient.this.streamClass.add4ByteInt(buyPrice);
                        mudclient.this.streamClass.addString(Integer.toString(amount));
                        mudclient.this.streamClass.formatPacket();
                     }
                  });
                  return;
               }

               int heldNow = this.inventoryCount(j2);
               if (heldNow > 0 && j >= 229 && j <= 240) {
                  int amount = 0;
                  if (i > 318 && i < 330) {
                     amount = 1;
                  }

                  if (heldNow >= 5 && i > 333 && i < 345) {
                     amount = 5;
                  }

                  if (heldNow >= 10 && i > 348 && i < 365) {
                     amount = 10;
                  }

                  if (heldNow >= 50 && i > 368 && i < 385) {
                     amount = 50;
                  }

                  if (amount > 0) {
                     int priceNow = this.shopItemSellPriceModifier * EntityHandler.getItemDef(j2).getBasePrice() / 100;
                     super.streamClass.createPacket(255);
                     super.streamClass.add2ByteInt(j2);
                     super.streamClass.add4ByteInt(priceNow);
                     super.streamClass.addString(Integer.toString(amount));
                     super.streamClass.formatPacket();
                     return;
                  }
               }

               if (heldNow > 0 && i > 388 && i < 400 && j >= 229 && j <= 240) {
                  /* Sell X -- see Buy X above. The old guard here was "< 0",
                     not "<= 0", so an empty or unparseable answer sent a sell
                     of nothing; the other three all treated zero as "do
                     nothing" and now so does this one. */
                  final int sellItem = j2;
                  final int sellPrice = this.shopItemSellPriceModifier * EntityHandler.getItemDef(j2).getBasePrice() / 100;
                  this.scriptPrompt.askAsync("Sell how many?", new ScriptPrompt.Answer() {
                     public void got(String text) {
                        int amount = SalvageInput(text);
                        if (amount <= 0) {
                           return;
                        }

                        int held = mudclient.this.inventoryCount(sellItem);
                        if (amount > held) {
                           amount = held;
                        }

                        if (amount <= 0) {
                           return;
                        }

                        mudclient.this.streamClass.createPacket(255);
                        mudclient.this.streamClass.add2ByteInt(sellItem);
                        mudclient.this.streamClass.add4ByteInt(sellPrice);
                        mudclient.this.streamClass.addString(Integer.toString(amount));
                        mudclient.this.streamClass.formatPacket();
                     }
                  });
                  return;
               }
            }
         }
      }

      int byte0 = 52 + this.loginOffsetX();
      int byte1 = 44 + this.loginOffsetY();
      this.gameGraphics.drawBox(byte0, byte1, 408, 12, 192);
      int l = 10000536;
      this.gameGraphics.drawBoxAlpha(byte0, byte1 + 12, 408, 17, l, 160);
      this.gameGraphics.drawBoxAlpha(byte0, byte1 + 29, 8, 170, l, 160);
      this.gameGraphics.drawBoxAlpha(byte0 + 399, byte1 + 29, 9, 170, l, 160);
      this.gameGraphics.drawBoxAlpha(byte0, byte1 + 199, 408, 47, l, 160);
      this.gameGraphics.drawString("Buying and selling items", byte0 + 1, byte1 + 10, 1, 16777215);
      int j1 = 16777215;
      if (super.mouseX > byte0 + 320 && super.mouseY >= byte1 && super.mouseX < byte0 + 408 && super.mouseY < byte1 + 12) {
         j1 = 16711680;
      }

      this.gameGraphics.drawBoxTextRight("Close window", byte0 + 406, byte1 + 10, 1, j1);
      this.gameGraphics.drawString("Shops stock in green", byte0 + 2, byte1 + 24, 1, 65280);
      this.gameGraphics.drawString("Number you own in blue", byte0 + 135, byte1 + 24, 1, 65535);
      this.gameGraphics.drawString("Your money: " + this.inventoryCount(10) + "gp", byte0 + 280, byte1 + 24, 1, 16776960);
      int k2 = 13684944;
      int k3 = 0;

      for (int k4 = 0; k4 < 5; k4++) {
         for (int l4 = 0; l4 < 8; l4++) {
            int j5 = byte0 + 7 + l4 * 49;
            int i6 = byte1 + 28 + k4 * 34;
            if (this.selectedShopItemIndex == k3) {
               this.gameGraphics.drawBoxAlpha(j5, i6, 49, 34, 16711680, 160);
            } else {
               this.gameGraphics.drawBoxAlpha(j5, i6, 49, 34, k2, 160);
            }

            this.gameGraphics.drawBoxEdge(j5, i6, 50, 35, 0);
            if (this.shopItems[k3] != -1) {
               this.gameGraphics
                  .spriteClip4(
                     j5,
                     i6,
                     48,
                     32,
                     2150 + EntityHandler.getItemDef(this.shopItems[k3]).getSprite(),
                     EntityHandler.getItemDef(this.shopItems[k3]).getPictureMask(),
                     0,
                     0,
                     false
                  );
               this.gameGraphics.drawString(String.valueOf(this.shopItemCount[k3]), j5 + 1, i6 + 10, 1, 65280);
               this.gameGraphics.drawBoxTextRight(String.valueOf(this.inventoryCount(this.shopItems[k3])), j5 + 47, i6 + 10, 1, 65535);
            }

            k3++;
         }
      }

      this.gameGraphics.drawLineX(byte0 + 5, byte1 + 222, 398, 0);
      if (this.selectedShopItemIndex == -1) {
         this.gameGraphics.drawText("Select an object to buy or sell", byte0 + 204, byte1 + 214, 3, 16776960);
      } else {
         int i5 = this.shopItems[this.selectedShopItemIndex];
         if (i5 != -1) {
            /* The 2015 rows: name and unit price on the left, Buy:/Sell: and
               the amount buttons on the right. Positions and gates match the
               click bands above. */
            int stock = this.shopItemCount[this.selectedShopItemIndex];
            if (stock > 0) {
               int j6 = this.shopItemBuyPriceModifier * EntityHandler.getItemDef(i5).getBasePrice() / 100;
               this.gameGraphics
                  .drawString(EntityHandler.getItemDef(i5).getName() + ": buy for " + j6 + "gp each", byte0 + 2, byte1 + 214, 1, 16776960);
               boolean buyRow = super.mouseY >= byte1 + 204 && super.mouseY <= byte1 + 215;
               this.gameGraphics.drawText("Buy:", byte0 + 285, byte1 + 214, 3, 16777215);
               this.drawShopAmount("1", byte0 + 320, byte1 + 214, buyRow, byte0 + 318, byte0 + 330);
               if (stock >= 5) {
                  this.drawShopAmount("5", byte0 + 335, byte1 + 214, buyRow, byte0 + 333, byte0 + 345);
               }

               if (stock >= 10) {
                  this.drawShopAmount("10", byte0 + 350, byte1 + 214, buyRow, byte0 + 348, byte0 + 365);
               }

               if (stock >= 50) {
                  this.drawShopAmount("50", byte0 + 370, byte1 + 214, buyRow, byte0 + 368, byte0 + 385);
               }

               this.drawShopAmount("X", byte0 + 390, byte1 + 214, buyRow, byte0 + 388, byte0 + 400);
            } else {
               this.gameGraphics.drawText("This item is not currently available to buy", byte0 + 204, byte1 + 214, 3, 16776960);
            }

            int held = this.inventoryCount(i5);
            if (held > 0) {
               int k6 = this.shopItemSellPriceModifier * EntityHandler.getItemDef(i5).getBasePrice() / 100;
               this.gameGraphics
                  .drawString(EntityHandler.getItemDef(i5).getName() + ": sell for " + k6 + "gp each", byte0 + 2, byte1 + 239, 1, 16776960);
               boolean sellRow = super.mouseY >= byte1 + 229 && super.mouseY <= byte1 + 240;
               this.gameGraphics.drawText("Sell:", byte0 + 285, byte1 + 239, 3, 16777215);
               this.drawShopAmount("1", byte0 + 320, byte1 + 239, sellRow, byte0 + 318, byte0 + 330);
               if (held >= 5) {
                  this.drawShopAmount("5", byte0 + 335, byte1 + 239, sellRow, byte0 + 333, byte0 + 345);
               }

               if (held >= 10) {
                  this.drawShopAmount("10", byte0 + 350, byte1 + 239, sellRow, byte0 + 348, byte0 + 365);
               }

               if (held >= 50) {
                  this.drawShopAmount("50", byte0 + 370, byte1 + 239, sellRow, byte0 + 368, byte0 + 385);
               }

               this.drawShopAmount("X", byte0 + 390, byte1 + 239, sellRow, byte0 + 388, byte0 + 400);
            } else {
               this.gameGraphics.drawText("You do not have any of this item to sell", byte0 + 204, byte1 + 239, 3, 16776960);
            }
         }
      }
   }

   /*
    * The window is the user's to size now. GameFrame reports every drag; the
    * new size is picked up here, on the game thread, between ticks. Nothing
    * is scaled -- the framebuffer gains rows and columns, the camera is
    * re-pointed at it, and the menus that were built against the old edges
    * are built against the new ones. 512x334 is the floor: the vanilla
    * layout, and the size every fixed dialog was drawn for.
    */
   private final void applyPendingResize() {
      int reportedWidth = super.resizedWidth;
      int reportedHeight = super.resizedHeight;
      if (reportedWidth == 0 && reportedHeight == 0) {
         return;
      }

      /* The report is the frame interior; the world view is what remains
         after the chat tabs take their strip along the bottom. The strip is
         12 rows but the interior has always been windowHeight + 11 -- the
         last row was clipped in vanilla and stays clipped, because using 12
         here would shrink the window by a pixel on every launch. */
      int newWidth = Math.max(512, Math.min(reportedWidth, 2560));
      int newHeight = Math.max(334, Math.min(reportedHeight - (CHAT_TABS_HEIGHT - 1), 1440));
      if (newWidth == this.windowWidth && newHeight == this.windowHeight) {
         return;
      }

      this.windowWidth = newWidth;
      this.windowHeight = newHeight;
      if (this.gameGraphics != null) {
         /* The cached Graphics clips to the bounds it was born with; a fresh
            one covers the grown frame. */
         this.aGraphics936 = this.getGraphics();
         this.gameGraphics.resize(this.windowWidth, this.surfaceHeight(), this);
         this.gameGraphics.setDimensions(0, 0, this.windowWidth, this.surfaceHeight());
         this.rebuildSizedMenus();
      }

      if (this.gameCamera != null) {
         this.gameCamera
            .setCameraSize(this.windowWidth / 2, this.windowHeight / 2, this.windowWidth / 2, this.windowHeight / 2, this.windowWidth, this.cameraSizeInt);
      }

      this.resizeSaveDue = System.currentTimeMillis() + 1000L;
   }

   /*
    * The menus that were built once against the window edges, built again for
    * the new ones. Same numbers as startGame uses. Their text is re-added
    * every frame by the draw methods, so nothing is lost but scroll position
    * -- except the chat menu, which owns its scrollback; a resize costs the
    * history above the visible rows, which vanilla lost on every relog anyway.
    */
   private final void rebuildSizedMenus() {
      int l = this.gameGraphics.menuDefaultWidth - 199;
      byte byte0 = 36;
      this.spellMenu = new Menu(this.gameGraphics, 5);
      this.spellMenuHandle = this.spellMenu.makeInteractiveTextList(l, byte0 + 24, 196, 90, 1, 500, true);
      this.friendsMenu = new Menu(this.gameGraphics, 5);
      this.friendsMenuHandle = this.friendsMenu.makeInteractiveTextList(l, byte0 + 40, 196, 126, 1, 500, true);
      this.questMenu = new Menu(this.gameGraphics, 5);
      this.questMenuHandle = this.questMenu.makeInteractiveTextList(l, byte0 + 24, 196, 251, 1, 500, true);
      if (this.gameMenu != null) {
         this.drawGameMenu();
      }

      /* The pre-login menus are built centred, so a new centre means building
         them again. Whatever was typed into the login boxes survives the
         rebuild; the welcome lines are re-filled from the chosen server. */
      if (this.menuLogin != null) {
         String typedUser = this.menuLogin.getText(this.loginUsernameTextBox);
         String typedPass = this.menuLogin.getText(this.loginPasswordTextBox);
         this.makeLoginMenus();
         this.makeCharacterDesignMenu();
         this.menuLogin.updateText(this.loginUsernameTextBox, typedUser);
         this.menuLogin.updateText(this.loginPasswordTextBox, typedPass);
         this.applyServerWelcome();
      }
   }

   private final void drawGameMenu() {
      this.gameMenu = new Menu(this.gameGraphics, 10);
      this.messagesHandleType2 = this.gameMenu.makeTextList(5, this.windowHeight - 65, this.windowWidth - 10, 56, 1, 20, true);
      this.chatHandle = this.gameMenu.makeTextInput(7, this.windowHeight - 10, this.windowWidth - 14, 14, 1, 80, false, true);
      this.messagesHandleType5 = this.gameMenu.makeTextList(5, this.windowHeight - 65, this.windowWidth - 10, 56, 1, 20, true);
      this.messagesHandleType6 = this.gameMenu.makeTextList(5, this.windowHeight - 65, this.windowWidth - 10, 56, 1, 20, true);
      this.gameMenu.setFocus(this.chatHandle);
   }

   @Override
   protected final byte[] load(String filename) {
      // Was a path under the client's cache directory; the asset is in memory.
      return super.load(Assets.get(filename));
   }

   private final void drawOptionsMenu(boolean flag) {
      int i = this.gameGraphics.menuDefaultWidth - 199;
      int j = 36;
      this.gameGraphics.drawPicture(i - 49, 3, 2006);
      char c = 196;
      this.gameGraphics.drawBoxAlpha(i, 36, c, 65, GameImage.convertRGBToLong(181, 181, 181), 160);
      this.gameGraphics.drawBoxAlpha(i, 101, c, 65, GameImage.convertRGBToLong(201, 201, 201), 160);
      this.gameGraphics.drawBoxAlpha(i, 166, c, 95, GameImage.convertRGBToLong(181, 181, 181), 160);
      this.gameGraphics.drawBoxAlpha(i, 261, c, 40, GameImage.convertRGBToLong(201, 201, 201), 160);
      int k = i + 3;
      int i1 = j + 15;
      this.gameGraphics.drawString("Game options - click to toggle", k, i1, 1, 0);
      i1 += 15;
      if (this.configAutoCameraAngle) {
         this.gameGraphics.drawString("Camera angle mode - @gre@Auto", k, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Camera angle mode - @red@Manual", k, i1, 1, 16777215);
      }

      i1 += 15;
      if (this.configMouseButtons) {
         this.gameGraphics.drawString("Mouse buttons - @red@One", k, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Mouse buttons - @gre@Two", k, i1, 1, 16777215);
      }

      i1 += 15;
      if (this.configSoundEffects) {
         this.gameGraphics.drawString("Sound effects - @red@off", k, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Sound effects - @gre@on", k, i1, 1, 16777215);
      }

      /*
       * The second box is Jagex's four lines of account-management text, not a
       * second set of toggles. RSCD had put "Client assists" here -- Hide
       * Roofs, Auto Screenshots, Fightmode Selector -- on exactly these four
       * rows; the three of them are on the F2 settings page now, which is where
       * things that are not Jagex's belong. They are still server-side
       * settings, so they still go up as packet 157.
       *
       * Font 0 rather than 1, which is Jagex's: these lines are long and the
       * panel is 196 wide.
       *
       * The fourth line is the one place this is not verbatim. Jagex had three
       * versions of it and every one of them names runescape.com, which is not
       * where anyone's account lives now.
       */
      i1 += 15;
      this.gameGraphics.drawString("To change your contact details,", k, i1, 0, 16777215);
      i1 += 15;
      this.gameGraphics.drawString("password, recovery questions, etc..", k, i1, 0, 16777215);
      i1 += 15;
      this.gameGraphics.drawString("please select 'account management'", k, i1, 0, 16777215);
      i1 += 15;
      this.gameGraphics.drawString("from the game's website", k, i1, 0, 16777215);
      i1 += 15;
      i1 += 5;
      this.gameGraphics.drawString("Privacy settings. Will be applied to", i + 3, i1, 1, 0);
      i1 += 15;
      this.gameGraphics.drawString("all people not on your friends list", i + 3, i1, 1, 0);
      i1 += 15;
      if (super.blockChatMessages == 0) {
         this.gameGraphics.drawString("Block chat messages: @red@<off>", i + 3, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Block chat messages: @gre@<on>", i + 3, i1, 1, 16777215);
      }

      i1 += 15;
      if (super.blockPrivateMessages == 0) {
         this.gameGraphics.drawString("Block private messages: @red@<off>", i + 3, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Block private messages: @gre@<on>", i + 3, i1, 1, 16777215);
      }

      i1 += 15;
      if (super.blockTradeRequests == 0) {
         this.gameGraphics.drawString("Block trade requests: @red@<off>", i + 3, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Block trade requests: @gre@<on>", i + 3, i1, 1, 16777215);
      }

      i1 += 15;
      if (super.blockDuelRequests == 0) {
         this.gameGraphics.drawString("Block duel requests: @red@<off>", i + 3, i1, 1, 16777215);
      } else {
         this.gameGraphics.drawString("Block duel requests: @gre@<on>", i + 3, i1, 1, 16777215);
      }

      i1 += 15;
      i1 += 5;
      this.gameGraphics.drawString("Always logout when you finish", k, i1, 1, 0);
      i1 += 15;
      int k1 = 16777215;
      if (super.mouseX > k && super.mouseX < k + c && super.mouseY > i1 - 12 && super.mouseY < i1 + 4) {
         k1 = 16776960;
      }

      this.gameGraphics.drawString("Click here to logout", i + 3, i1, 1, k1);
      if (flag) {
         i = super.mouseX - (this.gameGraphics.menuDefaultWidth - 199);
         j = super.mouseY - 36;
         if (i >= 0 && j >= 0 && i < 196 && j < 265) {
            int l1 = this.gameGraphics.menuDefaultWidth - 199;
            byte byte0 = 36;
            char c1 = 196;
            int l = l1 + 3;
            int j1 = byte0 + 30;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               this.configAutoCameraAngle = !this.configAutoCameraAngle;
               super.streamClass.createPacket(157);
               super.streamClass.addByte(0);
               super.streamClass.addByte(this.configAutoCameraAngle ? 1 : 0);
               super.streamClass.formatPacket();
            }

            j1 += 15;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               this.configMouseButtons = !this.configMouseButtons;
               super.streamClass.createPacket(157);
               super.streamClass.addByte(2);
               super.streamClass.addByte(this.configMouseButtons ? 1 : 0);
               super.streamClass.formatPacket();
            }

            j1 += 15;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               this.configSoundEffects = !this.configSoundEffects;
               super.streamClass.createPacket(157);
               super.streamClass.addByte(3);
               super.streamClass.addByte(this.configSoundEffects ? 1 : 0);
               super.streamClass.formatPacket();
            }

            /* Five dead rows: the account-management text is not clickable.
               Jagex counted them off one at a time and so does this, because
               the number that matters is where the row after them lands. */
            j1 += 15;
            j1 += 15;
            j1 += 15;
            j1 += 15;
            j1 += 15;
            boolean flag1 = false;
            j1 += 35;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               super.blockChatMessages = 1 - super.blockChatMessages;
               flag1 = true;
            }

            j1 += 15;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               super.blockPrivateMessages = 1 - super.blockPrivateMessages;
               flag1 = true;
            }

            j1 += 15;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               super.blockTradeRequests = 1 - super.blockTradeRequests;
               flag1 = true;
            }

            j1 += 15;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               super.blockDuelRequests = 1 - super.blockDuelRequests;
               flag1 = true;
            }

            j1 += 15;
            if (flag1) {
               this.sendUpdatedPrivacyInfo(super.blockChatMessages, super.blockPrivateMessages, super.blockTradeRequests, super.blockDuelRequests);
            }

            j1 += 20;
            if (super.mouseX > l && super.mouseX < l + c1 && super.mouseY > j1 - 12 && super.mouseY < j1 + 4 && this.mouseButtonClick == 1) {
               this.logout();
            }

            this.mouseButtonClick = 0;
         }
      }
   }

   /*
    * The three client assists RSCD had added to the Game options menu, which
    * the F2 settings page owns now. The flags are still the client's own and
    * the indices are still the server's -- 4 roofs, 5 auto screenshot, 6
    * fightmode selector, all inside the 0..6 GameSettingHandler accepts -- so
    * moving the switch has not stopped the server remembering the setting.
    */
   final void setClientAssist(int index, boolean on) {
      switch (index) {
         case 4:
            this.showRoof = on;
            break;
         case 5:
            this.autoScreenshot = on;
            break;
         case 6:
            this.combatWindow = on;
            break;
         default:
            return;
      }

      /* Nothing to tell if there is no connection: the panel can be open on a
         frame where the socket has just gone. */
      if (this.loggedIn == 1 && super.streamClass != null) {
         super.streamClass.createPacket(157);
         super.streamClass.addByte(index);
         super.streamClass.addByte(on ? 1 : 0);
         super.streamClass.formatPacket();
      }
   }

   private final void processGame() {
      if (this.systemUpdate > 1) {
         this.systemUpdate--;
      }

      this.sendPingPacketReadPacketData();

      /* Delivers the answer to a prompt the client thread itself asked --
         Withdraw X, Deposit X, Buy X, Sell X. Nothing happens on a frame where
         no such prompt is waiting, and a script's own prompt is not this
         method's business: that one is read by the thread parked in ask().
         Unconditional, at the top, so a pending answer cannot be stranded by
         whatever screen the player is on when it arrives. */
      this.scriptPrompt.poll();
      this.processAutocast();

      if (this.logoutTimeout > 0) {
         this.logoutTimeout--;
      }

      if (this.ourPlayer.currentSprite == 8 || this.ourPlayer.currentSprite == 9) {
         this.lastWalkTimeout = 500;
      }

      if (this.lastWalkTimeout > 0) {
         this.lastWalkTimeout--;
      }

      if (this.showCharacterLookScreen) {
         this.drawCharacterLookScreen();
      } else {
         for (int i = 0; i < this.playerCount; i++) {
            Mob mob = this.playerArray[i];
            int k = (mob.waypointCurrent + 1) % 10;
            if (mob.waypointEndSprite != k) {
               int i1 = -1;
               int l2 = mob.waypointEndSprite;
               int j4;
               if (l2 < k) {
                  j4 = k - l2;
               } else {
                  j4 = 10 + k - l2;
               }

               int j5 = 4;
               if (j4 > 2) {
                  j5 = (j4 - 1) * 4;
               }

               if (mob.waypointsX[l2] - mob.currentX <= this.magicLoc * 3
                  && mob.waypointsY[l2] - mob.currentY <= this.magicLoc * 3
                  && mob.waypointsX[l2] - mob.currentX >= -this.magicLoc * 3
                  && mob.waypointsY[l2] - mob.currentY >= -this.magicLoc * 3
                  && j4 <= 8) {
                  if (mob.currentX < mob.waypointsX[l2]) {
                     mob.currentX += j5;
                     mob.stepCount++;
                     i1 = 2;
                  } else if (mob.currentX > mob.waypointsX[l2]) {
                     mob.currentX -= j5;
                     mob.stepCount++;
                     i1 = 6;
                  }

                  if (mob.currentX - mob.waypointsX[l2] < j5 && mob.currentX - mob.waypointsX[l2] > -j5) {
                     mob.currentX = mob.waypointsX[l2];
                  }

                  if (mob.currentY < mob.waypointsY[l2]) {
                     mob.currentY += j5;
                     mob.stepCount++;
                     if (i1 == -1) {
                        i1 = 4;
                     } else if (i1 == 2) {
                        i1 = 3;
                     } else {
                        i1 = 5;
                     }
                  } else if (mob.currentY > mob.waypointsY[l2]) {
                     mob.currentY -= j5;
                     mob.stepCount++;
                     if (i1 == -1) {
                        i1 = 0;
                     } else if (i1 == 2) {
                        i1 = 1;
                     } else {
                        i1 = 7;
                     }
                  }

                  if (mob.currentY - mob.waypointsY[l2] < j5 && mob.currentY - mob.waypointsY[l2] > -j5) {
                     mob.currentY = mob.waypointsY[l2];
                  }
               } else {
                  mob.currentX = mob.waypointsX[l2];
                  mob.currentY = mob.waypointsY[l2];
               }

               if (i1 != -1) {
                  mob.currentSprite = i1;
               }

               if (mob.currentX == mob.waypointsX[l2] && mob.currentY == mob.waypointsY[l2]) {
                  mob.waypointEndSprite = (l2 + 1) % 10;
               }
            } else {
               mob.currentSprite = mob.nextSprite;
            }

            if (mob.lastMessageTimeout > 0) {
               mob.lastMessageTimeout--;
            }

            if (mob.bubbleTimeout > 0) {
               mob.bubbleTimeout--;
            }

            if (mob.combatTimer > 0) {
               mob.combatTimer--;
            }

            if (this.playerAliveTimeout > 0) {
               this.playerAliveTimeout--;
               if (this.playerAliveTimeout == 0) {
                  this.displayMessage("@pnk@ You have been granted another life. Be more careful this time!", 3, 0);
               }

               if (this.playerAliveTimeout == 0) {
                  this.displayMessage("@pnk@ You retain your skills. Your objects land where you died", 3, 0);
               }
            }
         }

         for (int j = 0; j < this.npcCount; j++) {
            Mob mob_1 = this.npcArray[j];
            int j1 = (mob_1.waypointCurrent + 1) % 10;
            if (mob_1.waypointEndSprite != j1) {
               int i3 = -1;
               int k4 = mob_1.waypointEndSprite;
               int k5;
               if (k4 < j1) {
                  k5 = j1 - k4;
               } else {
                  k5 = 10 + j1 - k4;
               }

               int l5 = 4;
               if (k5 > 2) {
                  l5 = (k5 - 1) * 4;
               }

               if (mob_1.waypointsX[k4] - mob_1.currentX <= this.magicLoc * 3
                  && mob_1.waypointsY[k4] - mob_1.currentY <= this.magicLoc * 3
                  && mob_1.waypointsX[k4] - mob_1.currentX >= -this.magicLoc * 3
                  && mob_1.waypointsY[k4] - mob_1.currentY >= -this.magicLoc * 3
                  && k5 <= 8) {
                  if (mob_1.currentX < mob_1.waypointsX[k4]) {
                     mob_1.currentX += l5;
                     mob_1.stepCount++;
                     i3 = 2;
                  } else if (mob_1.currentX > mob_1.waypointsX[k4]) {
                     mob_1.currentX -= l5;
                     mob_1.stepCount++;
                     i3 = 6;
                  }

                  if (mob_1.currentX - mob_1.waypointsX[k4] < l5 && mob_1.currentX - mob_1.waypointsX[k4] > -l5) {
                     mob_1.currentX = mob_1.waypointsX[k4];
                  }

                  if (mob_1.currentY < mob_1.waypointsY[k4]) {
                     mob_1.currentY += l5;
                     mob_1.stepCount++;
                     if (i3 == -1) {
                        i3 = 4;
                     } else if (i3 == 2) {
                        i3 = 3;
                     } else {
                        i3 = 5;
                     }
                  } else if (mob_1.currentY > mob_1.waypointsY[k4]) {
                     mob_1.currentY -= l5;
                     mob_1.stepCount++;
                     if (i3 == -1) {
                        i3 = 0;
                     } else if (i3 == 2) {
                        i3 = 1;
                     } else {
                        i3 = 7;
                     }
                  }

                  if (mob_1.currentY - mob_1.waypointsY[k4] < l5 && mob_1.currentY - mob_1.waypointsY[k4] > -l5) {
                     mob_1.currentY = mob_1.waypointsY[k4];
                  }
               } else {
                  mob_1.currentX = mob_1.waypointsX[k4];
                  mob_1.currentY = mob_1.waypointsY[k4];
               }

               if (i3 != -1) {
                  mob_1.currentSprite = i3;
               }

               if (mob_1.currentX == mob_1.waypointsX[k4] && mob_1.currentY == mob_1.waypointsY[k4]) {
                  mob_1.waypointEndSprite = (k4 + 1) % 10;
               }
            } else {
               mob_1.currentSprite = mob_1.nextSprite;
               if (mob_1.type == 43) {
                  mob_1.stepCount++;
               }
            }

            if (mob_1.lastMessageTimeout > 0) {
               mob_1.lastMessageTimeout--;
            }

            if (mob_1.bubbleTimeout > 0) {
               mob_1.bubbleTimeout--;
            }

            if (mob_1.combatTimer > 0) {
               mob_1.combatTimer--;
            }
         }

         /*
          * The other half of Jagex's minimap anti-tamper. GameImage counts, per
          * frame, how many blips were drawn at the compass rotation versus not;
          * this keeps the run of consecutive frames where they all agreed. A
          * client hacked to draw the minimap unrotated (the classic macro aid)
          * never mismatches, so the run grows past 20 forever. Only the reset
          * survives here -- whatever Jagex did on detection went with the
          * original obfuscated body.
          */
         if (this.mouseOverMenu != 2) {
            if (GameImage.spriteRotationMatchCount > 0) {
               this.spriteRotationMatchRun++;
            }

            if (GameImage.spriteRotationMismatchCount > 0) {
               this.spriteRotationMatchRun = 0;
            }

            GameImage.spriteRotationMatchCount = 0;
            GameImage.spriteRotationMismatchCount = 0;
         }

         for (int l = 0; l < this.playerCount; l++) {
            Mob mob_2 = this.playerArray[l];
            if (mob_2.projectileCountdown > 0) {
               mob_2.projectileCountdown--;
            }
         }

         // Npc-fired shots have to age out on the same clock, or one would sit
         // frozen at full range and be redrawn every frame for ever.
         for (int l = 0; l < this.npcCount; l++) {
            Mob mob_3 = this.npcArray[l];
            if (mob_3.projectileCountdown > 0) {
               mob_3.projectileCountdown--;
            }
         }

         if (this.cameraAutoAngleDebug) {
            if (this.lastAutoCameraRotatePlayerX - this.ourPlayer.currentX < -500
               || this.lastAutoCameraRotatePlayerX - this.ourPlayer.currentX > 500
               || this.lastAutoCameraRotatePlayerY - this.ourPlayer.currentY < -500
               || this.lastAutoCameraRotatePlayerY - this.ourPlayer.currentY > 500) {
               this.lastAutoCameraRotatePlayerX = this.ourPlayer.currentX;
               this.lastAutoCameraRotatePlayerY = this.ourPlayer.currentY;
            }
         } else {
            if (this.lastAutoCameraRotatePlayerX - this.ourPlayer.currentX < -500
               || this.lastAutoCameraRotatePlayerX - this.ourPlayer.currentX > 500
               || this.lastAutoCameraRotatePlayerY - this.ourPlayer.currentY < -500
               || this.lastAutoCameraRotatePlayerY - this.ourPlayer.currentY > 500) {
               this.lastAutoCameraRotatePlayerX = this.ourPlayer.currentX;
               this.lastAutoCameraRotatePlayerY = this.ourPlayer.currentY;
            }

            if (this.lastAutoCameraRotatePlayerX != this.ourPlayer.currentX) {
               this.lastAutoCameraRotatePlayerX = this.lastAutoCameraRotatePlayerX
                  + (this.ourPlayer.currentX - this.lastAutoCameraRotatePlayerX) / (16 + (this.cameraHeight - 500) / 15);
            }

            if (this.lastAutoCameraRotatePlayerY != this.ourPlayer.currentY) {
               this.lastAutoCameraRotatePlayerY = this.lastAutoCameraRotatePlayerY
                  + (this.ourPlayer.currentY - this.lastAutoCameraRotatePlayerY) / (16 + (this.cameraHeight - 500) / 15);
            }

            if (this.configAutoCameraAngle) {
               int k1 = this.cameraAutoAngle * 32;
               int j3 = k1 - this.cameraRotation;
               byte byte0 = 1;
               if (j3 != 0) {
                  this.cameraRotationBaseAddition++;
                  if (j3 > 128) {
                     byte0 = -1;
                     j3 = 256 - j3;
                  } else if (j3 > 0) {
                     byte0 = 1;
                  } else if (j3 < -128) {
                     byte0 = 1;
                     j3 += 256;
                  } else if (j3 < 0) {
                     byte0 = -1;
                     j3 = -j3;
                  }

                  this.cameraRotation = this.cameraRotation + (this.cameraRotationBaseAddition * j3 + 255) / 256 * byte0;
                  this.cameraRotation &= 255;
               } else {
                  this.cameraRotationBaseAddition = 0;
               }
            }
         }

         if (this.spriteRotationMatchRun > 20) {
            this.spriteRotationMatchRun = 0;
         }

         /*
          * The chat tabs, and who owns the bottom of the screen.
          *
          * This strip takes every click below windowHeight - 4 and then clears
          * the button, so the tab it just switched cannot also be read as
          * something else further down. That is right while the tabs are what
          * is down there.
          *
          * With a panel drawn over them they are not. The world map's control
          * strip sits on rows 324..341 and the tabs start at 330, so every row
          * of those buttons but the top seven had its button taken away before
          * the map was ever asked -- a row of buttons that only answers along
          * its top edge.
          */
         if (super.mouseY > this.windowHeight - 4 && !this.panelOwnsScreen()) {
            if (super.mouseX > 15 && super.mouseX < 96 && super.lastMouseDownButton == 1) {
               this.messagesTab = 0;
            }

            if (super.mouseX > 110 && super.mouseX < 194 && super.lastMouseDownButton == 1) {
               this.messagesTab = 1;
               this.gameMenu.menuListScrollOffset[this.messagesHandleType2] = 999999;
            }

            if (super.mouseX > 215 && super.mouseX < 295 && super.lastMouseDownButton == 1) {
               this.messagesTab = 2;
               this.gameMenu.menuListScrollOffset[this.messagesHandleType5] = 999999;
            }

            if (super.mouseX > 315 && super.mouseX < 395 && super.lastMouseDownButton == 1) {
               this.messagesTab = 3;
               this.gameMenu.menuListScrollOffset[this.messagesHandleType6] = 999999;
            }

            if (super.mouseX > 417 && super.mouseX < 497 && super.lastMouseDownButton == 1) {
               this.showAbuseWindow = 1;
               this.abuseSelectedType = 0;
               super.inputText = "";
               super.enteredText = "";
            }

            super.lastMouseDownButton = 0;
            super.mouseDownButton = 0;
         }

         this.gameMenu.updateActions(super.mouseX, super.mouseY, super.lastMouseDownButton, super.mouseDownButton);
         /* Same again for the message scrollbar, which claims the right hand
            column the map's Close button also stands in.
            (The map's own Close button already tracks windowWidth through
            WorldMapPanel.viewW(), and panelOwnsScreen() below hands input to
            the map instead of this guard whenever it is open, so the two
            never actually fight over a click -- verified, not just assumed.)

            494 was this guard hard-coded for the old fixed 512px layout: the
            box built in drawGameMenu() is x=5, width=windowWidth-10, so its
            scrollbar sits windowWidth-18 from the left (512-18 == 494). Tying
            the guard to that same expression keeps it lined up with the
            scrollbar's real position as the window resizes, instead of only
            being correct at the one width it was measured at. */
         if (this.messagesTab > 0 && super.mouseX >= this.windowWidth - 18 && super.mouseY >= this.windowHeight - 66 && !this.panelOwnsScreen()) {
            super.lastMouseDownButton = 0;
         }

         if (this.gameMenu.hasActivated(this.chatHandle)) {
            String s = this.lastMessage = this.gameMenu.getText(this.chatHandle);
            this.gameMenu.updateText(this.chatHandle, "");
            if (s.startsWith("::")) {
               /* The admin plane: the server's commands, handed on untouched.
                  The client keeps nothing on :: any more. */
               this.sendChatString(s.substring(2));
            } else if (s.startsWith("/")) {
               /* The user plane -- STS's / layer plus the script commands. A
                  / line belongs to the client: recognised or not, it is never
                  chat and never reaches the server, exactly as STS treated
                  it. That eats a chat line that merely starts with / ("/50
                  each" in a haggle), which is STS's own tradeoff kept: better
                  a hint than a typoed command said aloud. OnInput fires here
                  rather than inside the handlers so a recognised command is
                  not also echoed to the script. */
               s = s.substring(1);
               if (!this.handleScriptCommand(s) && !this.handleCommand(s)) {
                  if (this.scriptRunner != null) {
                     this.scriptRunner.fireInput(s);
                     if (this.scriptDebug) {
                        this.scriptRunner.fireDebug(s);
                     }
                  }

                  this.displayMessage("@gry@ Unknown command: /" + s, 3, 0);
               }
            } else {
               byte[] chatMessage = DataConversions.stringToByteArray(s);
               this.sendChatMessage(chatMessage, chatMessage.length);
               s = DataConversions.byteToString(chatMessage, 0, chatMessage.length);
               this.ourPlayer.lastMessageTimeout = 150;
               this.ourPlayer.lastMessage = s;
               this.displayMessage(this.ourPlayer.name + ": " + s, 2, this.ourPlayer.admin);
            }
         }

         if (this.messagesTab == 0) {
            for (int l1 = 0; l1 < 5; l1++) {
               if (this.messagesTimeout[l1] > 0) {
                  this.messagesTimeout[l1]--;
               }
            }
         }

         if (this.playerAliveTimeout != 0) {
            super.lastMouseDownButton = 0;
         }

         if (!this.showTradeWindow && !this.showDuelWindow) {
            this.mouseDownTime = 0;
            this.itemIncrement = 0;
         } else {
            if (super.mouseDownButton != 0) {
               this.mouseDownTime++;
            } else {
               this.mouseDownTime = 0;
            }

            if (this.mouseDownTime > 500) {
               this.itemIncrement += 100000;
            } else if (this.mouseDownTime > 350) {
               this.itemIncrement += 10000;
            } else if (this.mouseDownTime > 250) {
               this.itemIncrement += 1000;
            } else if (this.mouseDownTime > 150) {
               this.itemIncrement += 100;
            } else if (this.mouseDownTime > 100) {
               this.itemIncrement += 10;
            } else if (this.mouseDownTime > 50) {
               this.itemIncrement++;
            } else if (this.mouseDownTime > 20 && (this.mouseDownTime & 5) == 0) {
               this.itemIncrement++;
            }
         }

         if (super.lastMouseDownButton == 1) {
            this.mouseButtonClick = 1;
         } else if (super.lastMouseDownButton == 2) {
            this.mouseButtonClick = 2;
         }

         /*
          * While the menu is up it takes the click and the game does not get
          * one -- otherwise pressing Start also walks the character to wherever
          * that button happened to be. Only the click is swallowed: movement,
          * packets and the script itself carry on underneath.
          */
         /*
          * A prompt is modal, so it eats the click before anything else sees
          * it. It draws over the menu but had no say in input, so clicking it
          * -- which is the natural thing to do to give a dialog focus -- fell
          * straight through to whatever menu row happened to be underneath and
          * launched a second script.
          *
          * There is nothing to hit-test: the box is answered from the keyboard,
          * so every click anywhere is simply discarded while it is up. That
          * also stops the click reaching the world and walking the character
          * somewhere while a script waits on an answer.
          */
         if (this.scriptPrompt.isOpen()) {
            this.mouseButtonClick = 0;
         }

         /*
          * The map is drawn over the F2 menu, so it takes the mouse before it.
          * It takes the held button rather than the click because panning is a
          * drag and a drag is only visible in the held state; the click is
          * discarded either way, so it can neither fall through to the menu
          * underneath nor walk the character across the world behind it.
          */
         if (this.worldMapPanel != null && this.worldMapPanel.isOpen()) {
            this.worldMapPanel.update(super.mouseX, super.mouseY, super.mouseDownButton);
            this.mouseButtonClick = 0;
         }

         /* Drawn over the F2 menu, so it takes the mouse before it. */
         if (this.calculatorPanel != null && this.calculatorPanel.isOpen() && this.mouseButtonClick != 0) {
            this.calculatorPanel.handleClick(super.mouseX, super.mouseY, this.mouseButtonClick);
            this.mouseButtonClick = 0;
         }

         if (this.scriptPanel != null && this.scriptPanel.isOpen()) {
            /* Every tick, held button or not, so the scroll triangles can
               repeat while it stays down -- see ScriptPanel.tick(). */
            this.scriptPanel.tick(super.mouseX, super.mouseY, super.mouseDownButton);
            if (this.mouseButtonClick != 0) {
               this.scriptPanel.handleClick(super.mouseX, super.mouseY, this.mouseButtonClick);
               this.mouseButtonClick = 0;
            }
         }

         this.gameCamera.updateMouseCoords(super.mouseX, super.mouseY);
         super.lastMouseDownButton = 0;
         if (this.configAutoCameraAngle) {
            if (this.cameraRotationBaseAddition == 0 || this.cameraAutoAngleDebug) {
               if (super.keyLeftDown) {
                  this.cameraAutoAngle = this.cameraAutoAngle + 1 & 7;
                  super.keyLeftDown = false;
                  if (!this.zoomCamera) {
                     if ((this.cameraAutoAngle & 1) == 0) {
                        this.cameraAutoAngle = this.cameraAutoAngle + 1 & 7;
                     }

                     for (int i2 = 0; i2 < 8 && !this.enginePlayerVisible(this.cameraAutoAngle); i2++) {
                        this.cameraAutoAngle = this.cameraAutoAngle + 1 & 7;
                     }
                  }
               }

               if (super.keyRightDown) {
                  this.cameraAutoAngle = this.cameraAutoAngle + 7 & 7;
                  super.keyRightDown = false;
                  if (!this.zoomCamera) {
                     if ((this.cameraAutoAngle & 1) == 0) {
                        this.cameraAutoAngle = this.cameraAutoAngle + 7 & 7;
                     }

                     for (int j2 = 0; j2 < 8 && !this.enginePlayerVisible(this.cameraAutoAngle); j2++) {
                        this.cameraAutoAngle = this.cameraAutoAngle + 7 & 7;
                     }
                  }
               }
            }
         } else if (super.keyLeftDown) {
            this.cameraRotation = this.cameraRotation + 2 & 0xFF;
         } else if (super.keyRightDown) {
            this.cameraRotation = this.cameraRotation - 2 & 0xFF;
         }

         if (this.actionPictureType > 0) {
            this.actionPictureType--;
         } else if (this.actionPictureType < 0) {
            this.actionPictureType++;
         }

         this.gameCamera.scrollTexture(17);
         this.modelUpdatingTimer++;
         if (this.modelUpdatingTimer > 5) {
            this.modelUpdatingTimer = 0;
            this.modelFireLightningSpellNumber = (this.modelFireLightningSpellNumber + 1) % 3;
            this.modelTorchNumber = (this.modelTorchNumber + 1) % 4;
            this.modelClawSpellNumber = (this.modelClawSpellNumber + 1) % 5;
         }

         for (int k2 = 0; k2 < this.objectCount; k2++) {
            int l3 = this.objectX[k2];
            int l4 = this.objectY[k2];
            if (l3 >= 0 && l4 >= 0 && l3 < 96 && l4 < 96 && this.objectType[k2] == 74) {
               this.objectModelArray[k2].rotateBy(1, 0, 0);
            }
         }

         for (int i4 = 0; i4 < this.teleBubbleCount; i4++) {
            this.teleBubbleTime[i4]++;
            if (this.teleBubbleTime[i4] > 50) {
               this.teleBubbleCount--;

               for (int i5 = i4; i5 < this.teleBubbleCount; i5++) {
                  this.teleBubbleX[i5] = this.teleBubbleX[i5 + 1];
                  this.teleBubbleY[i5] = this.teleBubbleY[i5 + 1];
                  this.teleBubbleTime[i5] = this.teleBubbleTime[i5 + 1];
                  this.teleBubbleType[i5] = this.teleBubbleType[i5 + 1];
               }
            }
         }
      }
   }

   private final void loadSounds() {
      try {
         this.drawLoadingBarText(84, "Unpacking Sound Effects");
         this.sounds = this.load("sounds1.mem");
         this.audioReader = new AudioReader();
      } catch (Throwable var2) {
         System.out.println("Unable to init sounds:" + var2);
      }
   }

   private final void drawCombatStyleWindow() {
      byte byte0 = 7;
      byte byte1 = 15;
      char c = 175;
      if (this.mouseButtonClick != 0) {
         for (int i = 0; i < 5; i++) {
            if (i > 0
               && super.mouseX > byte0
               && super.mouseX < byte0 + c
               && super.mouseY > byte1 + i * 20
               && super.mouseY < byte1 + i * 20 + 20
               && this.lockedCombatStyle == -1) {
               this.combatStyle = i - 1;
               this.mouseButtonClick = 0;
               super.streamClass.createPacket(42);
               super.streamClass.addByte(this.combatStyle);
               super.streamClass.formatPacket();
               break;
            }
         }
      }

      for (int j = 0; j < 5; j++) {
         if (j == this.combatStyle + 1) {
            this.gameGraphics.drawBoxAlpha(byte0, byte1 + j * 20, c, 20, GameImage.convertRGBToLong(255, 0, 0), 128);
         } else {
            this.gameGraphics.drawBoxAlpha(byte0, byte1 + j * 20, c, 20, GameImage.convertRGBToLong(190, 190, 190), 128);
         }

         this.gameGraphics.drawLineX(byte0, byte1 + j * 20, c, 0);
         this.gameGraphics.drawLineX(byte0, byte1 + j * 20 + 20, c, 0);
      }

      this.gameGraphics.drawText("Select combat style", byte0 + c / 2, byte1 + 16, 3, 16777215);
      this.gameGraphics.drawText("Controlled (+1 of each)", byte0 + c / 2, byte1 + 36, 3, 0);
      this.gameGraphics.drawText("Aggressive (+3 strength)", byte0 + c / 2, byte1 + 56, 3, 0);
      this.gameGraphics.drawText("Accurate   (+3 attack)", byte0 + c / 2, byte1 + 76, 3, 0);
      this.gameGraphics.drawText("Defensive  (+3 defense)", byte0 + c / 2, byte1 + 96, 3, 0);
   }

   private final void drawDuelConfirmWindow() {
      int byte0 = 22 + this.loginOffsetX();
      int byte1 = 36 + this.loginOffsetY();
      this.gameGraphics.drawBox(byte0, byte1, 468, 16, 192);
      int i = 10000536;
      this.gameGraphics.drawBoxAlpha(byte0, byte1 + 16, 468, 246, i, 160);
      this.gameGraphics
         .drawText("Please confirm your duel with @yel@" + DataOperations.longToString(this.duelOpponentNameLong), byte0 + 234, byte1 + 12, 1, 16777215);
      this.gameGraphics.drawText("Your stake:", byte0 + 117, byte1 + 30, 1, 16776960);

      for (int j = 0; j < this.duelConfirmMyItemCount; j++) {
         String s = EntityHandler.getItemDef(this.duelConfirmMyItems[j]).getName();
         if (EntityHandler.getItemDef(this.duelConfirmMyItems[j]).isStackable()) {
            s = s + " x " + formatItemCount(this.duelConfirmMyItemsCount[j]);
         }

         this.gameGraphics.drawText(s, byte0 + 117, byte1 + 42 + j * 12, 1, 16777215);
      }

      if (this.duelConfirmMyItemCount == 0) {
         this.gameGraphics.drawText("Nothing!", byte0 + 117, byte1 + 42, 1, 16777215);
      }

      this.gameGraphics.drawText("Your opponent's stake:", byte0 + 351, byte1 + 30, 1, 16776960);

      for (int k = 0; k < this.duelConfirmOpponentItemCount; k++) {
         String s1 = EntityHandler.getItemDef(this.duelConfirmOpponentItems[k]).getName();
         if (EntityHandler.getItemDef(this.duelConfirmOpponentItems[k]).isStackable()) {
            s1 = s1 + " x " + formatItemCount(this.duelConfirmOpponentItemsCount[k]);
         }

         this.gameGraphics.drawText(s1, byte0 + 351, byte1 + 42 + k * 12, 1, 16777215);
      }

      if (this.duelConfirmOpponentItemCount == 0) {
         this.gameGraphics.drawText("Nothing!", byte0 + 351, byte1 + 42, 1, 16777215);
      }

      if (this.duelCantRetreat == 0) {
         this.gameGraphics.drawText("You can retreat from this duel", byte0 + 234, byte1 + 180, 1, 65280);
      } else {
         this.gameGraphics.drawText("No retreat is possible!", byte0 + 234, byte1 + 180, 1, 16711680);
      }

      if (this.duelUseMagic == 0) {
         this.gameGraphics.drawText("Magic may be used", byte0 + 234, byte1 + 192, 1, 65280);
      } else {
         this.gameGraphics.drawText("Magic cannot be used", byte0 + 234, byte1 + 192, 1, 16711680);
      }

      if (this.duelUsePrayer == 0) {
         this.gameGraphics.drawText("Prayer may be used", byte0 + 234, byte1 + 204, 1, 65280);
      } else {
         this.gameGraphics.drawText("Prayer cannot be used", byte0 + 234, byte1 + 204, 1, 16711680);
      }

      if (this.duelUseWeapons == 0) {
         this.gameGraphics.drawText("Weapons may be used", byte0 + 234, byte1 + 216, 1, 65280);
      } else {
         this.gameGraphics.drawText("Weapons cannot be used", byte0 + 234, byte1 + 216, 1, 16711680);
      }

      this.gameGraphics.drawText("If you are sure click 'Accept' to begin the duel", byte0 + 234, byte1 + 230, 1, 16777215);
      if (!this.duelWeAccept) {
         this.gameGraphics.drawPicture(byte0 + 118 - 35, byte1 + 238, 2025);
         this.gameGraphics.drawPicture(byte0 + 352 - 35, byte1 + 238, 2026);
      } else {
         this.gameGraphics.drawText("Waiting for other player...", byte0 + 234, byte1 + 250, 1, 16776960);
      }

      if (this.mouseButtonClick == 1) {
         if (super.mouseX < byte0 || super.mouseY < byte1 || super.mouseX > byte0 + 468 || super.mouseY > byte1 + 262) {
            this.showDuelConfirmWindow = false;
            super.streamClass.createPacket(35);
            super.streamClass.formatPacket();
         }

         if (super.mouseX >= byte0 + 118 - 35 && super.mouseX <= byte0 + 118 + 70 && super.mouseY >= byte1 + 238 && super.mouseY <= byte1 + 238 + 21) {
            this.duelWeAccept = true;
            super.streamClass.createPacket(87);
            super.streamClass.formatPacket();
         }

         if (super.mouseX >= byte0 + 352 - 35 && super.mouseX <= byte0 + 353 + 70 && super.mouseY >= byte1 + 238 && super.mouseY <= byte1 + 238 + 21) {
            this.showDuelConfirmWindow = false;
            super.streamClass.createPacket(35);
            super.streamClass.formatPacket();
         }

         this.mouseButtonClick = 0;
      }
   }

   private final void updateBankItems() {
      this.bankItemCount = this.newBankItemCount;

      for (int i = 0; i < this.newBankItemCount; i++) {
         this.bankItems[i] = this.newBankItems[i];
         this.bankItemsCount[i] = this.newBankItemsCount[i];
      }

      for (int j = 0; j < this.inventoryCount && this.bankItemCount < this.bankItemsMax; j++) {
         int k = this.inventoryItems[j];
         boolean flag = false;
         int l = 0;

         while (true) {
            if (l < this.bankItemCount) {
               if (this.bankItems[l] != k) {
                  l++;
                  continue;
               }

               flag = true;
            }

            if (!flag) {
               this.bankItems[this.bankItemCount] = k;
               this.bankItemsCount[this.bankItemCount] = 0;
               this.bankItemCount++;
            }
            break;
         }
      }
   }

   private final void makeCharacterDesignMenu() {
      /* Centred like the login menus; drawCharacterDesignScreen draws the preview heads with
         the same offsets. */
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      this.characterDesignMenu = new Menu(this.gameGraphics, 100);
      this.characterDesignMenu.drawText(256 + ox, 10 + oy, "Please design Your Character", 4, true);
      int i = 140 + ox;
      int j = 34 + oy;
      i += 116;
      j -= 10;
      this.characterDesignMenu.drawText(i - 55, j + 110, "Front", 3, true);
      this.characterDesignMenu.drawText(i, j + 110, "Side", 3, true);
      this.characterDesignMenu.drawText(i + 55, j + 110, "Back", 3, true);
      byte byte0 = 54;
      j += 145;
      this.characterDesignMenu.makeRoundedBox(i - byte0, j, 53, 41);
      this.characterDesignMenu.drawText(i - byte0, j - 8, "Head", 1, true);
      this.characterDesignMenu.drawText(i - byte0, j + 8, "Type", 1, true);
      this.characterDesignMenu.makePicture(i - byte0 - 40, j, 2107);
      this.characterDesignHeadButton1 = this.characterDesignMenu.makeButton(i - byte0 - 40, j, 20, 20);
      this.characterDesignMenu.makePicture(i - byte0 + 40, j, 2106);
      this.characterDesignHeadButton2 = this.characterDesignMenu.makeButton(i - byte0 + 40, j, 20, 20);
      this.characterDesignMenu.makeRoundedBox(i + byte0, j, 53, 41);
      this.characterDesignMenu.drawText(i + byte0, j - 8, "Hair", 1, true);
      this.characterDesignMenu.drawText(i + byte0, j + 8, "Colour", 1, true);
      this.characterDesignMenu.makePicture(i + byte0 - 40, j, 2107);
      this.characterDesignHairColourButton1 = this.characterDesignMenu.makeButton(i + byte0 - 40, j, 20, 20);
      this.characterDesignMenu.makePicture(i + byte0 + 40, j, 2106);
      this.characterDesignHairColourButton2 = this.characterDesignMenu.makeButton(i + byte0 + 40, j, 20, 20);
      j += 50;
      this.characterDesignMenu.makeRoundedBox(i - byte0, j, 53, 41);
      this.characterDesignMenu.drawText(i - byte0, j, "Gender", 1, true);
      this.characterDesignMenu.makePicture(i - byte0 - 40, j, 2107);
      this.characterDesignGenderButton1 = this.characterDesignMenu.makeButton(i - byte0 - 40, j, 20, 20);
      this.characterDesignMenu.makePicture(i - byte0 + 40, j, 2106);
      this.characterDesignGenderButton2 = this.characterDesignMenu.makeButton(i - byte0 + 40, j, 20, 20);
      this.characterDesignMenu.makeRoundedBox(i + byte0, j, 53, 41);
      this.characterDesignMenu.drawText(i + byte0, j - 8, "Top", 1, true);
      this.characterDesignMenu.drawText(i + byte0, j + 8, "Colour", 1, true);
      this.characterDesignMenu.makePicture(i + byte0 - 40, j, 2107);
      this.characterDesignTopColourButton1 = this.characterDesignMenu.makeButton(i + byte0 - 40, j, 20, 20);
      this.characterDesignMenu.makePicture(i + byte0 + 40, j, 2106);
      this.characterDesignTopColourButton2 = this.characterDesignMenu.makeButton(i + byte0 + 40, j, 20, 20);
      j += 50;
      this.characterDesignMenu.makeRoundedBox(i - byte0, j, 53, 41);
      this.characterDesignMenu.drawText(i - byte0, j - 8, "Skin", 1, true);
      this.characterDesignMenu.drawText(i - byte0, j + 8, "Colour", 1, true);
      this.characterDesignMenu.makePicture(i - byte0 - 40, j, 2107);
      this.characterDesignSkinColourButton1 = this.characterDesignMenu.makeButton(i - byte0 - 40, j, 20, 20);
      this.characterDesignMenu.makePicture(i - byte0 + 40, j, 2106);
      this.characterDesignSkinColourButton2 = this.characterDesignMenu.makeButton(i - byte0 + 40, j, 20, 20);
      this.characterDesignMenu.makeRoundedBox(i + byte0, j, 53, 41);
      this.characterDesignMenu.drawText(i + byte0, j - 8, "Bottom", 1, true);
      this.characterDesignMenu.drawText(i + byte0, j + 8, "Colour", 1, true);
      this.characterDesignMenu.makePicture(i + byte0 - 40, j, 2107);
      this.characterDesignBottomColourButton1 = this.characterDesignMenu.makeButton(i + byte0 - 40, j, 20, 20);
      this.characterDesignMenu.makePicture(i + byte0 + 40, j, 2106);
      this.characterDesignBottomColourButton2 = this.characterDesignMenu.makeButton(i + byte0 + 40, j, 20, 20);
      j += 82;
      j -= 35;
      this.characterDesignMenu.drawBox(i, j, 200, 30);
      this.characterDesignMenu.drawText(i, j, "Accept", 4, false);
      this.characterDesignAcceptButton = this.characterDesignMenu.makeButton(i, j, 200, 30);
   }

   private final void drawAbuseWindow2() {
      if (super.enteredText.length() > 0) {
         String s = super.enteredText.trim();
         super.inputText = "";
         super.enteredText = "";
         if (s.length() > 0) {
            long l = DataOperations.stringLength12ToLong(s);
            super.streamClass.createPacket(7);
            super.streamClass.addTwo4ByteInts(l);
            super.streamClass.addByte(this.abuseSelectedType);
            super.streamClass.formatPacket();
         }

         this.showAbuseWindow = 0;
      } else {
         int ox = this.loginOffsetX();
         int oy = this.loginOffsetY();
         this.gameGraphics.drawBox(56 + ox, 130 + oy, 400, 100, 0);
         this.gameGraphics.drawBoxEdge(56 + ox, 130 + oy, 400, 100, 16777215);
         int i = 160 + oy;
         this.gameGraphics.drawText("Now type the name of the offending player, and press enter", 256 + ox, i, 1, 16776960);
         i += 18;
         this.gameGraphics.drawText("Name: " + super.inputText + "*", 256 + ox, i, 4, 16777215);
         int var5 = 222 + oy;
         int j = 16777215;
         if (super.mouseX > 196 + ox && super.mouseX < 316 + ox && super.mouseY > var5 - 13 && super.mouseY < var5 + 2) {
            j = 16776960;
            if (this.mouseButtonClick == 1) {
               this.mouseButtonClick = 0;
               this.showAbuseWindow = 0;
            }
         }

         this.gameGraphics.drawText("Click here to cancel", 256 + ox, var5, 1, j);
         if (this.mouseButtonClick == 1 && (super.mouseX < 56 + ox || super.mouseX > 456 + ox || super.mouseY < 130 + oy || super.mouseY > 230 + oy)) {
            this.mouseButtonClick = 0;
            this.showAbuseWindow = 0;
         }
      }
   }

   final void displayMessage(String message, int type, int status) {
      if (type == 2 || type == 4 || type == 6) {
         while (message.length() > 5 && message.charAt(0) == '@' && message.charAt(4) == '@') {
            message = message.substring(5);
         }
      }

      message = message.replaceAll("\\#pmd\\#", "");
      message = message.replaceAll("\\#mod\\#", "");
      message = message.replaceAll("\\#adm\\#", "");
      if (type == 2) {
         message = "@yel@" + message;
      }

      if (type == 3 || type == 4) {
         message = "@whi@" + message;
      }

      if (type == 6) {
         message = "@cya@" + message;
      }

      if (status == 1) {
         message = "#pmd#" + message;
      }

      if (status == 2) {
         message = "#mod#" + message;
      }

      if (status == 3) {
         message = "#adm#" + message;
      }

      if (this.messagesTab != 0) {
         if (type == 4 || type == 3) {
            this.allMessagesTabFlash = 200;
         }

         if (type == 2 && this.messagesTab != 1) {
            this.chatHistoryTabFlash = 200;
         }

         if (type == 5 && this.messagesTab != 2) {
            this.questHistoryTabFlash = 200;
         }

         if (type == 6 && this.messagesTab != 3) {
            this.privateHistoryTabFlash = 200;
         }

         if (type == 3 && this.messagesTab != 0) {
            this.messagesTab = 0;
         }

         if (type == 6 && this.messagesTab != 3 && this.messagesTab != 0) {
            this.messagesTab = 0;
         }
      }

      for (int k = 4; k > 0; k--) {
         this.messagesArray[k] = this.messagesArray[k - 1];
         this.messagesTimeout[k] = this.messagesTimeout[k - 1];
      }

      this.messagesArray[0] = message;
      this.messagesTimeout[0] = 300;
      if (type == 2) {
         if (this.gameMenu.menuListScrollOffset[this.messagesHandleType2] == this.gameMenu.menuListTextCount[this.messagesHandleType2] - 4) {
            this.gameMenu.addString(this.messagesHandleType2, message, true);
         } else {
            this.gameMenu.addString(this.messagesHandleType2, message, false);
         }
      }

      if (type == 5) {
         if (this.gameMenu.menuListScrollOffset[this.messagesHandleType5] == this.gameMenu.menuListTextCount[this.messagesHandleType5] - 4) {
            this.gameMenu.addString(this.messagesHandleType5, message, true);
         } else {
            this.gameMenu.addString(this.messagesHandleType5, message, false);
         }
      }

      if (type == 6) {
         if (this.gameMenu.menuListScrollOffset[this.messagesHandleType6] == this.gameMenu.menuListTextCount[this.messagesHandleType6] - 4) {
            this.gameMenu.addString(this.messagesHandleType6, message, true);
            return;
         }

         this.gameMenu.addString(this.messagesHandleType6, message, false);
      }
   }

   @Override
   protected final void logoutAndStop() {
      this.sendLogoutPacket();
      this.garbageCollect();
      if (this.audioReader != null) {
         this.audioReader.stopAudio();
      }
   }

   private final void replaceObjectModel(int index, String modelName) {
      int j = this.objectX[index];
      int k = this.objectY[index];
      int l = j - this.ourPlayer.currentX / 128;
      int i1 = k - this.ourPlayer.currentY / 128;
      byte byte0 = 7;
      if (j >= 0 && k >= 0 && j < 96 && k < 96 && l > -byte0 && l < byte0 && i1 > -byte0 && i1 < byte0) {
         this.gameCamera.removeModel(this.objectModelArray[index]);
         int j1 = EntityHandler.storeModel(modelName);

         try {
            Model model = this.gameDataModels[j1].copy();
            this.gameCamera.addModel(model);
            model.setLight(true, 48, 48, -50, -10, -50);
            model.copyPosition(this.objectModelArray[index]);
            model.key = index;
            this.objectModelArray[index] = model;
         } catch (Exception var10) {
         }
      }
   }

   @Override
   protected final void resetVars() {
      this.systemUpdate = 0;
      this.combatStyle = 0;
      this.logoutTimeout = 0;
      this.loginScreenNumber = 0;
      this.loggedIn = 1;
      /* We are in, so the next time we are not, AutoLogin starts from the
         short wait again rather than the backed-off one. */
      this.autoLoginTicks = AUTO_LOGIN_WAIT;
      this.autoLoginTries = 0;
      /* A bot's script must outlive the outage that just ended -- see
         pumpScriptResume(), which acts on this from the game tick. */
      this.scripts().loginHappened();
      this.resetPrivateMessageStrings();
      this.gameGraphics.clearScreen();
      this.gameGraphics.drawImage(this.aGraphics936, 0, 0);

      for (int i = 0; i < this.objectCount; i++) {
         this.gameCamera.removeModel(this.objectModelArray[i]);
         this.engineHandle.updateObject(this.objectX[i], this.objectY[i], this.objectType[i], this.objectID[i]);
      }

      for (int j = 0; j < this.doorCount; j++) {
         this.gameCamera.removeModel(this.doorModel[j]);
         this.engineHandle.updateDoor(this.doorX[j], this.doorY[j], this.doorDirection[j], this.doorType[j]);
      }

      this.objectCount = 0;
      this.doorCount = 0;
      this.groundItemCount = 0;
      this.playerCount = 0;

      for (int k = 0; k < this.mobArray.length; k++) {
         this.mobArray[k] = null;
      }

      for (int l = 0; l < this.playerArray.length; l++) {
         this.playerArray[l] = null;
      }

      this.npcCount = 0;

      for (int i1 = 0; i1 < this.npcRecordArray.length; i1++) {
         this.npcRecordArray[i1] = null;
      }

      for (int j1 = 0; j1 < this.npcArray.length; j1++) {
         this.npcArray[j1] = null;
      }

      for (int k1 = 0; k1 < this.prayerOn.length; k1++) {
         this.prayerOn[k1] = false;
      }

      this.mouseButtonClick = 0;
      super.lastMouseDownButton = 0;
      super.mouseDownButton = 0;
      this.showShop = false;
      this.showBank = false;
      super.friendsCount = 0;
   }

   /*
    * Opens the Offer/Stake quantity popup for an item under the mouse in
    * "Your Inventory" on the trade or duel screen. Captured here rather than
    * read again at click time: which slot the player right-clicked can stop
    * being valid (item traded away by the other panel, inventory reordered)
    * by the time they pick a quantity, and addToTradeOffer/addToDuelStake
    * both re-check the slot still holds the same item before doing anything.
    */
   private final void openOfferMenu(int itemId, int invIndex, boolean isDuel) {
      this.offerMenuItem = itemId;
      this.offerMenuInvIndex = invIndex;
      this.offerMenuIsDuel = isDuel;

      /* Bounds are fixed here, once, rather than recomputed by both the click
         handler and the renderer every frame -- the same split the world
         right-click menu makes between building menuX/menuY/menuWidth at open
         time and just reading them afterwards in drawRightClickMenu. */
      String verb = isDuel ? "Stake" : "Offer";
      String[] options = new String[]{verb + " 1", verb + " 5", verb + " 10", verb + " All", verb + " X"};
      int width = this.gameGraphics.textWidth("Choose option", 1) + 5;

      for (int i = 0; i < options.length; i++) {
         int w = this.gameGraphics.textWidth(options[i], 1) + 5;
         if (w > width) {
            width = w;
         }
      }

      this.offerMenuWidth = width;
      this.offerMenuHeight = (options.length + 1) * 15;
      this.offerMenuBoxX = Math.max(0, super.mouseX - width / 2);
      this.offerMenuBoxY = Math.max(0, super.mouseY - 7);
      this.showOfferMenu = true;
   }

   /*
    * Adds up to `amount` more of `itemId` (currently at inventory slot
    * `invIndex`) to the trade offer -- the single-shot form of
    * drawTradeWindow's click-and-hold add, used by the Offer 1/5/10/X menu
    * entries directly and by Offer All via amount=Integer.MAX_VALUE (the room
    * checks below clamp it either way). A stackable item tops up its one
    * offer slot; a non-stackable item takes one offer slot per copy, so it
    * can only add as many as the trade has open slots and the player still
    * holds unoffered.
    */
   private final void addToTradeOffer(int itemId, int invIndex, int amount) {
      if (amount <= 0 || invIndex < 0 || invIndex >= this.inventoryCount || this.inventoryItems[invIndex] != itemId) {
         return;
      }

      boolean changed = false;
      if (EntityHandler.getItemDef(itemId).isStackable()) {
         int held = this.inventoryItemsCount[invIndex];
         int slot = -1;
         for (int i = 0; i < this.tradeMyItemCount; i++) {
            if (this.tradeMyItems[i] == itemId) {
               slot = i;
               break;
            }
         }

         if (slot == -1 && this.tradeMyItemCount < 12) {
            slot = this.tradeMyItemCount;
            this.tradeMyItems[slot] = itemId;
            this.tradeMyItemsCount[slot] = 0;
            this.tradeMyItemCount++;
         }

         if (slot != -1) {
            int add = Math.min(amount, held - this.tradeMyItemsCount[slot]);
            if (add > 0) {
               this.tradeMyItemsCount[slot] += add;
               changed = true;
            }
         }
      } else {
         int alreadyOffered = 0;
         for (int i = 0; i < this.tradeMyItemCount; i++) {
            if (this.tradeMyItems[i] == itemId) {
               alreadyOffered++;
            }
         }

         int room = this.inventoryCount(itemId) - alreadyOffered;
         int toAdd = Math.min(amount, room);
         for (int i = 0; i < toAdd && this.tradeMyItemCount < 12; i++) {
            this.tradeMyItems[this.tradeMyItemCount] = itemId;
            this.tradeMyItemsCount[this.tradeMyItemCount] = 1;
            this.tradeMyItemCount++;
            changed = true;
         }
      }

      if (changed) {
         super.streamClass.createPacket(70);
         super.streamClass.addByte(this.tradeMyItemCount);

         for (int i = 0; i < this.tradeMyItemCount; i++) {
            super.streamClass.add2ByteInt(this.tradeMyItems[i]);
            super.streamClass.add4ByteInt(this.tradeMyItemsCount[i]);
         }

         super.streamClass.formatPacket();
         this.tradeOtherAccepted = false;
         this.tradeWeAccepted = false;
      }
   }

   /* Duel's stake, same shape as addToTradeOffer -- see it for the reasoning
      behind the stackable/non-stackable split. Separate rather than shared
      because the arrays, slot cap (8, not 12) and packet (123, not 70) all
      differ, matching every other trade/duel pair in this class. */
   private final void addToDuelStake(int itemId, int invIndex, int amount) {
      if (amount <= 0 || invIndex < 0 || invIndex >= this.inventoryCount || this.inventoryItems[invIndex] != itemId) {
         return;
      }

      boolean changed = false;
      if (EntityHandler.getItemDef(itemId).isStackable()) {
         int held = this.inventoryItemsCount[invIndex];
         int slot = -1;
         for (int i = 0; i < this.duelMyItemCount; i++) {
            if (this.duelMyItems[i] == itemId) {
               slot = i;
               break;
            }
         }

         if (slot == -1 && this.duelMyItemCount < 8) {
            slot = this.duelMyItemCount;
            this.duelMyItems[slot] = itemId;
            this.duelMyItemsCount[slot] = 0;
            this.duelMyItemCount++;
         }

         if (slot != -1) {
            int add = Math.min(amount, held - this.duelMyItemsCount[slot]);
            if (add > 0) {
               this.duelMyItemsCount[slot] += add;
               changed = true;
            }
         }
      } else {
         int alreadyStaked = 0;
         for (int i = 0; i < this.duelMyItemCount; i++) {
            if (this.duelMyItems[i] == itemId) {
               alreadyStaked++;
            }
         }

         int room = this.inventoryCount(itemId) - alreadyStaked;
         int toAdd = Math.min(amount, room);
         for (int i = 0; i < toAdd && this.duelMyItemCount < 8; i++) {
            this.duelMyItems[this.duelMyItemCount] = itemId;
            this.duelMyItemsCount[this.duelMyItemCount] = 1;
            this.duelMyItemCount++;
            changed = true;
         }
      }

      if (changed) {
         super.streamClass.createPacket(123);
         super.streamClass.addByte(this.duelMyItemCount);

         for (int i = 0; i < this.duelMyItemCount; i++) {
            super.streamClass.add2ByteInt(this.duelMyItems[i]);
            super.streamClass.add4ByteInt(this.duelMyItemsCount[i]);
         }

         super.streamClass.formatPacket();
         this.duelOpponentAccepted = false;
         this.duelMyAccepted = false;
      }
   }

   /*
    * Consumes a click against the Offer/Stake popup, or dismisses it on a
    * click anywhere else -- called at the very top of drawTradeWindow /
    * drawDuelWindow, before either screen gets its own turn at the same
    * click, so a tap on a popup option can never also land on whatever the
    * trade/duel screen has underneath it. Read-only when there is no click
    * this tick (mouseButtonClick == 0): the popup stays open and
    * drawOfferMenuOverlay draws its hover state off the same live mouse
    * position on its own, later pass.
    *
    * Click-to-dismiss rather than the world right-click menu's mouse-off
    * dismiss (drawRightClickMenu closes the instant the cursor leaves its
    * bounds, click or not) -- that would make this unusable on a
    * touchscreen, where there is no hover state between "not touching" and
    * "tapping the option". See rscd-www's mobile keyboard work: this client
    * is played on tablets and phones too.
    */
   private final void handleOfferMenuClick() {
      if (this.mouseButtonClick == 0) {
         return;
      }

      int chosen = -1;
      for (int i = 0; i < 5; i++) {
         int lineY = this.offerMenuBoxY + 27 + i * 15;
         if (super.mouseX > this.offerMenuBoxX
            && super.mouseY > lineY - 12
            && super.mouseY < lineY + 4
            && super.mouseX < this.offerMenuBoxX + this.offerMenuWidth) {
            chosen = i;
            break;
         }
      }

      this.mouseButtonClick = 0;
      this.showOfferMenu = false;
      if (chosen == -1) {
         return;
      }

      final int itemId = this.offerMenuItem;
      final int invIndex = this.offerMenuInvIndex;
      final boolean isDuel = this.offerMenuIsDuel;
      if (chosen == 0) {
         this.applyOfferAmount(itemId, invIndex, isDuel, 1);
      } else if (chosen == 1) {
         this.applyOfferAmount(itemId, invIndex, isDuel, 5);
      } else if (chosen == 2) {
         this.applyOfferAmount(itemId, invIndex, isDuel, 10);
      } else if (chosen == 3) {
         this.applyOfferAmount(itemId, invIndex, isDuel, Integer.MAX_VALUE);
      } else {
         this.scriptPrompt.askAsync((isDuel ? "Stake" : "Offer") + " how many?", new ScriptPrompt.Answer() {
            public void got(String text) {
               int amount = SalvageInput(text);
               if (amount > 0) {
                  mudclient.this.applyOfferAmount(itemId, invIndex, isDuel, amount);
               }
            }
         });
      }
   }

   /* The draw half of the popup -- see handleOfferMenuClick for the click
      half and why they are split. Styled like drawRightClickMenu (same box
      colour, same hover highlight) so it reads as part of the same UI
      language. Called after the trade/duel screen draws its own contents, so
      it overlays on top rather than getting painted over. */
   private final void drawOfferMenuOverlay() {
      String verb = this.offerMenuIsDuel ? "Stake" : "Offer";
      String[] options = new String[]{verb + " 1", verb + " 5", verb + " 10", verb + " All", verb + " X"};
      int x = this.offerMenuBoxX;
      int y = this.offerMenuBoxY;

      this.gameGraphics.drawBoxAlpha(x, y, this.offerMenuWidth, this.offerMenuHeight, 13684944, 160);
      this.gameGraphics.drawString("Choose option", x + 2, y + 12, 1, 65535);

      for (int i = 0; i < options.length; i++) {
         int lineY = y + 27 + i * 15;
         int colour = 16777215;
         if (super.mouseX > x && super.mouseY > lineY - 12 && super.mouseY < lineY + 4 && super.mouseX < x + this.offerMenuWidth) {
            colour = 16776960;
         }

         this.gameGraphics.drawString(options[i], x + 2, lineY, 1, colour);
      }
   }

   private final void applyOfferAmount(int itemId, int invIndex, boolean isDuel, int amount) {
      if (isDuel) {
         this.addToDuelStake(itemId, invIndex, amount);
      } else {
         this.addToTradeOffer(itemId, invIndex, amount);
      }
   }

   private final void drawTradeWindow() {
      if (this.showOfferMenu && !this.offerMenuIsDuel) {
         /* The trade can also close from a server packet (other player
            declined, logged out) while this is open, not only from a click
            drawTradeWindow itself sees -- so it needs to notice the window is
            gone here too, not just when handleOfferMenuClick eventually
            consumes a click that may never come. */
         if (!this.showTradeWindow) {
            this.showOfferMenu = false;
         } else {
            this.handleOfferMenuClick();
         }
      } else {
         /* Right-click on an inventory item opens Offer 1/5/10/All/X instead
            of the plain add-one-and-repeat-while-held a left click still
            does below -- see openOfferMenu. Consuming the click here (before
            the generic mouseButtonClick != 0 check just past it) keeps a
            right-click from also being read as a left one. */
         if (this.mouseButtonClick == 2 && this.itemIncrement == 0) {
            int menuI = super.mouseX - (22 + this.loginOffsetX());
            int menuJ = super.mouseY - (36 + this.loginOffsetY());
            if (menuI > 216 && menuJ > 30 && menuI < 462 && menuJ < 235) {
               int menuK = (menuI - 217) / 49 + (menuJ - 31) / 34 * 5;
               if (menuK >= 0 && menuK < this.inventoryCount) {
                  this.openOfferMenu(this.inventoryItems[menuK], menuK, false);
                  this.mouseButtonClick = 0;
               }
            }
         }

         if (this.mouseButtonClick != 0 && this.itemIncrement == 0) {
            this.itemIncrement = 1;
         }

         if (this.itemIncrement > 0) {
            int i = super.mouseX - (22 + this.loginOffsetX());
            int j = super.mouseY - (36 + this.loginOffsetY());
            if (i >= 0 && j >= 0 && i < 468 && j < 262) {
               if (i > 216 && j > 30 && i < 462 && j < 235) {
                  int k = (i - 217) / 49 + (j - 31) / 34 * 5;
                  if (k >= 0 && k < this.inventoryCount) {
                     boolean flag = false;
                     int l1 = 0;
                     int k2 = this.inventoryItems[k];

                     for (int k3 = 0; k3 < this.tradeMyItemCount; k3++) {
                     if (this.tradeMyItems[k3] == k2) {
                        if (EntityHandler.getItemDef(k2).isStackable()) {
                           for (int i4 = 0; i4 < this.itemIncrement; i4++) {
                              if (this.tradeMyItemsCount[k3] < this.inventoryItemsCount[k]) {
                                 this.tradeMyItemsCount[k3]++;
                              }

                              flag = true;
                           }
                        } else {
                           l1++;
                        }
                     }
                  }

                  if (this.inventoryCount(k2) <= l1) {
                     flag = true;
                  }

                  if (!flag && this.tradeMyItemCount < 12) {
                     this.tradeMyItems[this.tradeMyItemCount] = k2;
                     this.tradeMyItemsCount[this.tradeMyItemCount] = 1;
                     this.tradeMyItemCount++;
                     flag = true;
                  }

                  if (flag) {
                     super.streamClass.createPacket(70);
                     super.streamClass.addByte(this.tradeMyItemCount);

                     for (int j4 = 0; j4 < this.tradeMyItemCount; j4++) {
                        super.streamClass.add2ByteInt(this.tradeMyItems[j4]);
                        super.streamClass.add4ByteInt(this.tradeMyItemsCount[j4]);
                     }

                     super.streamClass.formatPacket();
                     this.tradeOtherAccepted = false;
                     this.tradeWeAccepted = false;
                  }
               }
            }

            if (i > 8 && j > 30 && i < 205 && j < 133) {
               int l = (i - 9) / 49 + (j - 31) / 34 * 4;
               if (l >= 0 && l < this.tradeMyItemCount) {
                  int j1 = this.tradeMyItems[l];

                  for (int i2 = 0; i2 < this.itemIncrement; i2++) {
                     if (!EntityHandler.getItemDef(j1).isStackable() || this.tradeMyItemsCount[l] <= 1) {
                        this.tradeMyItemCount--;
                        this.mouseDownTime = 0;

                        for (int l2 = l; l2 < this.tradeMyItemCount; l2++) {
                           this.tradeMyItems[l2] = this.tradeMyItems[l2 + 1];
                           this.tradeMyItemsCount[l2] = this.tradeMyItemsCount[l2 + 1];
                        }
                        break;
                     }

                     this.tradeMyItemsCount[l]--;
                  }

                  super.streamClass.createPacket(70);
                  super.streamClass.addByte(this.tradeMyItemCount);

                  for (int i3 = 0; i3 < this.tradeMyItemCount; i3++) {
                     super.streamClass.add2ByteInt(this.tradeMyItems[i3]);
                     super.streamClass.add4ByteInt(this.tradeMyItemsCount[i3]);
                  }

                  super.streamClass.formatPacket();
                  this.tradeOtherAccepted = false;
                  this.tradeWeAccepted = false;
               }
            }

            if (i >= 217 && j >= 238 && i <= 286 && j <= 259) {
               this.tradeWeAccepted = true;
               super.streamClass.createPacket(211);
               super.streamClass.formatPacket();
            }

            if (i >= 394 && j >= 238 && i < 463 && j < 259) {
               this.showTradeWindow = false;
               super.streamClass.createPacket(216);
               super.streamClass.formatPacket();
            }
         } else if (this.mouseButtonClick != 0) {
            this.showTradeWindow = false;
            super.streamClass.createPacket(216);
            super.streamClass.formatPacket();
         }

         this.mouseButtonClick = 0;
         this.itemIncrement = 0;
      }
      }

      if (this.showTradeWindow) {
         int byte0 = 22 + this.loginOffsetX();
         int byte1 = 36 + this.loginOffsetY();
         this.gameGraphics.drawBox(byte0, byte1, 468, 12, 192);
         int i1 = 10000536;
         this.gameGraphics.drawBoxAlpha(byte0, byte1 + 12, 468, 18, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0, byte1 + 30, 8, 248, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 205, byte1 + 30, 11, 248, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 462, byte1 + 30, 6, 248, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 133, 197, 22, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 258, 197, 20, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 216, byte1 + 235, 246, 43, i1, 160);
         int k1 = 13684944;
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 30, 197, 103, k1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 155, 197, 103, k1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 216, byte1 + 30, 246, 205, k1, 160);

         for (int j2 = 0; j2 < 4; j2++) {
            this.gameGraphics.drawLineX(byte0 + 8, byte1 + 30 + j2 * 34, 197, 0);
         }

         for (int j3 = 0; j3 < 4; j3++) {
            this.gameGraphics.drawLineX(byte0 + 8, byte1 + 155 + j3 * 34, 197, 0);
         }

         for (int l3 = 0; l3 < 7; l3++) {
            this.gameGraphics.drawLineX(byte0 + 216, byte1 + 30 + l3 * 34, 246, 0);
         }

         for (int k4 = 0; k4 < 6; k4++) {
            if (k4 < 5) {
               this.gameGraphics.drawLineY(byte0 + 8 + k4 * 49, byte1 + 30, 103, 0);
            }

            if (k4 < 5) {
               this.gameGraphics.drawLineY(byte0 + 8 + k4 * 49, byte1 + 155, 103, 0);
            }

            this.gameGraphics.drawLineY(byte0 + 216 + k4 * 49, byte1 + 30, 205, 0);
         }

         this.gameGraphics.drawString("Trading with: " + this.tradeOtherPlayerName, byte0 + 1, byte1 + 10, 1, 16777215);
         this.gameGraphics.drawString("Your Offer", byte0 + 9, byte1 + 27, 4, 16777215);
         this.gameGraphics.drawString("Opponent's Offer", byte0 + 9, byte1 + 152, 4, 16777215);
         this.gameGraphics.drawString("Your Inventory", byte0 + 216, byte1 + 27, 4, 16777215);
         if (!this.tradeWeAccepted) {
            this.gameGraphics.drawPicture(byte0 + 217, byte1 + 238, 2025);
         }

         this.gameGraphics.drawPicture(byte0 + 394, byte1 + 238, 2026);
         if (this.tradeOtherAccepted) {
            this.gameGraphics.drawText("Other player", byte0 + 341, byte1 + 246, 1, 16777215);
            this.gameGraphics.drawText("has accepted", byte0 + 341, byte1 + 256, 1, 16777215);
         }

         if (this.tradeWeAccepted) {
            this.gameGraphics.drawText("Waiting for", byte0 + 217 + 35, byte1 + 246, 1, 16777215);
            this.gameGraphics.drawText("other player", byte0 + 217 + 35, byte1 + 256, 1, 16777215);
         }

         for (int l4 = 0; l4 < this.inventoryCount; l4++) {
            int i5 = 217 + byte0 + l4 % 5 * 49;
            int k5 = 31 + byte1 + l4 / 5 * 34;
            this.gameGraphics
               .spriteClip4(
                  i5,
                  k5,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.inventoryItems[l4]).getSprite(),
                  EntityHandler.getItemDef(this.inventoryItems[l4]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.inventoryItems[l4]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.inventoryItemsCount[l4]), i5 + 1, k5 + 10, 1, 16776960);
            }
         }

         for (int j5 = 0; j5 < this.tradeMyItemCount; j5++) {
            int l5 = 9 + byte0 + j5 % 4 * 49;
            int j6 = 31 + byte1 + j5 / 4 * 34;
            this.gameGraphics
               .spriteClip4(
                  l5,
                  j6,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.tradeMyItems[j5]).getSprite(),
                  EntityHandler.getItemDef(this.tradeMyItems[j5]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.tradeMyItems[j5]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.tradeMyItemsCount[j5]), l5 + 1, j6 + 10, 1, 16776960);
            }

            if (super.mouseX > l5 && super.mouseX < l5 + 48 && super.mouseY > j6 && super.mouseY < j6 + 32) {
               this.gameGraphics
                  .drawString(
                     EntityHandler.getItemDef(this.tradeMyItems[j5]).getName() + ": @whi@" + EntityHandler.getItemDef(this.tradeMyItems[j5]).getDescription(),
                     byte0 + 8,
                     byte1 + 273,
                     1,
                     16776960
                  );
            }
         }

         for (int i6 = 0; i6 < this.tradeOtherItemCount; i6++) {
            int k6 = 9 + byte0 + i6 % 4 * 49;
            int l6 = 156 + byte1 + i6 / 4 * 34;
            this.gameGraphics
               .spriteClip4(
                  k6,
                  l6,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.tradeOtherItems[i6]).getSprite(),
                  EntityHandler.getItemDef(this.tradeOtherItems[i6]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.tradeOtherItems[i6]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.tradeOtherItemsCount[i6]), k6 + 1, l6 + 10, 1, 16776960);
            }

            if (super.mouseX > k6 && super.mouseX < k6 + 48 && super.mouseY > l6 && super.mouseY < l6 + 32) {
               this.gameGraphics
                  .drawString(
                     EntityHandler.getItemDef(this.tradeOtherItems[i6]).getName()
                        + ": @whi@"
                        + EntityHandler.getItemDef(this.tradeOtherItems[i6]).getDescription(),
                     byte0 + 8,
                     byte1 + 273,
                     1,
                     16776960
                  );
            }
         }
      }

      if (this.showTradeWindow && this.showOfferMenu && !this.offerMenuIsDuel) {
         this.drawOfferMenuOverlay();
      }
   }

   private final boolean enginePlayerVisible(int i) {
      int j = this.ourPlayer.currentX / 128;
      int k = this.ourPlayer.currentY / 128;
      int l = 2;

      while (l >= 1) {
         if (i != 1
            || (this.engineHandle.walkableValue[j][k - l] & 128) != 128
               && (this.engineHandle.walkableValue[j - l][k] & 128) != 128
               && (this.engineHandle.walkableValue[j - l][k - l] & 128) != 128) {
            if (i != 3
               || (this.engineHandle.walkableValue[j][k + l] & 128) != 128
                  && (this.engineHandle.walkableValue[j - l][k] & 128) != 128
                  && (this.engineHandle.walkableValue[j - l][k + l] & 128) != 128) {
               if (i != 5
                  || (this.engineHandle.walkableValue[j][k + l] & 128) != 128
                     && (this.engineHandle.walkableValue[j + l][k] & 128) != 128
                     && (this.engineHandle.walkableValue[j + l][k + l] & 128) != 128) {
                  if (i != 7
                     || (this.engineHandle.walkableValue[j][k - l] & 128) != 128
                        && (this.engineHandle.walkableValue[j + l][k] & 128) != 128
                        && (this.engineHandle.walkableValue[j + l][k - l] & 128) != 128) {
                     if (i == 0 && (this.engineHandle.walkableValue[j][k - l] & 128) == 128) {
                        return false;
                     }

                     if (i == 2 && (this.engineHandle.walkableValue[j - l][k] & 128) == 128) {
                        return false;
                     }

                     if (i == 4 && (this.engineHandle.walkableValue[j][k + l] & 128) == 128) {
                        return false;
                     }

                     if (i == 6 && (this.engineHandle.walkableValue[j + l][k] & 128) == 128) {
                        return false;
                     }

                     l--;
                     continue;
                  }

                  return false;
               }

               return false;
            }

            return false;
         }

         return false;
      }

      return true;
   }

   private Mob getLastPlayer(int serverIndex) {
      for (int i1 = 0; i1 < this.lastPlayerCount; i1++) {
         if (this.lastPlayerArray[i1].serverIndex == serverIndex) {
            return this.lastPlayerArray[i1];
         }
      }

      return null;
   }

   private Mob getLastNpc(int serverIndex) {
      for (int i1 = 0; i1 < this.lastNpcCount; i1++) {
         if (this.lastNpcArray[i1].serverIndex == serverIndex) {
            return this.lastNpcArray[i1];
         }
      }

      return null;
   }

   @Override
   protected final void handleIncomingPacket(int command, int length, byte[] data) {
      try {
         this.noteIncomingForAutocast();

         if (command == 110) {
            int i = 1;
            this.serverStartTime = DataOperations.getUnsigned8Bytes(data, i);
            i += 8;
            this.serverLocation = new String(data, i, length - i);
            return;
         }

         if (command == 145) {
            if (!this.hasWorldInfo) {
               return;
            }

            this.lastPlayerCount = this.playerCount;

            for (int k = 0; k < this.lastPlayerCount; k++) {
               this.lastPlayerArray[k] = this.playerArray[k];
            }

            int currentOffset = 8;
            this.sectionX = DataOperations.getIntFromByteArray(data, currentOffset, 11);
            currentOffset += 11;
            this.sectionY = DataOperations.getIntFromByteArray(data, currentOffset, 13);
            currentOffset += 13;
            int mobSprite = DataOperations.getIntFromByteArray(data, currentOffset, 4);
            currentOffset += 4;
            boolean sectionLoaded = this.loadSection(this.sectionX, this.sectionY);
            this.sectionX = this.sectionX - this.areaX;
            this.sectionY = this.sectionY - this.areaY;
            int mapEnterX = this.sectionX * this.magicLoc + 64;
            int mapEnterY = this.sectionY * this.magicLoc + 64;
            if (sectionLoaded) {
               this.ourPlayer.waypointCurrent = 0;
               this.ourPlayer.waypointEndSprite = 0;
               this.ourPlayer.currentX = this.ourPlayer.waypointsX[0] = mapEnterX;
               this.ourPlayer.currentY = this.ourPlayer.waypointsY[0] = mapEnterY;
            }

            this.playerCount = 0;
            this.ourPlayer = this.makePlayer(this.serverIndex, mapEnterX, mapEnterY, mobSprite);
            int newPlayerCount = DataOperations.getIntFromByteArray(data, currentOffset, 8);
            currentOffset += 8;

            for (int currentNewPlayer = 0; currentNewPlayer < newPlayerCount; currentNewPlayer++) {
               Mob lastMob = this.getLastPlayer(DataOperations.getIntFromByteArray(data, currentOffset, 16));
               currentOffset += 16;
               int nextPlayer = DataOperations.getIntFromByteArray(data, currentOffset, 1);
               currentOffset++;
               if (nextPlayer != 0) {
                  int waypointsLeft = DataOperations.getIntFromByteArray(data, currentOffset, 1);
                  currentOffset++;
                  if (waypointsLeft == 0) {
                     int currentNextSprite = DataOperations.getIntFromByteArray(data, currentOffset, 3);
                     currentOffset += 3;
                     int currentWaypoint = lastMob.waypointCurrent;
                     int newWaypointX = lastMob.waypointsX[currentWaypoint];
                     int newWaypointY = lastMob.waypointsY[currentWaypoint];
                     if (currentNextSprite == 2 || currentNextSprite == 1 || currentNextSprite == 3) {
                        newWaypointX += this.magicLoc;
                     }

                     if (currentNextSprite == 6 || currentNextSprite == 5 || currentNextSprite == 7) {
                        newWaypointX -= this.magicLoc;
                     }

                     if (currentNextSprite == 4 || currentNextSprite == 3 || currentNextSprite == 5) {
                        newWaypointY += this.magicLoc;
                     }

                     if (currentNextSprite == 0 || currentNextSprite == 1 || currentNextSprite == 7) {
                        newWaypointY -= this.magicLoc;
                     }

                     lastMob.nextSprite = currentNextSprite;
                     int var289;
                     lastMob.waypointCurrent = var289 = (currentWaypoint + 1) % 10;
                     lastMob.waypointsX[var289] = newWaypointX;
                     lastMob.waypointsY[var289] = newWaypointY;
                  } else {
                     int needsNextSprite = DataOperations.getIntFromByteArray(data, currentOffset, 4);
                     currentOffset += 4;
                     if ((needsNextSprite & 12) == 12) {
                        continue;
                     }

                     lastMob.nextSprite = needsNextSprite;
                  }
               }

               this.playerArray[this.playerCount++] = lastMob;
            }

            int mobCount = 0;

            while (currentOffset + 24 < length * 8) {
               int mobIndex = DataOperations.getIntFromByteArray(data, currentOffset, 16);
               currentOffset += 16;
               int areaMobX = DataOperations.getIntFromByteArray(data, currentOffset, 5);
               currentOffset += 5;
               if (areaMobX > 15) {
                  areaMobX -= 32;
               }

               int areaMobY = DataOperations.getIntFromByteArray(data, currentOffset, 5);
               currentOffset += 5;
               if (areaMobY > 15) {
                  areaMobY -= 32;
               }

               int mobArrayMobID = DataOperations.getIntFromByteArray(data, currentOffset, 4);
               currentOffset += 4;
               int addIndex = DataOperations.getIntFromByteArray(data, currentOffset, 1);
               currentOffset++;
               int mobX = (this.sectionX + areaMobX) * this.magicLoc + 64;
               int mobY = (this.sectionY + areaMobY) * this.magicLoc + 64;
               this.makePlayer(mobIndex, mobX, mobY, mobArrayMobID);
               if (addIndex == 0) {
                  this.mobArrayIndexes[mobCount++] = mobIndex;
               }
            }

            if (mobCount > 0) {
               super.streamClass.createPacket(83);
               super.streamClass.add2ByteInt(mobCount);

               for (int currentMob = 0; currentMob < mobCount; currentMob++) {
                  Mob dummyMob = this.mobArray[this.mobArrayIndexes[currentMob]];
                  super.streamClass.add2ByteInt(dummyMob.serverIndex);
                  super.streamClass.add2ByteInt(dummyMob.mobIntUnknown);
               }

               super.streamClass.formatPacket();
               int var255 = 0;
            }

            return;
         }

         if (command == 109) {
            int l = 1;

            while (l < length) {
               if (DataOperations.getUnsignedByte(data[l]) == 255) {
                  int newCount = 0;
                  int newSectionX = this.sectionX + data[l + 1] >> 3;
                  int newSectionY = this.sectionY + data[l + 2] >> 3;
                  l += 3;

                  for (int groundItem = 0; groundItem < this.groundItemCount; groundItem++) {
                     int newX = (this.groundItemX[groundItem] >> 3) - newSectionX;
                     int newY = (this.groundItemY[groundItem] >> 3) - newSectionY;
                     if (newX != 0 || newY != 0) {
                        if (groundItem != newCount) {
                           this.groundItemX[newCount] = this.groundItemX[groundItem];
                           this.groundItemY[newCount] = this.groundItemY[groundItem];
                           this.groundItemType[newCount] = this.groundItemType[groundItem];
                           this.groundItemObjectVar[newCount] = this.groundItemObjectVar[groundItem];
                        }

                        newCount++;
                     }
                  }

                  this.groundItemCount = newCount;
               } else {
                  int i8 = DataOperations.getUnsigned2Bytes(data, l);
                  l += 2;
                  int k14 = this.sectionX + data[l++];
                  int j19 = this.sectionY + data[l++];
                  if ((i8 & 32768) == 0) {
                     this.groundItemX[this.groundItemCount] = k14;
                     this.groundItemY[this.groundItemCount] = j19;
                     this.groundItemType[this.groundItemCount] = i8;
                     this.groundItemObjectVar[this.groundItemCount] = 0;
                     int k23 = 0;

                     while (true) {
                        if (k23 < this.objectCount) {
                           if (this.objectX[k23] != k14 || this.objectY[k23] != j19) {
                              k23++;
                              continue;
                           }

                           this.groundItemObjectVar[this.groundItemCount] = EntityHandler.getObjectDef(this.objectType[k23]).getGroundItemVar();
                        }

                        this.groundItemCount++;
                        break;
                     }
                  } else {
                     i8 &= 32767;
                     int l23 = 0;

                     for (int k26 = 0; k26 < this.groundItemCount; k26++) {
                        if (this.groundItemX[k26] == k14 && this.groundItemY[k26] == j19 && this.groundItemType[k26] == i8) {
                           i8 = -123;
                        } else {
                           if (k26 != l23) {
                              this.groundItemX[l23] = this.groundItemX[k26];
                              this.groundItemY[l23] = this.groundItemY[k26];
                              this.groundItemType[l23] = this.groundItemType[k26];
                              this.groundItemObjectVar[l23] = this.groundItemObjectVar[k26];
                           }

                           l23++;
                        }
                     }

                     this.groundItemCount = l23;
                  }
               }
            }

            return;
         }

         if (command == 27) {
            int i1 = 1;

            while (i1 < length) {
               if (DataOperations.getUnsignedByte(data[i1]) == 255) {
                  int j8 = 0;
                  int l14 = this.sectionX + data[i1 + 1] >> 3;
                  int k19 = this.sectionY + data[i1 + 2] >> 3;
                  i1 += 3;

                  for (int i24 = 0; i24 < this.objectCount; i24++) {
                     int l26 = (this.objectX[i24] >> 3) - l14;
                     int k29 = (this.objectY[i24] >> 3) - k19;
                     if (l26 == 0 && k29 == 0) {
                        this.gameCamera.removeModel(this.objectModelArray[i24]);
                        this.engineHandle.updateObject(this.objectX[i24], this.objectY[i24], this.objectType[i24], this.objectID[i24]);
                     } else {
                        if (i24 != j8) {
                           this.objectModelArray[j8] = this.objectModelArray[i24];
                           this.objectModelArray[j8].key = j8;
                           this.objectX[j8] = this.objectX[i24];
                           this.objectY[j8] = this.objectY[i24];
                           this.objectType[j8] = this.objectType[i24];
                           this.objectID[j8] = this.objectID[i24];
                        }

                        j8++;
                     }
                  }

                  this.objectCount = j8;
               } else {
                  int k8 = DataOperations.getUnsigned2Bytes(data, i1);
                  i1 += 2;
                  int i15 = this.sectionX + data[i1++];
                  int l19 = this.sectionY + data[i1++];
                  int l29 = data[i1++];
                  int j24 = 0;

                  for (int i27 = 0; i27 < this.objectCount; i27++) {
                     if (this.objectX[i27] == i15 && this.objectY[i27] == l19 && this.objectID[i27] == l29) {
                        this.gameCamera.removeModel(this.objectModelArray[i27]);
                        this.engineHandle.updateObject(this.objectX[i27], this.objectY[i27], this.objectType[i27], this.objectID[i27]);
                     } else {
                        if (i27 != j24) {
                           this.objectModelArray[j24] = this.objectModelArray[i27];
                           this.objectModelArray[j24].key = j24;
                           this.objectX[j24] = this.objectX[i27];
                           this.objectY[j24] = this.objectY[i27];
                           this.objectType[j24] = this.objectType[i27];
                           this.objectID[j24] = this.objectID[i27];
                        }

                        j24++;
                     }
                  }

                  this.objectCount = j24;
                  if (k8 != 60000) {
                     this.engineHandle.registerObjectDir(i15, l19, l29);
                     int i34;
                     int j37;
                     if (l29 != 0 && l29 != 4) {
                        j37 = EntityHandler.getObjectDef(k8).getWidth();
                        i34 = EntityHandler.getObjectDef(k8).getHeight();
                     } else {
                        i34 = EntityHandler.getObjectDef(k8).getWidth();
                        j37 = EntityHandler.getObjectDef(k8).getHeight();
                     }

                     int j40 = (i15 + i15 + i34) * this.magicLoc / 2;
                     int i42 = (l19 + l19 + j37) * this.magicLoc / 2;
                     int k43 = EntityHandler.getObjectDef(k8).modelID;
                     Model model_1 = this.gameDataModels[k43].copy();
                     this.gameCamera.addModel(model_1);
                     model_1.key = this.objectCount;
                     model_1.rotateBy(0, l29 * 32, 0);
                     model_1.translateBy(j40, -this.engineHandle.getAveragedElevation(j40, i42), i42);
                     model_1.setLight(true, 48, 48, -50, -10, -50);
                     this.engineHandle.registerObjectCollision(i15, l19, k8, l29);
                     if (k8 == 74) {
                        model_1.translateBy(0, -480, 0);
                     }

                     this.objectX[this.objectCount] = i15;
                     this.objectY[this.objectCount] = l19;
                     this.objectType[this.objectCount] = k8;
                     this.objectID[this.objectCount] = l29;
                     this.objectModelArray[this.objectCount++] = model_1;
                  }
               }
            }

            return;
         }

         if (command == 114) {
            int invOffset = 1;
            this.inventoryCount = data[invOffset++] & 255;

            for (int invItem = 0; invItem < this.inventoryCount; invItem++) {
               int j15 = DataOperations.getUnsigned2Bytes(data, invOffset);
               invOffset += 2;
               this.inventoryItems[invItem] = j15 & 32767;
               this.wearing[invItem] = j15 / 32768;
               if (EntityHandler.getItemDef(j15 & 32767).isStackable()) {
                  this.inventoryItemsCount[invItem] = DataOperations.readInt(data, invOffset);
                  invOffset += 4;
               } else {
                  this.inventoryItemsCount[invItem] = 1;
               }
            }

            return;
         }

         if (command == 53) {
            int mobCount = DataOperations.getUnsigned2Bytes(data, 1);
            int mobUpdateOffset = 3;

            for (int currentMob = 0; currentMob < mobCount; currentMob++) {
               int mobArrayIndex = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
               mobUpdateOffset += 2;
               if (mobArrayIndex < 0 || mobArrayIndex > this.mobArray.length) {
                  return;
               }

               Mob mob = this.mobArray[mobArrayIndex];
               if (mob == null) {
                  return;
               }

               byte mobUpdateType = data[mobUpdateOffset++];
               if (mobUpdateType == 0) {
                  int i30 = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                  mobUpdateOffset += 2;
                  if (mob != null) {
                     mob.bubbleTimeout = 150;
                     mob.bubbleItem = i30;
                  }
               } else if (mobUpdateType == 1) {
                  byte byte7 = data[mobUpdateOffset++];
                  if (mob != null) {
                     String s2 = DataConversions.byteToString(data, mobUpdateOffset, byte7);
                     mob.lastMessageTimeout = 150;
                     mob.lastMessage = s2;
                     this.displayMessage(mob.name + ": " + mob.lastMessage, 2, mob.admin);
                     if (this.scriptRunner != null) {
                        this.scriptRunner.fireChatMessage(mob.name, s2);
                     }
                  }

                  mobUpdateOffset += byte7;
               } else if (mobUpdateType == 2) {
                  int j30 = DataOperations.getUnsignedByte(data[mobUpdateOffset++]);
                  int hits = DataOperations.getUnsignedByte(data[mobUpdateOffset++]);
                  int hitsBase = DataOperations.getUnsignedByte(data[mobUpdateOffset++]);
                  if (mob != null) {
                     mob.damageTaken = j30;
                     mob.hitPointsCurrent = hits;
                     mob.hitPointsBase = hitsBase;
                     mob.combatTimer = 200;
                     if (mob == this.ourPlayer) {
                        this.playerStatCurrent[3] = hits;
                        this.playerStatBase[3] = hitsBase;
                        this.showWelcomeBox = false;
                     }
                  }
               } else if (mobUpdateType == 3) {
                  int k30 = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                  mobUpdateOffset += 2;
                  int k34 = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                  mobUpdateOffset += 2;
                  if (mob != null) {
                     mob.attackingCameraInt = k30;
                     mob.attackingNpcIndex = k34;
                     mob.attackingMobIndex = -1;
                     mob.projectileCountdown = this.projectileFlightDuration;
                  }
               } else if (mobUpdateType == 4) {
                  int l30 = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                  mobUpdateOffset += 2;
                  int l34 = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                  mobUpdateOffset += 2;
                  if (mob != null) {
                     mob.attackingCameraInt = l30;
                     mob.attackingMobIndex = l34;
                     mob.attackingNpcIndex = -1;
                     mob.projectileCountdown = this.projectileFlightDuration;
                  }
               } else if (mobUpdateType == 5) {
                  if (mob != null) {
                     mob.mobIntUnknown = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                     int var147 = mobUpdateOffset + 2;
                     mob.nameLong = DataOperations.getUnsigned8Bytes(data, var147);
                     mobUpdateOffset = var147 + 8;
                     mob.name = DataOperations.longToString(mob.nameLong);
                     int i31 = DataOperations.getUnsignedByte(data[mobUpdateOffset]);
                     mobUpdateOffset++;

                     for (int i35 = 0; i35 < i31; i35++) {
                        // Two bytes per worn slot, not one: the value is an AnimationDef
                        // array index + 1, and the table outgrew a byte the day the first
                        // appended worn look (kiteshield) landed past index 254. The
                        // server's write side (PlayerUpdatePacketBuilder) widened in the
                        // same change.
                        mob.animationCount[i35] = DataOperations.getUnsigned2Bytes(data, mobUpdateOffset);
                        mobUpdateOffset += 2;
                     }

                     for (int l37 = i31; l37 < 12; l37++) {
                        mob.animationCount[l37] = 0;
                     }

                     mob.colourHairType = data[mobUpdateOffset++] & 255;
                     mob.colourTopType = data[mobUpdateOffset++] & 255;
                     mob.colourBottomType = data[mobUpdateOffset++] & 255;
                     mob.colourSkinType = data[mobUpdateOffset++] & 255;
                     mob.level = data[mobUpdateOffset++] & 255;
                     mob.skullVisible = data[mobUpdateOffset++] & 255;
                     mob.admin = data[mobUpdateOffset++] & 255;
                  } else {
                     mobUpdateOffset += 14;
                     int j31 = DataOperations.getUnsignedByte(data[mobUpdateOffset]);
                     mobUpdateOffset += j31 + 1;
                  }
               } else if (mobUpdateType == 6) {
                  byte byte8 = data[mobUpdateOffset];
                  mobUpdateOffset++;
                  if (mob != null) {
                     String s3 = DataConversions.byteToString(data, mobUpdateOffset, byte8);
                     mob.lastMessageTimeout = 150;
                     mob.lastMessage = s3;
                     if (mob == this.ourPlayer) {
                        this.displayMessage(mob.name + ": " + mob.lastMessage, 5, mob.admin);
                     }
                  }

                  mobUpdateOffset += byte8;
               }
            }

            return;
         }

         if (command == 129) {
            this.combatStyle = DataOperations.getUnsignedByte(data[1]);
            return;
         }

         if (command == 95) {
            int l1 = 1;

            while (l1 < length) {
               if (DataOperations.getUnsignedByte(data[l1]) == 255) {
                  int j9 = 0;
                  int l15 = this.sectionX + data[l1 + 1] >> 3;
                  int j20 = this.sectionY + data[l1 + 2] >> 3;
                  l1 += 3;

                  for (int currentDoor = 0; currentDoor < this.doorCount; currentDoor++) {
                     int j27 = (this.doorX[currentDoor] >> 3) - l15;
                     int k31 = (this.doorY[currentDoor] >> 3) - j20;
                     if (j27 == 0 && k31 == 0) {
                        this.gameCamera.removeModel(this.doorModel[currentDoor]);
                        this.engineHandle
                           .updateDoor(this.doorX[currentDoor], this.doorY[currentDoor], this.doorDirection[currentDoor], this.doorType[currentDoor]);
                     } else {
                        if (currentDoor != j9) {
                           this.doorModel[j9] = this.doorModel[currentDoor];
                           this.doorModel[j9].key = j9 + 10000;
                           this.doorX[j9] = this.doorX[currentDoor];
                           this.doorY[j9] = this.doorY[currentDoor];
                           this.doorDirection[j9] = this.doorDirection[currentDoor];
                           this.doorType[j9] = this.doorType[currentDoor];
                        }

                        j9++;
                     }
                  }

                  this.doorCount = j9;
               } else {
                  int k9 = DataOperations.getUnsigned2Bytes(data, l1);
                  l1 += 2;
                  int i16 = this.sectionX + data[l1++];
                  int k20 = this.sectionY + data[l1++];
                  byte byte5 = data[l1++];
                  int k27 = 0;

                  for (int l31 = 0; l31 < this.doorCount; l31++) {
                     if (this.doorX[l31] == i16 && this.doorY[l31] == k20 && this.doorDirection[l31] == byte5) {
                        this.gameCamera.removeModel(this.doorModel[l31]);
                        this.engineHandle.updateDoor(this.doorX[l31], this.doorY[l31], this.doorDirection[l31], this.doorType[l31]);
                     } else {
                        if (l31 != k27) {
                           this.doorModel[k27] = this.doorModel[l31];
                           this.doorModel[k27].key = k27 + 10000;
                           this.doorX[k27] = this.doorX[l31];
                           this.doorY[k27] = this.doorY[l31];
                           this.doorDirection[k27] = this.doorDirection[l31];
                           this.doorType[k27] = this.doorType[l31];
                        }

                        k27++;
                     }
                  }

                  this.doorCount = k27;
                  if (k9 != 60000) {
                     this.engineHandle.registerDoorCollision(i16, k20, byte5, k9);
                     Model model = this.makeModel(i16, k20, byte5, k9, this.doorCount);
                     this.doorModel[this.doorCount] = model;
                     this.doorX[this.doorCount] = i16;
                     this.doorY[this.doorCount] = k20;
                     this.doorType[this.doorCount] = k9;
                     this.doorDirection[this.doorCount++] = byte5;
                  }
               }
            }

            return;
         }

         if (command == 77) {
            this.lastNpcCount = this.npcCount;
            this.npcCount = 0;

            for (int lastNpcIndex = 0; lastNpcIndex < this.lastNpcCount; lastNpcIndex++) {
               this.lastNpcArray[lastNpcIndex] = this.npcArray[lastNpcIndex];
            }

            int newNpcOffset = 8;
            int newNpcCount = DataOperations.getIntFromByteArray(data, newNpcOffset, 8);
            newNpcOffset += 8;

            for (int newNpcIndex = 0; newNpcIndex < newNpcCount; newNpcIndex++) {
               Mob newNPC = this.getLastNpc(DataOperations.getIntFromByteArray(data, newNpcOffset, 16));
               newNpcOffset += 16;
               int npcNeedsUpdate = DataOperations.getIntFromByteArray(data, newNpcOffset, 1);
               newNpcOffset++;
               if (npcNeedsUpdate != 0) {
                  int i32 = DataOperations.getIntFromByteArray(data, newNpcOffset, 1);
                  newNpcOffset++;
                  if (i32 == 0) {
                     int nextSprite = DataOperations.getIntFromByteArray(data, newNpcOffset, 3);
                     newNpcOffset += 3;
                     int waypointCurrent = newNPC.waypointCurrent;
                     int waypointX = newNPC.waypointsX[waypointCurrent];
                     int waypointY = newNPC.waypointsY[waypointCurrent];
                     if (nextSprite == 2 || nextSprite == 1 || nextSprite == 3) {
                        waypointX += this.magicLoc;
                     }

                     if (nextSprite == 6 || nextSprite == 5 || nextSprite == 7) {
                        waypointX -= this.magicLoc;
                     }

                     if (nextSprite == 4 || nextSprite == 3 || nextSprite == 5) {
                        waypointY += this.magicLoc;
                     }

                     if (nextSprite == 0 || nextSprite == 1 || nextSprite == 7) {
                        waypointY -= this.magicLoc;
                     }

                     newNPC.nextSprite = nextSprite;
                     int var261;
                     newNPC.waypointCurrent = var261 = (waypointCurrent + 1) % 10;
                     newNPC.waypointsX[var261] = waypointX;
                     newNPC.waypointsY[var261] = waypointY;
                  } else {
                     int nextSpriteOffset = DataOperations.getIntFromByteArray(data, newNpcOffset, 4);
                     newNpcOffset += 4;
                     if ((nextSpriteOffset & 12) == 12) {
                        continue;
                     }

                     newNPC.nextSprite = nextSpriteOffset;
                  }
               }

               this.npcArray[this.npcCount++] = newNPC;
            }

            while (newNpcOffset + 34 < length * 8) {
               int serverIndex = DataOperations.getIntFromByteArray(data, newNpcOffset, 16);
               newNpcOffset += 16;
               int i28 = DataOperations.getIntFromByteArray(data, newNpcOffset, 5);
               newNpcOffset += 5;
               if (i28 > 15) {
                  i28 -= 32;
               }

               int j32 = DataOperations.getIntFromByteArray(data, newNpcOffset, 5);
               newNpcOffset += 5;
               if (j32 > 15) {
                  j32 -= 32;
               }

               int nextSpritex = DataOperations.getIntFromByteArray(data, newNpcOffset, 4);
               newNpcOffset += 4;
               int x = (this.sectionX + i28) * this.magicLoc + 64;
               int y = (this.sectionY + j32) * this.magicLoc + 64;
               int type = DataOperations.getIntFromByteArray(data, newNpcOffset, 10);
               newNpcOffset += 10;
               if (type >= EntityHandler.npcCount()) {
                  type = 24;
               }

               this.addNPC(serverIndex, x, y, nextSpritex, type);
            }

            return;
         }

         if (command == 190) {
            int j2 = DataOperations.getUnsigned2Bytes(data, 1);
            int i10 = 3;

            for (int k16 = 0; k16 < j2; k16++) {
               int i21 = DataOperations.getUnsigned2Bytes(data, i10);
               i10 += 2;
               Mob mob_2 = this.npcRecordArray[i21];
               int j28 = DataOperations.getUnsignedByte(data[i10]);
               i10++;
               if (j28 == 1) {
                  int k32 = DataOperations.getUnsigned2Bytes(data, i10);
                  i10 += 2;
                  byte byte9 = data[i10];
                  i10++;
                  if (mob_2 != null) {
                     String s4 = DataConversions.byteToString(data, i10, byte9);
                     mob_2.lastMessageTimeout = 150;
                     mob_2.lastMessage = s4;
                     if (k32 == this.ourPlayer.serverIndex) {
                        this.displayMessage("@yel@" + EntityHandler.getNpcDef(mob_2.type).getName() + ": " + mob_2.lastMessage, 5, 0);
                        // An NPC talking to you is an OnChatMessage too -- that
                        // is how a script follows a quest dialogue.
                        if (this.scriptRunner != null) {
                           this.scriptRunner.fireChatMessage(EntityHandler.getNpcDef(mob_2.type).getName(), s4);
                        }
                     }
                  }

                  i10 += byte9;
               } else if (j28 == 2) {
                  int l32 = DataOperations.getUnsignedByte(data[i10]);
                  int i36 = DataOperations.getUnsignedByte(data[++i10]);
                  int k38 = DataOperations.getUnsignedByte(data[++i10]);
                  i10++;
                  if (mob_2 != null) {
                     mob_2.damageTaken = l32;
                     mob_2.hitPointsCurrent = i36;
                     mob_2.hitPointsBase = k38;
                     mob_2.combatTimer = 200;
                  }
               } else if (j28 == 3 || j28 == 4) {
                  // A projectile fired *by* this npc. The numbering matches the
                  // player block exactly -- 3 when the thing being shot at is an
                  // npc, 4 when it is a player -- because a shot changes blocks
                  // only by changing who fired it, never by changing shape.
                  int sprite = DataOperations.getUnsigned2Bytes(data, i10);
                  i10 += 2;
                  int targetIndex = DataOperations.getUnsigned2Bytes(data, i10);
                  i10 += 2;
                  if (mob_2 != null) {
                     mob_2.attackingCameraInt = sprite;
                     mob_2.attackingNpcIndex = j28 == 3 ? targetIndex : -1;
                     mob_2.attackingMobIndex = j28 == 3 ? -1 : targetIndex;
                     mob_2.projectileCountdown = this.projectileFlightDuration;
                  }
               }
            }

            return;
         }

         if (command == 223) {
            this.showQuestionMenu = true;
            int newQuestionMenuCount = DataOperations.getUnsignedByte(data[1]);
            this.questionMenuCount = newQuestionMenuCount;
            int newQuestionMenuOffset = 2;

            for (int l16 = 0; l16 < newQuestionMenuCount; l16++) {
               int newQuestionMenuQuestionLength = DataOperations.getUnsignedByte(data[newQuestionMenuOffset]);
               this.questionMenuAnswer[l16] = new String(data, ++newQuestionMenuOffset, newQuestionMenuQuestionLength);
               newQuestionMenuOffset += newQuestionMenuQuestionLength;
            }

            return;
         }

         if (command == 127) {
            this.showQuestionMenu = false;
            return;
         }

         if (command == 131) {
            this.notInWilderness = true;
            this.hasWorldInfo = true;
            this.serverIndex = DataOperations.getUnsigned2Bytes(data, 1);
            this.wildX = DataOperations.getUnsigned2Bytes(data, 3);
            this.wildY = DataOperations.getUnsigned2Bytes(data, 5);
            this.wildYSubtract = DataOperations.getUnsigned2Bytes(data, 7);
            this.wildYMultiplier = DataOperations.getUnsigned2Bytes(data, 9);
            this.wildY = this.wildY - this.wildYSubtract * this.wildYMultiplier;
            return;
         }

         if (command == 180) {
            int l2 = 1;

            for (int k10 = 0; k10 < 19; k10++) {
               this.playerStatCurrent[k10] = DataOperations.getUnsignedByte(data[l2++]);
            }

            for (int i17 = 0; i17 < 19; i17++) {
               this.playerStatBase[i17] = DataOperations.getUnsignedByte(data[l2++]);
            }

            for (int k21 = 0; k21 < 19; k21++) {
               this.playerStatExperience[k21] = DataOperations.readInt(data, l2);
               l2 += 4;
            }

            /* Jagex put quest points on the end of this packet. RSCD's server
               stops after the experience, so only read what was sent. */
            if (length > l2) {
               this.questPoints = DataOperations.getUnsignedByte(data[l2++]);
            }

            this.expGained = 0L;
            return;
         }

         if (command == 5) {
            /* One byte per quest, in QUEST_NAMES order: 0 not started, 1
               started, 2 complete. The server sends this on every login and
               quest-stage change (MiscPacketBuilder.sendQuests()). */
            for (int q = 0; q < this.questProgress.length && q + 1 < length; q++) {
               this.questProgress[q] = data[q + 1];
            }

            return;
         }

         if (command == 177) {
            int i3 = 1;

            for (int x = 0; x < 6; x++) {
               this.equipmentStatus[x] = DataOperations.getSigned2Bytes(data, i3);
               i3 += 2;
            }

            return;
         }

         if (command == 165) {
            this.playerAliveTimeout = 250;
            return;
         }

         if (command == 115) {
            int thingLength = (length - 1) / 4;

            for (int currentThing = 0; currentThing < thingLength; currentThing++) {
               int currentItemSectionX = this.sectionX + DataOperations.getSigned2Bytes(data, 1 + currentThing * 4) >> 3;
               int currentItemSectionY = this.sectionY + DataOperations.getSigned2Bytes(data, 3 + currentThing * 4) >> 3;
               int currentCount = 0;

               for (int currentItem = 0; currentItem < this.groundItemCount; currentItem++) {
                  int currentItemOffsetX = (this.groundItemX[currentItem] >> 3) - currentItemSectionX;
                  int currentItemOffsetY = (this.groundItemY[currentItem] >> 3) - currentItemSectionY;
                  if (currentItemOffsetX != 0 || currentItemOffsetY != 0) {
                     if (currentItem != currentCount) {
                        this.groundItemX[currentCount] = this.groundItemX[currentItem];
                        this.groundItemY[currentCount] = this.groundItemY[currentItem];
                        this.groundItemType[currentCount] = this.groundItemType[currentItem];
                        this.groundItemObjectVar[currentCount] = this.groundItemObjectVar[currentItem];
                     }

                     currentCount++;
                  }
               }

               this.groundItemCount = currentCount;
               currentCount = 0;

               for (int j33 = 0; j33 < this.objectCount; j33++) {
                  int k36 = (this.objectX[j33] >> 3) - currentItemSectionX;
                  int l38 = (this.objectY[j33] >> 3) - currentItemSectionY;
                  if (k36 == 0 && l38 == 0) {
                     this.gameCamera.removeModel(this.objectModelArray[j33]);
                     this.engineHandle.updateObject(this.objectX[j33], this.objectY[j33], this.objectType[j33], this.objectID[j33]);
                  } else {
                     if (j33 != currentCount) {
                        this.objectModelArray[currentCount] = this.objectModelArray[j33];
                        this.objectModelArray[currentCount].key = currentCount;
                        this.objectX[currentCount] = this.objectX[j33];
                        this.objectY[currentCount] = this.objectY[j33];
                        this.objectType[currentCount] = this.objectType[j33];
                        this.objectID[currentCount] = this.objectID[j33];
                     }

                     currentCount++;
                  }
               }

               this.objectCount = currentCount;
               currentCount = 0;

               for (int l36 = 0; l36 < this.doorCount; l36++) {
                  int i39 = (this.doorX[l36] >> 3) - currentItemSectionX;
                  int j41 = (this.doorY[l36] >> 3) - currentItemSectionY;
                  if (i39 == 0 && j41 == 0) {
                     this.gameCamera.removeModel(this.doorModel[l36]);
                     this.engineHandle.updateDoor(this.doorX[l36], this.doorY[l36], this.doorDirection[l36], this.doorType[l36]);
                  } else {
                     if (l36 != currentCount) {
                        this.doorModel[currentCount] = this.doorModel[l36];
                        this.doorModel[currentCount].key = currentCount + 10000;
                        this.doorX[currentCount] = this.doorX[l36];
                        this.doorY[currentCount] = this.doorY[l36];
                        this.doorDirection[currentCount] = this.doorDirection[l36];
                        this.doorType[currentCount] = this.doorType[l36];
                     }

                     currentCount++;
                  }
               }

               this.doorCount = currentCount;
            }

            return;
         }

         if (command == 207) {
            this.showCharacterLookScreen = true;
            return;
         }

         if (command == 4) {
            int currentMob = DataOperations.getUnsigned2Bytes(data, 1);
            if (this.mobArray[currentMob] != null) {
               this.tradeOtherPlayerName = this.mobArray[currentMob].name;
            }

            this.showTradeWindow = true;
            this.tradeOtherAccepted = false;
            this.tradeWeAccepted = false;
            this.tradeMyItemCount = 0;
            this.tradeOtherItemCount = 0;
            return;
         }

         if (command == 187) {
            this.showTradeWindow = false;
            this.showTradeConfirmWindow = false;
            this.showOfferMenu = false;
            return;
         }

         if (command == 250) {
            this.tradeOtherItemCount = data[1] & 255;
            int l3 = 2;

            for (int i11 = 0; i11 < this.tradeOtherItemCount; i11++) {
               this.tradeOtherItems[i11] = DataOperations.getUnsigned2Bytes(data, l3);
               l3 += 2;
               this.tradeOtherItemsCount[i11] = DataOperations.readInt(data, l3);
               l3 += 4;
            }

            this.tradeOtherAccepted = false;
            this.tradeWeAccepted = false;
            return;
         }

         if (command == 92) {
            this.tradeOtherAccepted = data[1] == 1;
         }

         if (command == 253) {
            this.showShop = true;
            int i4 = 1;
            int j11 = data[i4++] & 255;
            byte byte4 = data[i4++];
            this.shopItemSellPriceModifier = data[i4++] & 255;
            this.shopItemBuyPriceModifier = data[i4++] & 255;

            for (int i22 = 0; i22 < 40; i22++) {
               this.shopItems[i22] = -1;
            }

            for (int j25 = 0; j25 < j11; j25++) {
               this.shopItems[j25] = DataOperations.getUnsigned2Bytes(data, i4);
               i4 += 2;
               this.shopItemCount[j25] = DataOperations.getUnsigned2Bytes(data, i4);
               i4 += 2;
            }

            if (byte4 == 1) {
               int l28 = 39;

               for (int k33 = 0; k33 < this.inventoryCount && l28 >= j11; k33++) {
                  boolean flag2 = false;

                  for (int j39 = 0; j39 < 40; j39++) {
                     if (this.shopItems[j39] == this.inventoryItems[k33]) {
                        flag2 = true;
                        break;
                     }
                  }

                  if (this.inventoryItems[k33] == 10) {
                     flag2 = true;
                  }

                  if (!flag2) {
                     this.shopItems[l28] = this.inventoryItems[k33] & 32767;
                     this.shopItemCount[l28] = 0;
                     l28--;
                  }
               }
            }

            if (this.selectedShopItemIndex >= 0 && this.selectedShopItemIndex < 40 && this.shopItems[this.selectedShopItemIndex] != this.selectedShopItemType) {
               this.selectedShopItemIndex = -1;
               this.selectedShopItemType = -2;
            }

            return;
         }

         if (command == 220) {
            this.showShop = false;
            return;
         }

         if (command == 18) {
            this.tradeWeAccepted = data[1] == 1;
         }

         if (command == 152) {
            this.configAutoCameraAngle = DataOperations.getUnsignedByte(data[1]) == 1;
            this.configMouseButtons = DataOperations.getUnsignedByte(data[2]) == 1;
            this.configSoundEffects = DataOperations.getUnsignedByte(data[3]) == 1;
            this.showRoof = DataOperations.getUnsignedByte(data[4]) == 1;
            this.autoScreenshot = DataOperations.getUnsignedByte(data[5]) == 1;
            this.combatWindow = DataOperations.getUnsignedByte(data[6]) == 1;
            return;
         }

         if (command == 209) {
            for (int currentPrayer = 0; currentPrayer < length - 1; currentPrayer++) {
               boolean prayerOff = data[currentPrayer + 1] == 1;
               if (!this.prayerOn[currentPrayer] && prayerOff) {
                  this.playSound("prayeron");
               }

               if (this.prayerOn[currentPrayer] && !prayerOff) {
                  this.playSound("prayeroff");
               }

               this.prayerOn[currentPrayer] = prayerOff;
            }

            return;
         }

         if (command == 93) {
            this.showBank = true;
            int l4 = 1;
            this.newBankItemCount = data[l4++] & 255;
            this.bankItemsMax = data[l4++] & 255;

            for (int k11 = 0; k11 < this.newBankItemCount; k11++) {
               this.newBankItems[k11] = DataOperations.getUnsigned2Bytes(data, l4);
               l4 += 2;
               this.newBankItemsCount[k11] = DataOperations.getUnsigned4Bytes(data, l4);
               l4 += 4;
            }

            this.updateBankItems();
            return;
         }

         if (command == 171) {
            this.showBank = false;
            return;
         }

         if (command == 211) {
            int idx = data[1] & 255;
            int oldExp = this.playerStatExperience[idx];
            this.playerStatExperience[idx] = DataOperations.readInt(data, 2);
            if (this.playerStatExperience[idx] > oldExp) {
               this.expGained = this.expGained + (long)(this.playerStatExperience[idx] - oldExp);
            }

            return;
         }

         if (command == 229) {
            int j5 = DataOperations.getUnsigned2Bytes(data, 1);
            if (this.mobArray[j5] != null) {
               this.duelOpponentName = this.mobArray[j5].name;
            }

            this.showDuelWindow = true;
            this.duelMyItemCount = 0;
            this.duelOpponentItemCount = 0;
            this.duelOpponentAccepted = false;
            this.duelMyAccepted = false;
            this.duelNoRetreating = false;
            this.duelNoMagic = false;
            this.duelNoPrayer = false;
            this.duelNoWeapons = false;
            return;
         }

         if (command == 160) {
            this.showDuelWindow = false;
            this.showDuelConfirmWindow = false;
            this.showOfferMenu = false;
            return;
         }

         if (command == 251) {
            this.showTradeConfirmWindow = true;
            this.tradeConfirmAccepted = false;
            this.showTradeWindow = false;
            this.showOfferMenu = false;
            int k5 = 1;
            this.tradeConfirmOtherNameLong = DataOperations.getUnsigned8Bytes(data, k5);
            k5 += 8;
            this.tradeConfirmOtherItemCount = data[k5++] & 255;

            for (int l11 = 0; l11 < this.tradeConfirmOtherItemCount; l11++) {
               this.tradeConfirmOtherItems[l11] = DataOperations.getUnsigned2Bytes(data, k5);
               k5 += 2;
               this.tradeConfirmOtherItemsCount[l11] = DataOperations.readInt(data, k5);
               k5 += 4;
            }

            this.tradeConfirmItemCount = data[k5++] & 255;

            for (int k17 = 0; k17 < this.tradeConfirmItemCount; k17++) {
               this.tradeConfirmItems[k17] = DataOperations.getUnsigned2Bytes(data, k5);
               k5 += 2;
               this.tradeConfirmItemsCount[k17] = DataOperations.readInt(data, k5);
               k5 += 4;
            }

            return;
         }

         if (command == 63) {
            this.duelOpponentItemCount = data[1] & 255;
            int l5 = 2;

            for (int i12 = 0; i12 < this.duelOpponentItemCount; i12++) {
               this.duelOpponentItems[i12] = DataOperations.getUnsigned2Bytes(data, l5);
               l5 += 2;
               this.duelOpponentItemsCount[i12] = DataOperations.readInt(data, l5);
               l5 += 4;
            }

            this.duelOpponentAccepted = false;
            this.duelMyAccepted = false;
            return;
         }

         if (command == 198) {
            this.duelNoRetreating = data[1] == 1;
            this.duelNoMagic = data[2] == 1;
            this.duelNoPrayer = data[3] == 1;
            this.duelNoWeapons = data[4] == 1;
            this.duelOpponentAccepted = false;
            this.duelMyAccepted = false;
            return;
         }

         if (command == 139) {
            int bankDataOffset = 1;
            int bankSlot = data[bankDataOffset++] & 255;
            int bankItemId = DataOperations.getUnsigned2Bytes(data, bankDataOffset);
            bankDataOffset += 2;
            int bankItemCount = DataOperations.getUnsigned4Bytes(data, bankDataOffset);
            bankDataOffset += 4;
            if (bankItemCount == 0) {
               this.newBankItemCount--;

               for (int currentBankSlot = bankSlot; currentBankSlot < this.newBankItemCount; currentBankSlot++) {
                  this.newBankItems[currentBankSlot] = this.newBankItems[currentBankSlot + 1];
                  this.newBankItemsCount[currentBankSlot] = this.newBankItemsCount[currentBankSlot + 1];
               }
            } else {
               this.newBankItems[bankSlot] = bankItemId;
               this.newBankItemsCount[bankSlot] = bankItemCount;
               if (bankSlot >= this.newBankItemCount) {
                  this.newBankItemCount = bankSlot + 1;
               }
            }

            this.updateBankItems();
            return;
         }

         if (command == 228) {
            int j6 = 1;
            int k12 = 1;
            int i18 = data[j6++] & 255;
            int k22 = DataOperations.getUnsigned2Bytes(data, j6);
            j6 += 2;
            if (EntityHandler.getItemDef(k22 & 32767).isStackable()) {
               k12 = DataOperations.readInt(data, j6);
               j6 += 4;
            }

            this.inventoryItems[i18] = k22 & 32767;
            this.wearing[i18] = k22 / 32768;
            this.inventoryItemsCount[i18] = k12;
            if (i18 >= this.inventoryCount) {
               this.inventoryCount = i18 + 1;
            }

            return;
         }

         if (command == 191) {
            int k6 = data[1] & 255;
            this.inventoryCount--;

            for (int l12 = k6; l12 < this.inventoryCount; l12++) {
               this.inventoryItems[l12] = this.inventoryItems[l12 + 1];
               this.inventoryItemsCount[l12] = this.inventoryItemsCount[l12 + 1];
               this.wearing[l12] = this.wearing[l12 + 1];
            }

            return;
         }

         if (command == 208) {
            int pointer = 1;
            int idx = data[pointer++] & 255;
            int oldExp = this.playerStatExperience[idx];
            this.playerStatCurrent[idx] = DataOperations.getUnsignedByte(data[pointer++]);
            this.playerStatBase[idx] = DataOperations.getUnsignedByte(data[pointer++]);
            this.playerStatExperience[idx] = DataOperations.readInt(data, pointer);
            pointer += 4;
            if (this.playerStatExperience[idx] > oldExp) {
               this.expGained = this.expGained + (long)(this.playerStatExperience[idx] - oldExp);
            }

            return;
         }

         if (command == 65) {
            this.duelOpponentAccepted = data[1] == 1;
         }

         if (command == 197) {
            this.duelMyAccepted = data[1] == 1;
         }

         if (command == 147) {
            this.showDuelConfirmWindow = true;
            this.duelWeAccept = false;
            this.showDuelWindow = false;
            this.showOfferMenu = false;
            int i7 = 1;
            this.duelOpponentNameLong = DataOperations.getUnsigned8Bytes(data, i7);
            i7 += 8;
            this.duelConfirmOpponentItemCount = data[i7++] & 255;

            for (int j13 = 0; j13 < this.duelConfirmOpponentItemCount; j13++) {
               this.duelConfirmOpponentItems[j13] = DataOperations.getUnsigned2Bytes(data, i7);
               i7 += 2;
               this.duelConfirmOpponentItemsCount[j13] = DataOperations.readInt(data, i7);
               i7 += 4;
            }

            this.duelConfirmMyItemCount = data[i7++] & 255;

            for (int j18 = 0; j18 < this.duelConfirmMyItemCount; j18++) {
               this.duelConfirmMyItems[j18] = DataOperations.getUnsigned2Bytes(data, i7);
               i7 += 2;
               this.duelConfirmMyItemsCount[j18] = DataOperations.readInt(data, i7);
               i7 += 4;
            }

            this.duelCantRetreat = data[i7++] & 255;
            this.duelUseMagic = data[i7++] & 255;
            this.duelUsePrayer = data[i7++] & 255;
            this.duelUseWeapons = data[i7++] & 255;
            return;
         }

         if (command == 11) {
            String s = new String(data, 1, length - 1);
            this.playSound(s);
            return;
         }

         if (command == 23) {
            if (this.teleBubbleCount < 50) {
               int j7 = data[1] & 255;
               int k13 = data[2] + this.sectionX;
               int k18 = data[3] + this.sectionY;
               this.teleBubbleType[this.teleBubbleCount] = j7;
               this.teleBubbleTime[this.teleBubbleCount] = 0;
               this.teleBubbleX[this.teleBubbleCount] = k13;
               this.teleBubbleY[this.teleBubbleCount] = k18;
               this.teleBubbleCount++;
            }

            return;
         }

         if (command == 248) {
            if (!this.hasReceivedWelcomeBoxDetails) {
               this.lastLoggedInDays = DataOperations.getUnsigned2Bytes(data, 1);
               this.subscriptionLeftDays = DataOperations.getUnsigned2Bytes(data, 3);
               this.lastLoggedInAddress = new String(data, 5, length - 5);
               /* Auto-login has nobody at the keyboard to click the box away. */
               this.showWelcomeBox = !this.loginWasAutomatic;
               this.hasReceivedWelcomeBoxDetails = true;
            }

            return;
         }

         if (command == 148) {
            this.serverMessage = new String(data, 1, length - 1);
            this.showServerMessageBox = true;
            this.serverMessageBoxTop = false;
            return;
         }

         if (command == 64) {
            this.serverMessage = new String(data, 1, length - 1);
            this.showServerMessageBox = true;
            this.serverMessageBoxTop = true;
            return;
         }

         if (command == 126) {
            this.fatigue = DataOperations.getUnsigned2Bytes(data, 1);
            return;
         }

         if (command == 181) {
            if (this.autoScreenshot) {
               this.takeScreenshot(false);
            }

            return;
         }

         if (command == 172) {
            this.systemUpdate = DataOperations.getUnsigned2Bytes(data, 1) * 32;
            return;
         }
      } catch (RuntimeException var18) {
         var18.printStackTrace();
         if (this.handlePacketErrorCount < 3) {
            super.streamClass.createPacket(156);
            super.streamClass.addString(var18.toString());
            super.streamClass.formatPacket();
            this.handlePacketErrorCount++;
         }
      }
   }

   @Override
   protected final void lostConnection() {
      /* Original, unchanged: a socket that dies under you reconnects with the
         credentials login() already stashed, which is what RSC has always
         done. AutoLogin is a separate thing and does not gate this -- see
         updateLoginScreen(). */
      this.systemUpdate = 0;
      if (this.logoutTimeout != 0) {
         this.resetIntVars();
      } else {
         super.lostConnection();
      }
   }

   final void playSound(String s) {
      if (this.audioReader != null) {
         if (!this.configSoundEffects) {
            this.audioReader.loadData(this.sounds, DataOperations.getEntryOffset(s + ".pcm", this.sounds), DataOperations.getEntrySize(s + ".pcm", this.sounds));
         }
      }
   }

   final boolean sendWalkCommand(int walkSectionX, int walkSectionY, int x1, int y1, int x2, int y2, boolean stepBoolean, boolean coordsEqual) {
      int stepCount = this.engineHandle.getStepCount(walkSectionX, walkSectionY, x1, y1, x2, y2, this.sectionXArray, this.sectionYArray, stepBoolean);
      if (stepCount == -1) {
         if (!coordsEqual) {
            return false;
         }

         stepCount = 1;
         this.sectionXArray[0] = x1;
         this.sectionYArray[0] = y1;
      }

      walkSectionX = this.sectionXArray[--stepCount];
      walkSectionY = this.sectionYArray[stepCount];
      stepCount--;
      if (coordsEqual) {
         super.streamClass.createPacket(246);
      } else {
         super.streamClass.createPacket(132);
      }

      super.streamClass.add2ByteInt(walkSectionX + this.areaX);
      super.streamClass.add2ByteInt(walkSectionY + this.areaY);
      if (coordsEqual && stepCount == -1 && (walkSectionX + this.areaX) % 5 == 0) {
         stepCount = 0;
      }

      for (int currentStep = stepCount; currentStep >= 0 && currentStep > stepCount - 25; currentStep--) {
         super.streamClass.addByte(this.sectionXArray[currentStep] - walkSectionX);
         super.streamClass.addByte(this.sectionYArray[currentStep] - walkSectionY);
      }

      super.streamClass.formatPacket();
      this.actionPictureType = -24;
      this.actionPictureX = super.mouseX;
      this.actionPictureY = super.mouseY;
      return true;
   }

   /**
    * A ground click on the tile you are standing on: packet 132 carrying the
    * current position and no steps. The pathfinder refuses a zero-length walk
    * so sendWalkCommand() never emits this, but it is all the server needs to
    * read "running from the fight" -- movement is not part of it.
    */
   final void sendFleeCommand() {
      super.streamClass.createPacket(132);
      super.streamClass.add2ByteInt(this.sectionX + this.areaX);
      super.streamClass.add2ByteInt(this.sectionY + this.areaY);
      super.streamClass.formatPacket();
   }

   final boolean sendWalkCommandIgnoreCoordsEqual(
      int walkSectionX, int walkSectionY, int x1, int y1, int x2, int y2, boolean stepBoolean, boolean coordsEqual
   ) {
      int stepCount = this.engineHandle.getStepCount(walkSectionX, walkSectionY, x1, y1, x2, y2, this.sectionXArray, this.sectionYArray, stepBoolean);
      if (stepCount == -1) {
         return false;
      } else {
         walkSectionX = this.sectionXArray[--stepCount];
         walkSectionY = this.sectionYArray[stepCount];
         stepCount--;
         if (coordsEqual) {
            super.streamClass.createPacket(246);
         } else {
            super.streamClass.createPacket(132);
         }

         super.streamClass.add2ByteInt(walkSectionX + this.areaX);
         super.streamClass.add2ByteInt(walkSectionY + this.areaY);
         if (coordsEqual && stepCount == -1 && (walkSectionX + this.areaX) % 5 == 0) {
            stepCount = 0;
         }

         for (int currentStep = stepCount; currentStep >= 0 && currentStep > stepCount - 25; currentStep--) {
            super.streamClass.addByte(this.sectionXArray[currentStep] - walkSectionX);
            super.streamClass.addByte(this.sectionYArray[currentStep] - walkSectionY);
         }

         super.streamClass.formatPacket();
         this.actionPictureType = -24;
         this.actionPictureX = super.mouseX;
         this.actionPictureY = super.mouseY;
         return true;
      }
   }

   @Override
   public final Image createImage(int i, int j) {
      return GameWindow.gameFrame != null ? GameWindow.gameFrame.createImage(i, j) : super.createImage(i, j);
   }

   private final void drawTradeConfirmWindow() {
      int byte0 = 22 + this.loginOffsetX();
      int byte1 = 36 + this.loginOffsetY();
      this.gameGraphics.drawBox(byte0, byte1, 468, 16, 192);
      int i = 10000536;
      this.gameGraphics.drawBoxAlpha(byte0, byte1 + 16, 468, 246, i, 160);
      this.gameGraphics
         .drawText("Please confirm your trade with @yel@" + DataOperations.longToString(this.tradeConfirmOtherNameLong), byte0 + 234, byte1 + 12, 1, 16777215);
      this.gameGraphics.drawText("You are about to give:", byte0 + 117, byte1 + 30, 1, 16776960);

      for (int j = 0; j < this.tradeConfirmItemCount; j++) {
         String s = EntityHandler.getItemDef(this.tradeConfirmItems[j]).getName();
         if (EntityHandler.getItemDef(this.tradeConfirmItems[j]).isStackable()) {
            s = s + " x " + formatItemCount(this.tradeConfirmItemsCount[j]);
         }

         this.gameGraphics.drawText(s, byte0 + 117, byte1 + 42 + j * 12, 1, 16777215);
      }

      if (this.tradeConfirmItemCount == 0) {
         this.gameGraphics.drawText("Nothing!", byte0 + 117, byte1 + 42, 1, 16777215);
      }

      this.gameGraphics.drawText("In return you will receive:", byte0 + 351, byte1 + 30, 1, 16776960);

      for (int k = 0; k < this.tradeConfirmOtherItemCount; k++) {
         String s1 = EntityHandler.getItemDef(this.tradeConfirmOtherItems[k]).getName();
         if (EntityHandler.getItemDef(this.tradeConfirmOtherItems[k]).isStackable()) {
            s1 = s1 + " x " + formatItemCount(this.tradeConfirmOtherItemsCount[k]);
         }

         this.gameGraphics.drawText(s1, byte0 + 351, byte1 + 42 + k * 12, 1, 16777215);
      }

      if (this.tradeConfirmOtherItemCount == 0) {
         this.gameGraphics.drawText("Nothing!", byte0 + 351, byte1 + 42, 1, 16777215);
      }

      this.gameGraphics.drawText("Are you sure you want to do this?", byte0 + 234, byte1 + 200, 4, 65535);
      this.gameGraphics.drawText("There is NO WAY to reverse a trade if you change your mind.", byte0 + 234, byte1 + 215, 1, 16777215);
      this.gameGraphics.drawText("Remember that not all players are trustworthy", byte0 + 234, byte1 + 230, 1, 16777215);
      if (!this.tradeConfirmAccepted) {
         this.gameGraphics.drawPicture(byte0 + 118 - 35, byte1 + 238, 2025);
         this.gameGraphics.drawPicture(byte0 + 352 - 35, byte1 + 238, 2026);
      } else {
         this.gameGraphics.drawText("Waiting for other player...", byte0 + 234, byte1 + 250, 1, 16776960);
      }

      if (this.mouseButtonClick == 1) {
         if (super.mouseX < byte0 || super.mouseY < byte1 || super.mouseX > byte0 + 468 || super.mouseY > byte1 + 262) {
            this.showTradeConfirmWindow = false;
            super.streamClass.createPacket(216);
            super.streamClass.formatPacket();
         }

         if (super.mouseX >= byte0 + 118 - 35 && super.mouseX <= byte0 + 118 + 70 && super.mouseY >= byte1 + 238 && super.mouseY <= byte1 + 238 + 21) {
            this.tradeConfirmAccepted = true;
            super.streamClass.createPacket(53);
            super.streamClass.formatPacket();
         }

         if (super.mouseX >= byte0 + 352 - 35 && super.mouseX <= byte0 + 353 + 70 && super.mouseY >= byte1 + 238 && super.mouseY <= byte1 + 238 + 21) {
            this.showTradeConfirmWindow = false;
            super.streamClass.createPacket(216);
            super.streamClass.formatPacket();
         }

         this.mouseButtonClick = 0;
      }
   }

   final void walkToGroundItem(int walkSectionX, int walkSectionY, int x, int y, boolean coordsEqual) {
      if (!this.sendWalkCommandIgnoreCoordsEqual(walkSectionX, walkSectionY, x, y, x, y, false, coordsEqual)) {
         this.sendWalkCommand(walkSectionX, walkSectionY, x, y, x, y, true, coordsEqual);
      }
   }

   private final Mob addNPC(int serverIndex, int x, int y, int nextSprite, int type) {
      if (this.npcRecordArray[serverIndex] == null) {
         this.npcRecordArray[serverIndex] = new Mob();
         this.npcRecordArray[serverIndex].serverIndex = serverIndex;
      }

      Mob mob = this.npcRecordArray[serverIndex];
      boolean npcAlreadyExists = false;

      for (int lastNpcIndex = 0; lastNpcIndex < this.lastNpcCount; lastNpcIndex++) {
         if (this.lastNpcArray[lastNpcIndex].serverIndex == serverIndex) {
            npcAlreadyExists = true;
            break;
         }
      }

      if (npcAlreadyExists) {
         mob.type = type;
         mob.nextSprite = nextSprite;
         int waypointCurrent = mob.waypointCurrent;
         if (x != mob.waypointsX[waypointCurrent] || y != mob.waypointsY[waypointCurrent]) {
            int var10;
            mob.waypointCurrent = var10 = (waypointCurrent + 1) % 10;
            mob.waypointsX[var10] = x;
            mob.waypointsY[var10] = y;
         }
      } else {
         mob.serverIndex = serverIndex;
         mob.waypointEndSprite = 0;
         mob.waypointCurrent = 0;
         mob.waypointsX[0] = mob.currentX = x;
         mob.waypointsY[0] = mob.currentY = y;
         mob.type = type;
         mob.nextSprite = mob.currentSprite = nextSprite;
         mob.stepCount = 0;
      }

      this.npcArray[this.npcCount++] = mob;
      return mob;
   }

   private final void drawDuelWindow() {
      if (this.showOfferMenu && this.offerMenuIsDuel) {
         /* See drawTradeWindow's copy of this check for why. */
         if (!this.showDuelWindow) {
            this.showOfferMenu = false;
         } else {
            this.handleOfferMenuClick();
         }
      } else {
         /* See drawTradeWindow's copy of this block for why. */
         if (this.mouseButtonClick == 2 && this.itemIncrement == 0) {
            int menuI = super.mouseX - (22 + this.loginOffsetX());
            int menuJ = super.mouseY - (36 + this.loginOffsetY());
            if (menuI > 216 && menuJ > 30 && menuI < 462 && menuJ < 235) {
               int menuK = (menuI - 217) / 49 + (menuJ - 31) / 34 * 5;
               if (menuK >= 0 && menuK < this.inventoryCount) {
                  this.openOfferMenu(this.inventoryItems[menuK], menuK, true);
                  this.mouseButtonClick = 0;
               }
            }
         }

      if (this.mouseButtonClick != 0 && this.itemIncrement == 0) {
         this.itemIncrement = 1;
      }

      if (this.itemIncrement > 0) {
         int i = super.mouseX - (22 + this.loginOffsetX());
         int j = super.mouseY - (36 + this.loginOffsetY());
         if (i >= 0 && j >= 0 && i < 468 && j < 262) {
            if (i > 216 && j > 30 && i < 462 && j < 235) {
               int k = (i - 217) / 49 + (j - 31) / 34 * 5;
               if (k >= 0 && k < this.inventoryCount) {
                  boolean flag1 = false;
                  int l1 = 0;
                  int k2 = this.inventoryItems[k];

                  for (int k3 = 0; k3 < this.duelMyItemCount; k3++) {
                     if (this.duelMyItems[k3] == k2) {
                        if (EntityHandler.getItemDef(k2).isStackable()) {
                           for (int i4 = 0; i4 < this.itemIncrement; i4++) {
                              if (this.duelMyItemsCount[k3] < this.inventoryItemsCount[k]) {
                                 this.duelMyItemsCount[k3]++;
                              }

                              flag1 = true;
                           }
                        } else {
                           l1++;
                        }
                     }
                  }

                  if (this.inventoryCount(k2) <= l1) {
                     flag1 = true;
                  }

                  if (!flag1 && this.duelMyItemCount < 8) {
                     this.duelMyItems[this.duelMyItemCount] = k2;
                     this.duelMyItemsCount[this.duelMyItemCount] = 1;
                     this.duelMyItemCount++;
                     flag1 = true;
                  }

                  if (flag1) {
                     super.streamClass.createPacket(123);
                     super.streamClass.addByte(this.duelMyItemCount);

                     for (int duelItem = 0; duelItem < this.duelMyItemCount; duelItem++) {
                        super.streamClass.add2ByteInt(this.duelMyItems[duelItem]);
                        super.streamClass.add4ByteInt(this.duelMyItemsCount[duelItem]);
                     }

                     super.streamClass.formatPacket();
                     this.duelOpponentAccepted = false;
                     this.duelMyAccepted = false;
                  }
               }
            }

            if (i > 8 && j > 30 && i < 205 && j < 129) {
               int l = (i - 9) / 49 + (j - 31) / 34 * 4;
               if (l >= 0 && l < this.duelMyItemCount) {
                  int j1 = this.duelMyItems[l];

                  for (int i2 = 0; i2 < this.itemIncrement; i2++) {
                     if (!EntityHandler.getItemDef(j1).isStackable() || this.duelMyItemsCount[l] <= 1) {
                        this.duelMyItemCount--;
                        this.mouseDownTime = 0;

                        for (int l2 = l; l2 < this.duelMyItemCount; l2++) {
                           this.duelMyItems[l2] = this.duelMyItems[l2 + 1];
                           this.duelMyItemsCount[l2] = this.duelMyItemsCount[l2 + 1];
                        }
                        break;
                     }

                     this.duelMyItemsCount[l]--;
                  }

                  super.streamClass.createPacket(123);
                  super.streamClass.addByte(this.duelMyItemCount);

                  for (int i3 = 0; i3 < this.duelMyItemCount; i3++) {
                     super.streamClass.add2ByteInt(this.duelMyItems[i3]);
                     super.streamClass.add4ByteInt(this.duelMyItemsCount[i3]);
                  }

                  super.streamClass.formatPacket();
                  this.duelOpponentAccepted = false;
                  this.duelMyAccepted = false;
               }
            }

            boolean flag = false;
            if (i >= 93 && j >= 221 && i <= 104 && j <= 232) {
               this.duelNoRetreating = !this.duelNoRetreating;
               flag = true;
            }

            if (i >= 93 && j >= 240 && i <= 104 && j <= 251) {
               this.duelNoMagic = !this.duelNoMagic;
               flag = true;
            }

            if (i >= 191 && j >= 221 && i <= 202 && j <= 232) {
               this.duelNoPrayer = !this.duelNoPrayer;
               flag = true;
            }

            if (i >= 191 && j >= 240 && i <= 202 && j <= 251) {
               this.duelNoWeapons = !this.duelNoWeapons;
               flag = true;
            }

            if (flag) {
               super.streamClass.createPacket(225);
               super.streamClass.addByte(this.duelNoRetreating ? 1 : 0);
               super.streamClass.addByte(this.duelNoMagic ? 1 : 0);
               super.streamClass.addByte(this.duelNoPrayer ? 1 : 0);
               super.streamClass.addByte(this.duelNoWeapons ? 1 : 0);
               super.streamClass.formatPacket();
               this.duelOpponentAccepted = false;
               this.duelMyAccepted = false;
            }

            if (i >= 217 && j >= 238 && i <= 286 && j <= 259) {
               this.duelMyAccepted = true;
               super.streamClass.createPacket(252);
               super.streamClass.formatPacket();
            }

            if (i >= 394 && j >= 238 && i < 463 && j < 259) {
               this.showDuelWindow = false;
               super.streamClass.createPacket(35);
               super.streamClass.formatPacket();
            }
         } else if (this.mouseButtonClick != 0) {
            this.showDuelWindow = false;
            super.streamClass.createPacket(35);
            super.streamClass.formatPacket();
         }

         this.mouseButtonClick = 0;
         this.itemIncrement = 0;
      }
      }

      if (this.showDuelWindow) {
         int byte0 = 22 + this.loginOffsetX();
         int byte1 = 36 + this.loginOffsetY();
         this.gameGraphics.drawBox(byte0, byte1, 468, 12, 13175581);
         int i1 = 10000536;
         this.gameGraphics.drawBoxAlpha(byte0, byte1 + 12, 468, 18, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0, byte1 + 30, 8, 248, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 205, byte1 + 30, 11, 248, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 462, byte1 + 30, 6, 248, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 99, 197, 24, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 192, 197, 23, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 258, 197, 20, i1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 216, byte1 + 235, 246, 43, i1, 160);
         int k1 = 13684944;
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 30, 197, 69, k1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 123, 197, 69, k1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 8, byte1 + 215, 197, 43, k1, 160);
         this.gameGraphics.drawBoxAlpha(byte0 + 216, byte1 + 30, 246, 205, k1, 160);

         for (int j2 = 0; j2 < 3; j2++) {
            this.gameGraphics.drawLineX(byte0 + 8, byte1 + 30 + j2 * 34, 197, 0);
         }

         for (int j3 = 0; j3 < 3; j3++) {
            this.gameGraphics.drawLineX(byte0 + 8, byte1 + 123 + j3 * 34, 197, 0);
         }

         for (int l3 = 0; l3 < 7; l3++) {
            this.gameGraphics.drawLineX(byte0 + 216, byte1 + 30 + l3 * 34, 246, 0);
         }

         for (int k4 = 0; k4 < 6; k4++) {
            if (k4 < 5) {
               this.gameGraphics.drawLineY(byte0 + 8 + k4 * 49, byte1 + 30, 69, 0);
            }

            if (k4 < 5) {
               this.gameGraphics.drawLineY(byte0 + 8 + k4 * 49, byte1 + 123, 69, 0);
            }

            this.gameGraphics.drawLineY(byte0 + 216 + k4 * 49, byte1 + 30, 205, 0);
         }

         this.gameGraphics.drawLineX(byte0 + 8, byte1 + 215, 197, 0);
         this.gameGraphics.drawLineX(byte0 + 8, byte1 + 257, 197, 0);
         this.gameGraphics.drawLineY(byte0 + 8, byte1 + 215, 43, 0);
         this.gameGraphics.drawLineY(byte0 + 204, byte1 + 215, 43, 0);
         this.gameGraphics.drawString("Preparing to duel with: " + this.duelOpponentName, byte0 + 1, byte1 + 10, 1, 16777215);
         this.gameGraphics.drawString("Your Stake", byte0 + 9, byte1 + 27, 4, 16777215);
         this.gameGraphics.drawString("Opponent's Stake", byte0 + 9, byte1 + 120, 4, 16777215);
         this.gameGraphics.drawString("Duel Options", byte0 + 9, byte1 + 212, 4, 16777215);
         this.gameGraphics.drawString("Your Inventory", byte0 + 216, byte1 + 27, 4, 16777215);
         this.gameGraphics.drawString("No retreating", byte0 + 8 + 1, byte1 + 215 + 16, 3, 16776960);
         this.gameGraphics.drawString("No magic", byte0 + 8 + 1, byte1 + 215 + 35, 3, 16776960);
         this.gameGraphics.drawString("No prayer", byte0 + 8 + 102, byte1 + 215 + 16, 3, 16776960);
         this.gameGraphics.drawString("No weapons", byte0 + 8 + 102, byte1 + 215 + 35, 3, 16776960);
         this.gameGraphics.drawBoxEdge(byte0 + 93, byte1 + 215 + 6, 11, 11, 16776960);
         if (this.duelNoRetreating) {
            this.gameGraphics.drawBox(byte0 + 95, byte1 + 215 + 8, 7, 7, 16776960);
         }

         this.gameGraphics.drawBoxEdge(byte0 + 93, byte1 + 215 + 25, 11, 11, 16776960);
         if (this.duelNoMagic) {
            this.gameGraphics.drawBox(byte0 + 95, byte1 + 215 + 27, 7, 7, 16776960);
         }

         this.gameGraphics.drawBoxEdge(byte0 + 191, byte1 + 215 + 6, 11, 11, 16776960);
         if (this.duelNoPrayer) {
            this.gameGraphics.drawBox(byte0 + 193, byte1 + 215 + 8, 7, 7, 16776960);
         }

         this.gameGraphics.drawBoxEdge(byte0 + 191, byte1 + 215 + 25, 11, 11, 16776960);
         if (this.duelNoWeapons) {
            this.gameGraphics.drawBox(byte0 + 193, byte1 + 215 + 27, 7, 7, 16776960);
         }

         if (!this.duelMyAccepted) {
            this.gameGraphics.drawPicture(byte0 + 217, byte1 + 238, 2025);
         }

         this.gameGraphics.drawPicture(byte0 + 394, byte1 + 238, 2026);
         if (this.duelOpponentAccepted) {
            this.gameGraphics.drawText("Other player", byte0 + 341, byte1 + 246, 1, 16777215);
            this.gameGraphics.drawText("has accepted", byte0 + 341, byte1 + 256, 1, 16777215);
         }

         if (this.duelMyAccepted) {
            this.gameGraphics.drawText("Waiting for", byte0 + 217 + 35, byte1 + 246, 1, 16777215);
            this.gameGraphics.drawText("other player", byte0 + 217 + 35, byte1 + 256, 1, 16777215);
         }

         for (int l4 = 0; l4 < this.inventoryCount; l4++) {
            int i5 = 217 + byte0 + l4 % 5 * 49;
            int k5 = 31 + byte1 + l4 / 5 * 34;
            this.gameGraphics
               .spriteClip4(
                  i5,
                  k5,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.inventoryItems[l4]).getSprite(),
                  EntityHandler.getItemDef(this.inventoryItems[l4]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.inventoryItems[l4]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.inventoryItemsCount[l4]), i5 + 1, k5 + 10, 1, 16776960);
            }
         }

         for (int j5 = 0; j5 < this.duelMyItemCount; j5++) {
            int l5 = 9 + byte0 + j5 % 4 * 49;
            int j6 = 31 + byte1 + j5 / 4 * 34;
            this.gameGraphics
               .spriteClip4(
                  l5,
                  j6,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.duelMyItems[j5]).getSprite(),
                  EntityHandler.getItemDef(this.duelMyItems[j5]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.duelMyItems[j5]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.duelMyItemsCount[j5]), l5 + 1, j6 + 10, 1, 16776960);
            }

            if (super.mouseX > l5 && super.mouseX < l5 + 48 && super.mouseY > j6 && super.mouseY < j6 + 32) {
               this.gameGraphics
                  .drawString(
                     EntityHandler.getItemDef(this.duelMyItems[j5]).getName() + ": @whi@" + EntityHandler.getItemDef(this.duelMyItems[j5]).getDescription(),
                     byte0 + 8,
                     byte1 + 273,
                     1,
                     16776960
                  );
            }
         }

         for (int i6 = 0; i6 < this.duelOpponentItemCount; i6++) {
            int k6 = 9 + byte0 + i6 % 4 * 49;
            int l6 = 124 + byte1 + i6 / 4 * 34;
            this.gameGraphics
               .spriteClip4(
                  k6,
                  l6,
                  48,
                  32,
                  2150 + EntityHandler.getItemDef(this.duelOpponentItems[i6]).getSprite(),
                  EntityHandler.getItemDef(this.duelOpponentItems[i6]).getPictureMask(),
                  0,
                  0,
                  false
               );
            if (EntityHandler.getItemDef(this.duelOpponentItems[i6]).isStackable()) {
               this.gameGraphics.drawString(String.valueOf(this.duelOpponentItemsCount[i6]), k6 + 1, l6 + 10, 1, 16776960);
            }

            if (super.mouseX > k6 && super.mouseX < k6 + 48 && super.mouseY > l6 && super.mouseY < l6 + 32) {
               this.gameGraphics
                  .drawString(
                     EntityHandler.getItemDef(this.duelOpponentItems[i6]).getName()
                        + ": @whi@"
                        + EntityHandler.getItemDef(this.duelOpponentItems[i6]).getDescription(),
                     byte0 + 8,
                     byte1 + 273,
                     1,
                     16776960
                  );
            }
         }
      }

      if (this.showDuelWindow && this.showOfferMenu && this.offerMenuIsDuel) {
         this.drawOfferMenuOverlay();
      }
   }

   private final void drawServerMessageBox() {
      char c = 400;
      char c1 = 'd';
      if (this.serverMessageBoxTop) {
         char var5 = 450;
         c1 = 300;
      }

      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      this.gameGraphics.drawBox(256 + ox - c / 2, 167 + oy - c1 / 2, c, c1, 0);
      this.gameGraphics.drawBoxEdge(256 + ox - c / 2, 167 + oy - c1 / 2, c, c1, 16777215);
      this.gameGraphics.drawBoxTextColour(this.serverMessage, 256 + ox, 167 + oy - c1 / 2 + 20, 1, 16777215, c - '(');
      int i = 157 + c1 / 2 + oy;
      int j = 16777215;
      if (super.mouseY > i - 12 && super.mouseY <= i && super.mouseX > 106 + ox && super.mouseX < 406 + ox) {
         j = 16711680;
      }

      this.gameGraphics.drawText("Click here to close window", 256 + ox, i, 1, j);
      if (this.mouseButtonClick == 1) {
         if (j == 16711680) {
            this.showServerMessageBox = false;
         }

         /*
          * This looks wrong next to bank/shop/input, which all close on an OR
          * of the two ranges (outside in x, OR outside in y -- true anywhere
          * off the box). This one is an AND (outside in x AND outside in y --
          * true only off a corner), so a click directly above, below or
          * beside the box, but not past both edges at once, does not close
          * it.
          *
          * It is not an RSCD bug. The genuine decompiled Jagex client (method
          * l(byte) in _reference/rsclassic-src/client.java, ~line 10854) draws
          * this exact box -- same 256/167 centre, same 400x100 default growing
          * to 450x300, even the same dead store (n3 = 450 immediately
          * overwritten by n3 = 300, mirrored here by the unused `var5 = 450`
          * a few lines up) -- and its own close check decompiles to
          * !((x in range) || (y in range)), which by De Morgan is exactly
          * (x out of range) && (y out of range). Jagex shipped the AND here;
          * every other box in this class just happens not to share it.
          * Left as-is.
          */
         if ((super.mouseX < 256 + ox - c / 2 || super.mouseX > 256 + ox + c / 2) && (super.mouseY < 167 + oy - c1 / 2 || super.mouseY > 167 + oy + c1 / 2)) {
            this.showServerMessageBox = false;
         }
      }

      this.mouseButtonClick = 0;
   }

   private final void makeLoginMenus() {
      /* The layout is Jagex's 512x334; in a bigger window the whole panel
         sits centred, the backdrop with it (drawLoginScreen uses the same
         offsets). Menu stores absolute coordinates, so building at the offset
         is all it takes -- the clicks land where the pixels are. */
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      this.menuWelcome = new Menu(this.gameGraphics, 50);
      int i = 40 + oy;
      // Both lines are handles now: they say whatever the server the player
      // chose says, and are filled in by applyServerWelcome() below. What is
      // written here is only what shows before anything has been chosen.
      this.welcomeLine1Text = this.menuWelcome.drawText(256 + ox, 200 + i, "Welcome to RSCD Community Client", 4, true);
      this.welcomeLine2Text = this.menuWelcome.drawText(256 + ox, 215 + i, "For support please visit " + Config.WEB_IP, 4, true);
      this.menuWelcome.drawBox(256 + ox, 250 + i, 200, 35);
      this.menuWelcome.drawText(256 + ox, 250 + i, "Click here to login", 5, false);
      this.loginButtonExistingUser = this.menuWelcome.makeButton(256 + ox, 250 + i, 200, 35);
      this.menuNewUser = new Menu(this.gameGraphics, 50);
      int var2 = 230 + oy;
      this.menuNewUser.drawText(256 + ox, var2 + 8, "To create an account please go back to the", 4, true);
      var2 += 20;
      this.menuNewUser.drawText(256 + ox, var2 + 8, Config.WEB_IP + " front page, and choose 'register'", 4, true);
      var2 += 30;
      this.menuNewUser.drawBox(256 + ox, var2 + 17, 150, 34);
      this.menuNewUser.drawText(256 + ox, var2 + 17, "Ok", 5, false);
      this.newUserOkButton = this.menuNewUser.makeButton(256 + ox, var2 + 17, 150, 34);
      this.menuLogin = new Menu(this.gameGraphics, 50);
      /*
       * 230 is where Jagex started this panel, and it is the same number the
       * new-user panel above still uses. RSCD had moved it up to 145 to make
       * room for a third row; with the backdrop restored that put the status
       * text and the Username box on top of the artwork, which runs from
       * LOGIN_LOGO_Y to LOGIN_LOGO_Y + 140, i.e. y 10..150.
       */
      int var5 = 230 + oy;
      this.loginStatusText = this.menuLogin.drawText(256 + ox, var5 - 10, "Please enter your username and password", 4, true);
      var5 += 32;
      this.menuLogin.drawBox(190 + ox, var5, 200, 40);
      this.menuLogin.drawText(190 + ox, var5 - 10, "Username:", 4, false);
      this.loginUsernameTextBox = this.menuLogin.makeTextBox(190 + ox, var5 + 10, 200, 40, 4, 12, false, false);
      var5 += 47;
      this.menuLogin.drawBox(190 + ox, var5, 200, 40);
      this.menuLogin.drawText(190 + ox, var5 - 10, "Password:", 4, false);
      this.loginPasswordTextBox = this.menuLogin.makeTextBox(190 + ox, var5 + 10, 200, 40, 4, 20, true, false);
      /*
       * Two rows, which is how Jagex drew it. RSCD added a third for the world
       * number; it pushed the box down over the RUNESCAPE logo and there is
       * nowhere for it to go while only one world is up. LOGIN_WORLD replaces
       * it -- the plumbing is untouched, so login() still takes a world and
       * Methods.ChangeWorld() still moves between 1 and 2.
       *
       * 55 rather than the 102 the three-row build used, which is that number
       * less the 47 the row occupied: Ok and Cancel do not move.
       */
      var5 -= 55;
      this.menuLogin.drawBox(375 + ox, var5, 120, 25);
      this.menuLogin.drawText(375 + ox, var5, "Ok", 4, false);
      this.loginOkButton = this.menuLogin.makeButton(375 + ox, var5, 120, 25);
      var5 += 30;
      this.menuLogin.drawBox(375 + ox, var5, 120, 25);
      this.menuLogin.drawText(375 + ox, var5, "Cancel", 4, false);
      this.loginCancelButton = this.menuLogin.makeButton(375 + ox, var5, 120, 25);
      /*
       * Worlds, the third button, on the y this build has always computed and
       * thrown away: the fields on the left span 242 to 329, and three 25px
       * buttons at 254 / 284 / 314 divide that column evenly against them.
       * Nothing above moves.
       */
      var5 += 30;
      this.menuLogin.drawBox(375 + ox, var5, 120, 25);
      this.menuLogin.drawText(375 + ox, var5, "Worlds", 4, false);
      this.loginWorldsButton = this.menuLogin.makeButton(375 + ox, var5, 120, 25);
      this.menuLogin.setFocus(this.loginUsernameTextBox);
   }

   /*
    * The Worlds panel. Full width, drawn over the whole login screen rather
    * than beside the artwork -- picking where you play is the biggest decision
    * the client asks for, and on a fresh install it is the only one.
    *
    * Rows come from the registry at Config.API_URL. Everything below the list
    * works without it: the search filters what has already been fetched, and
    * the address box connects to anything you can name, which is how you reach
    * localhost, a server that has not registered, or one that has been
    * delisted. Being taken off the list costs a server its discoverability and
    * nothing else.
    *
    * Built on first use rather than beside the login menu, because it owns its
    * own drawing and needs nothing from Menu's widget table.
    */
   final WorldsPanel worldsPanel() {
      if (this.worldsPanel == null) {
         this.worldsPanel = new WorldsPanel(this, this.worldList);
      }

      return this.worldsPanel;
   }

   /*
    * Opens the Worlds screen, fetching the list the first time it is shown.
    * The screen is drawn immediately and says "Loading..." while that is in
    * flight; the fetch is on its own thread and the game loop never waits for
    * it.
    */
   private final void showWorldsScreen() {
      this.loginScreenNumber = 4;
      this.worldsPanel().open();
   }

   /*
    * The Worlds screen, before there is a game to show it in.
    *
    * This runs on the game thread from startGame(), before the main loop, and
    * blocks until joinWorld() sets worldChosen. Everything it needs already
    * exists at that point: the window is up, the AWT listeners have been
    * feeding mouseX/mouseY/mouseDownButton since createWindow(), keys reach
    * handleMenuKeyDown() and from there the panel (loginScreenNumber is 4),
    * and the fonts were loaded before startGame() was called.
    *
    * What does not exist is gameGraphics, and it cannot: GameImage's
    * constructor reads Sprites.xml.data out of Assets and exits the process if
    * it is missing, and Assets is empty until the download this screen is
    * standing in front of. So the panel paints into an ordinary BufferedImage
    * and that is blitted to the window -- the same pixels either way, because
    * Skin draws through Graphics2D in both cases.
    *
    * The backdrop is the panel's own stone rather than any artwork. There is
    * deliberately no server's branding on screen while the question of which
    * server is still open.
    */
   private final void chooseWorldBeforeBoot() {
      this.loginScreenNumber = 4;
      this.worldsPanel().open();

      BufferedImage frame = new BufferedImage(WorldsPanel.SCREEN_W, WorldsPanel.SCREEN_H, BufferedImage.TYPE_INT_RGB);

      /*
       * Geometry is recomputed every pass, not once before the loop.
       *
       * This loop is the only screen in the client that owns the window
       * outright: it runs before startGame(), so there is no framebuffer, no
       * renderFrame(), and nothing else painting. Whatever it does not draw
       * keeps whatever AWT last put there -- which on a fresh frame is the
       * default component background. Reading the canvas size once meant a
       * window resized while the chooser was up kept drawing the panel at the
       * old offset, in the top-left, on grey. The login screen looked right
       * next to it only because it is drawn through the game's own frame and
       * that path already re-reads the size and clears to black.
       *
       * So: track the size, and when it changes, take the window's Graphics
       * again (the cached one is clipped to the old shape) and repaint the
       * whole canvas black before the panel goes back on top of it.
       */
      int offsetX = -1;
      int offsetY = -1;
      int lastWidth = -1;
      int lastHeight = -1;

      while (!this.worldChosen) {
         int width = canvasWidth();
         int height = canvasHeight();
         boolean resized = width != lastWidth || height != lastHeight;

         if (resized) {
            lastWidth = width;
            lastHeight = height;
            /* Centred, and clamped at zero so a window smaller than the panel
               clips on the right rather than drawing off the left edge. The
               same offsets come off the pointer below, or every hit test would
               be wrong by exactly the margin. */
            offsetX = Math.max(0, (width - WorldsPanel.SCREEN_W) / 2);
            offsetY = Math.max(0, (height - WorldsPanel.SCREEN_H) / 2);

            Graphics resizedScreen = this.refreshScreenGraphics();
            if (resizedScreen != null) {
               resizedScreen.setColor(Color.black);
               resizedScreen.fillRect(0, 0, width, height);
            }
         }

         Graphics2D g = frame.createGraphics();

         try {
            g.setColor(Color.black);
            g.fillRect(0, 0, WorldsPanel.SCREEN_W, WorldsPanel.SCREEN_H);
            this.worldsPanel().paint(g, super.mouseX - offsetX, super.mouseY - offsetY);
         } finally {
            g.dispose();
         }

         Graphics screen = this.screenGraphics();
         if (screen != null) {
            screen.drawImage(frame, offsetX, offsetY, null);
         }

         this.worldsPanel().update(super.mouseX - offsetX, super.mouseY - offsetY, super.mouseDownButton);

         try {
            Thread.sleep(20L);
         } catch (InterruptedException var7) {
            Thread.currentThread().interrupt();
            return;
         }
      }

      this.worldChosen = false;
   }

   /*
    * Back is only offered when there is something behind this screen. On the
    * boot-time chooser there is not -- the client has not loaded anything and
    * has nowhere to go -- and after a fresh install leaveWorldsScreen() sends
    * you straight back here anyway.
    */
   final boolean canLeaveWorldsScreen() {
      return this.assetsLoaded && Config.DEFAULT_TARGET.length() > 0;
   }

   /*
    * Load the chosen world's content, with the loading screen back up.
    *
    * Called from the top of a tick, so nothing is mid-draw. loadGameData()
    * builds a whole new framebuffer and a whole new set of menus, which is
    * exactly what is wanted: every one of them was sized and filled from the
    * previous world's data.
    */
   private final void reloadForChosenWorld() {
      this.reloadPending = false;
      this.enterLoadingScreen("Loading...");
      this.loadGameData();
      this.leaveLoadingScreen();

      /* loadGameData() ends in resetLoginVars(), which puts up the welcome
         panel because a default server is now set. The player did not ask for
         the welcome panel -- they asked to join a world -- so finish the join
         they started before the loader interrupted it. */
      if (!this.lastLoadedNull && this.menuLogin != null) {
         this.showSignIn(this.joinedRow);
      }

      this.joinedRow = null;
   }

   /*
    * Joins a server: point the client at it, remember it, and hand over to the
    * sign-in screen -- which now shows that server's own welcome, because the
    * next thing the player reads should belong to whoever they chose.
    */
   /*
    * A second launch is offering us its rscd:// link. Runs on the listener's
    * thread, so it decides and queues -- it does not join here.
    *
    * Signed in means no. Not "ask", not "finish this fight first": a link
    * click in a browser must never be able to end a session that is already
    * running, and there is no interruption mild enough to be worth it. The
    * other process opens its own window in that case, which is what it would
    * have done anyway before any of this existed.
    */
   @Override
   public boolean join(String uri) {
      if (this.loggedIn != 0 || uri == null) {
         return false;
      }

      this.pendingJoinUri = uri;
      return true;
   }

   /*
    * Acted on at the top of a tick, off the listener thread.
    *
    * Goes through joinWorld like any other join, so a link to a server whose
    * content differs from what is loaded takes the same reload path a click on
    * the Worlds screen would -- there is no second, quieter way into a world.
    */
   private void applyPendingJoin() {
      String uri = this.pendingJoinUri;
      this.pendingJoinUri = null;
      if (uri == null || !Config.applyJoinUri(uri)) {
         return;
      }

      WorldList.Row row = WorldList.direct(Config.SERVER_IP + ":" + Config.SERVER_PORT);
      if (row == null) {
         return;
      }
      if (Config.SERVER_NAME.length() > 0) {
         row.serverName = Config.SERVER_NAME;
      }

      /* A link carries a name but no welcome text. Keeping the one on screen
         would caption a different server's sign-in with the last server's
         greeting; dropping it for a genuinely different target is the honest
         choice, and joining the world already loaded keeps it. */
      if (!row.target().equalsIgnoreCase(Config.DEFAULT_TARGET)) {
         row.welcome1 = "";
         row.welcome2 = "";
      } else {
         row.welcome1 = Config.WELCOME_LINE1;
         row.welcome2 = Config.WELCOME_LINE2;
      }
      row.cacheUrl = Config.CACHE_URL;

      this.joinWorld(row);
      this.raiseWindow();
   }

   /* The player clicked a link and expects to be looking at the client. */
   private void raiseWindow() {
      try {
         if (gameFrame != null) {
            gameFrame.setState(GameFrame.NORMAL);
            gameFrame.toFront();
            gameFrame.requestFocus();
         }
      } catch (Throwable ignored) {
         /* Raising a window is a request the window manager may refuse, and a
            no-op in a browser tab. Never worth failing a join over. */
      }
   }

   final void joinWorld(WorldList.Row row) {
      WorldList.remember(row);
      this.setServer(row.host, row.port - (row.world - 1));
      this.currentWorld = row.world;

      /*
       * Three ways out of here, and which one applies is entirely about whose
       * content is in memory.
       *
       * Nothing loaded yet: this is the boot-time chooser, and the answer it
       * was waiting for has arrived. Return before touching any Menu -- none
       * of them exist, because makeLoginMenus() has not run. The welcome text
       * is not lost by skipping it here: remember() has already put it in
       * Config, and resetLoginVars() applies it at the end of the boot.
       *
       * Loaded, but from somewhere else: go back through the loader. Note that
       * remember() has already pointed Config.CACHE_URL at the new server, so
       * this compares the new address against what was actually downloaded.
       *
       * Loaded from here: straight to the sign-in screen, as before.
       */
      if (!this.assetsLoaded) {
         this.worldChosen = true;
         return;
      }

      if (!Config.CACHE_URL.equals(this.loadedCacheUrl)) {
         this.joinedRow = row;
         this.reloadPending = true;
         return;
      }

      this.applyServerWelcome();
      this.showSignIn(row);
   }

   /*
    * Hand over to the sign-in screen for a world that has just been joined.
    *
    * Reached either straight from joinWorld() or, when the world's content had
    * to be downloaded first, from the far side of the loader -- which is why
    * this is not inline in joinWorld() any more. Both arrive here with the
    * same server selected and the same row in hand.
    */
   private final void showSignIn(WorldList.Row row) {
      this.loginScreenNumber = 2;

      /*
       * The list already marks this row "Old client"; saying it again here is
       * for the player who clicked anyway, or who joined from a favourite
       * without reading the label. The server would refuse on submit with the
       * same meaning, so this only moves the answer earlier -- and the row
       * stays joinable, because the registry entry may be stale and the server
       * is the one entitled to decide.
       */
      this.menuLogin.updateText(this.loginStatusText,
         row != null && row.needsNewerClient(GameWindowMiddleMan.clientVersion)
            ? "This server needs a newer client than yours"
            : "Please enter your username and password");
      this.menuLogin.updateText(this.loginUsernameTextBox, this.currentUser);
      this.menuLogin.updateText(this.loginPasswordTextBox, this.currentPass);
      this.menuLogin.setFocus(this.loginUsernameTextBox);
   }

   /*
    * The welcome panel's two lines belong to the selected server, not to us.
    * They arrive with its registry entry rather than over the connection
    * because the panel is shown before there is a connection.
    *
    * A server that supplies neither falls back to what this panel always said,
    * so a listing with half its fields filled in still reads sensibly.
    */
   private final void applyServerWelcome() {
      String line1 = Config.WELCOME_LINE1;
      String line2 = Config.WELCOME_LINE2;
      if (line1 == null || line1.length() == 0) {
         line1 = Config.SERVER_NAME != null && Config.SERVER_NAME.length() > 0
            ? "Welcome to " + Config.SERVER_NAME
            : "Welcome to RSCD Community Client";
      }

      if (line2 == null || line2.length() == 0) {
         line2 = "For support please visit " + Config.WEB_IP;
      }

      this.menuWelcome.updateText(this.welcomeLine1Text, line1);
      this.menuWelcome.updateText(this.welcomeLine2Text, line2);
   }

   private final void drawGameWindowsMenus() {
      if (this.logoutTimeout != 0) {
         this.drawLoggingOutBox();
      } else if (this.showWelcomeBox) {
         this.drawWelcomeBox();
      } else if (this.showServerMessageBox) {
         this.drawServerMessageBox();
      } else if (this.wildernessType == 1) {
         this.drawWildernessWarningBox();
      } else if (this.showBank && this.lastWalkTimeout == 0) {
         this.drawBankBox();
      } else if (this.showShop && this.lastWalkTimeout == 0) {
         this.drawShopBox();
      } else if (this.showTradeConfirmWindow) {
         this.drawTradeConfirmWindow();
      } else if (this.showTradeWindow) {
         this.drawTradeWindow();
      } else if (this.showDuelConfirmWindow) {
         this.drawDuelConfirmWindow();
      } else if (this.showDuelWindow) {
         this.drawDuelWindow();
      } else if (this.showAbuseWindow == 1) {
         this.drawAbuseWindow1();
      } else if (this.showAbuseWindow == 2) {
         this.drawAbuseWindow2();
      } else if (this.inputBoxType != 0) {
         this.drawInputBox();
      } else {
         if (this.showQuestionMenu) {
            this.drawQuestionMenu();
         }

         if (this.ourPlayer.currentSprite == 8 || this.ourPlayer.currentSprite == 9 || this.combatWindow) {
            this.drawCombatStyleWindow();
         }

         this.checkMouseOverMenus();
         boolean noMenusShown = !this.showQuestionMenu && !this.showRightClickMenu;
         if (noMenusShown) {
            this.menuLength = 0;
         }

         if (this.mouseOverMenu == 0 && noMenusShown) {
            this.drawInventoryRightClickMenu();
         }

         if (this.mouseOverMenu == 1) {
            this.drawInventoryMenu(noMenusShown);
         }

         if (this.mouseOverMenu == 2) {
            this.drawMapMenu(noMenusShown);
         }

         if (this.mouseOverMenu == 3) {
            this.drawPlayerInfoMenu(noMenusShown);
         }

         if (this.mouseOverMenu == 4) {
            this.drawMagicWindow(noMenusShown);
         }

         if (this.mouseOverMenu == 5) {
            this.drawFriendsWindow(noMenusShown);
         }

         if (this.mouseOverMenu == 6) {
            this.drawOptionsMenu(noMenusShown);
         }

         if (!this.showRightClickMenu && !this.showQuestionMenu) {
            this.checkMouseStatus();
         }

         if (this.showRightClickMenu && !this.showQuestionMenu) {
            this.drawRightClickMenu();
         }
      }

      this.mouseButtonClick = 0;
   }

   final void walkToTile(int sectionX, int sectionY, int x, int y, boolean stopShort) {
      this.sendWalkCommand(sectionX, sectionY, x, y, x, y, false, stopShort);
   }

   private final void drawInputBox() {
      int ox = this.loginOffsetX();
      int oy = this.loginOffsetY();
      if (this.mouseButtonClick != 0) {
         this.mouseButtonClick = 0;
         if (this.inputBoxType == 1 && (super.mouseX < 106 + ox || super.mouseY < 145 + oy || super.mouseX > 406 + ox || super.mouseY > 215 + oy)) {
            this.inputBoxType = 0;
            return;
         }

         if (this.inputBoxType == 2 && (super.mouseX < 6 + ox || super.mouseY < 145 + oy || super.mouseX > 506 + ox || super.mouseY > 215 + oy)) {
            this.inputBoxType = 0;
            return;
         }

         if (this.inputBoxType == 3 && (super.mouseX < 106 + ox || super.mouseY < 145 + oy || super.mouseX > 406 + ox || super.mouseY > 215 + oy)) {
            this.inputBoxType = 0;
            return;
         }

         if (super.mouseX > 236 + ox && super.mouseX < 276 + ox && super.mouseY > 193 + oy && super.mouseY < 213 + oy) {
            this.inputBoxType = 0;
            return;
         }
      }

      int i = 145 + oy;
      if (this.inputBoxType == 1) {
         this.gameGraphics.drawBox(106 + ox, i, 300, 70, 0);
         this.gameGraphics.drawBoxEdge(106 + ox, i, 300, 70, 16777215);
         int var4 = i + 20;
         this.gameGraphics.drawText("Enter name to add to friends list", 256 + ox, var4, 4, 16777215);
         i = var4 + 20;
         this.gameGraphics.drawText(super.inputText + "*", 256 + ox, i, 4, 16777215);
         if (super.enteredText.length() > 0) {
            String s = super.enteredText.trim();
            super.inputText = "";
            super.enteredText = "";
            this.inputBoxType = 0;
            if (s.length() > 0 && DataOperations.stringLength12ToLong(s) != this.ourPlayer.nameLong) {
               this.addToFriendsList(s);
            }
         }
      }

      if (this.inputBoxType == 2) {
         this.gameGraphics.drawBox(6 + ox, i, 500, 70, 0);
         this.gameGraphics.drawBoxEdge(6 + ox, i, 500, 70, 16777215);
         int var5 = i + 20;
         this.gameGraphics.drawText("Enter message to send to " + DataOperations.longToString(this.privateMessageTarget), 256 + ox, var5, 4, 16777215);
         i = var5 + 20;
         this.gameGraphics.drawText(super.inputMessage + "*", 256 + ox, i, 4, 16777215);
         if (super.enteredMessage.length() > 0) {
            String s1 = super.enteredMessage;
            super.inputMessage = "";
            super.enteredMessage = "";
            this.inputBoxType = 0;
            byte[] message = DataConversions.stringToByteArray(s1);
            this.sendPrivateMessage(this.privateMessageTarget, message, message.length);
            s1 = DataConversions.byteToString(message, 0, message.length);
            this.handleServerMessage("@pri@You tell " + DataOperations.longToString(this.privateMessageTarget) + ": " + s1);
         }
      }

      if (this.inputBoxType == 3) {
         this.gameGraphics.drawBox(106 + ox, i, 300, 70, 0);
         this.gameGraphics.drawBoxEdge(106 + ox, i, 300, 70, 16777215);
         i += 20;
         this.gameGraphics.drawText("Enter name to add to ignore list", 256 + ox, i, 4, 16777215);
         i += 20;
         this.gameGraphics.drawText(super.inputText + "*", 256 + ox, i, 4, 16777215);
         if (super.enteredText.length() > 0) {
            String s2 = super.enteredText.trim();
            super.inputText = "";
            super.enteredText = "";
            this.inputBoxType = 0;
            if (s2.length() > 0 && DataOperations.stringLength12ToLong(s2) != this.ourPlayer.nameLong) {
               this.addToIgnoreList(s2);
            }
         }
      }

      int j = 16777215;
      if (super.mouseX > 236 + ox && super.mouseX < 276 + ox && super.mouseY > 193 + oy && super.mouseY < 213 + oy) {
         j = 16776960;
      }

      this.gameGraphics.drawText("Cancel", 256 + ox, 208 + oy, 1, j);
   }

   /*
    * A wielded elemental staff stands in for its rune: 31/32/33/34 are the
    * fire/water/air/earth runes, and each triple is the plain staff, the
    * battlestaff and the enchanted battlestaff of that element.
    */
   final boolean hasRequiredRunes(int i, int j) {
      if (i != 31 || !this.isItemWielded(197) && !this.isItemWielded(615) && !this.isItemWielded(682)) {
         if (i != 32 || !this.isItemWielded(102) && !this.isItemWielded(616) && !this.isItemWielded(683)) {
            if (i != 33 || !this.isItemWielded(101) && !this.isItemWielded(617) && !this.isItemWielded(684)) {
               return i != 34 || !this.isItemWielded(103) && !this.isItemWielded(618) && !this.isItemWielded(685) ? this.inventoryCount(i) >= j : true;
            } else {
               return true;
            }
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   private final void resetPrivateMessageStrings() {
      super.inputMessage = "";
      super.enteredMessage = "";
   }

   private final boolean isItemWielded(int itemId) {
      for (int j = 0; j < this.inventoryCount; j++) {
         if (this.inventoryItems[j] == itemId && this.wearing[j] == 1) {
            return true;
         }
      }

      return false;
   }

   private final void setPixelsAndAroundColour(int x, int y, int colour) {
      this.gameGraphics.setPixelColour(x, y, colour);
      this.gameGraphics.setPixelColour(x - 1, y, colour);
      this.gameGraphics.setPixelColour(x + 1, y, colour);
      this.gameGraphics.setPixelColour(x, y - 1, colour);
      this.gameGraphics.setPixelColour(x, y + 1, colour);
   }

   private final void drawOverheads() {
      for (int i = 0; i < this.mobMessageCount; i++) {
         int j = this.gameGraphics.messageFontHeight(1);
         int l = this.mobMessagesX[i];
         int k1 = this.mobMessagesY[i];
         int j2 = this.mobMessagesWidth[i];
         int i3 = this.mobMessagesHeight[i];
         boolean flag = true;

         while (flag) {
            flag = false;

            for (int i4 = 0; i4 < i; i4++) {
               if (k1 + i3 > this.mobMessagesY[i4] - j
                  && k1 - j < this.mobMessagesY[i4] + this.mobMessagesHeight[i4]
                  && l - j2 < this.mobMessagesX[i4] + this.mobMessagesWidth[i4]
                  && l + j2 > this.mobMessagesX[i4] - this.mobMessagesWidth[i4]
                  && this.mobMessagesY[i4] - j - i3 < k1) {
                  k1 = this.mobMessagesY[i4] - j - i3;
                  flag = true;
               }
            }
         }

         this.mobMessagesY[i] = k1;
         this.gameGraphics.drawBoxTextColour(this.mobMessages[i], l, k1, 1, 16776960, 300);
      }

      for (int k = 0; k < this.bubbleCount; k++) {
         int i1 = this.bubbleX[k];
         int l1 = this.bubbleY[k];
         int k2 = this.bubbleScale[k];
         int j3 = this.bubbleItemId[k];
         int l3 = 39 * k2 / 100;
         int j4 = 27 * k2 / 100;
         int k4 = l1 - j4;
         this.gameGraphics.spriteClip2(i1 - l3 / 2, k4, l3, j4, 2009, 85);
         int l4 = 36 * k2 / 100;
         int i5 = 24 * k2 / 100;
         this.gameGraphics
            .spriteClip4(
               i1 - l4 / 2,
               k4 + j4 / 2 - i5 / 2,
               l4,
               i5,
               EntityHandler.getItemDef(j3).getSprite() + 2150,
               EntityHandler.getItemDef(j3).getPictureMask(),
               0,
               0,
               false
            );
      }

      for (int j1 = 0; j1 < this.healthBarCount; j1++) {
         int i2 = this.healthBarX[j1];
         int l2 = this.healthBarY[j1];
         int k3 = this.healthBarValue[j1];
         this.gameGraphics.drawBoxAlpha(i2 - 15, l2 - 3, k3, 5, 65280, 192);
         this.gameGraphics.drawBoxAlpha(i2 - 15 + k3, l2 - 3, 30 - k3, 5, 16711680, 192);
      }
   }

   private final void drawMapMenu(boolean flag) {
      int i = this.gameGraphics.menuDefaultWidth - 199;
      char c = 156;
      char c2 = 152;
      this.gameGraphics.drawPicture(i - 49, 3, 2002);
      i += 40;
      this.gameGraphics.drawBox(i, 36, c, c2, 0);
      this.gameGraphics.setDimensions(i, 36, i + c, '$' + c2);
      int k = 192 + this.minimapRandomZoom;
      int i1 = this.cameraRotation + this.minimapRandomRotation & 0xFF;
      int k1 = (this.ourPlayer.currentX - 6040) * 3 * k / 2048;
      int i3 = (this.ourPlayer.currentY - 6040) * 3 * k / 2048;
      int k4 = Camera.sinCosTable1024[1024 - i1 * 4 & 1023];
      int i5 = Camera.sinCosTable1024[(1024 - i1 * 4 & 1023) + 1024];
      int k5 = i3 * k4 + k1 * i5 >> 18;
      i3 = i3 * i5 - k1 * k4 >> 18;
      this.gameGraphics.drawMinimapSprite(i + c / 2 - k5, 36 + c2 / 2 + i3, 1999, i1 + 64 & 0xFF, k);

      for (int i7 = 0; i7 < this.objectCount; i7++) {
         int l1 = (this.objectX[i7] * this.magicLoc + 64 - this.ourPlayer.currentX) * 3 * k / 2048;
         int j3 = (this.objectY[i7] * this.magicLoc + 64 - this.ourPlayer.currentY) * 3 * k / 2048;
         int l5 = j3 * k4 + l1 * i5 >> 18;
         j3 = j3 * i5 - l1 * k4 >> 18;
         this.setPixelsAndAroundColour(i + c / 2 + l5, 36 + c2 / 2 - j3, 65535);
      }

      for (int j7 = 0; j7 < this.groundItemCount; j7++) {
         int i2 = (this.groundItemX[j7] * this.magicLoc + 64 - this.ourPlayer.currentX) * 3 * k / 2048;
         int k3 = (this.groundItemY[j7] * this.magicLoc + 64 - this.ourPlayer.currentY) * 3 * k / 2048;
         int i6 = k3 * k4 + i2 * i5 >> 18;
         k3 = k3 * i5 - i2 * k4 >> 18;
         this.setPixelsAndAroundColour(i + c / 2 + i6, 36 + c2 / 2 - k3, 16711680);
      }

      for (int k7 = 0; k7 < this.npcCount; k7++) {
         Mob mob = this.npcArray[k7];
         int j2 = (mob.currentX - this.ourPlayer.currentX) * 3 * k / 2048;
         int l3 = (mob.currentY - this.ourPlayer.currentY) * 3 * k / 2048;
         int j6 = l3 * k4 + j2 * i5 >> 18;
         l3 = l3 * i5 - j2 * k4 >> 18;
         this.setPixelsAndAroundColour(i + c / 2 + j6, 36 + c2 / 2 - l3, 16776960);
      }

      for (int l7 = 0; l7 < this.playerCount; l7++) {
         Mob mob_1 = this.playerArray[l7];
         int k2 = (mob_1.currentX - this.ourPlayer.currentX) * 3 * k / 2048;
         int i4 = (mob_1.currentY - this.ourPlayer.currentY) * 3 * k / 2048;
         int k6 = i4 * k4 + k2 * i5 >> 18;
         i4 = i4 * i5 - k2 * k4 >> 18;
         int j8 = 16777215;

         for (int k8 = 0; k8 < super.friendsCount; k8++) {
            if (mob_1.nameLong == super.friendsListLongs[k8] && super.friendsListOnlineStatus[k8] == 99) {
               j8 = 65280;
               break;
            }
         }

         this.setPixelsAndAroundColour(i + c / 2 + k6, 36 + c2 / 2 - i4, j8);
      }

      this.gameGraphics.drawCircle(i + c / 2, 36 + c2 / 2, 2, 16777215, 255);
      this.gameGraphics.drawMinimapSprite(i + 19, 55, 2024, this.cameraRotation + 128 & 0xFF, 128);
      this.gameGraphics.setDimensions(0, 0, this.windowWidth, this.surfaceHeight());
      if (flag) {
         i = super.mouseX - (this.gameGraphics.menuDefaultWidth - 199);
         int i8 = super.mouseY - 36;
         if (i >= 40 && i8 >= 0 && i < 196 && i8 < 152) {
            char c1 = 156;
            char c3 = 152;
            int l = 192 + this.minimapRandomZoom;
            int j1 = this.cameraRotation + this.minimapRandomRotation & 0xFF;
            int j = this.gameGraphics.menuDefaultWidth - 199;
            j += 40;
            int l2 = (super.mouseX - (j + c1 / 2)) * 16384 / (3 * l);
            int j4 = (super.mouseY - (36 + c3 / 2)) * 16384 / (3 * l);
            int l4 = Camera.sinCosTable1024[1024 - j1 * 4 & 1023];
            int j5 = Camera.sinCosTable1024[(1024 - j1 * 4 & 1023) + 1024];
            int l6 = j4 * l4 + l2 * j5 >> 15;
            j4 = j4 * j5 - l2 * l4 >> 15;
            l2 = l6 + this.ourPlayer.currentX;
            j4 = this.ourPlayer.currentY - j4;
            if (this.mouseButtonClick == 1) {
               this.walkToTile(this.sectionX, this.sectionY, l2 / 128, j4 / 128, false);
            }

            this.mouseButtonClick = 0;
         }
      }
   }

   public mudclient() {
      this.theConfig = new Config();
      this.combatWindow = false;
      this.threadSleepTime = 10;
      /*
       * Two IP lookups used to run here and print themselves: the host's own
       * address, and the local address of a throwaway Socket opened to
       * getDocumentBase().getHost() on port 80 -- the applet's way of asking
       * "which of my interfaces reaches the server that served me?".
       *
       * Neither value was ever read. Both went to stdout and nowhere else. The
       * second cannot work at all now: getDocumentBase() is Applet API, gone in
       * JDK 24, and in desktop mode it was null anyway, so the connect threw
       * instantly and printed "unknown" every launch. Removing it also takes a
       * blocking network call out of the constructor.
       */
      this.startTime = System.currentTimeMillis();
      this.duelMyItems = new int[8];
      this.duelMyItemsCount = new int[8];
      this.configAutoCameraAngle = true;
      this.questionMenuAnswer = new String[10];
      this.lastNpcArray = new Mob[500];
      this.currentUser = "";
      this.currentPass = "";
      this.currentWorld = LOGIN_WORLD;
      this.menuText1 = new String[250];
      this.duelOpponentAccepted = false;
      this.duelMyAccepted = false;
      this.tradeConfirmItems = new int[14];
      this.tradeConfirmItemsCount = new int[14];
      this.tradeConfirmOtherItems = new int[14];
      this.tradeConfirmOtherItemsCount = new int[14];
      this.serverMessage = "";
      this.duelOpponentName = "";
      this.inventoryItems = new int[35];
      this.inventoryItemsCount = new int[35];
      this.wearing = new int[35];
      this.mobMessages = new String[50];
      this.showBank = false;
      this.doorModel = new Model[500];
      this.mobMessagesX = new int[50];
      this.mobMessagesY = new int[50];
      this.mobMessagesWidth = new int[50];
      this.mobMessagesHeight = new int[50];
      this.npcArray = new Mob[500];
      this.equipmentStatus = new int[6];
      this.prayerOn = new boolean[50];
      this.tradeOtherAccepted = false;
      this.tradeWeAccepted = false;
      this.mobArray = new Mob[8000];
      this.bubbleScale = new int[50];
      this.bubbleItemId = new int[50];
      this.lastWildYSubtract = -1;
      this.memoryError = false;
      this.bankItemsMax = 48;
      this.showQuestionMenu = false;
      this.magicLoc = 128;
      this.cameraAutoAngle = 1;
      this.screenRotationXStep = 2;
      this.showServerMessageBox = false;
      this.hasReceivedWelcomeBoxDetails = false;
      this.playerStatCurrent = new int[19];
      this.wildYSubtract = -1;
      this.lastModelFireLightningSpellNumber = -1;
      this.lastModelTorchNumber = -1;
      this.lastModelClawSpellNumber = -1;
      this.sectionXArray = new int[8000];
      this.sectionYArray = new int[8000];
      this.selectedItem = -1;
      this.selectedItemName = "";
      this.duelOpponentItems = new int[8];
      this.duelOpponentItemsCount = new int[8];
      this.teleBubbleY = new int[50];
      this.menuID = new int[250];
      this.showCharacterLookScreen = false;
      this.lastPlayerArray = new Mob[500];
      this.gameDataModels = new Model[1000];
      this.configMouseButtons = false;
      this.duelNoRetreating = false;
      this.duelNoMagic = false;
      this.duelNoPrayer = false;
      this.duelNoWeapons = false;
      this.teleBubbleType = new int[50];
      this.duelConfirmOpponentItems = new int[8];
      this.duelConfirmOpponentItemsCount = new int[8];
      this.healthBarX = new int[50];
      this.healthBarY = new int[50];
      this.healthBarValue = new int[50];
      this.objectModelArray = new Model[1500];
      this.cameraRotation = 128;
      this.showWelcomeBox = false;
      this.characterBodyGender = 1;
      this.character2Colour = 2;
      this.characterHairColour = 2;
      this.characterTopColour = 8;
      this.characterBottomColour = 14;
      this.characterHeadGender = 1;
      this.selectedBankItem = -1;
      this.selectedBankItemType = -2;
      this.menuText2 = new String[250];
      this.objectAlreadyInMenu = new boolean[1500];
      this.playerStatBase = new int[19];
      this.menuActionType = new int[250];
      this.menuActionVariable = new int[250];
      this.menuActionVariable2 = new int[250];
      this.shopItems = new int[256];
      this.shopItemCount = new int[256];
      this.bubbleX = new int[50];
      this.bubbleY = new int[50];
      this.newBankItems = new int[256];
      this.newBankItemsCount = new int[256];
      this.duelConfirmMyItems = new int[8];
      this.duelConfirmMyItemsCount = new int[8];
      this.mobArrayIndexes = new int[500];
      this.messagesTimeout = new int[5];
      this.objectX = new int[1500];
      this.objectY = new int[1500];
      this.objectType = new int[1500];
      this.objectID = new int[1500];
      this.menuActionX = new int[250];
      this.menuActionY = new int[250];
      this.ourPlayer = new Mob();
      this.serverIndex = -1;
      this.inventoryMaxSlots = 30;
      this.showTradeConfirmWindow = false;
      this.tradeConfirmAccepted = false;
      this.playerArray = new Mob[500];
      this.serverMessageBoxTop = false;
      this.cameraHeight = 550;
      this.bankItems = new int[256];
      this.bankItemsCount = new int[256];
      this.notInWilderness = false;
      this.selectedSpell = -1;
      this.screenRotationYStep = 2;
      this.tradeOtherItems = new int[14];
      this.tradeOtherItemsCount = new int[14];
      this.menuIndexes = new int[250];
      this.zoomCamera = false;
      this.playerStatExperience = new int[19];
      this.cameraAutoAngleDebug = false;
      this.npcRecordArray = new Mob[8000];
      this.showDuelWindow = false;
      this.showOfferMenu = false;
      this.teleBubbleTime = new int[50];
      this.lastLoadedNull = false;
      this.experienceArray = new int[99];
      this.showShop = false;
      this.mouseClickXArray = new int[8192];
      this.mouseClickYArray = new int[8192];
      this.showDuelConfirmWindow = false;
      this.duelWeAccept = false;
      this.doorX = new int[500];
      this.doorY = new int[500];
      this.configSoundEffects = false;
      this.showRightClickMenu = false;
      this.projectileFlightDuration = 40;
      this.teleBubbleX = new int[50];
      this.doorDirection = new int[500];
      this.doorType = new int[500];
      this.groundItemX = new int[8000];
      this.groundItemY = new int[8000];
      this.groundItemType = new int[8000];
      this.groundItemObjectVar = new int[8000];
      this.selectedShopItemIndex = -1;
      this.selectedShopItemType = -2;
      this.messagesArray = new String[5];
      this.showTradeWindow = false;
      this.doorAlreadyInMenu = new boolean[500];
      this.tradeMyItems = new int[14];
      this.tradeMyItemsCount = new int[14];
      this.windowWidth = 512;
      this.windowHeight = 334;
      this.cameraSizeInt = 9;
      this.tradeOtherPlayerName = "";
   }
}
