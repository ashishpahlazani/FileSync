package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import client.file_change_detector.FileChangeDetectorTimerTask;
import client.file_change_detector.ScanBasedFileChangeDetector;

import common.Actions;
import common.ConfigurationException;
import common.MessageParseUtil;

/**
 * @author Ashish Pahlazani Client class takes care or of initializing all the
 *         components required to run a client It has a ServerConnectionWorker,
 *         which waits for messages from server and take appropriate actions It
 *         also contains a file change detector which continuously scans files
 *         for changes in files.
 */
public class Client implements IClient {
	private static final Logger logger = Logger.getLogger(Client.class);

	private int fileScanInterval = 1000;
	private String username = "Client1";
	private String password = "";
	private String serverIp = "localhost";
	private String rootFolderPath = "F:/FileSync";
	private int port = 2001;
	private int serverFilePort = 2002;
	private int clientFilePort = 3001;
	private Socket sock;
	private OutputStream socketOutputStream;
	private PrintWriter pwriter;
	private FileChangeDetectorTimerTask fileChangeDetector;
	private ClientSocketListenerWorker messageReceiver;
	private Timer timer;
	private Thread messageReceiverThread;
	private ServerSocket fileReceiveServerSocket;
	private Map<File, Actions> ignoreUpdateOnFileMap;
	private Map<String, Long> filesStatusMap;

	public Client(Properties properties) {
		if (logger.isDebugEnabled())
			logger.debug("Client Constructor - ENTER");

		loadClientProperties(properties);

		filesStatusMap = new ConcurrentHashMap<String, Long>();

		try {
			loadFilesStatus();
			sock = new Socket(serverIp, port);
			socketOutputStream = sock.getOutputStream();
			pwriter = new PrintWriter(socketOutputStream, true);
			fileReceiveServerSocket = new ServerSocket(clientFilePort);

			if (logger.isInfoEnabled())
				logger.info("Client Connected : Writing userName");

			sendMessageToServer(Actions.USRNAME_PASSWORD, username + ":"
					+ password);

			ignoreUpdateOnFileMap = new HashMap<File, Actions>();
			fileChangeDetector = new ScanBasedFileChangeDetector(rootFolderPath) {
				@Override
				protected void onChange(File file, Actions action) {
					if (logger.isInfoEnabled())
						logger.info("File = " + file.getAbsolutePath()
								+ " Operation = " + action);

					if (ignoreUpdateOnFileMap.get(file) != null) {
						if (logger.isDebugEnabled())
							logger.debug("File present in ignore map, ignoring update");
						ignoreUpdateOnFileMap.remove(file);
						return;
					}

					if (file.isDirectory()) {
						if (logger.isInfoEnabled())
							logger.info("directory operation ignored ");
					} else {
						String absolutePath = file.getAbsolutePath();
						String relativePath = absolutePath
								.substring(rootFolderPath.length());
						Long revisionNumber = filesStatusMap.get(relativePath);
						logger.info("filesStatusMap = " + filesStatusMap);
						if (revisionNumber != null)
							sendMessageToServer(action, relativePath
									+ MessageParseUtil.commaSeparator
									+ revisionNumber);
						else
							sendMessageToServer(action, relativePath
									+ MessageParseUtil.commaSeparator + -1);
						filesStatusMap.put(relativePath, -1L);
					}
				}
			};

			timer = new Timer();
			timer.schedule(fileChangeDetector, new Date(), fileScanInterval);
			if (logger.isInfoEnabled())
				logger.info("fileChangeDetector started");

			messageReceiver = new ClientSocketListenerWorker(this,
					fileReceiveServerSocket, sock);
			messageReceiverThread = new Thread(messageReceiver);
			messageReceiverThread.start();

			if (logger.isInfoEnabled())
				logger.info("ClientSocketListenerWorker started");
			try {
				messageReceiverThread.join();
			} catch (InterruptedException e) {
				logger.error("Exception on join" + e);
			}
			if (logger.isInfoEnabled())
				logger.info("ClientSocketListenerWorker stopped");
		} catch (IOException e) {
			logger.error("Exception while initializing client" + e);
		} finally {
			if (timer != null)
				timer.cancel();
			destroy();
		}

		if (logger.isDebugEnabled())
			logger.debug("Client Constructor - LEAVE");
	}

	/**
	 * @return the serverIp
	 */
	@Override
	public String getServerIp() {
		return serverIp;
	}

	/**
	 * @return the port
	 */
	@Override
	public int getPort() {
		return port;
	}

	/**
	 * @return the filePort
	 */
	@Override
	public int getServerFilePort() {
		return serverFilePort;
	}

	/**
	 * @return
	 */
	@Override
	public String getRootFolderPath() {
		return rootFolderPath;
	}

	@Override
	public long getRevisionNumber(String relativePathOfFile) {
		if (logger.isDebugEnabled())
			logger.debug("getRevisionNumber ENTER");

		Long revisionNumber = filesStatusMap.get(relativePathOfFile);
		if (revisionNumber == null)
			revisionNumber = -1L;

		if (logger.isDebugEnabled())
			logger.debug("getRevisionNumber LEAVE revisionNumber = "
					+ revisionNumber);
		return revisionNumber;
	}

	@Override
	public void setRevisionNumber(String relativePathOfFile, long revisionNumber) {
		if (logger.isDebugEnabled())
			logger.debug("setRevisionNumber - ENTER relativePathOfFile = "
					+ relativePathOfFile + " revisionNumber " + revisionNumber);
		filesStatusMap.put(relativePathOfFile, revisionNumber);
		if (logger.isDebugEnabled())
			logger.debug("setRevisionNumber - LEAVE");
	}

	@Override
	public void ignoreFileForUpdate(File file, Actions action) {
		ignoreUpdateOnFileMap.put(file, action);
	}

	/**
	 * This method loads the file status, i.e, the revision no. of each file in
	 * the repository The status will be saved in a file, using serialization.
	 * The status is loaded in concurrentHashMap as of now, but it can be
	 * maintained in database too
	 * 
	 * @throws IOException
	 */
	private void loadFilesStatus() throws IOException {
		if (logger.isDebugEnabled())
			logger.debug("loadFilesStatus - ENTER");

		File file = new File("client.dat");

		if (file.exists()) {
			FileInputStream fis = new FileInputStream(file);
			ObjectInputStream ois = new ObjectInputStream(fis);
			try {
				filesStatusMap = (Map<String, Long>) ois.readObject();
				logger.info(" filesStatusMap = " + filesStatusMap);
			} catch (ClassNotFoundException e) {
				logger.error("Exception while loading fileStatus" + e);
			} finally {
				if (ois != null)
					ois.close();
				if (fis != null)
					fis.close();
			}
		} else {
			if (logger.isInfoEnabled())
				logger.info("Client.dat does not exist");
		}
		if (logger.isDebugEnabled())
			logger.debug("loadFilesStatus - LEAVE");
	}

	@Override
	public void removeFileFromIgnoreList(File file) {
		if (logger.isDebugEnabled())
			logger.debug("removeFileFromIgnoreList - ENTER");

		if (ignoreUpdateOnFileMap.remove(file) != null) {
			if (logger.isInfoEnabled())
				logger.info("file still present in the list, these must be some problem reading file from server or writing file on client");
		}

		if (logger.isDebugEnabled())
			logger.debug("removeFileFromIgnoreList - LEAVE");
	}

	private void loadClientProperties(Properties properties) {
		if (logger.isDebugEnabled())
			logger.debug("loadClientProperties - ENTER");

		try {
			rootFolderPath = properties.getProperty("ROOT_FOLDER_PATH");

			if (!new File(rootFolderPath).exists()) {
				System.err.println("Root folder does not exist");
				System.exit(0);
			}

			serverIp = properties.getProperty("SERVER_IP");
			port = Integer.parseInt(properties.getProperty("PORT"));
			clientFilePort = Integer.parseInt(properties
					.getProperty("CLIENT_FILE_PORT"));
			serverFilePort = Integer.parseInt(properties
					.getProperty("SERVER_FILE_PORT"));
			fileScanInterval = Integer.parseInt(properties
					.getProperty("FILE_SCAN_INTERVAL"));
			username = properties.getProperty("USERNAME");
			password = properties.getProperty("PASSWORD");
		} catch (NumberFormatException e) {
			logger.error("Exception while loading Client Proprties : " + e);
			throw new ConfigurationException(
					"exception while reading properties file");
		}

		if (logger.isDebugEnabled())
			logger.debug("loadClientProperties - LEAVE");
	}

	@Override
	public void sendMessageToServer(Actions action, String message) {
		if (logger.isDebugEnabled())
			logger.debug("sendMessageToServer - ENTER Action = " + action
					+ " message = " + message);

		synchronized (sock) {
			pwriter.println(action.ordinal() + ":" + message);
		}

		if (logger.isDebugEnabled())
			logger.debug("sendMessageToServer - LEAVE");
	}

	/**
	 * This method will close all the open resources
	 */
	public void destroy() {
		if (logger.isDebugEnabled())
			logger.debug("destroy - ENTER");

		try {
			if (socketOutputStream != null)
				socketOutputStream.close();
			if (pwriter != null)
				pwriter.close();
			if (sock != null)
				sock.close();
			if (fileReceiveServerSocket != null)
				fileReceiveServerSocket.close();

			saveFilesStatus();
		} catch (IOException e) {
			logger.error("Exception while closing Server Sockets Exception : "
					+ e);
		}

		if (logger.isDebugEnabled())
			logger.debug("destroy - LEAVE");
	}

	/**
	 * This method serialize the filesStatus map in a file. this method will be
	 * called when the server is shutting down.
	 * 
	 * @throws IOException
	 */
	private void saveFilesStatus() throws IOException {
		if (logger.isDebugEnabled())
			logger.debug("saveFilesStatus - ENTER");

		File file = new File("client.dat");

		FileOutputStream fos = null;
		ObjectOutputStream oos = null;

		try {
			if (!file.exists()) {
				if (logger.isInfoEnabled())
					logger.info("client.dat does not exist, creating file");
				file.createNewFile();
			}
			fos = new FileOutputStream(file);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(filesStatusMap);

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
			logger.debug("saveFilesStatus - ENTER");
	}

	public static void main(String[] args) {
		Properties prop = new Properties();
		FileInputStream fileInputStream = null;
		initializeLogger("config/log4j.properties");

		try {
			fileInputStream = new FileInputStream("client.properties");
			prop.load(fileInputStream);
			new Client(prop);

		} catch (IOException ex) {
			logger.error("Exception while loading properties : " + ex);
		} finally {
			if (fileInputStream != null) {
				try {
					fileInputStream.close();
				} catch (IOException e) {
					logger.error("Exception while closing FileInputStream" + e);
				}
			}
		}
	}

	private static void initializeLogger(String propertyFilePath) {
		Properties logProperties = new Properties();
		try {
			logProperties.load(new FileInputStream(propertyFilePath));
			PropertyConfigurator.configure(logProperties);
		} catch (FileNotFoundException e1) {
			logger.error("Exception while initializing Logger : " + e1);
		} catch (IOException e1) {
			logger.error("Exception while initializing Logger : " + e1);
		}
	}
}