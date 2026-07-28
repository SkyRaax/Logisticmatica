package com.skyraax.logisticmatica.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import com.skyraax.logisticmatica.Logisticmatica;
import com.skyraax.logisticmatica.share.ShareProtocol;

/** Persistent world-local project metadata plus content-addressed litematic blobs. */
public final class ShareStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final long MAX_DECOMPRESSED_NBT_BYTES = 256L * 1024L * 1024L;
	private static final long SAVE_INTERVAL_TICKS = 100L;
	private static final long STOP_FLUSH_TIMEOUT_MILLIS = 10_000L;

	private final Map<UUID, SharedProject> projects = new LinkedHashMap<>();
	private final Object writeLock = new Object();
	private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Logisticmatica store writer");
		thread.setDaemon(true);
		return thread;
	});
	private UUID serverId = UUID.randomUUID();
	@Nullable private Path root;
	@Nullable private Path blobs;
	@Nullable private WriteRequest pendingWrite;
	private boolean writerRunning;
	private volatile boolean dirty;
	private long nextSaveTick;

	private record WriteRequest(Path target, byte[] bytes) {}

	public Collection<SharedProject> projects() {
		return this.projects.values();
	}

	public UUID serverId() {
		return this.serverId;
	}

	@Nullable
	public SharedProject get(UUID id) {
		return this.projects.get(id);
	}

	public void put(SharedProject project) {
		this.projects.put(project.id(), project);
	}

	@Nullable
	public SharedProject remove(UUID id) {
		return this.projects.remove(id);
	}

	public void start(MinecraftServer server) {
		this.root = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(Logisticmatica.MOD_ID);
		this.blobs = this.root.resolve("schematics");
		this.projects.clear();
		this.dirty = false;
		this.nextSaveTick = 0L;

		this.serverId = UUID.randomUUID();
		try {
			Files.createDirectories(this.blobs);
			this.load();
		} catch (IOException e) {
			Logisticmatica.LOGGER.error("[{}] Could not load sharing store: {}", Logisticmatica.MOD_NAME, e.getMessage(), e);
		}
	}

	public void stop() {
		this.flushNow();
		this.projects.clear();
		this.root = null;
		this.blobs = null;
	}

	/** Validates, hashes and stores a compressed NBT schematic. Returns the SHA-256 hash. */
	public String storeSchematic(byte[] bytes) throws IOException {
		if (bytes.length == 0 || bytes.length > ShareProtocol.MAX_SCHEMATIC_BYTES) {
			throw new IOException("Schematic size must be between 1 and " + ShareProtocol.MAX_SCHEMATIC_BYTES + " bytes");
		}

		// A .litematic is compressed NBT. Parsing with an explicit heap budget rejects corrupt files
		// and compressed bombs without introducing a server-side Litematica dependency.
		CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(bytes),
				NbtAccounter.create(MAX_DECOMPRESSED_NBT_BYTES));
		if (!root.contains("Version") || !root.contains("Metadata") || !root.contains("Regions")) {
			throw new IOException("NBT does not contain the required Litematica structure");
		}

		String hash = sha256(bytes);
		Path blobPath = this.blobPath(hash);
		if (!Files.exists(blobPath)) {
			writeAtomically(blobPath, bytes);
		}
		return hash;
	}

	public byte[] readSchematic(String hash) throws IOException {
		Path file = this.blobPath(hash);
		long size = Files.size(file);
		if (size <= 0 || size > ShareProtocol.MAX_SCHEMATIC_BYTES) {
			throw new IOException("Stored schematic has invalid size " + size);
		}
		return Files.readAllBytes(file);
	}

	/** Marks the store dirty. Runtime disk I/O is coalesced and performed off the server thread. */
	public void save() {
		if (this.root != null) {
			this.dirty = true;
		}
	}

	/** Captures at most one immutable persistence image per interval and queues its disk write. */
	public void tick(long serverTick) {
		if (!this.dirty || this.root == null || serverTick < this.nextSaveTick) {
			return;
		}
		this.enqueueCurrentState();
		this.nextSaveTick = serverTick + SAVE_INTERVAL_TICKS;
	}

	private void flushNow() {
		if (this.root == null) {
			return;
		}
		if (this.dirty) {
			this.enqueueCurrentState();
		}
		long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STOP_FLUSH_TIMEOUT_MILLIS);
		synchronized (this.writeLock) {
			while ((this.writerRunning || this.pendingWrite != null) && System.nanoTime() < deadline) {
				try {
					long remaining = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
					this.writeLock.wait(remaining);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			if (this.writerRunning || this.pendingWrite != null) {
				Logisticmatica.LOGGER.warn("[{}] Timed out waiting for the sharing store writer during shutdown.",
						Logisticmatica.MOD_NAME);
			}
		}
	}

	private void enqueueCurrentState() {
		if (this.root == null) {
			return;
		}

		JsonObject rootJson = new JsonObject();
		rootJson.addProperty("schema", SharedProject.SCHEMA_VERSION);
		JsonArray projectsJson = new JsonArray();
		rootJson.addProperty("serverId", this.serverId.toString());
		for (SharedProject project : this.projects.values()) {
			projectsJson.add(project.toJson());
		}
		rootJson.add("projects", projectsJson);

		byte[] bytes = GSON.toJson(rootJson).getBytes(StandardCharsets.UTF_8);
		this.dirty = false;
		this.enqueueWrite(new WriteRequest(this.root.resolve("projects.json"), bytes));
	}

	private void enqueueWrite(WriteRequest request) {
		synchronized (this.writeLock) {
			// Latest state wins while an older image is still being written. This bounds the queue to one image.
			this.pendingWrite = request;
			if (this.writerRunning) {
				return;
			}
			this.writerRunning = true;
			this.writer.execute(this::drainWrites);
		}
	}

	private void drainWrites() {
		while (true) {
			WriteRequest request;
			synchronized (this.writeLock) {
				request = this.pendingWrite;
				this.pendingWrite = null;
				if (request == null) {
					this.writerRunning = false;
					this.writeLock.notifyAll();
					return;
				}
			}
			try {
				Files.createDirectories(request.target().getParent());
				writeAtomically(request.target(), request.bytes());
			} catch (IOException e) {
				this.dirty = true;
				Logisticmatica.LOGGER.error("[{}] Could not save sharing store: {}",
						Logisticmatica.MOD_NAME, e.getMessage(), e);
			}
		}
	}

	private void load() throws IOException {
		Path file = this.root.resolve("projects.json");
		if (!Files.isRegularFile(file)) {
			return;
		}

		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				throw new IOException("projects.json is not a JSON object");
			}

			JsonObject json = parsed.getAsJsonObject();
			if (json.has("serverId")) {
				try {
					this.serverId = UUID.fromString(json.get("serverId").getAsString());
				} catch (IllegalArgumentException e) {
					throw new IOException("Invalid sharing server id", e);
				}
			}
			if (!json.has("projects") || !json.get("projects").isJsonArray()) {
				return;
			}

			for (JsonElement raw : json.getAsJsonArray("projects")) {
				if (!raw.isJsonObject()) {
					continue;
				}
				SharedProject project = SharedProject.fromJson(raw.getAsJsonObject());
				if (project != null && this.withinLimits(project)
						&& Files.isRegularFile(this.blobPath(project.schematicHash()))) {
					this.projects.put(project.id(), project);
				} else {
					Logisticmatica.LOGGER.warn("[{}] Skipping invalid shared project entry.", Logisticmatica.MOD_NAME);
				}
			}
		}

		Logisticmatica.LOGGER.info("[{}] Loaded {} shared project(s).", Logisticmatica.MOD_NAME, this.projects.size());
	}

	private boolean withinLimits(SharedProject project) {
		if (this.projects.size() >= ShareProtocol.MAX_PROJECTS_PER_PLAYER
				|| project.members().size() > ShareProtocol.MAX_MEMBERS_PER_PROJECT
				|| project.substitutions().size() > ShareProtocol.MAX_SUBSTITUTIONS_PER_PROJECT
				|| project.containers().size() > ShareProtocol.MAX_CONTAINERS_PER_PROJECT) return false;
		int totalContainers = this.projects.values().stream()
				.mapToInt(existing -> existing.containers().size()).sum() + project.containers().size();
		if (totalContainers > ShareProtocol.MAX_CONTAINERS_GLOBAL) return false;
		return project.containers().values().stream()
				.allMatch(items -> items.size() <= ShareProtocol.MAX_ITEM_TYPES_PER_CONTAINER);
	}

	private Path blobPath(String hash) throws IOException {
		if (this.blobs == null || !hash.matches("[0-9a-f]{64}")) {
			throw new IOException("Invalid or unavailable schematic hash");
		}
		return this.blobs.resolve(hash + ".litematic");
	}

	private static String sha256(byte[] bytes) throws IOException {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 is unavailable", e);
		}
	}

	private static void writeAtomically(Path target, byte[] bytes) throws IOException {
		Path temp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.write(temp, bytes);
		try {
			Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
