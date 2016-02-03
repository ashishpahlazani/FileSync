package client.file_change_detector;

import java.io.File;
import java.util.TimerTask;

import common.Actions;

/**
 * @author Ashish Pahlazani
 *  Interface for file Chnage detector
 */
public abstract class FileChangeDetectorTimerTask extends TimerTask{
	/**
	 * @param file
	 * @param action
	 * 
	 * This method is called whenever there is any update in the repository
	 */
	protected abstract void onChange(File file, Actions action);
}
