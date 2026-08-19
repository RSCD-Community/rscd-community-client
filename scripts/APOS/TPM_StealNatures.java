import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Choice;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import com.aposbot.Constants;
import com.aposbot.StandardCloseHandler;

public final class TPM_StealNatures extends Script
{
    private long loot_time;
	private long respawn_time;
    private long menu_time;
	private long move_time;
	private long start_time;
	private int natures;
	private int gold;
	private int move_x;
	private int move_y;
	
    public TPM_StealNatures( Extension ex )
	{
        super( ex );
    }

    @Override
    public void init( String params ) 
	{
		natures = 0;
		move_time = -1L;
		loot_time = -1L;
		start_time = System.currentTimeMillis();
		respawn_time = 15000L;
		move_x = getX();    
		move_y = getY();  
    }   

    @Override
    public int main() 
	{	
		if ( move_time != -1L )
		{
			walkTo( 539 , 1545 );
		
			if ( move_x != getX() || move_y != getY() )
			{
				move_time = -1L;
			}
			
			return random(1000, 1500);
		}
	
		if ( getFatigue() >= 90 )
		{	
			if ( ! isSleeping() )
			{
				useSleepingBag();
				
				return random( 1200 , 2400 );
			}
		}
		
		if ( System.currentTimeMillis() - loot_time >= respawn_time )
		{
			int[] chest = getObjectById( 340 );
				  
			if ( chest[0] != -1 )
			{
				atObject2( chest[1] , chest[2] );
				
				return 10;
			}
		}
		
		return 10;
    }
	
	@Override
    public void paint() 
	{
        final int orange = 0xFFD900;
        final int white = 0xFFFFFF;
        int x = 105;
        int y = 40;
        drawString("Runtime: " + _getRuntime(), x + 10, y, 1, white);
		y+=15;
		drawString("Natures: " + natures , x + 10, y, 1, white);
		y+=15;
		drawString("Gold: " + gold , x + 10, y, 1, white);
    }
	private String _getRuntime() {
        long secs = ((System.currentTimeMillis() - start_time) / 1000L);
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
	
	private int[] _getItemFast( int... ids ) 
	{
        int count = getGroundItemCount();
        int x = getX();
        int y = getY();
		
        for ( int i = 0; i < count; ++i ) 
		{
            if (getItemX( i ) != x || getItemY( i ) != y ) 
			{
                continue;
            }
			
            int id = getGroundItemId( i );
			
            if ( inArray( ids , id ) ) 
			{
                return new int[] { id , x , y };
            }
        }
        return new int[] { -1, -1, -1 };
    }

    private int[] _getReachableNpc( int... ids ) 
	{
        int[] npc = new int[] { -1, -1, -1 };
        int max_dist = Integer.MAX_VALUE;
        int count = countNpcs();
        
		for ( int i = 0; i < count; i++ ) 
		{
            if ( isNpcInCombat( i ) ) 
			{
				continue;
            }
			
			if ( inArray( ids , getNpcId( i ) ) )
			{
                int x = getNpcX( i );
                int y = getNpcY( i );
				
                if ( ! isReachable( x , y ) ) 
				{
					continue;
                }
				
                int dist = distanceTo( x , y , getX() , getY() );
				
                if ( dist < max_dist ) 
				{
                    npc[0] = i;
                    npc[1] = x;
                    npc[2] = y;
                    max_dist = dist;
                }
            }
        }
        return npc;
    }

    private void _walkApprox( int x , int y , int range ) 
	{
        int dx, dy;
        int loop = 0;
        do 
		{
            dx = x + random( -range , range );
            dy = y + random( -range , range );
            if ( ( ++loop ) > 100 ) return;
        } 
		while ( ! isReachable( dx , dy ) );
        walkTo( dx, dy );
    }
	
    @Override
    public void walkTo( int x , int y ) 
	{
        if ( ! isWalking() ) 
		{
            super.walkTo( x , y );
        }
    }
    
    @Override
    public void talkToNpc( int i ) 
	{
        super.talkToNpc( i );
    }
    
    @Override
    public void useOnNpc( int npc , int slot ) 
	{
        super.useOnNpc( npc , slot );
    }
    
    @Override
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		if ( str.contains( "search the chest" ) ) 
		{
			gold+=3;
			natures++;
			loot_time = System.currentTimeMillis();
		}
		
		if ( str.contains( "nothing interesting happens" ) )
			loot_time = System.currentTimeMillis();
			
		if ( str.contains( "standing here" ) ) 
		{
			move_x = getX();
			move_y = getY();
			move_time = System.currentTimeMillis();
		}
	}
}
