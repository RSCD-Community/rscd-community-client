package org.rscdaemon.client;

/*
 * Jagex's menu palette is twelve colours built through
 * convertRGBToLongWithModifier. RSCD kept the first eight and dropped the last
 * four -- the ones that draw a panel's fill and its bevel -- replacing them
 * with three hardcoded literals: 4930859 (75,61,43), 3812378 (58,44,26) and
 * 2760982 (42,33,22). All three are brown, which is why the login screen's
 * boxes and buttons were brown; vanilla's are a blue-grey.
 *
 * The four are restored below in Jagex's own order and put back at every site
 * that used a literal. Note that RSCD had also collapsed two distinct colours
 * into one: vanilla bevels the top-left edges with bevelColourLight and the
 * bottom-right with bevelColourDark, and 3812378 was standing in for both.
 */
public class Menu {
   protected GameImage gameImage;
   int menuObjectCount;
   int unusedConstructorInt;
   public boolean[] menuObjectCanAcceptActions;
   public boolean[] listScrollbarGrabbed;
   public boolean[] menuObjectMaskText;
   public boolean[] menuObjectHasAction;
   public int[] menuListScrollOffset;
   public int[] menuListTextCount;
   public int[] menuObjectSelected;
   public int[] menuListEntryMouseOver;
   boolean[] menuObjectColourMask;
   int[] menuObjectX;
   int[] menuObjectY;
   int[] menuObjectType;
   int[] menuObjectWidth;
   int[] menuObjectHeight;
   int[] handleMaxTextLength;
   int[] menuObjectTextType;
   String[] menuObjectText;
   String[][] menuListText;
   int mouseX;
   int mouseY;
   int lastMouseButton;
   int mouseButton;
   int currentFocusHandle = -1;
   int mouseClicksConsecutive;
   int scrollbarTrackLight;
   int scrollbarTrackDark;
   int scrollbarHandleLight;
   int scrollbarHandleMid;
   int scrollbarHandleDark;
   int roundedBoxEdgeOuter;
   int roundedBoxEdgeMid;
   int roundedBoxEdgeInner;
   /* The restored four: panel light and dark ends plus the two bevel
      colours. Light draws the top-left edges and the gradient's bright end,
      dark the bottom-right edges and the gradient's deep end. */
   int panelColourLight;
   int bevelColourLight;
   int bevelColourDark;
   int panelColourDark;
   public boolean redStringColour = true;
   public static boolean drawBackgroundTexture = true;
   public static int redModifier = 114;
   public static int greeModifier = 114;
   public static int blueModifier = 176;
   public static int listLineHeightReduction;

   public Menu(GameImage gi, int i) {
      this.gameImage = gi;
      this.unusedConstructorInt = i;
      this.menuObjectCanAcceptActions = new boolean[i];
      this.listScrollbarGrabbed = new boolean[i];
      this.menuObjectMaskText = new boolean[i];
      this.menuObjectHasAction = new boolean[i];
      this.menuObjectColourMask = new boolean[i];
      this.menuListScrollOffset = new int[i];
      this.menuListTextCount = new int[i];
      this.menuObjectSelected = new int[i];
      this.menuListEntryMouseOver = new int[i];
      this.menuObjectX = new int[i];
      this.menuObjectY = new int[i];
      this.menuObjectType = new int[i];
      this.menuObjectWidth = new int[i];
      this.menuObjectHeight = new int[i];
      this.handleMaxTextLength = new int[i];
      this.menuObjectTextType = new int[i];
      this.menuObjectText = new String[i];
      this.menuListText = new String[i][];
      this.scrollbarTrackLight = this.convertRGBToLongWithModifier(114, 114, 176);
      this.scrollbarTrackDark = this.convertRGBToLongWithModifier(14, 14, 62);
      this.scrollbarHandleLight = this.convertRGBToLongWithModifier(200, 208, 232);
      this.scrollbarHandleMid = this.convertRGBToLongWithModifier(96, 129, 184);
      this.scrollbarHandleDark = this.convertRGBToLongWithModifier(53, 95, 115);
      this.roundedBoxEdgeOuter = this.convertRGBToLongWithModifier(117, 142, 171);
      this.roundedBoxEdgeMid = this.convertRGBToLongWithModifier(98, 122, 158);
      this.roundedBoxEdgeInner = this.convertRGBToLongWithModifier(86, 100, 136);
      this.panelColourLight = this.convertRGBToLongWithModifier(135, 146, 179);
      this.bevelColourLight = this.convertRGBToLongWithModifier(97, 112, 151);
      this.bevelColourDark = this.convertRGBToLongWithModifier(88, 102, 136);
      this.panelColourDark = this.convertRGBToLongWithModifier(84, 93, 120);
   }

   public int convertRGBToLongWithModifier(int red, int green, int blue) {
      return GameImage.convertRGBToLong(redModifier * red / 114, greeModifier * green / 114, blueModifier * blue / 176);
   }

   public void updateActions(int x, int y, int lastMouseDownButton, int mouseDownButton) {
      this.mouseX = x;
      this.mouseY = y;
      this.mouseButton = mouseDownButton;
      if (lastMouseDownButton != 0) {
         this.lastMouseButton = lastMouseDownButton;
      }

      if (lastMouseDownButton == 1) {
         for (int menuObject = 0; menuObject < this.menuObjectCount; menuObject++) {
            if (this.menuObjectCanAcceptActions[menuObject]
               && this.menuObjectType[menuObject] == 10
               && this.mouseX >= this.menuObjectX[menuObject]
               && this.mouseY >= this.menuObjectY[menuObject]
               && this.mouseX <= this.menuObjectX[menuObject] + this.menuObjectWidth[menuObject]
               && this.mouseY <= this.menuObjectY[menuObject] + this.menuObjectHeight[menuObject]) {
               this.menuObjectHasAction[menuObject] = true;
            }

            if (this.menuObjectCanAcceptActions[menuObject]
               && this.menuObjectType[menuObject] == 14
               && this.mouseX >= this.menuObjectX[menuObject]
               && this.mouseY >= this.menuObjectY[menuObject]
               && this.mouseX <= this.menuObjectX[menuObject] + this.menuObjectWidth[menuObject]
               && this.mouseY <= this.menuObjectY[menuObject] + this.menuObjectHeight[menuObject]) {
               this.menuObjectSelected[menuObject] = 1 - this.menuObjectSelected[menuObject];
            }
         }
      }

      if (mouseDownButton == 1) {
         this.mouseClicksConsecutive++;
      } else {
         this.mouseClicksConsecutive = 0;
      }

      if (lastMouseDownButton == 1 || this.mouseClicksConsecutive > 20) {
         for (int j1 = 0; j1 < this.menuObjectCount; j1++) {
            if (this.menuObjectCanAcceptActions[j1]
               && this.menuObjectType[j1] == 15
               && this.mouseX >= this.menuObjectX[j1]
               && this.mouseY >= this.menuObjectY[j1]
               && this.mouseX <= this.menuObjectX[j1] + this.menuObjectWidth[j1]
               && this.mouseY <= this.menuObjectY[j1] + this.menuObjectHeight[j1]) {
               this.menuObjectHasAction[j1] = true;
            }
         }

         this.mouseClicksConsecutive -= 5;
      }
   }

   public boolean hasActivated(int i) {
      if (this.menuObjectCanAcceptActions[i] && this.menuObjectHasAction[i]) {
         this.menuObjectHasAction[i] = false;
         return true;
      } else {
         return false;
      }
   }

   public void keyDown(int key) {
      if (key != 0) {
         if (this.currentFocusHandle != -1 && this.menuObjectText[this.currentFocusHandle] != null && this.menuObjectCanAcceptActions[this.currentFocusHandle]) {
            int textLength = this.menuObjectText[this.currentFocusHandle].length();
            if (key == 8 && textLength > 0) {
               this.menuObjectText[this.currentFocusHandle] = this.menuObjectText[this.currentFocusHandle].substring(0, textLength - 1);
            }

            if ((key == 10 || key == 13) && textLength > 0) {
               this.menuObjectHasAction[this.currentFocusHandle] = true;
            }

            String validCharSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"£$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";
            if (textLength < this.handleMaxTextLength[this.currentFocusHandle]) {
               for (int k = 0; k < validCharSet.length(); k++) {
                  if (key == validCharSet.charAt(k)) {
                     this.menuObjectText[this.currentFocusHandle] = this.menuObjectText[this.currentFocusHandle] + (char)key;
                  }
               }
            }

            if (key == 9) {
               do {
                  this.currentFocusHandle = (this.currentFocusHandle + 1) % this.menuObjectCount;
               } while (this.menuObjectType[this.currentFocusHandle] != 5 && this.menuObjectType[this.currentFocusHandle] != 6);
            }
         }
      }
   }

   public void drawMenu() {
      for (int menuObject = 0; menuObject < this.menuObjectCount; menuObject++) {
         if (this.menuObjectCanAcceptActions[menuObject]) {
            if (this.menuObjectType[menuObject] == 0) {
               this.drawTextAddHeight(
                  menuObject, this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectText[menuObject], this.menuObjectTextType[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 1) {
               this.drawTextAddHeight(
                  menuObject,
                  this.menuObjectX[menuObject] - this.gameImage.textWidth(this.menuObjectText[menuObject], this.menuObjectTextType[menuObject]) / 2,
                  this.menuObjectY[menuObject],
                  this.menuObjectText[menuObject],
                  this.menuObjectTextType[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 2) {
               this.drawPanelBox(this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectWidth[menuObject], this.menuObjectHeight[menuObject]);
            } else if (this.menuObjectType[menuObject] == 3) {
               this.drawHorizontalLine(this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectWidth[menuObject]);
            } else if (this.menuObjectType[menuObject] == 4) {
               this.drawTextList(
                  menuObject,
                  this.menuObjectX[menuObject],
                  this.menuObjectY[menuObject],
                  this.menuObjectWidth[menuObject],
                  this.menuObjectHeight[menuObject],
                  this.menuObjectTextType[menuObject],
                  this.menuListText[menuObject],
                  this.menuListTextCount[menuObject],
                  this.menuListScrollOffset[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 5 || this.menuObjectType[menuObject] == 6) {
               this.drawTextInput(
                  menuObject,
                  this.menuObjectX[menuObject],
                  this.menuObjectY[menuObject],
                  this.menuObjectWidth[menuObject],
                  this.menuObjectHeight[menuObject],
                  this.menuObjectText[menuObject],
                  this.menuObjectTextType[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 7) {
               this.drawOptionListHoriz(
                  menuObject, this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectTextType[menuObject], this.menuListText[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 8) {
               this.drawOptionListVert(
                  menuObject, this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectTextType[menuObject], this.menuListText[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 9) {
               this.drawInteractiveTextList(
                  menuObject,
                  this.menuObjectX[menuObject],
                  this.menuObjectY[menuObject],
                  this.menuObjectWidth[menuObject],
                  this.menuObjectHeight[menuObject],
                  this.menuObjectTextType[menuObject],
                  this.menuListText[menuObject],
                  this.menuListTextCount[menuObject],
                  this.menuListScrollOffset[menuObject]
               );
            } else if (this.menuObjectType[menuObject] == 11) {
               this.drawRoundedBox(this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectWidth[menuObject], this.menuObjectHeight[menuObject]);
            } else if (this.menuObjectType[menuObject] == 12) {
               this.drawPicture(this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectTextType[menuObject]);
            } else if (this.menuObjectType[menuObject] == 14) {
               this.drawCheckbox(
                  menuObject, this.menuObjectX[menuObject], this.menuObjectY[menuObject], this.menuObjectWidth[menuObject], this.menuObjectHeight[menuObject]
               );
            }
         }
      }

      this.lastMouseButton = 0;
   }

   protected void drawCheckbox(int i, int j, int k, int l, int i1) {
      this.gameImage.drawBox(j, k, l, i1, 16777215);
      this.gameImage.drawLineX(j, k, l, this.panelColourLight);
      this.gameImage.drawLineY(j, k, i1, this.panelColourLight);
      this.gameImage.drawLineX(j, k + i1 - 1, l, this.panelColourDark);
      this.gameImage.drawLineY(j + l - 1, k, i1, this.panelColourDark);
      if (this.menuObjectSelected[i] == 1) {
         for (int j1 = 0; j1 < i1; j1++) {
            this.gameImage.drawLineX(j + j1, k + j1, 1, 0);
            this.gameImage.drawLineX(j + l - 1 - j1, k + j1, 1, 0);
         }
      }
   }

   protected void drawTextAddHeight(int menuObject, int x, int y, String text, int type) {
      int i1 = y + this.gameImage.messageFontHeight(type) / 3;
      this.drawTextWithMask(menuObject, x, i1, text, type);
   }

   protected void drawTextWithMask(int menuObject, int x, int y, String text, int type) {
      int color;
      if (this.menuObjectColourMask[menuObject]) {
         color = 16777215;
      } else {
         color = 0;
      }

      this.gameImage.drawString(text, x, y, type, color);
   }

   protected void drawTextInput(int i, int j, int k, int l, int i1, String s, int j1) {
      if (this.menuObjectMaskText[i]) {
         int k1 = s.length();
         s = "";

         for (int i2 = 0; i2 < k1; i2++) {
            s = s + "X";
         }
      }

      if (this.menuObjectType[i] == 5) {
         if (this.lastMouseButton == 1 && this.mouseX >= j && this.mouseY >= k - i1 / 2 && this.mouseX <= j + l && this.mouseY <= k + i1 / 2) {
            this.currentFocusHandle = i;
         }
      } else if (this.menuObjectType[i] == 6) {
         if (this.lastMouseButton == 1 && this.mouseX >= j - l / 2 && this.mouseY >= k - i1 / 2 && this.mouseX <= j + l / 2 && this.mouseY <= k + i1 / 2) {
            this.currentFocusHandle = i;
         }

         j -= this.gameImage.textWidth(s, j1) / 2;
      }

      if (this.currentFocusHandle == i) {
         s = s + "*";
      }

      int l1 = k + this.gameImage.messageFontHeight(j1) / 3;
      this.drawTextWithMask(i, j, l1, s, j1);
   }

   public void drawPanelBox(int i, int j, int k, int l) {
      this.gameImage.setDimensions(i, j, i + k, j + l);
      this.gameImage.drawGradientBox(i, j, k, l, this.panelColourDark, this.panelColourLight);
      if (drawBackgroundTexture) {
         for (int i1 = i - (j & 63); i1 < i + k; i1 += 128) {
            for (int j1 = j - (j & 31); j1 < j + l; j1 += 128) {
               this.gameImage.drawSpriteAlpha(i1, j1, 2106, 128);
            }
         }
      }

      /* Top and left are lit, bottom and right are shadowed -- and the third
         line of each pair is a distinct colour, which is the pair RSCD had
         collapsed into one brown. */
      this.gameImage.drawLineX(i, j, k, this.panelColourLight);
      this.gameImage.drawLineX(i + 1, j + 1, k - 2, this.panelColourLight);
      this.gameImage.drawLineX(i + 2, j + 2, k - 4, this.bevelColourLight);
      this.gameImage.drawLineY(i, j, l, this.panelColourLight);
      this.gameImage.drawLineY(i + 1, j + 1, l - 2, this.panelColourLight);
      this.gameImage.drawLineY(i + 2, j + 2, l - 4, this.bevelColourLight);
      this.gameImage.drawLineX(i, j + l - 1, k, this.panelColourDark);
      this.gameImage.drawLineX(i + 1, j + l - 2, k - 2, this.panelColourDark);
      this.gameImage.drawLineX(i + 2, j + l - 3, k - 4, this.bevelColourDark);
      this.gameImage.drawLineY(i + k - 1, j, l, this.panelColourDark);
      this.gameImage.drawLineY(i + k - 2, j + 1, l - 2, this.panelColourDark);
      this.gameImage.drawLineY(i + k - 3, j + 2, l - 4, this.bevelColourDark);
      this.gameImage.resetDimensions();
   }

   public void drawRoundedBox(int i, int j, int k, int l) {
      this.gameImage.drawBox(i, j, k, l, 0);
      this.gameImage.drawBoxEdge(i, j, k, l, this.roundedBoxEdgeOuter);
      this.gameImage.drawBoxEdge(i + 1, j + 1, k - 2, l - 2, this.roundedBoxEdgeMid);
      this.gameImage.drawBoxEdge(i + 2, j + 2, k - 4, l - 4, this.roundedBoxEdgeInner);
      this.gameImage.drawPicture(i, j, 2102);
      this.gameImage.drawPicture(i + k - 7, j, 2103);
      this.gameImage.drawPicture(i, j + l - 7, 2104);
      this.gameImage.drawPicture(i + k - 7, j + l - 7, 2105);
   }

   protected void drawPicture(int i, int j, int k) {
      this.gameImage.drawPicture(i, j, k);
   }

   protected void drawHorizontalLine(int i, int j, int k) {
      this.gameImage.drawLineX(i, j, k, 0);
   }

   /* The scrollbar lives in the rightmost 12px of the list: a 12px arrow box
      at each end, the draggable thumb between them (a drag that started on
      the thumb keeps hold 12px either side of the gutter). The track
      interior is height - 27: the two arrows plus the frame lines. */
   protected void drawTextList(int i, int j, int k, int l, int i1, int j1, String[] as, int k1, int l1) {
      int i2 = i1 / this.gameImage.messageFontHeight(j1);
      if (l1 > k1 - i2) {
         l1 = k1 - i2;
      }

      if (l1 < 0) {
         l1 = 0;
      }

      this.menuListScrollOffset[i] = l1;
      if (i2 < k1) {
         int j2 = j + l - 12;
         int l2 = (i1 - 27) * i2 / k1;
         if (l2 < 6) {
            l2 = 6;
         }

         int j3 = (i1 - 27 - l2) * l1 / (k1 - i2);
         if (this.mouseButton == 1 && this.mouseX >= j2 && this.mouseX <= j2 + 12) {
            if (this.mouseY > k && this.mouseY < k + 12 && l1 > 0) {
               l1--;
            }

            if (this.mouseY > k + i1 - 12 && this.mouseY < k + i1 && l1 < k1 - i2) {
               l1++;
            }

            this.menuListScrollOffset[i] = l1;
         }

         if (this.mouseButton == 1
            && (this.mouseX >= j2 && this.mouseX <= j2 + 12 || this.mouseX >= j2 - 12 && this.mouseX <= j2 + 24 && this.listScrollbarGrabbed[i])) {
            if (this.mouseY > k + 12 && this.mouseY < k + i1 - 12) {
               this.listScrollbarGrabbed[i] = true;
               int l3 = this.mouseY - k - 12 - l2 / 2;
               l1 = l3 * k1 / (i1 - 24);
               if (l1 > k1 - i2) {
                  l1 = k1 - i2;
               }

               if (l1 < 0) {
                  l1 = 0;
               }

               this.menuListScrollOffset[i] = l1;
            }
         } else {
            this.listScrollbarGrabbed[i] = false;
         }

         j3 = (i1 - 27 - l2) * l1 / (k1 - i2);
         this.drawScrollbar(j, k, l, i1, j3, l2);
      }

      int k2 = i1 - i2 * this.gameImage.messageFontHeight(j1);
      int i3 = k + this.gameImage.messageFontHeight(j1) * 5 / 6 + k2 / 2;

      for (int k3 = l1; k3 < k1; k3++) {
         this.drawTextWithMask(i, j + 2, i3, as[k3], j1);
         i3 += this.gameImage.messageFontHeight(j1) - listLineHeightReduction;
         if (i3 >= k + i1) {
            return;
         }
      }
   }

   protected void drawScrollbar(int i, int j, int k, int l, int i1, int j1) {
      int k1 = i + k - 12;
      this.gameImage.drawBoxEdge(k1, j, 12, l, 0);
      this.gameImage.drawPicture(k1 + 1, j + 1, 2100);
      this.gameImage.drawPicture(k1 + 1, j + l - 12, 2101);
      this.gameImage.drawLineX(k1, j + 13, 12, 0);
      this.gameImage.drawLineX(k1, j + l - 13, 12, 0);
      this.gameImage.drawGradientBox(k1 + 1, j + 14, 11, l - 27, this.scrollbarTrackLight, this.scrollbarTrackDark);
      this.gameImage.drawBox(k1 + 3, i1 + j + 14, 7, j1, this.scrollbarHandleMid);
      this.gameImage.drawLineY(k1 + 2, i1 + j + 14, j1, this.scrollbarHandleLight);
      this.gameImage.drawLineY(k1 + 2 + 8, i1 + j + 14, j1, this.scrollbarHandleDark);
   }

   protected void drawOptionListHoriz(int i, int j, int k, int l, String[] as) {
      int i1 = 0;
      int j1 = as.length;

      for (int k1 = 0; k1 < j1; k1++) {
         i1 += this.gameImage.textWidth(as[k1], l);
         if (k1 < j1 - 1) {
            i1 += this.gameImage.textWidth("  ", l);
         }
      }

      int l1 = j - i1 / 2;
      int i2 = k + this.gameImage.messageFontHeight(l) / 3;

      for (int j2 = 0; j2 < j1; j2++) {
         int k2;
         if (this.menuObjectColourMask[i]) {
            k2 = 16777215;
         } else {
            k2 = 0;
         }

         if (this.mouseX >= l1
            && this.mouseX <= l1 + this.gameImage.textWidth(as[j2], l)
            && this.mouseY <= i2
            && this.mouseY > i2 - this.gameImage.messageFontHeight(l)) {
            if (this.menuObjectColourMask[i]) {
               k2 = 8421504;
            } else {
               k2 = 16777215;
            }

            if (this.lastMouseButton == 1) {
               this.menuObjectSelected[i] = j2;
               this.menuObjectHasAction[i] = true;
            }
         }

         if (this.menuObjectSelected[i] == j2) {
            if (this.menuObjectColourMask[i]) {
               k2 = 16711680;
            } else {
               k2 = 12582912;
            }
         }

         this.gameImage.drawString(as[j2], l1, i2, l, k2);
         l1 += this.gameImage.textWidth(as[j2] + "  ", l);
      }
   }

   protected void drawOptionListVert(int i, int j, int k, int l, String[] as) {
      int i1 = as.length;
      int j1 = k - this.gameImage.messageFontHeight(l) * (i1 - 1) / 2;

      for (int k1 = 0; k1 < i1; k1++) {
         int l1;
         if (this.menuObjectColourMask[i]) {
            l1 = 16777215;
         } else {
            l1 = 0;
         }

         int i2 = this.gameImage.textWidth(as[k1], l);
         if (this.mouseX >= j - i2 / 2 && this.mouseX <= j + i2 / 2 && this.mouseY - 2 <= j1 && this.mouseY - 2 > j1 - this.gameImage.messageFontHeight(l)) {
            if (this.menuObjectColourMask[i]) {
               l1 = 8421504;
            } else {
               l1 = 16777215;
            }

            if (this.lastMouseButton == 1) {
               this.menuObjectSelected[i] = k1;
               this.menuObjectHasAction[i] = true;
            }
         }

         if (this.menuObjectSelected[i] == k1) {
            if (this.menuObjectColourMask[i]) {
               l1 = 16711680;
            } else {
               l1 = 12582912;
            }
         }

         this.gameImage.drawString(as[k1], j - i2 / 2, j1, l, l1);
         j1 += this.gameImage.messageFontHeight(l);
      }
   }

   protected void drawInteractiveTextList(int i, int j, int k, int l, int i1, int j1, String[] as, int k1, int l1) {
      int i2 = i1 / this.gameImage.messageFontHeight(j1);
      if (i2 < k1) {
         int j2 = j + l - 12;
         int l2 = (i1 - 27) * i2 / k1;
         if (l2 < 6) {
            l2 = 6;
         }

         int j3 = (i1 - 27 - l2) * l1 / (k1 - i2);
         if (this.mouseButton == 1 && this.mouseX >= j2 && this.mouseX <= j2 + 12) {
            if (this.mouseY > k && this.mouseY < k + 12 && l1 > 0) {
               l1--;
            }

            if (this.mouseY > k + i1 - 12 && this.mouseY < k + i1 && l1 < k1 - i2) {
               l1++;
            }

            this.menuListScrollOffset[i] = l1;
         }

         if (this.mouseButton == 1
            && (this.mouseX >= j2 && this.mouseX <= j2 + 12 || this.mouseX >= j2 - 12 && this.mouseX <= j2 + 24 && this.listScrollbarGrabbed[i])) {
            if (this.mouseY > k + 12 && this.mouseY < k + i1 - 12) {
               this.listScrollbarGrabbed[i] = true;
               int l3 = this.mouseY - k - 12 - l2 / 2;
               l1 = l3 * k1 / (i1 - 24);
               if (l1 < 0) {
                  l1 = 0;
               }

               if (l1 > k1 - i2) {
                  l1 = k1 - i2;
               }

               this.menuListScrollOffset[i] = l1;
            }
         } else {
            this.listScrollbarGrabbed[i] = false;
         }

         j3 = (i1 - 27 - l2) * l1 / (k1 - i2);
         this.drawScrollbar(j, k, l, i1, j3, l2);
      } else {
         l1 = 0;
         this.menuListScrollOffset[i] = 0;
      }

      this.menuListEntryMouseOver[i] = -1;
      int k2 = i1 - i2 * this.gameImage.messageFontHeight(j1);
      int i3 = k + this.gameImage.messageFontHeight(j1) * 5 / 6 + k2 / 2;

      for (int k3 = l1; k3 < k1; k3++) {
         int i4;
         if (this.menuObjectColourMask[i]) {
            i4 = 16777215;
         } else {
            i4 = 0;
         }

         if (this.mouseX >= j + 2
            && this.mouseX <= j + 2 + this.gameImage.textWidth(as[k3], j1)
            && this.mouseY - 2 <= i3
            && this.mouseY - 2 > i3 - this.gameImage.messageFontHeight(j1)) {
            if (this.menuObjectColourMask[i]) {
               i4 = 8421504;
            } else {
               i4 = 16777215;
            }

            this.menuListEntryMouseOver[i] = k3;
            if (this.lastMouseButton == 1) {
               this.menuObjectSelected[i] = k3;
               this.menuObjectHasAction[i] = true;
            }
         }

         if (this.menuObjectSelected[i] == k3 && this.redStringColour) {
            i4 = 16711680;
         }

         this.gameImage.drawString(as[k3], j + 2, i3, j1, i4);
         i3 += this.gameImage.messageFontHeight(j1);
         if (i3 >= k + i1) {
            return;
         }
      }
   }

   public int drawText(int x, int y, String s, int type, boolean flag) {
      this.menuObjectType[this.menuObjectCount] = 1;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectTextType[this.menuObjectCount] = type;
      this.menuObjectColourMask[this.menuObjectCount] = flag;
      this.menuObjectX[this.menuObjectCount] = x;
      this.menuObjectY[this.menuObjectCount] = y;
      this.menuObjectText[this.menuObjectCount] = s;
      return this.menuObjectCount++;
   }

   public int drawBox(int i, int j, int k, int l) {
      this.menuObjectType[this.menuObjectCount] = 2;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectX[this.menuObjectCount] = i - k / 2;
      this.menuObjectY[this.menuObjectCount] = j - l / 2;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      return this.menuObjectCount++;
   }

   public int makeRoundedBox(int i, int j, int k, int l) {
      this.menuObjectType[this.menuObjectCount] = 11;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectX[this.menuObjectCount] = i - k / 2;
      this.menuObjectY[this.menuObjectCount] = j - l / 2;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      return this.menuObjectCount++;
   }

   public int makePicture(int i, int j, int k) {
      int l = this.gameImage.sprites[k].getWidth();
      int i1 = this.gameImage.sprites[k].getHeight();
      this.menuObjectType[this.menuObjectCount] = 12;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectX[this.menuObjectCount] = i - l / 2;
      this.menuObjectY[this.menuObjectCount] = j - i1 / 2;
      this.menuObjectWidth[this.menuObjectCount] = l;
      this.menuObjectHeight[this.menuObjectCount] = i1;
      this.menuObjectTextType[this.menuObjectCount] = k;
      return this.menuObjectCount++;
   }

   public int makeTextList(int i, int j, int k, int l, int i1, int j1, boolean flag) {
      this.menuObjectType[this.menuObjectCount] = 4;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectX[this.menuObjectCount] = i;
      this.menuObjectY[this.menuObjectCount] = j;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      this.menuObjectColourMask[this.menuObjectCount] = flag;
      this.menuObjectTextType[this.menuObjectCount] = i1;
      this.handleMaxTextLength[this.menuObjectCount] = j1;
      this.menuListTextCount[this.menuObjectCount] = 0;
      this.menuListScrollOffset[this.menuObjectCount] = 0;
      this.menuListText[this.menuObjectCount] = new String[j1];
      return this.menuObjectCount++;
   }

   public int makeTextInput(int i, int j, int k, int l, int i1, int j1, boolean flag, boolean flag1) {
      this.menuObjectType[this.menuObjectCount] = 5;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectMaskText[this.menuObjectCount] = flag;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectTextType[this.menuObjectCount] = i1;
      this.menuObjectColourMask[this.menuObjectCount] = flag1;
      this.menuObjectX[this.menuObjectCount] = i;
      this.menuObjectY[this.menuObjectCount] = j;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      this.handleMaxTextLength[this.menuObjectCount] = j1;
      this.menuObjectText[this.menuObjectCount] = "";
      return this.menuObjectCount++;
   }

   public int makeTextBox(int i, int j, int k, int l, int i1, int j1, boolean flag, boolean flag1) {
      this.menuObjectType[this.menuObjectCount] = 6;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectMaskText[this.menuObjectCount] = flag;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectTextType[this.menuObjectCount] = i1;
      this.menuObjectColourMask[this.menuObjectCount] = flag1;
      this.menuObjectX[this.menuObjectCount] = i;
      this.menuObjectY[this.menuObjectCount] = j;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      this.handleMaxTextLength[this.menuObjectCount] = j1;
      this.menuObjectText[this.menuObjectCount] = "";
      return this.menuObjectCount++;
   }

   public int makeInteractiveTextList(int i, int j, int k, int l, int i1, int j1, boolean flag) {
      this.menuObjectType[this.menuObjectCount] = 9;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectTextType[this.menuObjectCount] = i1;
      this.menuObjectColourMask[this.menuObjectCount] = flag;
      this.menuObjectX[this.menuObjectCount] = i;
      this.menuObjectY[this.menuObjectCount] = j;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      this.handleMaxTextLength[this.menuObjectCount] = j1;
      this.menuListText[this.menuObjectCount] = new String[j1];
      this.menuListTextCount[this.menuObjectCount] = 0;
      this.menuListScrollOffset[this.menuObjectCount] = 0;
      this.menuObjectSelected[this.menuObjectCount] = -1;
      this.menuListEntryMouseOver[this.menuObjectCount] = -1;
      return this.menuObjectCount++;
   }

   public int makeButton(int i, int j, int k, int l) {
      this.menuObjectType[this.menuObjectCount] = 10;
      this.menuObjectCanAcceptActions[this.menuObjectCount] = true;
      this.menuObjectHasAction[this.menuObjectCount] = false;
      this.menuObjectX[this.menuObjectCount] = i - k / 2;
      this.menuObjectY[this.menuObjectCount] = j - l / 2;
      this.menuObjectWidth[this.menuObjectCount] = k;
      this.menuObjectHeight[this.menuObjectCount] = l;
      return this.menuObjectCount++;
   }

   public void resetListTextCount(int menuHandle) {
      this.menuListTextCount[menuHandle] = 0;
   }

   public void setListScroll(int i, int base) {
      this.menuListScrollOffset[i] = base;
      this.menuListEntryMouseOver[i] = -1;
   }

   public void drawMenuListText(int menuHandle, int index, String text) {
      this.menuListText[menuHandle][index] = text;
      if (index + 1 > this.menuListTextCount[menuHandle]) {
         this.menuListTextCount[menuHandle] = index + 1;
      }
   }

   public void addString(int i, String s, boolean flag) {
      int j = this.menuListTextCount[i]++;
      if (j >= this.handleMaxTextLength[i]) {
         j--;
         this.menuListTextCount[i]--;

         for (int k = 0; k < j; k++) {
            this.menuListText[i][k] = this.menuListText[i][k + 1];
         }
      }

      this.menuListText[i][j] = s;
      if (flag) {
         this.menuListScrollOffset[i] = 999999;
      }
   }

   public void updateText(int i, String s) {
      this.menuObjectText[i] = s;
   }

   public String getText(int i) {
      return this.menuObjectText[i] == null ? "null" : this.menuObjectText[i];
   }

   public void show(int i) {
      this.menuObjectCanAcceptActions[i] = true;
   }

   public void hide(int i) {
      this.menuObjectCanAcceptActions[i] = false;
   }

   public void setFocus(int i) {
      this.currentFocusHandle = i;
   }

   /* Some text box in this menu currently has the caret -- login's username
      or password field, on the only menu that ever calls makeTextBox. Not
      meaningful on gameMenu: drawGameMenu() gives the chat line focus
      unconditionally every time it runs, so this would read true for the
      entire game rather than answering "did the player just click a box". */
   public boolean isEditingText() {
      return this.currentFocusHandle != -1;
   }

   public int selectedListIndex(int i) {
      return this.menuListEntryMouseOver[i];
   }

   public int getMenuIndex(int i) {
      return this.menuListScrollOffset[i];
   }

   /*
    * Mouse wheel scrolling for a type-4/9 list -- the message tabs, the
    * friends/ignore list, the quest list and the magic/prayer list all reach
    * this, since all four are built through makeTextList/makeInteractiveTextList and drawn
    * through drawTextList/drawInteractiveTextList.
    *
    * Vanilla never had a wheel to give these (see GameFrame's own note on
    * why), which is also why nothing clamps menuListScrollOffset here on our behalf.
    * drawTextList clamps the offset it is handed at the very top before it draws
    * anything; drawInteractiveTextList does not -- it only ever writes menuListScrollOffset back
    * from inside the arrow-click and drag branches, both of which already
    * compute an in-range value before doing so. A wheel event is neither of
    * those, so writing the offset directly here without clamping it first
    * would hand drawInteractiveTextList a value it has never had to cope with -- and,
    * downstream, an index into menuListText it was never asked to bounds-check.
    */
   public void scrollList(int handle, int delta) {
      int fontHeight = this.gameImage.messageFontHeight(this.menuObjectTextType[handle]);
      int visible = fontHeight > 0 ? this.menuObjectHeight[handle] / fontHeight : 0;
      int max = Math.max(0, this.menuListTextCount[handle] - visible);
      int scrolled = this.menuListScrollOffset[handle] + delta;
      if (scrolled < 0) {
         scrolled = 0;
      } else if (scrolled > max) {
         scrolled = max;
      }

      this.menuListScrollOffset[handle] = scrolled;
   }
}
