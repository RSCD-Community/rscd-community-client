/*
blood's low wall jumper
*/

public class lowwall extends Script {
    long startXp=0,time=0,move=0;
    String skill = "agility";
    private Extension extension;

    public lowwall (Extension e) {
        super(e);
        extension = e;
    }

    public int main() {

        if(getFatigue() >= 90) {
            useSleepingBag();
            return 2000;
        }

        if(move != 0L) {
            if(System.currentTimeMillis() >= move) {
                writeLine("5 minute warning detected, moving");
                walk(); move = 0L;
                return random(3000, 4500);
            }
            return 0;
        }

        if(startXp < 1) {
            startXp = getXpForLevel(skillName(skill));
            time = System.currentTimeMillis();
            System.out.println("Stored starting experience");
            return 500;
        }

        int[] lowwall = getWallObjectById(164);
        if(lowwall[0] != -1 && !isWalking()) {
            atWallObject(lowwall[1], lowwall[2]);
            return 250;
        }
        return 200;
    }

    public void paint() {
        int x = 12;
        int y = 50;
        drawString("@gre@Blood's Low Wall Jumper", 7, 33, 4, 0xFFFFFF);
        drawVLine(8,37,62,0xFFFFFF);
        drawHLine(8,99,183,0xFFFFFF);
        drawString(SKILL[skillName(skill)] + " XP Gained: " + (getXpForLevel(skillName(skill)) - startXp), x, y, 1, 0xFFFFFF);
        y += 15;
        drawString("XP/H: " + getXpH(), x, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Runtime: " + getRunTime(), x, y, 1, 0xFFFFFF);
        y += 15;
        drawString("Hopped the wall " + (getXpForLevel(skillName(skill)) - startXp) / 5 + " times.", x, y, 1, 0xFFFFFF);
    }
    
    private long getXpH() {
        try {
            long xph = (((getXpForLevel(skillName(skill)) - startXp) * 60) * 60) / (((System.currentTimeMillis() - time) / 1000));
            return xph;
        }
        catch(ArithmeticException e) {}
        return -1;
    }

    private String getRunTime() {
        long ttime = ((System.currentTimeMillis() - time) / 1000);
        if (ttime >= 7200) {
            return new String((ttime / 3600) + " hours, " + ((ttime % 3600) / 60) + " minutes, " + (ttime % 60) + " seconds.");
        }
        if (ttime >= 3600 && ttime < 7200) {
            return new String((ttime / 3600) + " hour, " + ((ttime % 3600) / 60) + " minutes, " + (ttime % 60) + " seconds.");
        }
        if (ttime >= 60) {
            return new String(ttime / 60 + " minutes, " + (ttime % 60) + " seconds.");
        }
        return new String(ttime + " seconds.");
    }

    public int skillName(String s) {
        for (int i = 0; i <= 17; i++) {
            if (SKILL[i].equalsIgnoreCase(s)) {
                return i;
            }
        }
        return -1;
    }

    private void walk() {
        if (isReachable(getX(), getY() + 1)) {
            walkTo(getX(), getY() + 1);
        } else if (isReachable(getX(), getY() - 1)) {
            walkTo(getX(), getY() - 1);
        } else if (isReachable(getX() + 1, getY())) {
            walkTo(getX() + 1, getY());
        } else if (isReachable(getX() - 1, getY())) {
            walkTo(getX() - 1, getY());
        }
    }

    public void onServerMessage(String str) {
        if (str.contains("standing here")) {
            move = (System.currentTimeMillis() + random(1500, 1800));
        }
    }
}