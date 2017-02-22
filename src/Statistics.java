/**
 * Created by IntelliJ IDEA.
 * User: mkorby
 * Date: 4/30/12
 */
public class Statistics {

    private static final int ONE_MINUTE_IN_MILLIS = 60000;
    public int imagesDownloaded, videosDownloaded, audioFilesDownloaded, textsDownloaded, highResImagesCopied, originalVideosCopied, originalAudioFilesCopied = 0;
    private long startTime;

    public Statistics() {
        startTime = System.currentTimeMillis();
    }

    public void dump() {
	System.out.println(toString());
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("Job completed in ").append((System.currentTimeMillis() - startTime) / ONE_MINUTE_IN_MILLIS).append(" minutes\n");
        builder.append(imagesDownloaded).append(" images were downloaded. Out of those ").append(highResImagesCopied).append(" high-res images were grabbed from the high-res folder\n");
        builder.append(videosDownloaded).append(" videos were downloaded. Out of those ").append(originalVideosCopied).append(" original videos were grabbed from the high-res folder\n");
        builder.append(audioFilesDownloaded).append(" audio files were downloaded. Out of those ").append(originalAudioFilesCopied).append(" original audio files were grabbed from the high-res folder\n");
        builder.append(textsDownloaded).append(" text posts were downloaded\n");
        builder.append(imagesDownloaded + videosDownloaded + textsDownloaded).append(" total items downloaded");
	return builder.toString();
    }
}
