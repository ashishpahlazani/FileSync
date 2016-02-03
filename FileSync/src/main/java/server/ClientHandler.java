package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

import common.Actions;
import common.EncryptionUtil;
import common.FileDownloader;
import common.FileUploader;
import common.MessageParseUtil;

/**
 * @author Ashish Pahlazani This class manages communication with a client
 *         Authentication of clients is also taken care by this class
 */
public class ClientHandler implements Runnable {
	private static final Logger logger = Logger.getLogger(ClientHandler.class);

	private String username;
	private Socket socket;
	private boolean connected = false;
	private boolean isClientAuthenticated = false;
	private BufferedReader br;
	private PrintWriter printWriter;
	private IClientsSyncHandler clientsSyncHandler;
	private ServerSocket fileReceiveServerSocket;
	private int fileSendPort;
	private boolean isFileDownloadedSuccessfully;

	public ClientHandler(Socket socket, IClientsSyncHandler clientsSyncHandler,
			ServerSocket fileReceiveServerSocket) throws IOException {
		this.socket = socket;
		this.clientsSyncHandler = clientsSyncHandler;
		this.fileReceiveServerSocket = fileReceiveServerSocket;
		br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		printWriter = new PrintWriter(socket.getOutputStream(), true);
		connected = true;
		if (logger.isInfoEnabled())
			logger.info("client handler initialized");
	}

	public String getUsername() {
		return username;
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("Executing ClientHandler");

		String message;

		// read username and password
		try {
			if ((message = br.readLine()) != null) {
				parseMessage(message);
			}
		} catch (IOException e) {
			logger.error("Exception while reading message : " + e);
			connected = false;
		}

		while (connected && isClientAuthenticated
				&& clientsSyncHandler.isServerRunning()) {
			try {
				if ((message = br.readLine()) != null) {
					parseMessage(message);
				}
			} catch (IOException e) {
				logger.error("Exception while reading message : " + e);
				connected = false;
			}
		}
		
		shutdown();
		if (logger.isDebugEnabled())
			logger.debug("ClientHandler stopped");
	}

	private void parseMessage(String message) {
		String actualMessage = EncryptionUtil.decrypt(message);

		if (logger.isInfoEnabled())
			logger.info("actualMessage = " + actualMessage);

		try {
			int actionInt = Integer.parseInt(actualMessage.split(":", 2)[0]);
			Actions action = Actions.fromInteger(actionInt);

			String messageString = actualMessage.split(":", 2)[1];

			if (logger.isInfoEnabled())
				logger.info("Action :" + action
						+ " Message received from client = " + messageString);
			takeActionOnServer(action, messageString);

		} catch (NumberFormatException e) {
			logger.error("Exception while parsing message : " + e);
		}
	}

	private void takeActionOnServer(Actions action, String message) {
		switch (action) {
		case ADD:
			addFileToServer(message);
			break;
		case DELETE:
			deleteFileFromServer(message);
			break;
		case MODIFY:
			modifyFileOnServer(message);
			break;
		case USRNAME_PASSWORD:
			setUsernameAndValidatePassword(message);
			break;
		case LOAD_INITIAL_REPOSITORY:
			break;
		case SENDING_FILE_ADD:
		case SENDING_FILE_MODIFY:
			File file = new File(Server.rootFolderPath + "/" + message);
			createBackup(file);
			readFileFromClient(action, message);
			break;
		case FILE_SOCKET_PORT:
			try {
				fileSendPort = Integer.parseInt(message);
			} catch (Exception e) {
				logger.error("error while retrieving filePort");
				connected = false;
			}
		case EXCEPTION:
			break;
		case CONFLICT:
			break;
		case REQUEST_FILE_ADD:
		case REQUEST_FILE_MODIFY:
			clientsSyncHandler.enqueTaskInSyncPropogationWorker(action,
					message, this);
			// sendFileToClient(message);
			break;
		default:
			break;
		}
	}

	private void setUsernameAndValidatePassword(String message) {
		this.username = message.split(":")[0];
		String password = message.split(":")[1];
		String ipAddress = socket.getInetAddress().toString().substring(1);
		
		// code to validate username and password goes here
		// username and password can be maintained in a DB, or can be put in a
		// property file
		isClientAuthenticated = clientsSyncHandler.validateUsernameAndPassword(username, password, ipAddress);
	}

	private void modifyFileOnServer(String message) {
		String relativePath = message.split("','")[0];
		String absolutePath = Server.rootFolderPath + "/" + relativePath;
		long revisionNumber = Long.parseLong(message.split("','")[1]);
		if (logger.isInfoEnabled())
			logger.info("AbsolutePath = " + absolutePath + " revisionNumber = "
					+ revisionNumber);

		File f = new File(absolutePath);
		synchronized (f) {
			if (f.exists()) {
				if (clientsSyncHandler.getRevisionNumber(relativePath) > revisionNumber) {
					sendMessageToClient(Actions.CONFLICT, ":" + relativePath);
				} else {
					long newRevisionNumber = clientsSyncHandler
							.getCurrentRevisionNumber().incrementAndGet();

					sendMessageToClient(Actions.REQUEST_FILE_MODIFY,
							relativePath + "','" + newRevisionNumber);
					clientsSyncHandler.setRevisionNumber(relativePath,
							newRevisionNumber);
				}
			} else {
				sendMessageToClient(Actions.CONFLICT, ": File does not exist "
						+ relativePath);
			}
		}
	}

	private void deleteFileFromServer(String message) {
		String relativePath = message.split("','")[0];
		String absolutePath = Server.rootFolderPath + "/" + relativePath;
		long revisionNumber = Long.parseLong(message.split("','")[1]);
		if (logger.isInfoEnabled())
			logger.info("AbsolutePath = " + absolutePath + " revisionNumber = "
					+ revisionNumber);

		File f = new File(absolutePath);
		synchronized (f) {
			if (f.exists()) {

				if (clientsSyncHandler.getRevisionNumber(relativePath) > revisionNumber) {
					sendMessageToClient(Actions.CONFLICT, ":" + relativePath);
				} else {
					// create backup and remove file from server
					createBackup(f);
					f.delete();
					clientsSyncHandler.getCurrentRevisionNumber()
							.incrementAndGet();
					clientsSyncHandler.sendUpdateToOtherClients(Actions.DELETE,
							message, this);
				}
			} else {
				sendMessageToClient(Actions.CONFLICT,
						": File does not exist on server " + relativePath);
			}
		}
	}

	private void addFileToServer(String message) {
		String relativePath = message.split("','")[0];
		String absolutePath = Server.rootFolderPath + "/" + relativePath;
		long revisionNumber = Long.parseLong(message.split("','")[1]);
		if (logger.isInfoEnabled())
			logger.info("AbsolutePath = " + absolutePath + " revisionNumber = "
					+ revisionNumber);

		File f = new File(absolutePath);
		synchronized (f) {
			if (!f.exists()) {
				long newRevisionNumber = clientsSyncHandler
						.getCurrentRevisionNumber().incrementAndGet();

				sendMessageToClient(Actions.REQUEST_FILE_ADD, relativePath
						+ MessageParseUtil.commaSeparator + newRevisionNumber);
				clientsSyncHandler.setRevisionNumber(relativePath,
						newRevisionNumber);
			} else {
				sendMessageToClient(Actions.CONFLICT, ":" + relativePath);
			}
		}
	}

	/**
	 * @param File
	 *            whose backup is to created This method will create backup of
	 *            file based on the revisionNo present in client file.
	 */
	private void createBackup(File f) {
		if (logger.isDebugEnabled())
			logger.debug("createBackup - ENTER FilePath : "
					+ f.getAbsolutePath());

		if (logger.isDebugEnabled())
			logger.debug("createBackup - LEAVE ");
	}

	private void readFileFromClient(Actions action, String relativePath) {
		if (logger.isDebugEnabled())
			logger.debug("readFileFromClient - Enter action = " + action
					+ " relativePath : " + relativePath);

		File f = new File(Server.rootFolderPath + "/" + relativePath);

		FileDownloader download = new FileDownloader(fileReceiveServerSocket, f);

		isFileDownloadedSuccessfully = true;

		Thread.UncaughtExceptionHandler threadExceptionHandler = new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread th, Throwable ex) {
				logger.error("Exception occured while downloading file");
				isFileDownloadedSuccessfully = false;
			}
		};

		Thread downloadThread = new Thread(download);
		downloadThread.setUncaughtExceptionHandler(threadExceptionHandler);
		downloadThread.start();
		try {
			downloadThread.join();
		} catch (InterruptedException e) {
			logger.error("Exception while waiting for download thread : " + e);
		}

		if (isFileDownloadedSuccessfully) {
			Actions fileUpdateAction;
			if (action == Actions.SENDING_FILE_ADD)
				fileUpdateAction = Actions.ADD;
			else
				fileUpdateAction = Actions.MODIFY;

			clientsSyncHandler.sendUpdateToOtherClients(
					fileUpdateAction,
					relativePath
							+ MessageParseUtil.commaSeparator
							+ clientsSyncHandler
									.getRevisionNumber(relativePath), this);
		}

		/*
		 * FileOutputStream fos = null; try { f.createNewFile(); fos = new
		 * FileOutputStream(f); InputStream inputStream = sock.getInputStream();
		 * long fileLength = Long.parseLong(br.readLine());
		 * System.out.println("fileLength " + fileLength); byte[] bytes = new
		 * byte[1024]; int count; while (fileLength > 0 && (count =
		 * inputStream.read(bytes)) > 0) { System.out.println("Count = " + count
		 * + " fileLength = " + fileLength); fos.write(bytes, 0, count);
		 * fileLength -= count; } System.out
		 * .println("file received by client, writing message to server");
		 * printWriter.println("file received");
		 * System.out.println("File created, and fos is closed"); } catch
		 * (FileNotFoundException e) { e.printStackTrace(); } catch (IOException
		 * e) { System.out.println("Exception while reading file from client " +
		 * e); } finally{ try { if(fos != null) fos.close(); } catch
		 * (IOException e) { e.printStackTrace(); } }
		 */

		if (logger.isDebugEnabled())
			logger.debug("readFileFromClient - Leave");
	}

	public void sendMessageToClient(Actions action, String message) {
		if (logger.isDebugEnabled())
			logger.debug("sendMessageToClient - Enter Action : " + action
					+ " Message : " + message);
		printWriter.println(EncryptionUtil.encrypt(action.ordinal()
				+ MessageParseUtil.colonSeparator + message));
		/*
		 * printWriter.println(message); printWriter.flush();
		 */
		if (logger.isDebugEnabled())
			logger.debug("sendMessageToClient - Leave");
	}

	public void sendFileToClient(Actions action, String relativePath) {
		if (logger.isDebugEnabled())
			logger.debug("sendFileToClient - Enter path: " + relativePath);

		printWriter.println(action.ordinal() + ":" + relativePath);

		File file = new File(Server.rootFolderPath + "/" + relativePath);

		FileUploader fileUploader = new FileUploader(socket.getInetAddress()
				.toString().substring(1), fileSendPort, file);
		Thread fileUploaderThread = new Thread(fileUploader);
		fileUploaderThread.start();

		try {
			fileUploaderThread.join();
		} catch (InterruptedException e) {
			logger.error("Exception while waitimg file upload : " + e);
		}

		if (logger.isDebugEnabled())
			logger.debug("sendFileToClient - Leave");
	}

	public void shutdown() {
		connected = false;
		try {
			br.close();
			printWriter.close();
			socket.close();
		} catch (IOException e) {
			logger.error("Exception in destroy");
		}
	}
}
