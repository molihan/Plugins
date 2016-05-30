import java.io.IOException;


public class Launcher {

	public Launcher() {
		try {
			Process process = Runtime.getRuntime().exec("java -jar ../bin/20160519-api.jar");
			process.waitFor();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new Launcher();

	}

}
