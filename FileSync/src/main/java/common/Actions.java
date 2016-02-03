package common;

public enum Actions {
	ADD,
	MODIFY,
	DELETE,
	LOAD_INITIAL_REPOSITORY,
	USRNAME_PASSWORD,
	REQUEST_FILE_ADD,
	REQUEST_FILE_MODIFY,
	SENDING_FILE_ADD,
	SENDING_FILE_MODIFY,
	CONFLICT,
	EXCEPTION,
	FILE_SOCKET_PORT;
	
	public static Actions fromInteger(int x) {
        switch(x) {
        case 0:
            return ADD;
        case 1:
            return MODIFY;
        case 2:
            return DELETE;
        case 3:
            return LOAD_INITIAL_REPOSITORY;
        case 4:
            return USRNAME_PASSWORD;
        case 5:
            return REQUEST_FILE_ADD;
        case 6:
            return REQUEST_FILE_MODIFY;
        case 7:
        	return SENDING_FILE_ADD;
        case 8:
        	return SENDING_FILE_MODIFY;
        case 9:
            return CONFLICT;
        case 10:
        	return EXCEPTION;
        case 11:
        	return FILE_SOCKET_PORT;
        }
        return null;
    }
}
