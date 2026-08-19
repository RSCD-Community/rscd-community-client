/*

Xx C1ph3r xX

    location: stand inside bank

    if you want strings added to the bows you must make the third parameter true, otherwise make it false

    a sleeping bag and knife is required

    usage:    bowfletcher [normal/oak/willow/maple/yew/magic],[longbow/shortbow],[true/false]

*/

public class BowFletcher extends Script
{ 
    int     logId      = 0;
    int     unstrungId = 0;
    int     bowId       = 0;
    boolean longBow    = false;
    int     botStage   = 0;
    boolean stringBows = false;
    long    startTime  = 0;
    String  bowType    = "";
    int     completed  = 0;

    public BowFletcher(Extension e)
    {
        super(e);
    }

    private String convertMillis(long timeLapse)
    {
        int days = 0,hours = 0,minutes = 0,seconds = 0;

        timeLapse = timeLapse / 1000;

        while(timeLapse >= (24 * 60 * 60))
        {
            days++; timeLapse -= (24 * 60 * 60);
        }
        while(timeLapse >= (60 * 60))
        {
            hours++; timeLapse -= (60 * 60);
        }
        while(timeLapse >= 60)
        {
            minutes++; timeLapse -= 60;
        }
        while(timeLapse > 0)
        {
            seconds++; timeLapse--;
        }

        return (days > 0 ? (days + " days, ") : "") + (hours > 0 ? (hours + " hours, ") : "") + (minutes > 0 ? (minutes + " minutes, ") : "") + seconds + " seconds";
    }

    public void init(String params)
    {
        startTime = 0;
        completed = 0;

        String[] paramArray = params.split(",");

        if(paramArray.length < 3)
        {
            System.out.println("[BowFletcher] You are missing a parameter.\r\n Ex. bowfletcher magic,longbow,true");
        }
        else if(paramArray.length > 3)
        {
            System.out.println("[BowFletcher] You have too many parameters.\r\n Ex. bowfletcher magic,longbow,true");
        }
        else
        {
            if(paramArray[1].equalsIgnoreCase("longbow"))
            {
                longBow = true;
            }

            if(paramArray[2].equalsIgnoreCase("true"))
            {
                stringBows = true;
            }

            bowType = paramArray[0];

            if(paramArray[0].equalsIgnoreCase("normal"))
            {
                logId      = 14;
                unstrungId = 276;
                bowId      = 188;
                System.out.println("[BowFletcher] Configured to normal logs");
            }
            else if(paramArray[0].equalsIgnoreCase("oak"))
            {
                logId      = 632;
                unstrungId = 658;
                bowId      = 648;
                System.out.println("[BowFletcher] Configured to oak logs");
            }
            else if(paramArray[0].equalsIgnoreCase("willow"))
            {
                logId      = 633;
                unstrungId = 660;
                bowId      = 650;
                System.out.println("[BowFletcher] Configured to willow logs");
            }
            else if(paramArray[0].equalsIgnoreCase("maple"))
            {
                logId      = 634;
                unstrungId = 662;
                bowId      = 652;
                System.out.println("[BowFletcher] Configured to maple logs");
            }
            else if(paramArray[0].equalsIgnoreCase("yew"))
            {
                logId      = 635;
                unstrungId = 664;
                bowId      = 654;
                System.out.println("[BowFletcher] Configured to yew logs");
            }
            else if(paramArray[0].equalsIgnoreCase("magic"))
            {
                logId      = 636;
                unstrungId = 666;
                bowId      = 656;
                System.out.println("[BowFletcher] Configured to magic logs");
            }

            if(!longBow)
            {
                unstrungId++;
                bowId++;
                System.out.println("[BowFletcher] Configured to shortbows");
            }
            else
            {
                System.out.println("[BowFletcher] Configured to longbows");
            }

            if(!stringBows)
            {
                System.out.println("[BowFletcher] Configured to not string bows");
            }
            else
            {
                System.out.println("[BowFletcher] Configured to string bows");
            }
        }
    }

    public int main()
    {
        if(startTime == 0)
        {
            startTime = System.currentTimeMillis();
        }

        if(getInventoryCount(1263) == 0)
        {
            System.out.println("[BowFletcher] A sleeping bag is required.");
            stopScript();
            
            return 100;
        }

        if(getInventoryCount(13) == 0)
        {
            System.out.println("[BowFletcher] A knife is required.");
            stopScript();
            
            return 100;
        }

        if(getFatigue() > 90)
        {
               useSleepingBag();

            return random(1000, 2000);
        }

        if(botStage == 0)
        {
            // deposit bows withdraw logs and strings

            if(isBanking())
            {
                if(stringBows && (getInventoryCount(bowId) > 0))
                {
                    completed += getInventoryCount(bowId);
                    deposit(bowId, getInventoryCount(bowId));     
                    System.out.println("[BowFletcher] I have made  " + completed + " " + bowType + " " + (longBow == true ? "longbows" : "shortbows") + " since I started " + convertMillis(System.currentTimeMillis() - startTime) + " ago");    
                      
                           return random(1000, 1500);
                }
                else if(!stringBows && (getInventoryCount(unstrungId) > 0))
                {
                    completed += getInventoryCount(unstrungId);
                    deposit(unstrungId, getInventoryCount(unstrungId));       
                    System.out.println("[BowFletcher] I have made  " + completed + " unstrung " + bowType + " " + (longBow == true ? "longbows" : "shortbows") + " since I started " + convertMillis(System.currentTimeMillis() - startTime) + " ago");       
                     
                           return random(1000, 1500);
                }
                else if((getInventoryCount(unstrungId) == 0) && (getInventoryCount(bowId) == 0))
                {
                    if(stringBows && (getInventoryCount(logId) == 14) && (getInventoryCount(676) == 14))
                    {
                        closeBank();
                        botStage = 1;

                        return random(500, 1000);
                    }
                    else if(!stringBows && (getInventoryCount(logId) == 28))
                    {
                        closeBank();
                        botStage = 1;

                        return random(500, 1000);
                    }
                    else if(stringBows && (getInventoryCount(logId) > 14))
                    {
                        deposit(logId, getInventoryCount(logId) - 14);

                                return random(1000, 1500);       
                    }
                    else if(stringBows && (getInventoryCount(logId) < 14))
                    {
                        withdraw(logId, 14 - getInventoryCount(logId));

                                return random(1000, 1500);       
                    }
                    else if(stringBows && (getInventoryCount(676) < 14))
                    {
                        withdraw(676, 14 - getInventoryCount(676));

                                return random(1000, 1500); 
                    }
                    else if(!stringBows && (getInventoryCount(logId) < 28))
                    {
                        withdraw(logId, 28 - getInventoryCount(logId));

                                return random(1000, 1500); 
                    }
                }
            }
            else
            {
                if(isQuestMenu())
                {
                    answer(0);

                    return random(5000, 6000);
                }

                int banker[] = getNpcByIdNotTalk(BANKERS);

                if(banker[0] != -1)
                {
                    talkToNpc(banker[0]);

                    return 5500;
                }
            }
        }
        else if(botStage == 1)
        {
            // cut the bows

            if(getInventoryCount(logId) > 0)
            {
                if(isQuestMenu())
                {
                    if(!longBow && logId == 14)
                    {
                        answer(1);
                    }
                    else if(longBow && logId == 14)
                    {
                        answer(2);
                    }
                    else if(!longBow)
                    {
                        answer(0);
                    }
                    else if(longBow)
                    {
                        answer(1);
                    }
            
                    return random(400, 500);
                }

                useItemWithItem(getInventoryIndex(13), getInventoryIndex(logId));

                return random(600, 700);
            }
            else
            {
                if(stringBows)
                {
                    botStage = 2;

                    return random(500, 1000);
                }
                else
                {
                    botStage = 0;

                    return random(500, 1000);
                }
            }
        }
        else if(botStage == 2)
        {
            // string the bows

            if(getInventoryCount(unstrungId) > 0)
            {
                useItemWithItem(getInventoryIndex(676), getInventoryIndex(unstrungId));

                return random(200, 400);
            }
            else
            {
                botStage = 0;

                return random(500, 1000);
            }
        }

        return random(1000, 2000);
    }
}