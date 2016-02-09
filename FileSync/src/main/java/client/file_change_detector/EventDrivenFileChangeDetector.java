package client.file_change_detector;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import common.Actions;

/**
 * @author Ashish Pahlazani Incomplete implementation
 */
public abstract class EventDrivenFileChangeDetector extends FileChangeDetectorTimerTask {
	private static final int eventPollingInterval = 1000;
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
			key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
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

		HashSet<String> createEntrySet = new HashSet<String>();
		HashSet<String> oldCreateEntrySet = new HashSet<String>();

		while (true) {

			// wait for key to be signaled
			try {
				key = watcher.poll(eventPollingInterval, TimeUnit.MILLISECONDS);
			} catch (InterruptedException x) {
				return;
			} catch (ClosedWatchServiceException exception) {
				return;
			}

			// put existing entries in oldCreateEntrySet
			if (createEntrySet.size() > 0) {
				oldCreateEntrySet.addAll(createEntrySet);
				createEntrySet.clear();
			}

			if (key != null) {
				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					if (kind == OVERFLOW) {
						continue;
					}

					if (kind == ENTRY_CREATE) {
						System.out.println("create Entry");
						createEntrySet.add(event.context().toString());
						continue;
					}

					if (kind == ENTRY_MODIFY) {
						if (createEntrySet.contains(event.context().toString())) {
							System.out.println("modify in same cycle");
							kind = ENTRY_CREATE;
							createEntrySet.remove(event.context().toString());
						} else if (oldCreateEntrySet.contains(event.context().toString())) {
							System.out.println("modify in next cycle");
							kind = ENTRY_CREATE;
							oldCreateEntrySet.remove(event.context().toString());
						}
					}
					/*
					 * if (logger.isInfoEnabled()) logger.info("File " +
					 * event.context().toString());
					 */

					onChange(new File(rootFolderPath + "/" + event.context().toString()), getAction(kind));
				}
			}

			// if oldEntrySetStillContsins entries, then notify those actions,
			// this means that the create entries are not modified in next cycle
			// too. SO we can notify them
			for (String path : oldCreateEntrySet) {
				System.out.println("updating " + path + " in second cycle");
				onChange(new File(rootFolderPath + "/" + path), getAction(ENTRY_CREATE));
			}
			oldCreateEntrySet.clear();

			try {
				Thread.sleep(eventPollingInterval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Reset the key -- this step is critical if you want to
			// receive further watch events. If the key is no longer valid,
			// the directory is inaccessible so exit the loop.
			if (key != null) {
				boolean valid = key.reset();
				if (!valid) {
					break;
				}
			}
		}

		/*
		 * if (logger.isDebugEnabled())
		 * logger.debug("EventDrivenFileChangeDetector Run - LEAVE");
		 */
	}

	private Actions getAction(WatchEvent.Kind<?> kind) {
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

	public static void main(String[] args) {
		initializeLogger("config/log4j.properties");

		EventDrivenFileChangeDetector detector = new EventDrivenFileChangeDetector(
				"C:\\Users\\Ashish Pahlaz\\Desktop\\Ashish") {
			@Override
			protected void onChange(File file, Actions action) {
				System.out.println("File : " + file.getAbsolutePath() + " Action =" + action);
			}

		};

		new Thread(detector).start();
	}

	private static void initializeLogger(String propertyFilePath) {
		/*
		 * if (logger.isDebugEnabled())
		 * logger.debug("initializeLogger - ENTER");
		 */
		Properties logProperties = new Properties();
		try {
			logProperties.load(new FileInputStream(propertyFilePath));
			PropertyConfigurator.configure(logProperties);
		} catch (FileNotFoundException e1) {
			System.err.println("Exception while initializing Logger : " + e1);
		} catch (IOException e1) {
			System.err.println("Exception while initializing Logger : " + e1);
		}
		if (logger.isDebugEnabled())
			logger.debug("initializeLogger - LEAVE");
	}
}
