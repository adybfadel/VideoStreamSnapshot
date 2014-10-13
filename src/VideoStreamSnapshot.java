import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VideoStreamSnapshot extends Thread {

	private static final String CUSTOMER = "CUSTOMER";
	private static final String OPERATOR = "OPERATOR";
	private static final String URL_VIDEO_STREAM = "/usr/local/WowzaStreamingEngine/content/sonyGuru";
	private static final String LOG_FILE_NAME = "/opt/sonyguru/VideoStreamSnapshot.log";
	private static final long LOG_FILE_SIZE = 5000000;
	
	private String type;
	private String videoStream;
	private static List<String> listCustomers = new ArrayList<String>();
	private static List<String> listOperators = new ArrayList<String>();
	private static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	private static SimpleDateFormat sdfBkp = new SimpleDateFormat("dd-MM-yyyy_HH-mm");
	
	private static File logFile = null;

	public VideoStreamSnapshot(String audioSream, String type) {
		this.videoStream = audioSream;
		this.type = type;
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
					if (!listOperators.contains(fileName)) {
						listOperators.add(fileName);
						new VideoStreamSnapshot(fileName, OPERATOR).start();
					}
					if (!listCustomers.contains(fileName)) {
						listCustomers.add(fileName);
						new VideoStreamSnapshot(fileName, CUSTOMER).start();
					}
				}
				Thread.sleep(3000);
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
		String urlImgCons = "/usr/share/tomcat7/webapps/sonyguru/imgs/" + videoStream + "_%1d.jpg";
		String urlImgOper = "/usr/share/tomcat7/webapps/sonyguru/imgs/" + videoStream + "_op.jpg";
		String command = String.format("/opt/ffmpeg/ffmpeg -y -i %s -f image2 -vf fps=fps=1/10 %s", urlVideo, urlImgCons);
		if (OPERATOR.equals(type))
			command = String.format("/opt/ffmpeg/ffmpeg -y -i %s -f image2 -vf fps=fps=2/1 -update 1 %s", urlVideo, urlImgOper);

		try {
			log(String.format("Snapshoting: %s (%s)", videoStream, type));
			while (true) {
				Process process = Runtime.getRuntime().exec(command);
				File streamFile = new File(URL_VIDEO_STREAM + "/" + videoStream + ".mp4");
				long lastLength = streamFile.length();
				Thread.sleep(60000);
				process.destroy();
				if (CUSTOMER.equals(type)) {
					log(String.format("Stoped: %s (%s)", videoStream, type));
					Thread.sleep(60000);
					// Verifica se ainda esta ativo antes de arquivoar
					if (streamFile.length() == lastLength) {
						log(videoStream + " streaming ended...");
						pigeonhole(videoStream);
					}
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (OPERATOR.equals(type))
			listOperators.remove(videoStream);
		else
			listCustomers.remove(videoStream);
	}
	
	
	private void pigeonhole(final String filter) {
		
		File dir = new File(URL_VIDEO_STREAM);
		File[] files = dir.listFiles(new FileFilter(filter));
		for (File file: files) {
			if (!file.isDirectory()) {
				try {
					// So copia o arquivo principal
					if (file.getName().equals(filter + ".mp4")) {
						String dest = URL_VIDEO_STREAM + "/bkp/" + file.getName().replace(".mp4", "_" + sdfBkp.format(new Date()) + "_" + file.lastModified() + ".mp4");
						log("Filing " + file.getName());
						copyFile(file, new File(dest));
					}
					boolean del = file.delete();
					log("Removing " + file.getName() + " - " + del);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
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
    
    private class FileFilter implements FilenameFilter {
    	
    	private String prefix;
    	
    	public FileFilter(String prefix) {
    		this.prefix = prefix;
    	}
    	
    	public boolean accept(File dir, String name) {
			String lowercaseName = name.toLowerCase();
			if (lowercaseName.startsWith(prefix.toLowerCase()) && lowercaseName.endsWith(".mp4")) {
				return true;
			} else {
				return false;
			}
		}
    	
    }
	
}
