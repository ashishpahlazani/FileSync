package client;

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
 * @author Ashish Pahlazani This class takes care of all the requests/messages
 *         received from server, and take appropriate action
 */
public class ClientSocketListenerWorker implements Runnable {
	private static final Logger logger = Logger
			.getLogger(ClientSocketListenerWorker.class);

	private boolean connected = false;
	private Socket socket;
	private BufferedReader br;
	private PrintWriter printWriter;
	private String rootFolderPath;
	private IClient client;
	private ServerSocket fileReceiveServerSocket;

	protected boolean isFileDownloadedSuccessfully;

	public ClientSocketListenerWorker(IClient client,
			ServerSocket fileReceiveServerSocket, Socket s) {
		this.rootFolderPath = client.getRootFolderPath();
		this.fileReceiveServerSocket = fileReceiveServerSocket;
		this.socket = s;
		this.client = client;
		try {
			br = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			printWriter = new PrintWriter(socket.getOutputStream(), true);
			connected = true;
		} catch (IOException e) {
			logger.error("Exception while initializing ClientSocketListenerWorker : "
					+ e);
		}

	}

	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("ClientSocketListenerStarted");

		String message;

		// send file port
		if (connected) {
			client.sendMessageToServer(Actions.FILE_SOCKET_PORT,
					fileReceiveServerSocket.getLocalPort() + "");
		}

		while (connected) {
			try {
				if (logger.isInfoEnabled())
					logger.info("waiting for message from server");

				if ((message = br.readLine()) != null) {
					if (logger.isInfoEnabled())
						logger.info("message received from server = " + message);
					parseMessage(message);
				}
			} catch (IOException e) {
				logger.error("Exception while reading from server, CLosing thread :"
						+ e);
				connected = false;
			}
		}

		if (logger.isDebugEnabled())
			logger.debug("ClientSocketListener Stoped");
	}

	private void parseMessage(String message) {
		if (logger.isDebugEnabled())
			logger.debug("parseMessage - ENTER message = " + message);

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

			takeActionBasedOnAction(action, messageString);

		} catch (NumberFormatException e) {
			logger.error("Exception while reading message : " + e);
		}

		if (logger.isDebugEnabled())
			logger.debug("parseMessage - LEAVE");
	}

	private void takeActionBasedOnAction(Actions action, String message) {
		if (logger.isDebugEnabled())
			logger.debug("takeActionBasedOnAction - Enter Action = " + action
					+ " message = " + message);

		String relativePath = "";

		switch (action) {
		case REQUEST_FILE_ADD:
		case REQUEST_FILE_MODIFY:
			relativePath = message.split(MessageParseUtil.commaSeparator)[0];
			long revisionNumber = Long.parseLong(message
					.split(MessageParseUtil.commaSeparator)[1]);
			client.setRevisionNumber(relativePath, revisionNumber);
			sendFileToServer(action, relativePath);
			break;
		case CONFLICT:
			client.setRevisionNumber(message, -1);
			logger.error("Conflict in file " + message);
			break;
		case ADD:
			addFileToClient(message);
			break;
		case DELETE:
			deleteFileFromClient(message);
			break;
		case MODIFY:
			modifyFileOnClient(message);
			break;
		case EXCEPTION:
			logger.error("Exception : " + message);
			break;
		case LOAD_INITIAL_REPOSITORY:
			break;
		case SENDING_FILE_ADD:
		case SENDING_FILE_MODIFY:
			File file = new File(rootFolderPath + "/" + message);
			readFileFromServer(action, file);
			break;
		case USRNAME_PASSWORD:
			break;
		default:
			break;
		}
		if (logger.isDebugEnabled())
			logger.debug("takeActionBasedOnAction - LEAVE");
	}

	private void readFileFromServer(Actions action, File file) {
		if (logger.isDebugEnabled())
			logger.debug("readFileFromClient - Enter File : " + file);

		/*
		 * Thread.UncaughtExceptionHandler threadExceptionHandler = new
		 * Thread.UncaughtExceptionHandler() { public void
		 * uncaughtException(Thread th, Throwable ex) {
		 * logger.error("Exception occured while downloading file");
		 * isFileDownloadedSuccessfully = false; } };
		 * isFileDownloadedSuccessfully = true;
		 */

		if(action == Actions.SENDING_FILE_ADD)
			client.ignoreFileForUpdate(file, Actions.ADD);
		else if(action == Actions.SENDING_FILE_MODIFY)
			client.ignoreFileForUpdate(file, Actions.MODIFY);

		FileDownloader download = new FileDownloader(fileReceiveServerSocket,
				file);
		Thread downloadThread = new Thread(download);
		// downloadThread.setUncaughtExceptionHandler(threadExceptionHandler);
		downloadThread.start();
		try {
			downloadThread.join();
		} catch (InterruptedException e) {
			logger.error("Exception while waiting for file download : " + e);
		}

		// client.removeFileFromIgnoreList(file);

		if (logger.isDebugEnabled())
			logger.debug("readFileFromServer - LEAVE");
	}

	private void addFileToClient(String message) {
		String relativePath = message.split("','")[0];
		String absolutePath = rootFolderPath + "/" + relativePath;
		long revisionNumber = Long.parseLong(message
				.split(MessageParseUtil.commaSeparator)[1]);

		if (logger.isInfoEnabled())
			logger.info("AbsolutePath = " + absolutePath
					+ " revisionNumber = " + revisionNumber);

		File f = new File(absolutePath);
		synchronized (f) {
			if (!f.exists()) {
				client.sendMessageToServer(Actions.REQUEST_FILE_ADD, relativePath);
				client.setRevisionNumber(relativePath, revisionNumber);
			} else {
				client.sendMessageToServer(Actions.CONFLICT, relativePath);
				client.setRevisionNumber(relativePath, -1);
			}
		}
	}

	private void deleteFileFromClient(String message) {
		String relativePath = message.split("','")[0];
		String absolutePath = rootFolderPath + "/" + relativePath;
		long revisionNumber = Long.parseLong(message.split("','")[1]);
		if (logger.isInfoEnabled())
			logger.info("AbsolutePath = " + absolutePath
					+ " revisionNumber = " + revisionNumber);

		File f = new File(absolutePath);
		synchronized (f) {
			if (f.exists()) {
				long clientRevisionNumber = client
						.getRevisionNumber(relativePath);
				if (clientRevisionNumber == -1
						|| clientRevisionNumber > revisionNumber) {
					client.sendMessageToServer(Actions.CONFLICT, relativePath);
				} else {
					f.delete();
					client.setRevisionNumber(relativePath, revisionNumber);
				}
			} else {
				client.sendMessageToServer(Actions.CONFLICT, relativePath);
				client.setRevisionNumber(relativePath, -1);
			}
		}
	}

	private void modifyFileOnClient(String message) {
		String relativePath = message.split("','")[0];
		String absolutePath = rootFolderPath + "/" + relativePath;
		long revisionNumber = Long.parseLong(message.split("','")[1]);
		if (logger.isInfoEnabled())
			logger.info("AbsolutePath = " + absolutePath
					+ " revisionNumber = " + revisionNumber);

		File f = new File(absolutePath);
		synchronized (f) {
			if (f.exists()) {
				long clientRevisionNumber = client
						.getRevisionNumber(relativePath);
				if (clientRevisionNumber == -1
						|| clientRevisionNumber > revisionNumber) {
					client.sendMessageToServer(Actions.CONFLICT, relativePath);
					client.setRevisionNumber(relativePath, -1);
				} else {
					client.sendMessageToServer(Actions.REQUEST_FILE_MODIFY,
							relativePath);
					client.setRevisionNumber(relativePath, revisionNumber);
				}
			} else {
				client.sendMessageToServer(Actions.CONFLICT, relativePath);
				client.setRevisionNumber(relativePath, -1);
			}
		}
	}

	/*
	 * private void sendFileToServer(String relativePath) {
	 * System.out.println("sendFileToServer - Enter path: " + relativePath);
	 * File file = new File(rootFolderPath + "/" + relativePath); // Get the
	 * size of the file byte[] bytes = new byte[1024]; InputStream
	 * fileInputStream = null; OutputStream sockOutStream = null; try {
	 * fileInputStream = new FileInputStream(file); try { sockOutStream =
	 * socket.getOutputStream(); synchronized (socket) { // write file length on
	 * stream printWriter.println(Actions.SENDING_FILE.ordinal() + ":" +
	 * relativePath); printWriter.println(file.length());
	 * 
	 * System.out.println("File length = " + file.length());
	 * 
	 * int count; while ((count = fileInputStream.read(bytes)) > 0) {
	 * System.out.print("Count = " + count + ", "); sockOutStream.write(bytes,
	 * 0, count); } sockOutStream.flush(); System.out
	 * .println("file sending complete - waiting for message from server");
	 * System.out.println("file received from server - " + br.readLine()); } }
	 * catch (IOException e) { e.printStackTrace(); } } catch
	 * (FileNotFoundException e) { e.printStackTrace(); } finally { try { if
	 * (fileInputStream != null) fileInputStream.close(); } catch (IOException
	 * e) { e.printStackTrace(); } }
	 * System.out.println("sendFileToServer - Leave"); }
	 */

	private void sendFileToServer(Actions action, String relativePath) {
		if (logger.isDebugEnabled())
			logger.debug("sendFileToServer - ENTER path: " + relativePath);

		if(action == Actions.REQUEST_FILE_ADD)
			printWriter
				.println(Actions.SENDING_FILE_ADD.ordinal() + ":" + relativePath);
		else
			printWriter
			.println(Actions.SENDING_FILE_MODIFY.ordinal() + ":" + relativePath);

		File file = new File(rootFolderPath + "/" + relativePath);
		FileUploader fileUploader = new FileUploader(client.getServerIp(),
				client.getServerFilePort(), file);
		Thread fileUploaderThread = new Thread(fileUploader);
		fileUploaderThread.start();

		try {
			fileUploaderThread.join();
		} catch (InterruptedException e) {
			logger.error("Exception while waiting for file upload : " + e);
		}

		if (logger.isDebugEnabled())
			logger.debug("sendFileToServer - LEAVE");
	}

	public void shutdown() {
		if (logger.isDebugEnabled())
			logger.debug("shutdown - ENTER ");
		
		try {
			if (printWriter != null)
				printWriter.close();
			if (br != null)
				br.close();
			if (socket != null)
				socket.close();
		} catch (IOException e) {

		}
		
		if (logger.isDebugEnabled())
			logger.debug("shutdown - LEAVE ");
	}

}
