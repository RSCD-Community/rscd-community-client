// By Xx C1ph3r xX

public class YewCutter extends Script
{
    int  yewCount  = 0;
    long startTime = 0;

    public YewCutter(Extension e)
    {
        super(e);
    }

    public void init(String params)
    {
        System.out.println("Yew Cutter by Xx C1ph3r xX");
        startTime = 0;
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

    public int main()
    {
        if(startTime == 0)
        {
            startTime = System.currentTimeMillis();
        }

        if(getX() <= 495 && getX() >= 524)
        {
            System.out.println("[YewCutter]  You must be between camelot bank and the church yew yard to run.");
            stopScript();
        }
        else if(getY() <= 446 && getY() >= 479)
        {
            System.out.println("[YewCutter]  You must be between camelot bank and the church yew yard to run.");
            stopScript();
        }

        if(getFatigue() > 90)
        {
            useSleepingBag();
            return random(1000,2000);
        }

        if (getInventoryCount(635) == 28)
        {
            if(getY() < 454) // in bank
            {
                if(isBanking())
                {
                    if(getInventoryCount(635) > 1)
                    {
                        yewCount+=28;
                        deposit(635, getInventoryCount(635));
                        System.out.println("[YewCutter] I have collected " + yewCount + " yew logs since I started " + convertMillis(System.currentTimeMillis() - startTime) + " ago");                    
                        return random(2000,2500);
                    }
                }
                else
                {
                    if(isQuestMenu())
                    {
                        answer(0);
                        return random(5000, 6000);
                    }
                    int banker[] = getNpcByIdNotTalk(95);
                    if(banker[0] != -1)
                    {
                        talkToNpc(banker[0]);
                        return 5500;
                    }
                }
            }
            else if(getY()<460&&getY()>=453) // in between bank and church yew yard
            {
                walkTo(501,452);
                return 2000;
            }
            else if(getY()>=460) // in church yew yard
            {
                walkTo(511,459);
                return 2000;
            }
        }
        else
        {
            if(getY()<=459) // in bank
            {
                walkTo(511,460);
                return 2000;
            }
            else if(getY()<=468&&getY()>459) // in between bank and church yew yard
            {
                walkTo(519,469);
                return 2000;
            }
            else if(getY()>468) // in church yew yard
            {
                int[] tree = getObjectById(309); 

                if( tree[0]!=-1)
                {
                    atObject(tree[1], tree[2]);
                    return random(500,600);
                }
                else
                {
                    return random(1000,1500);
                }
            }
            
        return random(1000,2000);
        }
    return random(1000,2000);
    }
}