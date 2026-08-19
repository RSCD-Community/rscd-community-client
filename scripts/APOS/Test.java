import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;

public class Test extends Script {

    private Extension ex;

    public Test(Extension ex) {
        super(ex);
		this.ex = ex;
    }

    public void init(String params) {
        
    }
  
    public int main() {
		try {
			this.ex.requestFocusInWindow();
			String text = "hey";
			StringSelection stringSelection = new StringSelection(text);
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, stringSelection);
			Robot robot = new Robot();
			robot.keyPress(KeyEvent.VK_CONTROL);
			robot.keyPress(KeyEvent.VK_V);
			robot.keyRelease(KeyEvent.VK_V);
			robot.keyRelease(KeyEvent.VK_CONTROL);
			robot.keyPress( KeyEvent.VK_ENTER );
			robot.keyRelease( KeyEvent.VK_ENTER );
		} catch (AWTException e) {
			e.printStackTrace();
		}
		/*
		try
		{
			Class<Extension> extClass = this.ex.getClass();
			for ( Field field : ext.getDeclaredFields() ) 
			{
				field.setAccessible( true );
                Object object = field.get( this.ex );
				System.out.println( "" + field.getName() + ":" + object.toString() );
			}
			
		}
		catch( Exception e )
		{
			e.printStackTrace();
		}
		*/
		
		return 1000;
    }
}