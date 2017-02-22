import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by IntelliJ IDEA.
 * User: mkorby
 * Date: 4/12/12
 */
class StreamGobbler extends Thread
{
    InputStream is;
    String type;
    private final StringBuilder builder;

    StreamGobbler(InputStream is, String type)
    {
        this.is = is;
        this.type = type;
        builder = new StringBuilder();
    }
    
    public String getText() {
        return builder.toString();
    }

    public void run()
    {
        try
        {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line=null;
            while ( (line = br.readLine()) != null)
                builder.append(type).append(">").append(line).append('\n');
        } catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }
}