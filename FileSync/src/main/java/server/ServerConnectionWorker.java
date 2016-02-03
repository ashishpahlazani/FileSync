package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;

import common.Actions;

/**
 * @author Ashish Pahlazani This class waits continuously for connections from
 *         clients, for each client a ClientHandler object is created which
 *         takes care of all the requests received form client
 */
public class ServerConnectionWorker implements Runnable, IClientsSyncHandler {
	private static final Logger logger = Logger
			.getLogger(ServerConnectionWorker.class);

	private List<ClientHandler> clientHandlerList;
	private ServerSocket serverSocket;
	private ServerSocket fileReceiveServerSocket;
	private int maxConnectionCount;
	private ExecutorService executorService;
	private SyncPropogationWorker syncPropogationWorker;
	private IServer iServer ;
	
	public ServerConnectionWorker(ServerSocket serverSocket,
			ServerSocket fileReceiveServerSocket, IServer iServer) {
		if (logger.isDebugEnabled())
			logger.debug("ServerConnectionWorker Constructor - ENTER");
		
		this.iServer = iServer;
		this.maxConnectionCount = iServer.getMaxConnectionCount();
		this.serverSocket = serverSocket;
		this.fileReceiveServerSocket = fileReceiveServerSocket;

		clientHandlerList = new CopyOnWriteArrayList<ClientHandler>();
		executorService = Executors.newCachedThreadPool();

		syncPropogationWorker = new SyncPropogationWorker(clientHandlerList, this);
		executorService.execute(syncPropogationWorker);
		
		if (logger.isDebugEnabled())
			logger.debug("ServerConnectionWorker Constructor - LEAVE");
	}

	public void shutdown() {
		if (logger.isDebugEnabled())
			logger.debug("shutdown - ENTER");
		try {
			serverSocket.close();
		} catch (IOException e) {
			logger.error("Exception in destroy");
		}
		
		for(ClientHandler clientHandler : clientHandlerList)
		{
			clientHandler.shutdown();
		}
		executorService.shutdown();
		if (logger.isDebugEnabled())
			logger.debug("shutdown - LEAVE");
	}
	
	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("Server Connection Started");
		while (clientHandlerList.size() <= maxConnectionCount && iServer.isServerRunning()) {
			Socket socket;
			try {
				if (logger.isDebugEnabled())
					logger.debug("Waiting for new connection request");
				
				socket = serverSocket.accept();

				if (logger.isDebugEnabled())
					logger.debug("new Client connected");
				ClientHandler cHandler = new ClientHandler(socket, this,
						fileReceiveServerSocket);
				clientHandlerList.add(cHandler);
				executorService.execute(cHandler);
			} catch (IOException e) {
				logger.error("Exception while creating new connection " + e);
			}
		}
	}

	@Override
	public void sendUpdateToOtherClients(Actions action, String message,
			ClientHandler clientHandlerToBeExcluded) {
		if (logger.isDebugEnabled())
			logger.debug("sendUpdateToOtherClients ENTER");
		for (ClientHandler clientHandler : clientHandlerList) {
			if (clientHandler != clientHandlerToBeExcluded)
				syncPropogationWorker.enqueSyncTask(action, message,
						clientHandler);
			// clientHandler.sendMessageToClient(action, message);
		}
		if (logger.isDebugEnabled())
			logger.debug("sendUpdateToOtherClients LEAVE");
	}

	@Override
	public void enqueTaskInSyncPropogationWorker(Actions action,
			String message, ClientHandler clientHandler) {
		if (logger.isDebugEnabled())
			logger.debug("enqueTaskInSyncPropogationWorker ENTER Action = "
				+ action + " relativePath = " + message);

		syncPropogationWorker.enqueSyncTask(action, message, clientHandler);

		if (logger.isDebugEnabled())
			logger.debug("enqueTaskInSyncPropogationWorker LEAVE");
	}

	@Override
	public AtomicLong getCurrentRevisionNumber() {
		return iServer.getCurrentRevisionNumber();
	}

	@Override
	public long getRevisionNumber(String relativePathOfFile) {
		return iServer.getRevisionNumber(relativePathOfFile);
	}
	
	@Override
	public boolean isServerRunning() {
		return iServer.isServerRunning();
	}

	@Override
	public void setRevisionNumber(String relativePathOfFile, long newRevisionNumber) {
		iServer.setRevisionNumber(relativePathOfFile, newRevisionNumber);
	}

	@Override
	public boolean validateUsernameAndPassword(String usernam, String password,
			String ipAddress) {
		return iServer.validateUsernameAndPassword(usernam, password, ipAddress);
	}
}
