package client.file_change_detector;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import common.Actions;

/**
 * @author Ashish Pahlazani
 * This class checks if there is any change in the files inside rootFolder
 * It loads all the files present in the root folder and stores them in a Map
 * and scans the folder structure after specified interval and compares the timestamp with older timestamps
 * if there is a change in last modified time of any file, then onChange method is called with appropriate Actions type
 */
public abstract class ScanBasedFileChangeDetector extends FileChangeDetectorTimerTask {
    private static final Logger logger = Logger.getLogger(ScanBasedFileChangeDetector.class);
    
	private String path;
	private Map<File, Long> originalFilesMap = new HashMap<File, Long>();

	private FilenameFilter directoryFilter = new FilenameFilter() {
		@Override
		public boolean accept(File current, String name) {
			return new File(current, name).isDirectory();
		}
	};

	public ScanBasedFileChangeDetector(String path) {
	    if(logger.isDebugEnabled())
	        logger.debug("ScanBasedFileChangeDetector Constructor - ENTER");
	    
		this.path = path;
		this.originalFilesMap = loadFilesInMap(new File(path));
		
		if(logger.isDebugEnabled())
            logger.debug("ScanBasedFileChangeDetector Constructor - LEAVE");
	}

	/**
	 * @param rootFolder
	 * @return Map of all the files present in the rootFolder along with the lastModifiedTime
	 */
	private Map<File, Long> loadFilesInMap(File rootFolder) {
		Map<File, Long> filesMap = new HashMap<File, Long>();
		// add files in the
		File filesArray[] = rootFolder.listFiles();
		if (filesArray != null) {
			for (int i = 0; i < filesArray.length; i++) {
				filesMap.put(filesArray[i],
						new Long(filesArray[i].lastModified()));
			}
			File directoriesArray[] = rootFolder.listFiles(directoryFilter);
			if (directoriesArray != null) {
				for (int i = 0; i < directoriesArray.length; i++) {
					filesMap.putAll(loadFilesInMap(directoriesArray[i]));
				}
			}
		}

		return filesMap;
	}

	@Override
	public final void run() {
	    /*if(logger.isDebugEnabled())
            logger.debug("Scanning file changes - ENTER");*/
		HashSet<File> checkedFiles = new HashSet<File>();
		Map<File, Long> currentFilesMap = loadFilesInMap(new File(path));

		// scan the files and check for modification/addition
		for(File file : currentFilesMap.keySet())
		{
			Long current = originalFilesMap.get(file);
			checkedFiles.add(file);
			if (current == null) {
				originalFilesMap.put(file,
						new Long(file.lastModified()));
				onChange(file, Actions.ADD);
			} else if (current.longValue() != file.lastModified()) {
				// modified file
				originalFilesMap.put(file,
						new Long(file.lastModified()));
				onChange(file, Actions.MODIFY);
			}
		}

		// deleted files
		Set<File> ref = ((HashMap<File, Long>) ((HashMap<File, Long>) originalFilesMap)
				.clone()).keySet();
		ref.removeAll(checkedFiles);

		Iterator<File> it = ref.iterator();
		while (it.hasNext()) {
			File deletedFile = it.next();
			originalFilesMap.remove(deletedFile);
			onChange(deletedFile, Actions.DELETE);
		}
		
        /*if(logger.isDebugEnabled())
            logger.debug("Scanning file changes - LEAVE");*/
	}

	@Override
	protected abstract void onChange(File file, Actions action);
}