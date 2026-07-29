package com.skyraax.logisticmatica.server;

/** Low-cost counters exposed by the admin diagnostics command. */
final class ShareDiagnostics {
	record Snapshot(long dirtyScans, long fallbackScans, long changedContainers,
			long removedContainers, long syncPackets, long lastScanNanos, long maxScanNanos) {}

	private long dirtyScans;
	private long fallbackScans;
	private long changedContainers;
	private long removedContainers;
	private long syncPackets;
	private long lastScanNanos;
	private long maxScanNanos;

	void reset() {
		this.dirtyScans = 0L;
		this.fallbackScans = 0L;
		this.changedContainers = 0L;
		this.removedContainers = 0L;
		this.syncPackets = 0L;
		this.lastScanNanos = 0L;
		this.maxScanNanos = 0L;
	}

	void recordScanWork(int dirty, int fallback, int changed, int removed, long elapsedNanos) {
		this.dirtyScans += dirty;
		this.fallbackScans += fallback;
		this.changedContainers += changed;
		this.removedContainers += removed;
		this.lastScanNanos = Math.max(0L, elapsedNanos);
		this.maxScanNanos = Math.max(this.maxScanNanos, this.lastScanNanos);
	}

	void recordSyncPacket() {
		this.syncPackets++;
	}

	Snapshot snapshot() {
		return new Snapshot(this.dirtyScans, this.fallbackScans, this.changedContainers,
				this.removedContainers, this.syncPackets, this.lastScanNanos, this.maxScanNanos);
	}
}
