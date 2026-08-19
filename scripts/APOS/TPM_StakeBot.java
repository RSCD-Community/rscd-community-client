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

public final class TPM_StakeBot extends Script
{
    public TPM_StakeBot( Extension ex )
	{
        super( ex );
    }

    @Override
    public void init( String params ) 
	{
		
    }

    @Override
    public int main() 
	{
		if ( inCombat() )
		{
			int fightMode = getFightMode();
			if ( fightMode == 1 || fightMode == 2 ) 
			{
				int amuletIndex = getInventoryIndex( 316 );
						
				if ( amuletIndex != -1 && ! isItemEquipped( amuletIndex ) )
				{
					wearItem( amuletIndex );
				}
			}
			else
			{
				int amuletIndex = getInventoryIndex( 744 );
						
				if ( amuletIndex != -1 && ! isItemEquipped( amuletIndex ) )
				{
					wearItem( amuletIndex );
				}
			}
		}
		  
		return 500;
    }
	
    @Override
    public void onServerMessage( String str ) 
	{
        str = str.toLowerCase( Locale.ENGLISH );
		   
	}
}
