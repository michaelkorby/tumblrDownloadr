import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.jsoup.Jsoup;

/**
 * Created by IntelliJ IDEA.
 * User: mkorby
 * Date: 4/12/12
 */
public class TumblrDownloader {
	
	//System-specific path prefixes
	//LAPTOP
//	private static final String GOOGLE_DRIVE_ROOT = "C:\\users\\mkorb\\My Drive";
//	private static final String CONTENT_ROOT = "p:\\";
	
	//LENOVO
	private static final String GOOGLE_DRIVE_ROOT = "C:\\users\\mkorby\\Google Drive";
	private static final String CONTENT_ROOT = "c:\\Pictures";


	private static final String WGET_EXE = GOOGLE_DRIVE_ROOT + "\\Code\\TumblrDownloadr\\wget\\wget.exe";

	private static final String DATA_NPF = "data-npf='";

	private static final String LINK_TYPE = "link";

	private static final int RETRY_ATTEMPT_COUNT = 25;

	//Parameters containing paths
	private static final String EXIF_TOOL = "c:\\exiftool\\exiftool";
	private static final File CONTENT_FOLDER = new File(CONTENT_ROOT + "\\Baby\\Tumblr Backup");
	private static final String HIGH_RES_IMAGE_FOLDER = GOOGLE_DRIVE_ROOT + "\\Riley Tumblr Staging";

	/**
	 * Properties file
	 */
	private static final String TUMBLR_PROPERTIES = GOOGLE_DRIVE_ROOT + "\\Code\\TumblrDownloadr\\tumblr.properties";
	private static final String OATH_KEY = "oath.key";

	//Tumblr settings
	private static final String TUMBLR_BLOG_URL = "https://api.tumblr.com/v2/blog/riley-k.tumblr.com/posts/";
	private static final int HTTP_SUCCESS = 200;
	private static final int WINDOW = 20;
	private static final ImagePHash IMAGE_PHASH = new ImagePHash();
	private static final int MAX_PHASH_DISTANCE = 8; //Looking at matches, anything 8 or above is going to be a different picture
	private static final String PHOTO_TYPE = "photo";
	private static final String VIDEO_TYPE = "video";
	private static final String AUDIO_TYPE = "audio";
	private static final String TEXT_TYPE = "text";
	private static final String CHAT_TYPE = "chat";
	private static final String QUOTE_TYPE = "quote";

	//Here's how this pattern works. We're looking for a URL inside a bunch of HTML. The URL starts with the prefix visible in the pattern
	//we then go on to follow that with a [^']* which matches everything except an ' (as the URL is delimited by apostrophes
	//Then we close the group, put in the terminating apostrophe and finish the pattern
	//This used to be necessary in the old video JSON but Tumblr has fixed that and put this thing into a parameter. Keeping for posterity.
	//private static final Pattern EMBED_CODE_URL_PATTERN = Pattern.compile(".*(http://api.tumblr.com[^']*)'.*");

	private static final String INFO_EXTENSION = "info";
	private static final String DOWNLOAD_PASS_PREFIX = "Download Pass";
	private static final SimpleDateFormat DOWNLOAD_PASS_FILENAME_FORMAT = new SimpleDateFormat("'" + DOWNLOAD_PASS_PREFIX + " 'MM-dd-yyyy'." + INFO_EXTENSION + "'");
	private static final SimpleDateFormat TUMBLR_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss Z");
	private static final SimpleDateFormat POST_FILENAME_FORMAT = new SimpleDateFormat("'Post for 'MMddyyyy hh.mm'.txt'");
	private static final String JPG = "jpg";
	private static final String PNG = "png";
	private static final String ILLEGAL_CHARACTERS_IN_FILENAME = "[â€™/:?\\\\]";
	private static final String[] VIDEO_EXTENSIONS = new String[]{"mp4", "mov", "wmv", "m4v"};
	private static final String[] AUDIO_EXTENSIONS = new String[]{"wav", "mp3", "m4a"};
	private static final double HALF_OF_PI = Math.PI / 2;
	private static final SimpleDateFormat EXIF_TOOL_ALL_DATES_COMMAND_FORMAT = new SimpleDateFormat("'-AllDates='''yyyy:MM:dd HH:mm:ss''");
	private static final String DOUBLE_ESCAPED_QUOTE_CHARACTER = "\\\\\"";

	private final HashMap<String,String> _highResImages = new HashMap<String, String>();
	private final Statistics _stats = new Statistics();
	private TreeMap<Date,File> _videosByDate = new TreeMap<Date, File>();

	public TumblrDownloader() throws Exception {
		//Find out where we should end the download
		final Collection<File> downloadPassFiles;
		try {
			downloadPassFiles = FileUtils.listFiles(CONTENT_FOLDER, new WildcardFileFilter(DOWNLOAD_PASS_PREFIX + "*." + INFO_EXTENSION), null);
		} catch (IllegalArgumentException ie) {
			throw new RuntimeException("Looks like " + CONTENT_FOLDER + " is not reachable from this computer. Running on the wrong machine?", ie);
		}
		File newestFile = null;
		for (final File downloadPassFile : downloadPassFiles) {
			if (newestFile == null || FileUtils.isFileNewer(downloadPassFile, newestFile)) {
				newestFile = downloadPassFile;
			}
		}

		//Get the OAuth Key
		final Properties properties = new Properties();
		final InputStream fileInputStream;
		try {
			fileInputStream = new FileInputStream(TUMBLR_PROPERTIES);
		} catch (FileNotFoundException ie) {
			throw new RuntimeException("Cannot reach the properties file on Google Drive. Maybe not connected to Google Drive?", ie);
		}
		properties.load(fileInputStream);
		final String oauthKey = properties.getProperty(OATH_KEY);

		//Get the ID of the last post we successfully processed. We will process until we get to it
		final String idOfLastPassPost = getIdOfLastPassPost(newestFile);

		//Get all the posts
		int gotten = WINDOW;
		boolean done = false;

		//Go through and downlaod all the posts that we're going to need to process
		final LinkedList<JSONObject> allPostsWeNeedToDownload = new LinkedList<JSONObject>();

		int retryCount = 0;
		for (int offset = 0; gotten == WINDOW && !done; offset += gotten) {
			final JSONObject response = getRequest(TUMBLR_BLOG_URL, oauthKey, offset);
			final JSONArray posts;
			try {
				posts = (JSONArray) response.get("posts");
			} catch (final NullPointerException npe) {
				//This can happen when Tumblr fails to respond correctly. If this does, we want to jump back and re-do this.
				//Since gotten has been added to the offset for this iteration, let's subtract it to re-do this offset.
				System.err.println("NPE caught when trying to read JSON results. Rertying.");
				offset -= gotten;
				if (++retryCount < 10) {
					continue;
				} else {
					fileInputStream.close();
					throw new RuntimeException("Cannot connect to tumblr.com. Tried 10 times. Quitting");
				}
			}
			retryCount = 0;
			gotten = posts.size();

			//Go through all the posts
			for (final Object post : posts) {
				final JSONObject postObj = (JSONObject) post;

				//Get the ID of the post
				final Long postID = (Long)postObj.get("id");

				//Get the date of the post
				if (idOfLastPassPost != null && idOfLastPassPost.equals(String.valueOf(postID))) {
					//That's it, everything else was already done
					done = true;
					break;
				}

				//UNCOMMENT WHEN DONE
				allPostsWeNeedToDownload.add(postObj);


				//If you want to drill in on just one post, use this code in place of the above
				//                System.out.println(postID);
				//                                if (postID == 617750062949400576l) {
				//                
				//                                    allPostsWeNeedToDownload.add(postObj);
				//                                }

			}
		}

		if (!done && idOfLastPassPost != null) {
			//We do have a record with the last post ID set but we were unable to find it. That's bad. 
			//If we don't catch this here, the script will just download everything from birth.

			throw new RuntimeException("Last Post ID of " + idOfLastPassPost + " was not found on the site. " +
					"This can happen if the post in question was deleted. To fix, find the ID of the post at which the " +
					"downloader stopped and update the last Download Pass file with that ID. To troubleshoot, go to the page " +
					"for this post on the web and the ID will be in the URL");
		}

		//Now that we have all the posts, download them in reverse order
		Collections.reverse(allPostsWeNeedToDownload);
		for (final JSONObject postObj : allPostsWeNeedToDownload) {

			//Get the ID of the post
			final Long postID = (Long)postObj.get("id");

			final String dateTimeString = (String)postObj.get("date");
			final Date postDate = TUMBLR_DATE_FORMAT.parse(dateTimeString);

			String postType = (String) postObj.get("type");
			final String tags = getTags(postObj);

			//For some reason, text posts can be either text posts or video posts. Determine which it is:
			if (TEXT_TYPE.equals(postType) &&  ((String)postObj.get("body")).indexOf(DATA_NPF) > -1) {
				postType = VIDEO_TYPE;
			}

			if (PHOTO_TYPE.equals(postType)) {
				//This is a photo
				processPhoto(postObj, postDate, tags);
			} else if (VIDEO_TYPE.equals(postType)) {
				//This is a video
				processVideoOrAudio(postObj, postDate, tags, true);
			} else if (TEXT_TYPE.equals(postType) || CHAT_TYPE.equals(postType)) {
				//Text or chat post, they both have a body tag
				processText(postObj, postDate, tags, "body");
			} else if (QUOTE_TYPE.equals(postType)) {
				//Quote post has a text tag
				processText(postObj, postDate, tags, "text");
			} else if (AUDIO_TYPE.equals(postType)) {
				//Audio
				processVideoOrAudio(postObj, postDate, tags, false);
			} else if (LINK_TYPE.equals(postType)) {
				//Link
				processLink(postObj, postDate, tags);
			} else {
				throw new RuntimeException("Encountered unknown post type " + postType + ". Unable to process. Halting processing.");
			}


			//Create the info file with information about this download batch
			//Do this after each file is processed in case the job dies mid-stream
			final String downloadPassFilename = DOWNLOAD_PASS_FILENAME_FORMAT.format(new Date());
			createInfoFile(CONTENT_FOLDER.getAbsolutePath() + File.separator + downloadPassFilename,
					postID +
					"\nDownload and copyover completed on \n" + TUMBLR_DATE_FORMAT.format(new Date()) +
					"\n" + _stats.toString(),
					null, null, null);
		}

		System.err.println("Done processing posts published since the last known post.\n");
		_stats.dump();
	}

	/*
	 * Process link post
	 */
	private void processLink(JSONObject postObj, Date postDate, String tags) throws IOException {
		//Skipping links!

	}

	/**
	 * Process a text post
	 * @param postObj
	 * @param postDate
	 * @param tags
	 */
	private void processText(final JSONObject postObj, final Date postDate, final String tags, final String bodyTagName) throws IOException {
		final String body = stripCaption((String) postObj.get(bodyTagName));
		final Object title = postObj.get("title");
		final File file = new File(CONTENT_FOLDER, POST_FILENAME_FORMAT.format(postDate));
		createInfoFile(file.getAbsolutePath(), body,  tags, (String)title, postDate);
		_stats.textsDownloaded++;
	}

	/**
	 * Go through this file and return the ID of the last successful post contained on the first line
	 * @param newestFile
	 * @return
	 */
	private String getIdOfLastPassPost(final File newestFile) throws IOException, ParseException {
		if (newestFile == null) {
			return null;
		}
		final FileReader fileReader = new FileReader(newestFile);
		final LineNumberReader reader = new LineNumberReader(fileReader);

		//The ID will be on the first line
		final String idString = reader.readLine();
		fileReader.close();
		return idString;
	}

	/**
	 * Process video file
	 *
	 *
	 * @param postObject
	 * @param postTimestamp
	 * @param tags
	 * @throws Exception 
	 */
	private void processVideoOrAudio(final JSONObject postObject, final Date postTimestamp, final String tags, final boolean video) throws Exception {

		String caption = getAndStripCaption(postObject);

		final String embedUrlParameter = video ? "video_url" : "audio_url";

		String embedUrl = (String)postObject.get(embedUrlParameter);

		//There is a whole separate pattern for videos that are listed as text posts
		if (embedUrl == null) {
			embedUrl = getEmbedUrlFromDataNpfTag(postObject);
		}


		if (!video) {
			//Audio files require a bizarre set of manipulation to the URL to be performed before they can be accessed. The original URL returns a 403.

			//First, we need to strip out the last portion after the slash
			//Then, stuff it into the following prefix and suffix to make a valid URL. This appears to work, according to https://groups.google.com/forum/#!topic/tumblr-api/xQIPlEtMZ3Q
			embedUrl = "http://a.tumblr.com/" + getFilenameFromUrl(embedUrl) + "o1.mp3";
		}
		boolean downloaded = true;

		//Let's see if there's a matching one in the high-res folder. If so, we don't need to bother downloading
		
		//If there is one video for that date then we can definitively pick it
		final File newFileName;
		final Collection<File> matchingOriginals = getVideosOrAudiofilesForDate(postTimestamp, 0, video  ? VIDEO_EXTENSIONS : AUDIO_EXTENSIONS);
		if (matchingOriginals.size() == 1 && matchingOriginals.iterator().next().canRead()) {
			//There is just one match. Great. We should take this file.
			//Sometimes the next-day matching is a little overzealous and will pick up a file from the next day that's not the right one
			//it will already have been copied and will disappear from the original folder, so the 2nd clause is necessary
			newFileName = takeOriginalFileOverDownloadedOne(null, matchingOriginals.iterator().next());
			if (video) {
				_stats.originalVideosCopied++;
			} else {
				_stats.originalAudioFilesCopied++;
			}

		} else if (embedUrl != null) {
			//We cannot definitively identify the original video file in the library. Download it.
			File downloadedFile = null;
			for (int i = 0; i <= RETRY_ATTEMPT_COUNT; i++) {
				try {
					downloadedFile = downloadFile(embedUrl, CONTENT_FOLDER);
					//Downloaded successfully
					break;
				} catch (final RuntimeException | InterruptedException e) {
					//Sometimes this errors out for no obvious reason.
					if (i == RETRY_ATTEMPT_COUNT) {
						//We tried 5 times and it's not better
						System.err.println("Tried to download video or audio file " + RETRY_ATTEMPT_COUNT + " times to no avail.");
						throw e;
					}
				}
			}

			String captionForFilename = caption;
			if (StringUtils.isBlank(captionForFilename)) {
				captionForFilename = downloadedFile.getName();
			}
			if (captionForFilename.endsWith(".")) {
				captionForFilename = StringUtils.chop(captionForFilename);
			}

			//Remove any characters that can't be in filenames
			captionForFilename = captionForFilename.replaceAll(ILLEGAL_CHARACTERS_IN_FILENAME, "");
			final String extension = downloadedFile.getName().substring(downloadedFile.getName().lastIndexOf('.'));
			newFileName = new File(downloadedFile.getParentFile(), captionForFilename + extension);
			if (newFileName.exists()) {
				//A file of this name already exists. Delete it first
				newFileName.delete();
			}

			updateFileTimestamp(postTimestamp, downloadedFile);

			final boolean renamedSuccessfully = downloadedFile.renameTo(newFileName);
			if (!renamedSuccessfully) {
				throw new RuntimeException("Unable to rename movie file to " + newFileName);
			}
		} else {
			//This is a YouTube video or something else that we can't download. Make a fake filename
			//to create the associated info file. 
			newFileName = new File(CONTENT_ROOT, caption+".mov");
			downloaded = false;
		}

		createInfoFile(newFileName, "Caption: " + caption, tags, null, postTimestamp);
		if (downloaded) {
			if (video) {
				_stats.videosDownloaded++; 
			} else {
				_stats.audioFilesDownloaded++; 
			}
		}
	}


	/**
	 * Get the embedUrl for videos from the body tag of the data-npf style
	 * @param postObject
	 * @return
	 */
	private String getEmbedUrlFromDataNpfTag(final JSONObject postObject) {
		//First, let's check if this is a YouTube embed. YouTube videos can't be downloaded
		if (postObject.get("video") instanceof JSONObject && ((JSONObject)postObject.get("video")).get("youtube") != null) {
			//This is a YouTube video. We can't download it.
			return null;
		}
		
		String body = (String)postObject.get("body");
		final int startIndex = body.indexOf(DATA_NPF);
		if (startIndex > -1) {
			//This is the proper format
			int endStart = startIndex + DATA_NPF.length();
			final int endIndex = body.indexOf("'",endStart);
			String dataNpfJson = body.substring(endStart, endIndex);
			final JSONObject dataNpfObj = (JSONObject) JSONValue.parse(dataNpfJson);
			return (String)dataNpfObj.get("url");

		}
		return null;
	}

	private void updateFileTimestamp(final Date postTimestamp, final File file) {
		if (postTimestamp != null) {
			file.setLastModified(postTimestamp.getTime());
		}



	}

	private String getAndStripCaption(final JSONObject postObject) {
		return stripCaption(getStringProperty(postObject, "summary"));
	}
	
	/**
	 * Go through all of the videos in the high-res folder and arrange them by date
	 * Find all of the videos for the given date
	 * @return
	 * @param postTimestamp
	 */
	private LinkedList<File> getVideosOrAudiofilesForDate(final Date postTimestamp, final int offset, final String[] extensions) {
		if (_videosByDate.isEmpty()) {
			for (final File file : getAllFilesByExtension(HIGH_RES_IMAGE_FOLDER, extensions)) {
				_videosByDate.put(new Date(file.lastModified()), file);
			}
		}

		LinkedList<File> filesForDate = new LinkedList<File>();
		final Date adjustedPostTimestamp = DateUtils.addDays(postTimestamp, offset);
		for (final Date key : _videosByDate.descendingKeySet()) {
			if (DateUtils.isSameDay(key, adjustedPostTimestamp)) {
				filesForDate.add(_videosByDate.get(key));
			}
		}

		if (filesForDate.isEmpty() && offset == 0) {
			//For some reason, a lot of the movies on disk are datestamped for the day after the tumblr timestamp. Let's try that
			filesForDate = getVideosOrAudiofilesForDate(postTimestamp, offset+1, extensions);

		}
		return filesForDate;
	}

	/**
	 * Go through all the high res images in the high res folder and get their  hashes
	 *
	 * @return
	 */
	private void hashHighResImagesIfNeeded() throws Exception {
		if (_highResImages.isEmpty()) {
			File[] allFiles = getAllFilesByExtension(HIGH_RES_IMAGE_FOLDER, JPG, PNG);
			if (allFiles == null) {
				throw new RuntimeException("No high res image folder found: "+ HIGH_RES_IMAGE_FOLDER);
			}
			for (final File file : allFiles) {
				String compareToHash = null;
				//The image might need to be rotated first. Let's get the orientation from EXIF
				final JobResults jobResults = new JobResults(EXIF_TOOL, "-S", "-Orientation", file.getAbsolutePath()).invoke();
				if (jobResults.getExitVal() == 0) {
					//Succeeded. Let's read the output
					final String orientationString = jobResults.getStdout();
					final Orientation orientation = Orientation.getOrientation(orientationString);
					if (orientation == null) {
						//Unexpected orientation encountered
						throw new RuntimeException("Unexpected orientation encountered for file " + file.getAbsolutePath() + ": \"" + orientationString + "\"");
					}
					if (orientation.getRotationAngle() != 0) {
						//Need to rotate
						final BufferedImage rotated = createRotatedCopy(file, orientation);
						compareToHash = IMAGE_PHASH.getHash(rotated);
					}
				}  else {
					System.err.println("The Get Orientation operation returned with a " + jobResults.getExitVal() + " status\n");
					System.err.println(file.getAbsolutePath() + "\n" + jobResults.getStdout() + '\n' + jobResults.getStderr());
					throw new RuntimeException("Cannot get orientation");
				}

				if (compareToHash == null) {
					//This means that we didn't  need to mess with rotation. Just get the image hash
					compareToHash = IMAGE_PHASH.getHash(new FileInputStream(file));
				}
				_highResImages.put(file.getAbsolutePath(), compareToHash);
			}
		}
	}

	/**
	 * Rotate an image in memory
	 * @param rotatee
	 * @param orientation
	 * @return
	 * @throws java.io.IOException
	 */
	private BufferedImage createRotatedCopy(final File rotatee, final Orientation orientation) throws IOException {
		final BufferedImage img = ImageIO.read(rotatee);
		switch (orientation) {
		case NinetyDegreeClockwise:
			return rotateNinetyDegrees(HALF_OF_PI, img);
		case OneEightyDegrees:
			//Rotate to the right twice.
			final BufferedImage firstRotation = rotateNinetyDegrees(HALF_OF_PI, img);
			return rotateNinetyDegrees(HALF_OF_PI, firstRotation);
		case Blank: //THIS IS WRONG, OF COURSE, BUT IF WE NEED TO GO CCW, ENABLE THIS ENUM VALUE
			return rotateNinetyDegrees(-HALF_OF_PI, img);
		default:
			throw new RuntimeException("Orientation " + orientation + " is not currently supported by the rotation algorithm. Please add.");
		}
	}

	private BufferedImage rotateNinetyDegrees(final double theta, final BufferedImage img) {
		final int w = img.getWidth();
		final int h = img.getHeight();

		final BufferedImage rot = new BufferedImage(h, w, BufferedImage.TYPE_INT_RGB);
		final AffineTransform xform = new AffineTransform();
		xform.translate(0.5*h, 0.5*w);
		xform.rotate(theta);
		xform.translate(-0.5*w, -0.5*h);

		final Graphics2D g = (Graphics2D) rot.createGraphics();
		g.drawImage(img, xform, null);
		g.dispose();

		return rot;
	}


	private static File[] getAllFilesByExtension(final String directory, final String... extensions) {
		return new File(directory).listFiles(new FilenameFilter() {
			@Override
			public boolean accept(final File dir, final String name) {
				for (final String extension : extensions) {
					if (StringUtils.endsWithIgnoreCase(name, extension)) {
						return true;
					}
				}
				return  false;
			}
		});
	}

	/**
	 * Given a photo object, get it and save it with all of its info
	 */
	private void processPhoto(final JSONObject photoPost, final Date postTimestamp, final String tags) throws Exception {
		hashHighResImagesIfNeeded();

		final String caption = getAndStripCaption(photoPost);

		final JSONArray photos = (JSONArray) photoPost.get("photos");
		for (final Object photo : photos) {
			final String individualCaption = stripCaption(getStringProperty(photo, "caption"));
			final Object originalSize = ((JSONObject) photo).get("original_size");
			final String photoUrl = getStringProperty(originalSize, "url");

			File downloadedFile = downloadFile(photoUrl, CONTENT_FOLDER);

			//Now that we've downloaded the image from tumblr, let's see if there's a high res copy in the originals folder
			final FileInputStream fileInputStream = new FileInputStream(downloadedFile);
			final String thisImageHash = IMAGE_PHASH.getHash(fileInputStream);
			fileInputStream.close();

			int minDistance = Integer.MAX_VALUE;
			LinkedList<String> matchingFilenames = null;
			for (final String babyFile : _highResImages.keySet()) {
				final String babyHash = _highResImages.get(babyFile);
				final int distance = IMAGE_PHASH.distance(thisImageHash, babyHash);

				if (distance < minDistance) {
					minDistance = distance;
					matchingFilenames = new LinkedList<String>();
					matchingFilenames.add(babyFile);
				} else if (distance == minDistance) {
					matchingFilenames.add(babyFile);
				}
			}

			//Let's see how far we are from anything in the folder
			boolean usingHighResImage = false;
			if (minDistance <= MAX_PHASH_DISTANCE && matchingFilenames.size() == 1) {
				//Anything over this threshold is definitely not a match and if more than 1 images matched then we won't know which to pick

				//Copy the original file over the downloaded file
				final File highResFile = new File(matchingFilenames.get(0));
				if (!highResFile.canRead()) {
					//How can this be, it was there when we started. It must have gotten mismatched
					throw new RuntimeException("High-res file " + highResFile.getAbsolutePath() + " is not found. It must have been matched to an earlier image " +
							"or it occurs on the website twice.");
				}
				usingHighResImage = true;

				downloadedFile = takeOriginalFileOverDownloadedOne(downloadedFile, highResFile);
				_stats.highResImagesCopied++;

			} else {
				//If we don't have a matching file then let's at least update the timestamp of the tumblr one
				final String allDatesParameter = EXIF_TOOL_ALL_DATES_COMMAND_FORMAT.format(postTimestamp);

				try {
					final JobResults jobResults = new JobResults(EXIF_TOOL, allDatesParameter, "-overwrite_original", downloadedFile.getAbsolutePath()).invoke();
					if (jobResults.getExitVal() != 0) {
						System.err.println("Unable to change the dates for this image\n");
						System.err.println(downloadedFile.getAbsolutePath() + "\n" + jobResults.getStdout() + '\n' + jobResults.getStderr());
						throw new RuntimeException("Unable to change the dates for image");
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}

			//Annotate the file with the caption
			final String totalCaption = StringUtils.isBlank(individualCaption) ? caption : individualCaption;

			if (getExtension(downloadedFile.getName()).equals(JPG)) {
				//Update EXIF only for JPGs. Doesn't work for PNGs.
				updateExifForJPG(totalCaption, tags, downloadedFile, postTimestamp, usingHighResImage);
			} else {
				createInfoFile(downloadedFile, totalCaption, tags, null, postTimestamp);
			}
			_stats.imagesDownloaded++;
		}
	}

	private File takeOriginalFileOverDownloadedOne(final File downloadedFile, final File highResFile) throws IOException {
		final File movedFile = new File(CONTENT_FOLDER, highResFile.getName());

		if (movedFile.canRead()) {
			//This means that we already have a high-res file of the same name. Rename it by prepending a 1 to it.
			final File existingFile = movedFile;
			if (!existingFile.renameTo(new File(CONTENT_FOLDER, "1" + movedFile.getName()))) {
				//Unable to rename the file. We're about to clobber it if we proceed
				throw new RuntimeException("Could not rename " + movedFile.getAbsolutePath() + " by prepending a 1 to its name. This was necessary because a new high-res file of the same name is about to be copied into the folder. Cannot continue");
			}
		}

		//Copy the file over. Moving it as one operation doesn't always work if it can't delete the original file. Copying first and deleting the file afterwards appears to work.
		FileUtils.copyFileToDirectory(highResFile, CONTENT_FOLDER);

		//Check to see that the copyover worked
		if (!movedFile.canRead()) {
			//Sometimes the whole thing doesn't work. This happens very rarely. Try again.
			System.err.println("Failed to successfully copy over " + highResFile.getAbsolutePath() + ". Retrying\n");
			return takeOriginalFileOverDownloadedOne(downloadedFile, highResFile);
		}

		//Now, delete the original high-res file we just copied
		deleteFile(highResFile);

		//Delete the tumblr file
		deleteFile(downloadedFile);

		return movedFile;
	}

	private void deleteFile(final File deleteMe) {
		if (deleteMe != null) {
			if (!deleteMe.delete()) {
				//Could not delete the file. This can sometimes happen in the case of the original high-res file when the job erroneously thinks that this file is being used by someone else. That's usally not the case but it happens.
				System.err.println("Could not delete original file \"" + deleteMe.getAbsolutePath() + "\". Continuing.\n");
			}
		}
	}


	private String getTags(final JSONObject photoPost) {
		final Object tags = photoPost.get("tags");
		final StringBuilder tagsBuilder = new StringBuilder();
		for (final Object tag : (JSONArray) tags) {
			tagsBuilder.append(tag).append("; ");
		}
		return tagsBuilder.toString();
	}

	private void updateExifForJPG(String totalCaption, final String tags, final File downloadedFile, final Date postTimestamp, final boolean usingHighResImage) throws IOException, InterruptedException {

		//Escape any quotes in the caption as they will destroy the exiftool commandline
		totalCaption = totalCaption.replaceAll("\"", DOUBLE_ESCAPED_QUOTE_CHARACTER);

		//Replace Tumblr-style quotes with regular quotes that will show up correctly in the EXIF data
		//NOTE: This line requires the -encoding ISO-8859-1 switch to be used while compiling. Otherwise these quote characters make no sense to the compiler.
		totalCaption = totalCaption.replaceAll("[â€�â€œ]", DOUBLE_ESCAPED_QUOTE_CHARACTER);

		//Replace Tumblr apostrophes with regular apostrophes
		totalCaption = totalCaption.replaceAll("â€™", "'");

		final ArrayList<String> commandArray = new ArrayList<String>();
		commandArray.add(EXIF_TOOL);
		//-P: Do not alter timestamp
		commandArray.add("-P");
		//Caption is read by albump.pl 
		commandArray.add("-Caption=" + totalCaption);
		//Title is read by windows in properties
		commandArray.add("-Title=" + totalCaption);
		//Tags are set into the Tags field in Windows by putting them into the Subject field.  No idea why this works. The Tags field is read-only
		commandArray.add("-Subject=" + tags);
		commandArray.add(downloadedFile.getAbsolutePath());

		final JobResults jobResults = new JobResults(commandArray.toArray(new String[commandArray.size()])).invoke();
		final int exitVal = jobResults.getExitVal();
		if (exitVal != 0) {
			//There was an error. Grab the error and the output
			final String stderr = jobResults.getStderr();
			final String stdout = jobResults.getStdout();
			if (stderr.startsWith("ERROR>Warning")) {
				//This really isn't a problem. Ignore
			} else if (stderr.contains("Not a valid PNG")) {
				//This is fine. Let's just make a file alongside it that has the info
				createInfoFile(downloadedFile, totalCaption, tags, null, postTimestamp);
			} else {
				throw new RuntimeException("ExifTool returned an error:\n " + stderr + '\n' + stdout);
			}
		}

		//Delete the artifact _original file
		new File(downloadedFile.getAbsolutePath() + "_original").delete();
	}

	private void createInfoFile(final File fileWhoseNameToMimic, final String caption, final String tags, final String title, final Date postTimestamp) throws IOException {
		final String extension = getExtension(fileWhoseNameToMimic.getAbsolutePath());
		final String infoFilename = fileWhoseNameToMimic.getAbsolutePath().replace(extension, INFO_EXTENSION);
		createInfoFile(infoFilename, caption, tags, title, postTimestamp);
	}

	private void createInfoFile(final String infoFilenameAbsolutePath, final String caption, final String tags, final String title, final Date postTimestamp) throws IOException {
		final File file = new File(infoFilenameAbsolutePath);
		final FileWriter fileWriter = new FileWriter(file);
		if (StringUtils.isNotBlank(title)) {
			fileWriter.write("Title: " + title + "\n\n");
		}
		fileWriter.write(caption + '\n');
		if (StringUtils.isNotBlank(tags)) {
			fileWriter.write("Tags: " + tags + '\n');
		}
		if (postTimestamp != null) {
			fileWriter.write("Date: " + postTimestamp + '\n');
		}
		fileWriter.flush();
		fileWriter.close();
		updateFileTimestamp(postTimestamp, file);
	}

	private String stripCaption(final String caption) {
		String stripped = Jsoup.parse(caption).text();
		stripped = StringEscapeUtils.unescapeHtml3(stripped);
		return stripped.replaceAll("`","'");
	}

	private File downloadFile(final String fileURL, final File folder) throws IOException, InterruptedException {
		final String imageFilename = getFilenameFromUrl(fileURL);
		
		//The downloader calls an external wget.exe to download the file (image or video) from the given imgURL. This didn't use to be necessary but Tumblr changed something
		//and the image URL returns an HTML page with a bunch of mark-up around the image. The URL for the image itself is the same. If we use Java's URL class to download it
		//we always get the HTML page now instead of the image. Wget seems to work around that (some flags in the request maybe?) and gets just the image itself. This issue only
		//affects images and not videos but this method takes care of both.
		final ProcessBuilder pb = new ProcessBuilder(WGET_EXE,fileURL);
		pb.directory(folder); 

		// redirecting error stream 
		pb.redirectErrorStream(true); 
		final Process p = pb.start();

		//This is just to show what it's doing.
		final BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream())); 
		String s = null; 
		while ((s = stdInput.readLine()) != null) { 
			System.out.println(s); 
		} 
		
		p.waitFor();
		return new File(folder, imageFilename);
	}

	private String getFilenameFromUrl(final String imgURL) {
		return imgURL.substring(imgURL.lastIndexOf("/") + 1);
	}


	private String getExtension(final String imageFilename) {
		return imageFilename.substring(imageFilename.lastIndexOf('.') + 1).toLowerCase();
	}

	private String getStringProperty(final Object jsonObject, final String property) {
		return (String) ((JSONObject) jsonObject).get(property);
	}

	/**
	 * Execute a given request and return the JSONObject
	 *
	 * @param requestUrl
	 * @param apiKey
	 * @param offset
	 * @return
	 */
	public JSONObject getRequest(final String requestUrl, final String apiKey, final Integer offset) {
		JSONObject result = null;
		try {
			final URL serverAddress = new URL(requestUrl + "?api_key=" + apiKey + (offset == null ? "" : "&offset=" + offset));
			final HttpURLConnection connection = (HttpURLConnection) serverAddress.openConnection();
			connection.connect();
			final int rc = connection.getResponseCode();
			if (rc == HTTP_SUCCESS) {
				String line = null;
				final BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				final StringBuilder sb = new StringBuilder();
				while ((line = br.readLine()) != null)
					sb.append(line + '\n');
				final JSONObject obj = (JSONObject) JSONValue.parse(sb.toString());
				result = (JSONObject) obj.get("response");
			} else {
				System.err.println("HTTP error:" + rc);
			}
			connection.disconnect();
		} catch (java.net.MalformedURLException e) {
			e.printStackTrace();
		} catch (java.net.ProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static void main(final String... args) {
		try {
			new TumblrDownloader();
		} catch (final Throwable ie) {
			ie.printStackTrace();
		}
	}

}
