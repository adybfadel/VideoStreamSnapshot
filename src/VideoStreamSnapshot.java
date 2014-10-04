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
			command = String.format("/opt/ffmpeg/ffmpeg -y -i %s -f image2 -vf fps=fps=1/1 -update 1 %s", urlVideo, urlImgOper);

		try {
			System.out.println(String.format("[%s] Snapshoting: %s (%s) - %s", sdf.format(new Date()), videoStream, type, command));
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
		
		if (OPERATOR.equals(type))
			listOperators.remove(videoStream);
		else
			listCustomers.remove(videoStream);
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
					// So copia o arquivo principal
					if (file.getName().equals(filter + "_android.mp4")) {
						String dest = URL_VIDEO_STREAM + "/bkp/" + file.getName();
						System.out.println("Copying " + file.getName() + " to " + dest);
						copyFile(file, new File(dest));
					}
					boolean del = file.delete();
					System.out.println("Removing " + file.getName() + " - " + del);
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
