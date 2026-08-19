import java.util.Locale;

// By Xx C1ph3r xX

public class TPM_CoalTruckMiner extends Script
{
	private static final int[] IDS_TO_BANK = {
        149 , 383 , 152 , 155 , 202 , 150 , 151 , 153 , 154 , 409 ,
        160 , 159 , 158 , 157 , 542 , 889 , 890 , 891 , 
        161 , 162 , 163 , 164 , 523 , 892 , 893 , 894
    };
	
	private static final int[] BAD_GEMS = {
        889 , 890 , 891 , 892 , 893 , 894
    };
	
    private static final int[] GEMS = { 
        160 , 159 , 158 , 157 , 542 , 889 , 890 , 891 
    };
	
	private static final int[] COAL_ROCKS = { 
        111 , 110 
    };
	
	private static final int COAL_ORE    = 155;
	private static final int CHISEL      = 167;
	private static final int CRUSHED_GEM = 915;
    private static final int GNOME_BALL  = 981;
	
	private long startTime , moveTime;
	private int oreCount = 0;
	private int moveX , moveY;
	private boolean coalTrucksFull;
	private int[] initialXP;
				
    public TPM_CoalTruckMiner(Extension e)
    {
        super(e);
    }

    public void init(String params)
    {
        System.out.println( "Coal Miner w/ mine cart usage by T4hpur3mag3" );
		
		this.initialXP = new int[SKILL.length];
		this.oreCount = 0;
		this.coalTrucksFull = false;
		if ( getX() < 528 )
			this.coalTrucksFull = true;
        this.startTime = -1L;
		this.moveTime = -1L;
		this.moveX = 0;
		this.moveY = 0;
    }

    public int main()
    {
		if ( getLevel( 16 ) < 20 )
        {
            System.out.println("[TPM_CoalTruckMiner] You must be level 20 agility to use this script.");
            stopScript();
        }
		
		if ( getLevel( 14 ) < 30 )
        {
            System.out.println("[TPM_CoalTruckMiner] You must be level 30 mining to use this script.");
            stopScript();
        }
		
        if ( ( getX() <= 495 && getX() >= 524 ) || ( getY() <= 446 && getY() >= 479 ) )
        {
            System.out.println("[TPM_CoalTruckMiner] You must be between Camelot bank and the coal mining area to use this script.");
            stopScript();
        }
		
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
		
		// CHECK IF TIRED AND SLEEP IF NEED BE

        if ( getFatigue() > 90 )
        {
            useSleepingBag();
            return random( 1000 , 2000 );
        }
		
		// CHISEL ANY GEMS IF NECESSARY
		
		int chisel = getInventoryIndex( CHISEL );
		
        if ( chisel != -1 ) 
		{
            int gem = getInventoryIndex( GEMS );
           
        	if ( gem != -1 ) 
			{
                useItemWithItem( chisel , gem );
                return random( 700 , 900 );
            }
        }
		
		// DROP ANY BAD GEMS
		
		int badGem = getInventoryIndex( BAD_GEMS );
            
		if ( badGem != -1 ) 
		{
			dropItem( badGem );
			return random( 1200 , 2000 );
		}
		
		// DROP ANY CRUSHED GEMS
        
        int crushedGem = getInventoryIndex( CRUSHED_GEM );
		
        if ( crushedGem != -1 ) 
		{
            dropItem( crushedGem );
            return random( 1200 , 2000 );
        }
		
		// DROP ANY GNOME BALLS
		
		int gnomeBall = getInventoryIndex( GNOME_BALL );
		
        if ( gnomeBall != -1 ) 
		{
            dropItem( gnomeBall );
            return random( 1200 , 2000 );
        }
		
		// IF WE ARE BANKING
		
		if ( isBanking() )  
        {
			for ( int i = 0; i < IDS_TO_BANK.length; i++ ) 
			{
				if ( getInventoryCount( IDS_TO_BANK[i] ) > 0 )
				{
					deposit( IDS_TO_BANK[i] , getInventoryCount( IDS_TO_BANK[i] ) );
					return random( 1000 , 1500 );
				}
			}
			
			closeBank();
			return random( 1000 , 1200 );
		}
		
		// IF WE ARE AWAY FROM THE COAL AREA AND NEAR THE TRUCK DEPOT / BANK AREA
		
		if ( getX() < 528 )
		{
			// IF THE COAL TRUCKS ARE EMPTY AND PLAYER IS NOT HOLDING COAL, RETURN TO COAL AREA
			
			if ( ! this.coalTrucksFull && getInventoryCount( COAL_ORE ) == 0 )
			{
				if ( getX() >= 518 )
				{
					walkTo( 528 + random( 0 , 2 ) , 443 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 518 && getX() >= 508 )
				{
					walkTo( 518 + random( 0 , 2 ) , 446 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 508 )
				{
					int[] bank_doors = getObjectById( 64 );
				
					if ( bank_doors[0] != -1 ) 
					{
						atObject( bank_doors[1] , bank_doors[2] );
						return random( 1000 , 1500 );
					}
					
					walkTo( 511 + random( 0 , 2 ) , 446 );
					return random( 1000 , 1500 );
				}
			}
			
			// IF THE COAL TRUCKS ARE NOT EMPTY, BUT THE PLAYER INVENTORY IS, GET COAL FROM TRUCKS
			
			else if ( this.coalTrucksFull && ( getInventoryCount( COAL_ORE ) == 0 || getInventoryCount() < 30 ) )
			{
				if ( getX() >= 518 )
				{
					atObject( 520 , 443 );  
					return 250;
				}
				
				if ( getX() < 518 && getX() >= 508 )
				{
					walkTo( 518 + random( 0 , 2 ) , 446 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 508 )
				{
					int[] bank_doors = getObjectById( 64 );
				
					if ( bank_doors[0] != -1 ) 
					{
						atObject( bank_doors[1] , bank_doors[2] );
						return random( 1000 , 1500 );
					}
					
					walkTo( 511 + random( 0 , 2 ) , 446 );
					return random( 1000 , 1500 );
				}
			}
			
			// IF THE PLAYER IS HOLDING COAL
			
			else if ( getInventoryCount( COAL_ORE ) > 0 )
			{
				if ( getX() >= 518 )
				{
					walkTo( 511 + random( 0 , 2 ) , 446 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 518 && getX() >= 508 )
				{
					walkTo( 502 - random( 0 , 2 ) , 455 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 508 )
				{
					int[] bank_doors = getObjectById( 64 );
					
					if ( bank_doors[0] != -1 ) 
					{
						atObject( bank_doors[1] , bank_doors[2] );
						return random( 1000 , 1500 );
					}
					
					if( isQuestMenu() )  
                    { 
                        answer( 0 );
                        return 5500;
                    }
					  
                    int banker[] = getNpcByIdNotTalk( BANKERS );
					
                    if ( banker[0] != -1 )
                    {
                        talkToNpc( banker[0] );
                        return 4250;
                    }
				}
			}
		}
		else
		{
			// IF INVENTORY IS FULL AND THE COAL TRUCKS ARE FULL
			
			if ( getInventoryCount() == 30 && this.coalTrucksFull )
			{
				// IF IN THE MINING AREA
				
				if ( getX() > 601 )
				{
					walkTo( 601 - random( 0 , 2 ) , 458 + random( 0 , 2 ) );
					return random( 1000 , 1500 );
				}
				
				if ( getX() > 597 )
				{
					atObject( 597 , 458 );
					return random( 3000 , 4500 );
				}
				
				if ( getX() < 594 && getX() > 576 && getY() > 449 )
				{
					walkTo( 587 - random( 0 , 2 ) , 443 + random( 0 , 2 ) );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 594 && getX() > 577 && getY() <= 449 && getY() > 440 )
				{
					walkTo( 579 , 437 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 594 && getX() > 577 && getY() <= 439 )
				{
					walkTo( 575 - random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 576 && getX() > 564 && getY() <= 439 )
				{
					walkTo( 563 - random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 563 && getX() > 552 && getY() <= 439 )
				{
					walkTo( 551 - random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 551 && getX() > 540 && getY() <= 439 )
				{
					walkTo( 539 - random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 539 && getX() > 528 && getY() <= 449 )
				{
					walkTo( 527 - random( 0 , 2 ) , 443 );
					return random( 1000 , 1500 );
				}
			}
			
			// IF INVENTORY IS FULL AND THE COAL TRUCKS ARE NOT FULL
			
			else if ( getInventoryCount() == 30 && ! this.coalTrucksFull )
			{
				// IF IN THE COAL MINING AREA
				  
				if ( getX() > 596 )
				{
					if ( getInventoryCount( COAL_ORE ) > 0 )
					{
						useItemOnObject( COAL_ORE , 613 , 455 );
						return random( 1200 , 2400 );
					}
				}
			}
			
			// IF INVENTORY IS NOT FULL
			
			else
			{   
				// IF IN THE COAL MINING AREA
				
				if ( getX() > 596 )
				{
					int[] rock = getObjectById( COAL_ROCKS ); 

					// MINE THE ROCK IF IT IS AVAILABLE
					
					if ( rock[0] != -1 )
					{
						atObject( rock[1] , rock[2] );
						return random( 750 , 950 );
					}
				}
				
				// IF NOT IN THE COAL MINING AREA, WALK TOWARDS AND CROSS LOG
				
				if ( getX() < 594 && getY() > 449 )
				{
					atObject( 593 , 458 );
					return random( 1000 , 1500 );
				}
				   
				if ( getX() < 594 && getX() > 576 && getY() <= 449 && getY() > 440 )
				{
					walkTo( 592 - random( 0 , 2 ) , 452 + random( 0 , 2 ) );
					return random( 1000 , 1500 );
				}
				
				if ( getX() < 594 && getX() > 576 && getY() <= 439 )
				{  
					walkTo( 584 , 445 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 576 && getX() > 563 && getY() <= 449 )
				{
					walkTo( 577 + random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 563 && getX() > 551 && getY() <= 449 )
				{
					walkTo( 564 + random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
				
				if ( getX() <= 551 && getX() > 539 && getY() <= 449 )
				{
					walkTo( 552 + random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}      
				
				if ( getX() <= 539 && getX() >= 528 && getY() <= 449 )
				{
					walkTo( 540 + random( 0 , 2 ) , 436 );
					return random( 1000 , 1500 );
				}
			}
		}
		
		return random( 1000 , 2000 );
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
		   
		if ( str.contains( "coal truck is full" ) ) 
			this.coalTrucksFull = true;
		
		if ( str.contains( "no coal left" ) ) 
			this.coalTrucksFull = false;
		
		if ( str.contains( "obtain some coal" ) )
			this.oreCount++;
	}
	
	public void paint() 
	{
        final int orange = 0xFFD900;
        final int white  = 0xFFFFFF;
		
        int   x  = 105;
        int   y  = 40;
		int[] xp = getXpStatistics( 14 );
		
		drawString( "TPM_CoalTruckMiner" , x + 10 , y , 1 , orange );
		y += 15;
        drawString( "Runtime: " + _getRuntime() , x + 10 , y , 1 , white );
		y += 15;
		drawString( "Ores: " + this.oreCount + " - " + ( (int)( ( ( this.oreCount * 60L ) * 60L ) / ( ( ( System.currentTimeMillis() - ( this.startTime > 0L ? this.startTime : 1L ) ) / 1000L ) + 1 ) ) ) + " Ores/Hr" , x + 10 , y , 1 , white );
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