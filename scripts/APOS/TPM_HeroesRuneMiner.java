import java.util.Locale;

// By Xx C1ph3r xX

// Learns the spawn time of all 5 rune rocks and then hops to the appropriate world when necessary

public class TPM_HeroesRuneMiner extends Script
{
    int  runeOreCount;
	
	long moveTime;
    long startTime;
	long lastHopTime;
	long hopTime;
	
	private int moveX , moveY;
	
	private int[] initialXP;
	
    public TPM_HeroesRuneMiner( Extension e )
    {
        super(e);
    }

    public void init( String params )
    {
        System.out.println( "TPM_HeroesRuneMiner by Xx C1ph3r xX" );
		
        this.startTime   = -1L;
		this.moveTime    = -1L;
		this.hopTime     = -1L;
		this.lastHopTime = -1L;
		
		this.moveX = 0;
		this.moveY = 0;
		
		this.runeOreCount = 0;
		this.initialXP = new int[SKILL.length];
    }

    public int main()
    {
        if ( this.startTime == -1L )
		{
            this.startTime = System.currentTimeMillis();
		}
		
		if ( this.moveTime != -1L ) 
		{
			if ( getX() == this.moveX && getY() == this.moveY )
				walkTo( getX() + random( 0 , 2 ) - random( 0 , 4 ) , getY() + random( 0 , 2 ) - random( 0 , 4 ) );
			else
				this.moveTime = -1L;
			
			return random( 1000 , 1500 );
		}
		
		if ( this.initialXP[9] == 0 ) 
		{
            for ( int i = 0; i < SKILL.length; i++ ) 
                this.initialXP[i] = getXpForLevel( i );
            return 500;
        }
		
		if ( getFatigue() >= 95 )
		{	
			if ( ! isSleeping() )
			{
				useSleepingBag();
				
				return random( 1200 , 2400 );
			}
		}
		
		int heads  = getInventoryIndex( 671 );
		int shafts = getInventoryIndex( 637 );
		  
		if ( heads != -1 && shafts != -1 )
		{
			useItemWithItem(  heads , shafts );
		}
		
		return random( 800 , 1000 );
    }
	
	@Override
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		   
		if ( str.contains( "standing" ) ) 
		{
			this.moveX    = getX();
			this.moveY    = getY();
			this.moveTime = System.currentTimeMillis();
		}
		
		if ( str.contains( "attach steel heads" ) )
		{
			this.runeOreCount++;
		}
	}
	
	public void paint() 
	{
        final int orange = 0xFFD900;
        final int white  = 0xFFFFFF;
		
        int   x  = 105;
        int   y  = 40;
		int[] xp = getXpStatistics( 9 );
		
		drawString( "TPM_HeroesRuneMiner" , x + 10 , y , 1 , orange );
		y += 15;
        drawString( "Runtime: " + getScriptRuntime() , x + 10 , y , 1 , white );
		y += 15;
		drawString( "Rune ores: " + this.runeOreCount + " - " + ( (int)( ( ( this.runeOreCount * 60L ) * 60L ) / ( ( ( System.currentTimeMillis() - ( this.startTime > 0L ? this.startTime : 1L ) ) / 1000L ) + 1 ) ) ) + " Arrows/Hr" , x + 10 , y , 1 , white );
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
	
	private String getScriptRuntime() 
	{
        long secs = ( ( System.currentTimeMillis() - startTime ) / 1000L );
        
		if ( secs >= 3600 ) 
		{
            return ( secs / 3600 ) + " hours, " +
                   ( ( secs % 3600 ) / 60 ) + " mins, " +
                   ( secs % 60 ) + " secs.";
        }
		
        if ( secs >= 60 ) 
		{
            return ( secs / 60 ) + " mins, " +
                   ( secs % 60 ) + " secs.";
        }
		
        return secs + " secs.";
    }
}