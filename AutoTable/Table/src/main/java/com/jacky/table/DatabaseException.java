package com.jacky.table;

class DatabaseException extends RuntimeException {

	private static final long serialVersionUID = -716877249061673684L;

	public DatabaseException() {}
	
	public DatabaseException(String message) {
		super(message);
	}
}
