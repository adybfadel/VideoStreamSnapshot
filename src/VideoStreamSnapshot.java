import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VideoStreamSnapshot extends Thread {

	private static final String URL_VIDEO_STREAM = "/usr/local/WowzaStreamingEngine/content/sonyGuru";
	private static final String LOG_FILE_NAME = "/opt/sonyguru/VideoStreamSnapshot.log";
	private static final long LOG_FILE_SIZE = 5000000;
	private static final long SNAPSHOT_INTERVAL = 30000;
	
	private int idx = 1;
	private String videoStream;
	private static List<String> streamList = new ArrayList<String>();
	private static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	private static File logFile = null;

	public VideoStreamSnapshot(String audioSream) {
		this.videoStream = audioSream;
	}

	public static void main(String[] args) {

		if (args.length == 0 || !"-v".equals(args[0])) {
			try {
				logFile = new File(LOG_FILE_NAME);
				if (logFile.exists()) {
					copyFile(logFile, new File(logFile.getAbsolutePath().replace(".log", "_" + logFile.lastModified() + ".log")));
					logFile.delete();
					logFile.createNewFile();
				}
				System.setOut(new PrintStream(logFile));
			} catch (Exception e) {
				System.out.println("Error opening log file: " + logFile.getAbsolutePath());
				e.printStackTrace();
			}
		}
		
		try {
			log("Video snapshoting started...");
			Runtime.getRuntime().exec("sudo su");
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		
		while (true) {

			try {
				File dir = new File(URL_VIDEO_STREAM);
				if (dir.listFiles() == null) {
					log("Permission denied...");
					break;
				}
				for (File file: dir.listFiles()) {
					String fileName = file.getName();
					if (!fileName.endsWith(".mp4") || fileName.indexOf("_") > -1)
						continue;
					fileName = fileName.replace(".mp4", "");
					if (!streamList.contains(fileName)) {
						streamList.add(fileName);
						new VideoStreamSnapshot(fileName).start();
					}
				}
				Thread.sleep(SNAPSHOT_INTERVAL); // Espera igual periodo para prox. snapshot
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}
	
	private static void log(String log) {
		System.out.println(String.format("[%s] %s", sdf.format(new Date()), log));
		if (logFile != null && logFile.length() >= LOG_FILE_SIZE) {
			try {
				copyFile(logFile, new File(logFile.getAbsolutePath().replace(".log", "_" + System.currentTimeMillis() + ".log")));
				logFile.delete();
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {

		String urlVideo = "rtsp://localhost:1935/sonyGuru/" + videoStream;
		String urlImgCons = "/usr/share/tomcat7/webapps/sonyguru/imgs/" + videoStream;

		try {
			while (true) {
				log(String.format("Snapshot: %s", videoStream));
				
				String command = String.format("/opt/ffmpeg/ffmpeg -y -i %s -vf thumbnail=10 -frames:v 1 %s_%s.jpg", urlVideo, urlImgCons, idx++);
				Process process = Runtime.getRuntime().exec(command);
				process.waitFor();
				
				Thread.sleep(SNAPSHOT_INTERVAL); // Espera igual periodo para prox. snapshot
				
				if (idx > 6) {
					idx = 1;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		streamList.remove(videoStream);
	}
	
    public static void copyFile(File source, File destination) throws IOException {
        if (destination.exists())
            destination.delete();
        destination.createNewFile();

        FileChannel sourceChannel = null;
        FileChannel destinationChannel = null;

        try {
            sourceChannel = new FileInputStream(source).getChannel();
            destinationChannel = new FileOutputStream(destination).getChannel();
            sourceChannel.transferTo(0, sourceChannel.size(),
                    destinationChannel);
        } finally {
            if (sourceChannel != null && sourceChannel.isOpen())
                sourceChannel.close();
            if (destinationChannel != null && destinationChannel.isOpen())
                destinationChannel.close();
       }
    }
}
