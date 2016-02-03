package client;

import java.io.File;

import common.Actions;

/**
 * @author Ashish Pahlazani
 * interface for client
 */
public interface IClient {
	/**
	 * @param action
	 * @param message
	 */
	public void sendMessageToServer(Actions action, String message);
	/**
	 * @return
	 */
	public String getServerIp();

    /**
     * @return the port
     */
    public int getPort();

    /**
     * @return the filePort
     */
    public int getServerFilePort();
    
    /**
     * @return
     */
    public String getRootFolderPath();
    
    /**
     * @param file
     * @param action
     */
    public void ignoreFileForUpdate(File file, Actions action);
    
    /**
     * @param file
     */
    public void removeFileFromIgnoreList(File file);
    
    
	/**
	 * @param relativePathOfFile
	 * @return
	 */
	public long getRevisionNumber(String relativePathOfFile);
	
	/**
	 * @param relativePathOfFile
	 * @param revisionNumber
	 */
	public void setRevisionNumber(String relativePathOfFile, long revisionNumber);
}
