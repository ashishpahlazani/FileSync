package server;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.log4j.Logger;

import common.Actions;

/**
 * @author Ashish Pahlazani This class takes care of sync of actions taken on
 *         one client to all connected clients.
 */
public class SyncPropogationWorker implements Runnable {
	private static final Logger logger = Logger
			.getLogger(SyncPropogationWorker.class);

	private BlockingQueue<SyncPropogationTask> taskQueue;
	private IClientsSyncHandler clientSyncHandler;

	private class SyncPropogationTask {
		Actions action;
		String relativePath;
		ClientHandler clientHandler;

		public SyncPropogationTask(Actions action, String relativePath,
				ClientHandler clientHandler) {
			super();
			this.action = action;
			this.relativePath = relativePath;
			this.clientHandler = clientHandler;
		}

		public void execute() {
			logger.info("executing task : Action = " + action
					+ " relativePath " + relativePath + " Client = "
					+ clientHandler);

			if (action == Actions.REQUEST_FILE_ADD) {
				clientHandler.sendFileToClient(Actions.SENDING_FILE_ADD, relativePath);
			}
			else if (action == Actions.REQUEST_FILE_MODIFY) {
				clientHandler.sendFileToClient(Actions.SENDING_FILE_MODIFY, relativePath);
			}
			else {
				clientHandler.sendMessageToClient(action, relativePath);
			}
		}
	}

	public SyncPropogationWorker(List<ClientHandler> clientHandlerList,
			IClientsSyncHandler clientSyncHandler) {
		super();
		this.clientSyncHandler = clientSyncHandler;
		taskQueue = new LinkedBlockingDeque<SyncPropogationWorker.SyncPropogationTask>();
	}

	public void enqueSyncTask(Actions action, String relativePath,
			ClientHandler clientHandler) {
		if (logger.isDebugEnabled())
			logger.debug("enqueSyncTask ENTER action = " + action
					+ " relativePath = " + relativePath + " Client : "
					+ clientHandler.getUsername());
		taskQueue.add(new SyncPropogationTask(action, relativePath,
				clientHandler));
		if (logger.isDebugEnabled())
			logger.debug("enqueSyncTask LEAVE");
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("SyncPropogationWorker Thread -  ENTER");
		while (clientSyncHandler.isServerRunning()) {
			try {
				SyncPropogationTask task = taskQueue.take();
				task.execute();
			} catch (InterruptedException e) {
				logger.error("Exception while executing task : " + e);
			}
		}
		if (logger.isDebugEnabled())
			logger.debug("yncPropogationWorker Thread - LEAVE");
	}
}
