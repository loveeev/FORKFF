package org.mineacademy.fo.bungee.message;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.bungee.BungeeListener;
import org.mineacademy.fo.bungee.BungeeMessageType;
import org.mineacademy.fo.plugin.SimplePlugin;

import java.util.UUID;

/**
 * Represents a in/out message with a given action and server name
 * and a safety check for writing/reading the data
 * based on the action's content.
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class Message {

	/**
	 * The listener associated with this message
	 */
	private final BungeeListener listener;

	/**
	 * The UUID of the sender who initiated the packet, can be null
	 */
	@Getter
	private UUID senderUid;

	/**
	 * The server name
	 */
	@Getter
	private String serverName;

	/**
	 * The action
	 */
	private BungeeMessageType action;

	/**
	 * The current position of writing the data based on the
	 * {@link BungeeMessageType#getContent()}
	 */
	private int actionHead = 0;

	/**
	 * Set the sender UUID
	 *
	 * @param raw
	 */
	protected final void setSenderUid(String raw) {
		if (raw != null)
			try {
				this.senderUid = UUID.fromString(raw);
			} catch (final IllegalArgumentException ex) {
				throw new IllegalArgumentException("Expected UUID, got " + raw + " for packet " + this.action + " from server " + this.serverName);
			}
	}

	/**
	 * Set the server name for this message, reason it is here:
	 * cannot read in the constructor in {@link OutgoingMessage}
	 *
	 * @param serverName
	 */
	protected final void setServerName(String serverName) {
		Valid.checkBoolean(this.serverName == null, "Server name already set");
		Valid.checkNotNull(serverName, "Server name cannot be null!");

		this.serverName = serverName;
	}

	/**
	 * Set the action head for this message, reason it is here:
	 * static access in {@link OutgoingMessage}
	 */
	protected final void setAction(String actionName) {
		final BungeeMessageType action = BungeeMessageType.getByName(this.listener, actionName);

		Valid.checkNotNull(action, "Unknown plugin action named: " + actionName + ". IF YOU UPDATED THE PLUGIN BY RELOADING, you need to stop your entire network, ensure all servers were updated and start it again.");
		this.setAction(action);
	}

	/**
	 * Set the action head for this message, reason it is here:
	 * static access in {@link OutgoingMessage}
	 *
	 * @param action
	 */
	protected final void setAction(BungeeMessageType action) {
		Valid.checkBoolean(this.action == null, "Action already set");

		this.action = action;
	}

	/**
	 * Return the bungee action
	 *
	 * @param <T>
	 * @return
	 */
	public <T extends BungeeMessageType> T getAction() {
		return (T) this.action;
	}

	/**
	 * Ensures we are reading in the correct order as the given {@link BungeeMessageType}
	 * specifies in its {@link BungeeMessageType#getContent()} getter.
	 * <p>
	 * This also ensures we are reading the correct data type (both primitives and wrappers
	 * are supported).
	 *
	 * @param typeOf
	 */
	protected final void moveHead(Class<?> typeOf) {
		Valid.checkNotNull(this.serverName, "Server name not set!");
		Valid.checkNotNull(this.action, "Action not set!");

		final Class<?>[] content = this.action.getContent();
		Valid.checkBoolean(this.actionHead < content.length, "Head out of bounds! Max data size for " + this.action.name() + " is " + content.length);

		this.actionHead++;
	}

	/**
	 * Return the bungee channel this message is coming from
	 *
	 * @return
	 */
	public String getChannel() {
		return this.listener.getChannel();
	}

	protected abstract byte[] getData();

	/**
	 * Send this message with the current data to BungeeCord (you need an implementation
	 * upstream to handle it!)
	 */
	public final void sendToBungee() {
		Bukkit.getServer().sendPluginMessage(SimplePlugin.getInstance(), this.getChannel(), this.getData());
	}
}
