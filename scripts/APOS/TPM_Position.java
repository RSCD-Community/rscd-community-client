public class TPM_Position extends Script 
{
	public TPM_Position(Extension e) 
	{
		super(e);
	}

	public void init(String params) 
	{
	
	}

	public int main() 
	{
		return random( 1 , 500 );
	}
  
	@Override
	public void paint() 
	{
		final int orange = 0xFFD900;
		final int white = 0xFFFFFF;
		int x = 105;
		int y = 40;
		drawString("Position: " + getX() + "," + getY(), x + 10, y, 1, white);
	}
}