import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VideoStreamSnapshot extends Thread {

	private String videoStream;
	private static final String URL_VIDEO_STREAM = "/usr/local/WowzaStreamingEngine/content/sonyGuru";
	private static List<String> listStreams = new ArrayList<String>();
	private static SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:MM:ss");

	public VideoStreamSnapshot(String audioSream) {
		this.videoStream = audioSream;
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
				for (File file : dir.listFiles()) {
					String fileName = file.getName();
					if (!fileName.endsWith(".mp4") || fileName.indexOf("_") > -1)
						continue;
					fileName = fileName.replace(".mp4", "");
					if (!listStreams.contains(fileName)) {
						listStreams.add(fileName);
						new VideoStreamSnapshot(fileName).start();
					}
				}
				Thread.sleep(3000);
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	@Override
	public void run() {

		String urlVideo = "rtsp://localhost:1935/sonyGuru/" + videoStream;
		String urlImage = "/usr/share/tomcat7/webapps/sonyguru/imgs/" + videoStream + "_%1d.jpg";
		String cmd = String.format("sudo /opt/ffmpeg/ffmpeg -y -i %s -f image2 -vf fps=fps=1/10 %s", urlVideo, urlImage);

		try {
			System.out.println(String.format("[%s] Snapshoting: %s", sdf.format(new Date()), videoStream));
			File file = new File(URL_VIDEO_STREAM + "/" + videoStream + ".mp4");
			while (file.isFile()) {
				Process process = Runtime.getRuntime().exec(cmd);
				Thread.sleep(60000);
				process.destroy();
				Thread.sleep(120000);
			}
			System.out.println(String.format("[%s] Finished: %s", sdf.format(new Date()), videoStream));
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		listStreams.remove(videoStream);
	}

}
