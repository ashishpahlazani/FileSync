package common;

import org.apache.log4j.Logger;

public class EncryptionUtil {
    private static final Logger logger = Logger.getLogger(EncryptionUtil.class);
    
	public static String encrypt(String message)
	{
		if(logger.isDebugEnabled())
		    logger.debug("encrypt Enter message = " + message);
		return message;
	}
	
	public static String decrypt(String message)
	{
	    if(logger.isDebugEnabled())
            logger.debug("decrypt Enter message = " + message);
		return message;
	}
}
