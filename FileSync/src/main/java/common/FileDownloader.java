/*
 * Copyright (c) Siemens AG 2015 ALL RIGHTS RESERVED.
 *
 * R8  
 * 
 */

package common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.log4j.Logger;

public class FileDownloader implements Runnable {
	private static final Logger logger = Logger.getLogger(FileDownloader.class);

	private ServerSocket serverSock;
	private File file;
	private FileOutputStream fos = null;
	private Socket sock = null;
	private InputStream inputStream = null;

	public FileDownloader(ServerSocket serverSock, File file) {
		if (logger.isDebugEnabled())
			logger.debug("File downloader started file = "
					+ file.getAbsolutePath());
		this.serverSock = serverSock;
		this.file = file;
	}

	@Override
	public void run() {
		if (logger.isDebugEnabled())
			logger.debug("download started on Port : "
					+ serverSock.getLocalPort());
		try {
			sock = serverSock.accept();

			if (logger.isInfoEnabled())
				logger.info("Socket Connected");

			if (file.exists())
				file.delete();

			createFileAndFoldersIfDoesNotExist(file);
			fos = new FileOutputStream(file);
			inputStream = sock.getInputStream();

			byte[] bytes = new byte[1024];
			int count;
			while ((count = inputStream.read(bytes)) > 0) {
				if (logger.isInfoEnabled())
					logger.info("count = " + count);

				fos.write(bytes, 0, count);
			}

			if (logger.isInfoEnabled())
				logger.info("file received by client, writing message to server");
		} catch (FileNotFoundException e) {
			logger.error("File Uploader, file not found" + e);
		} catch (IOException e) {
			logger.error("Exception while reading file from client " + e);
		} finally {
			try {
				if (fos != null)
					fos.close();
				if (sock != null)
					sock.close();
			} catch (IOException e) {
				logger.error("Exception while closing fileOutStream/Socket : "
						+ e);
			}
		}
		if (logger.isDebugEnabled())
			logger.debug("download Complete");
	}

	private void createFileAndFoldersIfDoesNotExist(File file) throws IOException{
		if (logger.isDebugEnabled())
			logger.debug("createFileAndFoldersIfDoesNotExist - ENTER file : " + file.getAbsolutePath());
				
		File parentFolder = file.getParentFile();
		if(!parentFolder.exists() && !parentFolder.mkdirs()){
		    throw new IllegalStateException("Couldn't create dir: " + parentFolder);
		}
		
		file.createNewFile();
		
		if (logger.isDebugEnabled())
			logger.debug("createFileAndFoldersIfDoesNotExist - LEAVE");
	}
}

/*
 * Copyright (c) Siemens AG 2015 ALL RIGHTS RESERVED R8
 */
