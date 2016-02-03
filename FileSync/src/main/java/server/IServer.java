package server;

import java.util.concurrent.atomic.AtomicLong;

public interface IServer {
	public boolean validateUsernameAndPassword(String usernam, String password,
			String ipAddress);
	
	public int getMaxConnectionCount();
    
    public long getRevisionNumber(String relativePathOfFile);
    
    public void setRevisionNumber(String relativePathOfFile, long newRevisionNumber);
    
	public AtomicLong getCurrentRevisionNumber();

	public String getRootFolderPath();

	public int getPort();
	
	public boolean isServerRunning();
}
