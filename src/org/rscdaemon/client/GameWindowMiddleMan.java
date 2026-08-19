package org.rscdaemon.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.io.IOException;
import java.math.BigInteger;
import org.rscdaemon.client.util.Config;
import org.rscdaemon.client.util.DataConversions;

public abstract class GameWindowMiddleMan extends GameWindow {
   private long lastPacket = System.currentTimeMillis();
   public static int clientVersion = 1;
   public static int maxPacketReadCount;
   String username = "";
   String password = "";
   int theworld = 0;
   /*
    * The server currently being played on. It used to be implicit: one
    * hardcoded address, world 1 on port 43594 and world 2 on 43595, and
    * nothing else reachable. The Worlds screen picks a server now, so the
    * address has to be state the client carries rather than a constant.
    *
    * worldBasePort is the port of that server's world 1. RSCD numbers a
    * server's worlds along consecutive ports from there, which is where 43594
    * and 43595 came from in the first place -- so world hopping still works
    * against a server that does not happen to start at 43594.
    *
    * Null host means "not chosen yet", and resolves to whatever Config was
    * configured with.
    */
   String serverHost;
   int worldBasePort;
   public StreamClass streamClass;
   protected byte[] packetData = new byte[5000];
   int reconnectTries;
   long lastPing;
   public int friendsCount;
   public long[] friendsListLongs = new long[400];
   public int[] friendsListOnlineStatus = new int[400];
   public int ignoreListCount;
   public long[] ignoreListLongs = new long[200];
   public int blockChatMessages;
   public int blockPrivateMessages;
   public int blockTradeRequests;
   public int blockDuelRequests;
   private static BigInteger key = new BigInteger(
      "1370158896620336158431733257575682136836100155721926632321599369132092701295540721504104229217666225601026879393318399391095704223500673696914052239029335"
   );
   private static BigInteger modulus = new BigInteger(
      "1549611057746979844352781944553705273443228154042066840514290174539588436243191882510185738846985723357723362764835928526260868977814405651690121789896823"
   );
   public int socketTimeout;

   /*
    * Selects the server to play on. Called by the Worlds screen before it
    * hands over to sign-in, and by anything restoring a remembered server.
    * host may carry a ":port" suffix, which is what a hand-typed address from
    * a forum post looks like.
    */
   protected final void setServer(String host, int basePort) {
      this.serverHost = host;
      this.worldBasePort = basePort;
   }

   /*
    * The world-index form, kept because the scripting API exposes it
    * (Methods.ChangeWorld / HopServer) and nothing is ever removed from that
    * surface. Resolves the world to a port on the currently selected server
    * and defers to the address form below.
    *
    * It no longer refuses anything above 2. Two worlds was a fact about our
    * server, not about the protocol, and the client now connects to servers
    * whose world count it does not know -- so an out-of-range world is
    * discovered the same way a wrong address is, by the connection failing
    * with a message that says so.
    */
   protected final void login(String user, String pass, int world, boolean reconnecting) {
      String host = this.serverHost != null ? this.serverHost : Config.SERVER_IP;
      int base = this.worldBasePort > 0 ? this.worldBasePort : Config.SERVER_PORT;
      if (world < 1) {
         world = 1;
      }

      this.login(user, pass, host, base + (world - 1), world, reconnecting);
   }

   protected final void login(String user, String pass, String host, int port, int world, boolean reconnecting) {
      if (host == null || host.trim().length() == 0 || port <= 0 || port > 65535) {
         this.loginScreenPrint("No server selected.", "Choose one from the Worlds screen");
      } else if (this.socketTimeout > 0) {
         this.loginScreenPrint("Please wait...", "Connecting to server");

         try {
            Thread.sleep(2000L);
         } catch (Exception var13) {
         }

         this.loginScreenPrint("Sorry! The server is currently full.", "Please try again later");
      } else {
         try {
            this.username = user;
            user = DataOperations.addCharacters(user, 20);
            this.password = pass;
            /* Padded, not filtered. addCharacters -- which the username still
               needs -- rewrites every non-alphanumeric character to an
               underscore, so a password with any punctuation in it left here as
               a different string than the player typed and could never match
               the digest the account manager stored. */
            pass = DataOperations.padCharacters(pass, 20);

            // Where the world number used to be turned into one of two fixed
            // ports. The caller has already resolved the address, so this only
            // records it -- Config stays the client-wide view of what is
            // connected, which is what the rest of the client reads.
            this.serverHost = host;
            this.worldBasePort = port - (world - 1);
            Config.SERVER_IP = host;
            Config.SERVER_PORT = port;
            this.theworld = world;
            Config.SERVER_WORLD = world;
            if (user.trim().length() == 0) {
               this.loginScreenPrint("You must enter both a username", "and a password - Please try again");
            } else {
               if (reconnecting) {
                  this.gameBoxPrint("Connection lost! Please wait...", "Attempting to re-establish");
               } else {
                  this.loginScreenPrint("Please wait...", "Connecting to server");
               }

               // Whatever this field pointed at is about to be unreachable.
               // Every rejected sign-in used to leave its socket and reader
               // thread behind here, so a few wrong passwords in a row left a
               // handful of live connections to the server with nothing on
               // this side still reading them.
               if (this.streamClass != null) {
                  this.streamClass.closeStream();
                  this.streamClass = null;
               }

               this.streamClass = new StreamClass(this.makeSocket(host, port), this);
               this.streamClass.maxPacketReadCount = maxPacketReadCount;
               long l = DataOperations.stringLength12ToLong(user);
               this.streamClass.createPacket(32);
               this.streamClass.addByte((int)(l >> 16 & 31L));
               this.streamClass.addString(this.getClass().getName().toUpperCase());
               this.streamClass.finalisePacket();
               long sessionID = this.streamClass.read8ByteLong();
               if (sessionID == 0L) {
                  this.loginScreenPrint("Login server offline.", "Please try again in a few mins");
               } else {
                  System.out.print("Session ID: " + sessionID);
                  int[] sessionRotationKeys = new int[]{
                     (int)(Math.random() * 9.9999999E7), (int)(Math.random() * 9.9999999E7), (int)(sessionID >> 32), (int)sessionID
                  };
                  DataEncryption dataEncryption = new DataEncryption(new byte[500]);
                  dataEncryption.offset = 0;
                  dataEncryption.add4ByteInt(sessionRotationKeys[0]);
                  dataEncryption.add4ByteInt(sessionRotationKeys[1]);
                  dataEncryption.add4ByteInt(sessionRotationKeys[2]);
                  dataEncryption.add4ByteInt(sessionRotationKeys[3]);
                  dataEncryption.add4ByteInt(0);
                  dataEncryption.addString(user);
                  dataEncryption.addString(pass);
                  dataEncryption.encryptPacketWithKeys(key, modulus);
                  this.streamClass.createPacket(0);
                  if (reconnecting) {
                     this.streamClass.addByte(1);
                  } else {
                     this.streamClass.addByte(0);
                  }

                  this.streamClass.add2ByteInt(clientVersion);
                  this.streamClass.addBytes(dataEncryption.packet, 0, dataEncryption.offset);
                  this.streamClass.finalisePacket();
                  int loginResponse = this.streamClass.readInputStream();
                  System.out.println(" - Login Response:" + loginResponse);
                  if (loginResponse == 99) {
                     this.reconnectTries = 0;
                     this.resetVars();
                  } else if (loginResponse == 0) {
                     this.reconnectTries = 0;
                     this.resetVars();
                  } else if (loginResponse == 1) {
                     this.reconnectTries = 0;
                  } else if (reconnecting) {
                     user = "";
                     pass = "";
                     this.resetIntVars();
                  } else if (loginResponse == -1) {
                     this.loginScreenPrint("Error unable to login.", "Server timed out");
                  } else if (loginResponse == 2) {
                     this.loginScreenPrint("Invalid username or password.", "Try again, or create a new account");
                  } else if (loginResponse == 3) {
                     this.loginScreenPrint("That username is already logged in.", "Wait 60 seconds then retry");
                  } else if (loginResponse == 4) {
                     this.loginScreenPrint("The client has been updated.", "Please download the newest one");
                  } else if (loginResponse == 5) {
                     this.loginScreenPrint("Error unable to login.", "Server rejected session");
                  } else if (loginResponse == 6) {
                     this.loginScreenPrint("Account disabled.", "Contact an admin for details");
                  } else if (loginResponse == 7) {
                     this.loginScreenPrint("Error - failed to decode profile.", "Contact an admin");
                  } else if (loginResponse == 8) {
                     this.loginScreenPrint("IP Already in use.", "You may only login once at a time");
                  } else if (loginResponse == 9) {
                     this.loginScreenPrint("Account already in use.", "You may only login to one character at a time");
                  } else {
                     this.loginScreenPrint("Error unable to login.", "Unrecognised response code");
                  }

                  /*
                   * 0 and 99 are a successful sign-in and 1 a successful
                   * reconnect; the stream stays and becomes the game session.
                   * Everything else is a refusal, and the connection has no
                   * further purpose -- close it rather than leaving it to the
                   * next login attempt or to the server's idle timeout.
                   */
                  if (loginResponse != 0 && loginResponse != 1 && loginResponse != 99) {
                     this.streamClass.closeStream();
                     this.streamClass = null;
                  }
               }
            }
         } catch (Exception var14) {
            System.out.println(String.valueOf(var14));
            if (this.reconnectTries > 0) {
               try {
                  Thread.sleep(5000L);
               } catch (Exception var12) {
               }

               this.reconnectTries--;
               this.login(this.username, this.password, this.theworld, reconnecting);
            }

            if (reconnecting) {
               this.username = "";
               this.password = "";
               this.theworld = 0;
               this.resetIntVars();
            } else {
               this.loginScreenPrint("Sorry! Unable to connect.", "Check internet settings or try another world");
            }
         }
      }
   }

   protected final void sendLogoutPacket() {
      if (this.streamClass != null) {
         try {
            this.streamClass.createPacket(39);
            this.streamClass.finalisePacket();
         } catch (IOException var2) {
         }
      }

      this.username = "";
      this.password = "";
      this.theworld = 0;
      this.resetIntVars();
   }

   protected void lostConnection() {
      System.out.println("Lost connection");
      this.reconnectTries = 10;
      this.login(this.username, this.password, this.theworld, true);
   }

   protected final void gameBoxPrint(String s, String s1) {
      Graphics g = this.getGraphics();
      Font font = new Font("Helvetica", 1, 15);
      /* Centre on the live canvas, not the 512x344 the box was born at --
         a resized window drew it off-centre. */
      int c = canvasWidth > 0 ? canvasWidth : 512;
      int c1 = canvasHeight > 0 ? canvasHeight : 344;
      g.setColor(Color.black);
      g.fillRect(c / 2 - 140, c1 / 2 - 25, 280, 50);
      g.setColor(Color.white);
      g.drawRect(c / 2 - 140, c1 / 2 - 25, 280, 50);
      this.drawString(g, s, font, c / 2, c1 / 2 - 10);
      this.drawString(g, s1, font, c / 2, c1 / 2 + 10);
   }

   protected final void sendPingPacketReadPacketData() {
      long l = System.currentTimeMillis();
      if (this.streamClass.containsData()) {
         this.lastPing = l;
      }

      if (l - this.lastPing > 5000L) {
         this.lastPing = l;
         this.streamClass.createPacket(5);
         this.streamClass.formatPacket();
      }

      try {
         this.streamClass.writePacket(20);
      } catch (IOException var4) {
         this.lostConnection();
         return;
      }

      int packetLength = this.streamClass.readPacket(this.packetData);
      if (packetLength > 0) {
         this.checkIncomingPacket(this.packetData[0] & 255, packetLength);
      }
   }

   protected final void checkIncomingPacket(int command, int length) {
      if (command == 48) {
         String s = new String(this.packetData, 1, length - 1);
         this.handleServerMessage(s);
      }

      if (command == 222) {
         this.sendLogoutPacket();
      }

      if (command == 136) {
         this.cantLogout();
      } else if (command == 249) {
         this.friendsCount = DataOperations.getUnsignedByte(this.packetData[1]);

         for (int k = 0; k < this.friendsCount; k++) {
            this.friendsListLongs[k] = DataOperations.getUnsigned8Bytes(this.packetData, 2 + k * 9);
            this.friendsListOnlineStatus[k] = DataOperations.getUnsignedByte(this.packetData[10 + k * 9]);
         }

         this.reOrderFriendsListByOnlineStatus();
      } else if (command == 25) {
         long friend = DataOperations.getUnsigned8Bytes(this.packetData, 1);
         int status = this.packetData[9] & 255;

         for (int i2 = 0; i2 < this.friendsCount; i2++) {
            if (this.friendsListLongs[i2] == friend) {
               if (this.friendsListOnlineStatus[i2] == 0 && status != 0) {
                  this.handleServerMessage("@pri@" + DataOperations.longToString(friend) + " has logged in");
               }

               if (this.friendsListOnlineStatus[i2] != 0 && status == 0) {
                  this.handleServerMessage("@pri@" + DataOperations.longToString(friend) + " has logged out");
               }

               this.friendsListOnlineStatus[i2] = status;
               int var7 = 0;
               this.reOrderFriendsListByOnlineStatus();
               return;
            }
         }

         this.friendsListLongs[this.friendsCount] = friend;
         this.friendsListOnlineStatus[this.friendsCount] = status;
         this.friendsCount++;
         this.reOrderFriendsListByOnlineStatus();
      } else if (command != 2) {
         if (command == 158) {
            this.blockChatMessages = this.packetData[1];
            this.blockPrivateMessages = this.packetData[2];
            this.blockTradeRequests = this.packetData[3];
            this.blockDuelRequests = this.packetData[4];
         } else if (command == 170) {
            long user = DataOperations.getUnsigned8Bytes(this.packetData, 1);
            String s1 = DataConversions.byteToString(this.packetData, 9, length - 9);
            this.handleServerMessage("@pri@" + DataOperations.longToString(user) + " tells you: " + s1);
         } else {
            this.handleIncomingPacket(command, length, this.packetData);
         }
      } else {
         this.ignoreListCount = DataOperations.getUnsignedByte(this.packetData[1]);

         for (int i1 = 0; i1 < this.ignoreListCount; i1++) {
            this.ignoreListLongs[i1] = DataOperations.getUnsigned8Bytes(this.packetData, 2 + i1 * 8);
         }
      }
   }

   private final void reOrderFriendsListByOnlineStatus() {
      boolean flag = true;

      while (flag) {
         flag = false;

         for (int i = 0; i < this.friendsCount - 1; i++) {
            if (this.friendsListOnlineStatus[i] < this.friendsListOnlineStatus[i + 1]) {
               int j = this.friendsListOnlineStatus[i];
               this.friendsListOnlineStatus[i] = this.friendsListOnlineStatus[i + 1];
               this.friendsListOnlineStatus[i + 1] = j;
               long l = this.friendsListLongs[i];
               this.friendsListLongs[i] = this.friendsListLongs[i + 1];
               this.friendsListLongs[i + 1] = l;
               flag = true;
            }
         }
      }
   }

   protected final void sendUpdatedPrivacyInfo(int chatMessages, int privateMessages, int tradeRequests, int duelRequests) {
      this.streamClass.createPacket(176);
      this.streamClass.addByte(chatMessages);
      this.streamClass.addByte(privateMessages);
      this.streamClass.addByte(tradeRequests);
      this.streamClass.addByte(duelRequests);
      this.streamClass.formatPacket();
   }

   protected final void addToIgnoreList(String s) {
      long l = DataOperations.stringLength12ToLong(s);
      this.streamClass.createPacket(25);
      this.streamClass.addTwo4ByteInts(l);
      this.streamClass.formatPacket();

      for (int i = 0; i < this.ignoreListCount; i++) {
         if (this.ignoreListLongs[i] == l) {
            return;
         }
      }

      if (this.ignoreListCount < this.ignoreListLongs.length - 1) {
         this.ignoreListLongs[this.ignoreListCount++] = l;
      }
   }

   protected final void removeFromIgnoreList(long l) {
      this.streamClass.createPacket(108);
      this.streamClass.addTwo4ByteInts(l);
      this.streamClass.formatPacket();

      for (int i = 0; i < this.ignoreListCount; i++) {
         if (this.ignoreListLongs[i] == l) {
            this.ignoreListCount--;

            for (int j = i; j < this.ignoreListCount; j++) {
               this.ignoreListLongs[j] = this.ignoreListLongs[j + 1];
            }

            return;
         }
      }
   }

   protected final void addToFriendsList(String s) {
      this.streamClass.createPacket(168);
      this.streamClass.addTwo4ByteInts(DataOperations.stringLength12ToLong(s));
      this.streamClass.formatPacket();
      long l = DataOperations.stringLength12ToLong(s);

      for (int i = 0; i < this.friendsCount; i++) {
         if (this.friendsListLongs[i] == l) {
            return;
         }
      }

      if (this.friendsCount < this.friendsListLongs.length - 1) {
         this.friendsListLongs[this.friendsCount] = l;
         this.friendsListOnlineStatus[this.friendsCount] = 0;
         this.friendsCount++;
      }
   }

   protected final void removeFromFriends(long l) {
      this.streamClass.createPacket(52);
      this.streamClass.addTwo4ByteInts(l);
      this.streamClass.formatPacket();

      for (int i = 0; i < this.friendsCount; i++) {
         if (this.friendsListLongs[i] == l) {
            this.friendsCount--;

            for (int j = i; j < this.friendsCount; j++) {
               this.friendsListLongs[j] = this.friendsListLongs[j + 1];
               this.friendsListOnlineStatus[j] = this.friendsListOnlineStatus[j + 1];
            }
            break;
         }
      }

      this.handleServerMessage("@pri@" + DataOperations.longToString(l) + " has been removed from your friends list");
   }

   protected final void sendPrivateMessage(long user, byte[] message, int messageLength) {
      this.streamClass.createPacket(254);
      this.streamClass.addTwo4ByteInts(user);
      this.streamClass.addBytes(message, 0, messageLength);
      this.streamClass.formatPacket();
   }

   protected final void sendChatMessage(byte[] abyte0, int i) {
      this.streamClass.createPacket(145);
      this.streamClass.addBytes(abyte0, 0, i);
      this.streamClass.formatPacket();
   }

   protected final void sendChatString(String s) {
      this.streamClass.createPacket(90);
      this.streamClass.addString(s);
      this.streamClass.formatPacket();
   }

   protected abstract void loginScreenPrint(String var1, String var2);

   protected abstract void resetVars();

   protected abstract void resetIntVars();

   protected abstract void cantLogout();

   protected abstract void handleIncomingPacket(int var1, int var2, byte[] var3);

   protected abstract void handleServerMessage(String var1);
}
