package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import common.ConfigurationException;

/**
 * @author Ashish Pahlazani This is the main class for initializing server. It
 *         takes care of loading the server properties. Starting the worker
 *         thread for accepting connection requests from clients, and managing
 *         connections with clients
 */
public class Server implements IServer {
	private static final Logger logger = Logger.getLogger(Server.class);

	private static int maxConnectionCount = 1000;
	private volatile AtomicLong currentRevisionNumber = new AtomicLong(0);
	public static String rootFolderPath = "F:/FileSync/Server";
	private int port = 2001;
	private int filePort = 2002;
	private ServerSocket serverSocket;
	private ServerSocket fileReceiveServerSocket;
	private ServerConnectionWorker serverConnectionWorker;
	private Map<String, Long> filesStatus;
	private boolean isServerRunning = false;
	private Properties usernamePasswordProperties;

	public Server(Properties properties) {
		loadProperties(properties);
		filesStatus = new ConcurrentHashMap<String, Long>();

		try {
			loadRevisionNumberAndFileStatus();
			serverSocket = new ServerSocket(port);
			fileReceiveServerSocket = new ServerSocket(filePort);
			serverConnectionWorker = new ServerConnectionWorker(serverSocket,
					fileReceiveServerSocket, this);

			isServerRunning = true;

			Thread serverConnectionWorkerThread = new Thread(
					serverConnectionWorker);
			serverConnectionWorkerThread.start();

			if (logger.isDebugEnabled())
				logger.debug("serverConnectionThread started");
		} catch (IOException e) {
			shutdown();
			logger.error("Exception while initializing Server Exception : " + e);
		}
	}

	@Override
	public int getMaxConnectionCount() {
		return maxConnectionCount;
	}

	@Override
	public long getRevisionNumber(String relativePathOfFile) {
		if (logger.isDebugEnabled())
			logger.debug("getRevisionNumber Enter/Leave relativePathOfFile = "
					+ relativePathOfFile);

		Long revisionNumber = filesStatus.get(relativePathOfFile);
		if (revisionNumber == null)
			revisionNumber = -1L;

		if (logger.isDebugEnabled())
			logger.debug("revisionNumber = " + revisionNumber);
		return revisionNumber;
	}

	@Override
	public void setRevisionNumber(String relativePathOfFile,
			long newRevisionNumber) {
		if (logger.isDebugEnabled())
			logger.debug("setRevisionNumber - ENTER relativePathOfFile = "
					+ relativePathOfFile + " new revisionNumber = "
					+ newRevisionNumber);

		Long revisionNumber = filesStatus.get(relativePathOfFile);
		filesStatus.put(relativePathOfFile, newRevisionNumber);

		if (logger.isDebugEnabled())
			logger.debug("setRevisionNumber - LEAVE oldRevisionNumber = "
					+ revisionNumber + " newRevisionNumber = "
					+ newRevisionNumber);
	}

	@Override
	public AtomicLong getCurrentRevisionNumber() {
		if (logger.isDebugEnabled())
			logger.debug("getCurrentRevisionNumber Enter/Leave revisionNumber = "
					+ currentRevisionNumber);
		return currentRevisionNumber;
	}

	@Override
	public String getRootFolderPath() {
		return rootFolderPath;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public boolean isServerRunning() {
		return isServerRunning;
	}

	/**
	 * This method loads the file status, i.e, the revision no. of each file in
	 * the repository The status will be saved in a file, using serialization.
	 * The status is loaded in concurrentHashMap as of now, but it can be
	 * maintained in database too
	 * 
	 * @throws IOException
	 */
	private void loadRevisionNumberAndFileStatus() throws IOException {
		if (logger.isDebugEnabled())
			logger.debug("loadRevisionNumberAndFileStatus - ENTER");

		File file = new File("server.dat");

		if (file.exists()) {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			try {
				currentRevisionNumber = new AtomicLong(ois.readLong());
				logger.info("currentRevisionNumber = " + currentRevisionNumber);
				filesStatus = (Map<String, Long>) ois.readObject();
			} catch (ClassNotFoundException e) {
				logger.error("Exception while reading file status : " + e);
			} finally {
				if (ois != null)
					ois.close();
				if (fis != null)
					fis.close();
			}
		} else if (logger.isInfoEnabled())
			logger.info("Server.dat does not exist");

		if (logger.isDebugEnabled())
			logger.debug("loadRevisionNumberAndFileStatus - LEAVE");
	}

	/**
	 * @return the filePort
	 */
	int getFilePort() {
		return filePort;
	}

	private void loadProperties(Properties properties) {
		if (logger.isDebugEnabled())
			logger.debug("loadProperties - LEAVE");
		try {
			rootFolderPath = properties.getProperty("ROOT_FOLDER_PATH");
			if (!new File(rootFolderPath).exists()) {
				System.err.println("Root folder does not exist");
				System.exit(0);
			}

			port = Integer.parseInt(properties.getProperty("PORT"));
			filePort = Integer.parseInt(properties
					.getProperty("SERVER_FILE_PORT"));
			maxConnectionCount = Integer.parseInt(properties
					.getProperty("MAX_CONNECTION_COUNT"));

			loadUsernamePasswordProperties();

		} catch (NumberFormatException | ConfigurationException e) {
			logger.error("Exception while loading server properties : " + e);
			throw new ConfigurationException(
					"exception while reading properties file");
		}
		if (logger.isDebugEnabled())
			logger.debug("loadProperties - LEAVE");
	}

	private void loadUsernamePasswordProperties() {
		if (logger.isDebugEnabled())
			logger.debug("loadUsernamePasswordProperties - LEAVE");

		usernamePasswordProperties = new Properties();
		InputStream input = null;

		initializeLogger("config/log4j.properties");

		try {
			input = new FileInputStream("username_pwd.properties");
			usernamePasswordProperties.load(input);

		} catch (IOException ex) {
			logger.error("Exception while loading server.properties : " + ex);
			throw new ConfigurationException(
					"username_pwd.properties file not found");
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					logger.error("Exception while closing FileInputStream : "
							+ e);
				}
			}
		}
		if (logger.isDebugEnabled())
			logger.debug("loadUsernamePasswordProperties - LEAVE");
	}

	public void shutdown() {
		if (logger.isDebugEnabled())
			logger.debug("shutdown - ENTER");
		try {
			if (serverSocket != null)
				serverSocket.close();
			if (fileReceiveServerSocket != null)
				fileReceiveServerSocket.close();

			isServerRunning = false;

			saveRevisionNumberAndFilesStatus();
		} catch (IOException e) {
			logger.error("Exception while closing Server Sockets Exception : "
					+ e);
		}
		if (logger.isDebugEnabled())
			logger.debug("shutdown - LEAVE");
	}

	/**
	 * This method serialize the filesStatus map in a file. this method will be
	 * called when the server is shutting down.
	 * 
	 * @throws IOException
	 */
	private void saveRevisionNumberAndFilesStatus() {
		if (logger.isDebugEnabled())
			logger.debug("saveRevisionNumberAndFilesStatus - ENTER");

		File file = new File("server.dat");

		FileOutputStream fos = null;
		ObjectOutputStream oos = null;

		try {
			if (!file.exists()) {
				if (logger.isInfoEnabled())
					logger.info("Server.dat does not exist, creating file");
				file.createNewFile();
			}
			fos = new FileOutputStream(file);
			oos = new ObjectOutputStream(fos);
			oos.writeLong(currentRevisionNumber.get());
			oos.writeObject(filesStatus);

		} catch (IOException e) {
			logger.error("Exception while saving file status to file : " + e);
		} finally {
			try {
				if (oos != null)
					oos.close();
				if (fos != null)
					fos.close();
			} catch (IOException e) {
				logger.error("Exception while closing streams : " + e);
			}
		}
		if (logger.isDebugEnabled())
			logger.debug("saveRevisionNumberAndFilesStatus - LEAVE");
	}

	public static void main(String[] args) {
		Properties prop = new Properties();
		InputStream input = null;

		initializeLogger("config/log4j.properties");

		try {
			input = new FileInputStream("server.properties");
			prop.load(input);
			new Server(prop);

		} catch (IOException ex) {
			logger.error("Exception while loading server.properties : " + ex);
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					logger.error("Exception while closing FileInputStream : "
							+ e);
				}
			}
		}
	}

	private static void initializeLogger(String propertyFilePath) {
		/*if (logger.isDebugEnabled())
			logger.debug("initializeLogger - ENTER");*/
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

	@Override
	public boolean validateUsernameAndPassword(String username,
			String password, String ipAddress) {
		/*if (logger.isDebugEnabled())
			logger.debug("validateUsernameAndPassword - ENTER username = "
					+ username + " password = " + password + " ipAddress = "
					+ ipAddress);*/
		String clientPasswordAndIpAddress = usernamePasswordProperties
				.getProperty(username.toLowerCase());
		if (password.equals(clientPasswordAndIpAddress.split(":")[0])
				&& ipAddress.equals(clientPasswordAndIpAddress.split(":")[1])) {
			return true;
		}

		return false;
	}
}
