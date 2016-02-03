/*
 * Copyright (c) Siemens AG 2015 ALL RIGHTS RESERVED.
 *
 * R8  
 * 
 */

package common;

public class MessageParseUtil
{
    public static final String colonSeparator = ":";
    public static final String commaSeparator = "','";
    
    public static String createAddMessage(String relativePath, long revisionNumber)
    {
    	return Actions.ADD.ordinal() + colonSeparator + relativePath + commaSeparator + revisionNumber;
    }
    
    public static String createDeleteMessage(String relativePath, long revisionNumber)
    {
    	return Actions.DELETE.ordinal() + colonSeparator + relativePath + commaSeparator + revisionNumber;
    }
    
    public static String createModifyMessage(String relativePath, long revisionNumber)
    {
    	return Actions.DELETE.ordinal() + colonSeparator + relativePath + commaSeparator + revisionNumber;
    }
    
    public static String createConflictMessage(String relativePath, String fileRelativePath)
    {
    	return Actions.CONFLICT.ordinal() + colonSeparator + relativePath;
    }
}


/*
 * Copyright (c) Siemens AG 2015 ALL RIGHTS RESERVED
 *
 * R8
 */
