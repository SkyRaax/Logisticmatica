package com.skyraax.logisticmatica.server;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** FIFO dirty queue with key coalescing and a hard memory bound. */
final class BoundedDirtyQueue<T> {
	record Stats(int size, int capacity, long offered, long coalesced, long dropped, long processed) {}

	private final int capacity;
	private final ArrayDeque<T> queue = new ArrayDeque<>();
	private final Set<T> queued = new HashSet<>();
	private long offered;
	private long coalesced;
	private long dropped;
	private long processed;

	BoundedDirtyQueue(int capacity) {
		if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
		this.capacity = capacity;
	}

	boolean offer(T value) {
		this.offered++;
		if (!this.queued.add(value)) {
			this.coalesced++;
			return true;
		}
		if (this.queue.size() >= this.capacity) {
			this.queued.remove(value);
			this.dropped++;
			return false;
		}
		this.queue.addLast(value);
		return true;
	}

	T poll() {
		T value = this.queue.pollFirst();
		if (value != null) {
			this.queued.remove(value);
			this.processed++;
		}
		return value;
	}

	int size() {
		return this.queue.size();
	}

	boolean isEmpty() {
		return this.queue.isEmpty();
	}

	void clear() {
		this.queue.clear();
		this.queued.clear();
	}

	void reset() {
		this.clear();
		this.offered = 0L;
		this.coalesced = 0L;
		this.dropped = 0L;
		this.processed = 0L;
	}

	Stats stats() {
		return new Stats(this.queue.size(), this.capacity, this.offered,
				this.coalesced, this.dropped, this.processed);
	}
}
