package net.prominic.dm.report;

public final class UserDbAccess {
	private final String DbReplicaID;
	private final int accessLevel;

	public UserDbAccess(String dbReplicaId, int accessLevel) {
		this.DbReplicaID = dbReplicaId;
		this.accessLevel = accessLevel;
	}

	public String getDbReplicaID() {
		return this.DbReplicaID;
	}

	public int getAccessLevel() {
		return accessLevel;
	}
}