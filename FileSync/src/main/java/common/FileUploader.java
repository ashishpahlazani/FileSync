/*
 * Copyright (c) Siemens AG 2015 ALL RIGHTS RESERVED.
 *
 * R8  
 * 
 */

package common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import org.apache.log4j.Logger;

public class FileUploader implements Runnable {
    private static final Logger logger = Logger.getLogger(FileUploader.class);
    
	public String addr;
	public int port;
	public Socket socket;
	public FileInputStream fileInputStream;
	public OutputStream socketOutputStream;
	public File file;

	public FileUploader(String addr, int port, File filepath) {
	    if(logger.isDebugEnabled())
            logger.debug("FileUploader constructor ENTER addr = " + addr
				+ " port = " + port + " file path = "
				+ filepath.getAbsolutePath());
		try {
			file = filepath;
			socket = new Socket(addr, port);
			socketOutputStream = socket.getOutputStream();
			fileInputStream = new FileInputStream(filepath);
		} catch (Exception ex) {
			logger.error("Exception [Upload : Upload(...)] : " + ex);
		}
		if(logger.isDebugEnabled())
            logger.debug("FileUploader constructor LEAVE");
	}

	@Override
	public void run() {
	    if(logger.isDebugEnabled())
            logger.debug("Uploading file Started");
		try {
			byte[] buffer = new byte[1024];
			int count;

			while ((count = fileInputStream.read(buffer)) >= 0) {
				socketOutputStream.write(buffer, 0, count);
			}
			socketOutputStream.flush();
		} catch (Exception ex) {
			logger.error("Exception [Upload : run()]");
		} finally {
			try {
				if (fileInputStream != null) {
					fileInputStream.close();
				}
				if (socketOutputStream != null) {
					socketOutputStream.close();
				}
				if (socket != null) {
					socket.close();
				}
			} catch (IOException e) {

			}
		}
		
		if(logger.isDebugEnabled())
            logger.debug("Uploading file LEAVE");
	}
}

/*
 * Copyright (c) Siemens AG 2015 ALL RIGHTS RESERVED R8
 */
