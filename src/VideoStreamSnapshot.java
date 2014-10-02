import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VideoStreamSnapshot extends Thread {

	private String type;
	private String videoStream;
	private static final String CUSTOMER = "CUSTOMER";
	private static final String OPERATOR = "OPERATOR";
	private static final String URL_VIDEO_STREAM = "/usr/local/WowzaStreamingEngine/content/sonyGuru";
	private static List<String> listCustomers = new ArrayList<String>();
	private static List<String> listOperators = new ArrayList<String>();
	private static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

	public VideoStreamSnapshot(String audioSream, String type) {
		this.videoStream = audioSream;
		this.type = type;
	}

	public static void main(String[] arqs) {

		try {
			System.out.println(String.format("[%s] Video snapshoting started...", sdf.format(new Date())));
			Runtime.getRuntime().exec("sudo su");
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(1);
		}
		
		while (true) {

			try {
				File dir = new File(URL_VIDEO_STREAM);
				if (dir.listFiles() == null) {
					System.out.println("Permission denied...");
					break;
				}
				for (File file: dir.listFiles()) {
					String fileName = file.getName();
					if (!fileName.endsWith(".mp4") || fileName.indexOf("_") > -1)
						continue;
					fileName = fileName.replace(".mp4", "");
					if (!listCustomers.contains(fileName)) {
						listCustomers.add(fileName);
						new VideoStreamSnapshot(fileName, CUSTOMER).start();
					}
					if (!listOperators.contains(fileName)) {
						listOperators.add(fileName);
						new VideoStreamSnapshot(fileName, OPERATOR).start();
					}
				}
				Thread.sleep(30000);
			} catch (Exception e) {
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
			command = String.format("/opt/ffmpeg/ffmpeg -y -i %s -f image2 -vf fps=fps=3/1 -update 1 %s", urlVideo, urlImgOper);

		try {
			System.out.println(String.format("[%s] Snapshoting: %s (%s)", sdf.format(new Date()), videoStream, type));
			Process process = Runtime.getRuntime().exec(command);
			if (CUSTOMER.equals(type)) {
				Thread.sleep(60000);
				process.destroy();
			} else {
				process.waitFor();
			}
			pigeonhole(videoStream);
			System.out.println(String.format("[%s] Finished: %s (%s)", sdf.format(new Date()), videoStream, type));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (CUSTOMER.equals(type)) {
			try {
				Thread.sleep(120000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			listCustomers.remove(videoStream);
		} else {
			listOperators.remove(videoStream);
		}
	}
	
	private void pigeonhole(final String filter) {
		
		File dir = new File(URL_VIDEO_STREAM);
		
		FilenameFilter textFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				String lowercaseName = name.toLowerCase();
				if (lowercaseName.startsWith(filter) && lowercaseName.endsWith(".mp4")) {
					return true;
				} else {
					return false;
				}
			}
		};
		
		File[] files = dir.listFiles(textFilter);
		for (File file: files) {
			if (!file.isDirectory()) {
				try {
					// Soh copia o arquivo principal
					if (file.getName().equals(filter + ".mp4"))
						copyFile(file, new File(URL_VIDEO_STREAM + "/bkp/" + file.getName()));
					file.delete();
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

}
