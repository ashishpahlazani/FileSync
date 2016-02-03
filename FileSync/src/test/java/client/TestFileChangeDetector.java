package client;

import java.util.Timer;

import org.junit.Test;

public class TestFileChangeDetector {

	@Test
	public void addNewFile_FileShouldBeDetected_FileDetected() {
		String path = "F:/FileSync";
		//FileChangeDetectorImpl fcd = new FileChangeDetectorImpl(path);
		
		Timer timer = new Timer();
		//timer.schedule(fcd, 1000);
	}

}
