import java.io.IOException;

/**
* Created by IntelliJ IDEA.
* User: mkorby
* Date: 4/23/12
 *
 * Runs the given cmdarray and makes the exit val, outuput and stderr available
*/
class JobResults {
    private final String[] cmdarray;
    private StreamGobbler errorGobbler;
    private StreamGobbler outputGobbler;
    private int exitVal;

    public JobResults(final String... cmdarray) {
        this.cmdarray = cmdarray;
    }

    public String getStderr() {
        return errorGobbler.getText();
    }

    public String getStdout() {
        return outputGobbler.getText();
    }

    public int getExitVal() {
        return exitVal;
    }

    public JobResults invoke() throws IOException, InterruptedException {
        final Process process = Runtime.getRuntime().exec(cmdarray);
        // any error message?
        errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");
        // any output?
        outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");

        // kick them off
        errorGobbler.start();
        outputGobbler.start();

        // any error???
        exitVal = process.waitFor();
        return this;
    }
}
