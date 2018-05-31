package client.file_change_detector;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import common.Actions;

/**
 * @author Ashish Pahlazani Incomplete implementation
 */
public abstract class EventDrivenFileChangeDetector extends FileChangeDetectorTimerTask {
	private static final int EVENT_POLLING_INTERVAL = 1000;
	private static final Logger logger = Logger.getLogger(EventDrivenFileChangeDetector.class);
	private WatchService watcher;
	private WatchKey key;
	private Map<WatchKey, Path> keyMap = new HashMap<>();

	EventDrivenFileChangeDetector(String filePath) {
		if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Constructor - ENTER");
		Path path = Paths.get(filePath, "");
		try {
			watcher = FileSystems.getDefault().newWatchService();
			//key = path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
			registerAll(path);
		} catch (IOException e) {
			logger.error("Exception while initializing watcher : " + e);
		}
		if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Constructor - LEAVE");
	}
	
	private void registerAll(Path rootFolderPath)
	{
		try {
			Files.walkFileTree(rootFolderPath, new SimpleFileVisitor<Path>() {
			    @Override
			    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
			        throws IOException
			    {
			    	keyMap.put(dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), dir);
			        return FileVisitResult.CONTINUE;
			    }
			});
		} catch (IOException e) {
			logger.error("Exception while registering file for watcher " + rootFolderPath, e);
			//throw e;
		}
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("EventDrivenFileChangeDetector Run - ENTER");

		HashSet<String> createEntrySet = new HashSet<>();
		HashSet<String> oldCreateEntrySet = new HashSet<>();

		while (true) {

			// wait for key to be signaled
			try {
				key = watcher.poll(EVENT_POLLING_INTERVAL, TimeUnit.MILLISECONDS);
			} catch (InterruptedException x) {
				logger.warn( Thread.currentThread().getName() + " intrupted" , x);
				return;
			} catch (ClosedWatchServiceException exception) {
				return;
			}

			// put existing entries in oldCreateEntrySet
			if (!createEntrySet.isEmpty()) {
				oldCreateEntrySet.addAll(createEntrySet);
				createEntrySet.clear();
			}

			if (key != null) {
				Path dir = keyMap.get(key);
	            if (dir == null) {
	                logger.error("wrong watch key");
	                continue;
	            }
				
				for (WatchEvent<?> event : key.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					Path name = (Path) event.context();
					logger.debug("file name : " + name);
					Path absolutePath = dir.resolve(name);
					logger.debug("file name : " + absolutePath);
										
					if (kind == OVERFLOW) {
						continue;
					}

					if (kind == ENTRY_CREATE) {
						logger.info("create Entry");
						if(Files.isDirectory(absolutePath, LinkOption.NOFOLLOW_LINKS))
						{
							registerAll(absolutePath);
						}
						createEntrySet.add(absolutePath.toString());
						continue;
					}
					else if (kind == ENTRY_MODIFY) {
						if (createEntrySet.contains(absolutePath.toString())) {
							System.out.println("modify in same cycle");
							kind = ENTRY_CREATE;
							createEntrySet.remove(absolutePath.toString());
						} else if (oldCreateEntrySet.contains(absolutePath.toString())) {
							System.out.println("modify in next cycle");
							kind = ENTRY_CREATE;
							oldCreateEntrySet.remove(absolutePath.toString());
						}
					}
					/*
					 * if (logger.isInfoEnabled()) logger.info("File " +
					 * event.context().toString());
					 */

					onChange(new File(absolutePath.toString()), getAction(kind));
				}
			}

			// if oldEntrySetStillContsins entries, then notify those actions,
			// this means that the create entries are not modified in next cycle
			// too. SO we can notify them
			for (String path : oldCreateEntrySet) {
				System.out.println("updating " + path + " in second cycle");
				onChange(new File(path), getAction(ENTRY_CREATE));
			}
			oldCreateEntrySet.clear();

			try {
				Thread.sleep(EVENT_POLLING_INTERVAL);
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
		Properties logProperties = new Properties();
		try {
			logProperties.load(new FileInputStream(propertyFilePath));
			PropertyConfigurator.configure(logProperties);
		} catch (IOException e1) {
			System.err.println("Exception while initializing Logger : " + e1);
		}
		if (logger.isDebugEnabled())
			logger.debug("initializeLogger - LEAVE");
	}
}
