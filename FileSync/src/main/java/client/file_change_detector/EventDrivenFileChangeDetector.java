package client.file_change_detector;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.apache.log4j.Logger;

import common.Actions;

/**
 * @author Ashish Pahlazani
 * Incomplete implementation
 */
public abstract class EventDrivenFileChangeDetector extends
		FileChangeDetectorTimerTask {
	private static final Logger logger = Logger.getLogger(EventDrivenFileChangeDetector.class);
	private WatchService watcher;
	private WatchKey key;
	private String rootFolderPath;

	EventDrivenFileChangeDetector(String filePath) {
		if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Constructor - ENTER");
		this.rootFolderPath = filePath;
		Path path = Paths.get(filePath, "");
		try {
			watcher = FileSystems.getDefault().newWatchService();
			key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE,
					ENTRY_MODIFY);
		} catch (IOException e) {
			logger.error("Exception while initializing watcher : " + e);
		}
		if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Constructor - LEAVE");
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Run - ENTER");
		while (true) {
			for (WatchEvent<?> event : key.pollEvents()) {
				WatchEvent.Kind<?> kind = event.kind();
				if (kind == OVERFLOW) {
					continue;
				}
				if (logger.isInfoEnabled())
					logger.info("File " + event.context().toString());

				onChange(new File(rootFolderPath
						+ "/event.context().toString()"),
						getAction((WatchEvent.Kind<Path>) event.kind()));
			}
		}

		/*if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Run - LEAVE");*/
	}

	private Actions getAction(WatchEvent.Kind<Path> kind) {
		if (kind == ENTRY_CREATE) {
			return Actions.ADD;
		} else if (kind == ENTRY_DELETE) {
			return Actions.DELETE;
		} else if (kind == ENTRY_MODIFY) {
			return Actions.MODIFY;
		} else {
			if (logger.isInfoEnabled())
				logger.info("Wrong Kind passed in getAction");
			return Actions.EXCEPTION;
		}
	}
}
