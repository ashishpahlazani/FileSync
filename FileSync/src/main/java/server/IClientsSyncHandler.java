package server;

import java.util.concurrent.atomic.AtomicLong;

import common.Actions;

/**
 * @author Ashish Pahlazani
 *         Interface for client Sync
 *         The method in this interface will propogate the action taken on server to all the clients except the one which generated the
 *         action
 */
public interface IClientsSyncHandler
{
	public boolean validateUsernameAndPassword(String usernam, String password, String ipAddress);
	
    /**
     * @param action
     * @param message : contains the message to be passed to all the clients
     */
    public void sendUpdateToOtherClients(Actions action, String message, ClientHandler clientHandlerToBeExcluded);
    
    /**
     * @param action
     * @param message
     * @param clientHandlerToBeExcluded
     */
    public void enqueTaskInSyncPropogationWorker(Actions action, String message, ClientHandler clientHandler);
    
    /**
     * @return
     */
    public AtomicLong getCurrentRevisionNumber();
    
    /**
     * @param relativePathOfFile
     * @return
     */
    public long getRevisionNumber(String relativePathOfFile);
    
    /**
     * @param relativePathOfFile
     * @param revsionNumber
     * @return
     */
    public void setRevisionNumber(String relativePathOfFile, long newRevisionNumber);
    
    /**
     * @return
     */
    public boolean isServerRunning();
}
