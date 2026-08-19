import java.util.Locale;

// By Xx C1ph3r xX

public class TPM_MagicCutter extends Script
{
    int  magicCount  = 0;
    long startTime = 0;
	long moveTime = 0;
	private int moveX , moveY;
	private int[] initialXP;
	
    public TPM_MagicCutter(Extension e)
    {
        super(e);
    }

    public void init(String params)
    {
        System.out.println("Magic Cutter by Xx C1ph3r xX");
        this.startTime = -1L;
		this.moveTime = -1L;
		this.moveX = 0;
		this.moveY = 0;
		this.initialXP = new int[SKILL.length];
    }

    public int main()
    {
        if ( this.startTime == -1L )
            this.startTime = System.currentTimeMillis();

		if ( this.moveTime != -1L ) 
		{
			if ( getX() == this.moveX && getY() == this.moveY )
				walkTo( getX() + random( 0 , 2 ) - random( 0 , 4 ) , getY() + random( 0 , 2 ) - random( 0 , 4 ) );
			else
				this.moveTime = -1L;
			
			return random( 1000 , 1500 );
		}
		
		if ( this.initialXP[3] == 0 ) 
		{
            for ( int i = 0; i < SKILL.length; i++ ) 
                this.initialXP[i] = getXpForLevel( i );
            return 500;
        }
		
        if ( getX() <= 495 && getX() >= 524 )
        {
            System.out.println("[TPM_MagicCutter]  You must be between camelot bank and the magic tree area to run.");
            stopScript();
        }
        else if ( getY() <= 446 && getY() >= 497 )
        {
            System.out.println("[TPM_MagicCutter]  You must be between Camelot bank and the magic tree area to run.");
            stopScript();
        }

        if ( getFatigue() > 90 )
        {
            useSleepingBag();
            return random( 1000 , 2000 );
        }

        if ( getInventoryCount() == 30 )
        {
            if( getY() < 454 ) // in bank
            {
                if( isBanking() ) 
                {
                    if ( getInventoryCount( 636 ) > 0 )
                    {
                        deposit( 636 , getInventoryCount( 636 ) );
                        System.out.println( "[TPM_MagicCutter] I have collected " + magicCount + " magic logs since I started " + _getRuntime() + " ago" );                    
                        return random( 2000 , 2500 );
                    } 
                }
                else
                {
                    if( isQuestMenu() )
                    {
                        answer( 0 );
                        return random( 5000 , 6000 );
                    }
					
                    int banker[] = getNpcByIdNotTalk( BANKERS );
                    if ( banker[0] != -1 )
                    {
                        talkToNpc( banker[0] );
                        return 5500;
                    }
                }
            }
            else if ( getY() <= 470 && getY() >= 454 ) // in between bank and church yew yard
            {
				int[] bank_doors = getObjectById( 64 );
				
				if ( bank_doors[0] != -1 ) 
				{
					atObject( bank_doors[1] , bank_doors[2] );
					return random( 1000 , 1500 );
				}
				
                walkTo( 501 , 452 );
                return 2000;
            }
            else if ( getY() <= 484 && getY() > 470 ) // beside church yew yard
            {
                walkTo( 509 , 463 );
                return 2000;
            }
            else if ( getY() > 484 ) // in magic tree area
            {
                walkTo( 509 , 478 );
                return 2000;
            }  
        }
        else
        {
            if ( getY() <= 459 ) // in bank
            {
				int[] bank_doors = getObjectById( 64 );
				
				if ( bank_doors[0] != -1 ) 
				{
					atObject( bank_doors[1] , bank_doors[2] );
					return random( 1000 , 1500 );
				}
				
                walkTo( 509 , 463 );
                return 2000;   
            }
			else if( getY() <= 470 && getY() > 459 ) // in between bank and church yew yard
            {
                walkTo( 508 , 478 );
                return 2000;
            }
            else if ( getY() <= 484 && getY() > 470 ) // beside church yew yard
            {
                walkTo( 522 , 488 );
                return 2000;
            }
            else if ( getY() > 484 ) // in magic tree area
            {
                int[] tree = getObjectById( 310 ); 

                if ( tree[0] != -1 )
                {
                    atObject( tree[1] , tree[2] );
                    return random( 500 , 600 );
                }
                else
                {
                    autohop( false );
					return 5500;
                }
            }
            
			return random(1000,2000);
        }
		
		return random(1000,2000);
    }
	
	@Override
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		   
		if ( str.contains( "standing" ) ) 
		{
			this.moveX = getX();
			this.moveY = getY();
			this.moveTime = System.currentTimeMillis();
		}
		
		if ( str.contains( "get some wood" ) )
			magicCount++;
	}
	
	public void paint() 
	{
        final int orange = 0xFFD900;
        final int white  = 0xFFFFFF;
		
        int   x  = 105;
        int   y  = 40;
		int[] xp = getXpStatistics( 8 );
		
		drawString( "TPM_MagicCutter" , x + 10 , y , 1 , orange );
		y += 15;
        drawString( "Runtime: " + _getRuntime() , x + 10 , y , 1 , white );
		y += 15;
		drawString( "Logs: " + this.magicCount + " - " + ( (int)( ( ( this.magicCount * 60L ) * 60L ) / ( ( ( System.currentTimeMillis() - ( this.startTime > 0L ? this.startTime : 1L ) ) / 1000L ) + 1 ) ) ) + " Logs/Hr" , x + 10 , y , 1 , white );
		y += 15;
		drawString( "XP: " + xp[2] + " - " + xp[3] + " XP/Hr" , x + 10 , y , 1 , white );
    }
	
	private int[] getXpStatistics( int skill ) 
	{
        long time = ( ( System.currentTimeMillis() - ( this.startTime > 0L ? this.startTime : 1L ) ) / 1000L );
		
        if (time < 1L) 
            time = 1L;

        int startXP    = this.initialXP[skill];
        int currentXP  = getXpForLevel( skill );
        int[] intArray = new int[4];
		
        intArray[0] = currentXP;
        intArray[1] = startXP;
        intArray[2] = intArray[0] - intArray[1];
        intArray[3] = (int)( ( ( ( currentXP - startXP ) * 60L ) * 60L ) / time );
		
        return intArray;
    }
	
	private String _getRuntime() {
        long secs = ((System.currentTimeMillis() - startTime) / 1000L);
        if (secs >= 3600) {
            return (secs / 3600) + " hours, " +
                    ((secs % 3600) / 60) + " mins, " +
                    (secs % 60) + " secs.";
        }
        if (secs >= 60) {
            return secs / 60 + " mins, " +
                    (secs % 60) + " secs.";
        }
        return secs + " secs.";
    }
}