// By Xx C1ph3r xX

public class MapleCutter extends Script
{
    Extension ex;
    int       mapleCount  = 0;
    long      startTime = 0;

    public MapleCutter(Extension e)
    {
        super(e);
        this.ex = e;
    }

    public void init(String params)
    {
        System.out.println("Maple Cutter by Xx C1ph3r xX");
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

        if(getFatigue() > 90)
        {
            useSleepingBag();
            return random(1000,2000);
        }

        if (getInventoryCount(634) == 28)
        {
            if(getX() < 498 || getX() > 504 || getY() < 447 || getY() > 453)
            {
                walkTo(499,450);
                
                return random(1000,2000);
            }    
// 504,453 498,447

            if(isBanking())
            {
                if(getInventoryCount(634) > 1)
                {
                    mapleCount+=28;
                    deposit(634, getInventoryCount(634));
                    System.out.println("[MapleCutter] I have collected " + mapleCount + " maple logs since I started " + convertMillis(System.currentTimeMillis() - startTime) + " ago");                    
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
                int banker[] = getNpcByIdNotTalk(BANKERS);
                if(banker[0] != -1)
                {
                    talkToNpc(banker[0]);
                    return 5500;
                }
            }
        }
        else
        {
            
            int[] tree = getObjectById(308); 

            if( tree[0]!=-1)
            {
                atObject(tree[1], tree[2]);
                return random(500,600);
            }
            else
            {

                return random(1000,2000);
            }
        }
    return random(1000,2000);
    }
}