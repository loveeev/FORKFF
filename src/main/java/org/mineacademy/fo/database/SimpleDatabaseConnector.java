package org.mineacademy.fo.database;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.exception.FoException;
import org.mineacademy.fo.remain.Remain;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a MySQL database connector.<br>
 * This class does not run any queries. It only connects to the database.<br>
 * <b>To use this class you must know the MySQL command syntax!</b>
 * <br><br>
 * To run sql queries please use {@link SimpleDatabaseManager}, {@link SimpleDatabaseObject} or {@link SimpleFlatDatabase}
 * <br><br>
 * Before running any queries from managers make sure to connect to a database from this connector.
 * <br><br>
 * An example of connecting to the database and adding a manager may look like this:
 * <pre>
 * public class DatabaseConnector extends SimpleDatabaseConnector;
 * public class PhoneDatabaseManager extends SimpleDatabaseObject<Phone>;
 *
 * DatabaseConnector.getInstance()
 *       .addManager(PhoneDatabaseManager.getInstance())
 *       .connect("localhost", 3306, "test", "root", "");
 * </pre>
 *
 * After the connection is established, the {@link #onConnected()} method is run automatically.
 * Then all managers added to the connector automatically run their {@link #onConnected()} methods.
 *
 * @author kangarko
 * @author Rubix327
 * @since 6.2.5.6
 */
public class SimpleDatabaseConnector {

	private final List<SimpleDatabaseManager> managers = new ArrayList<>();

	/**
	 * The established connection, or null if none
	 */
	private volatile Connection connection;

	/**
	 * The raw URL from which the connection was created
	 */
	@Getter
	private String url;

	/**
	 * The last credentials from the connect function, or null if never called
	 */
	private LastCredentials lastCredentials;

	/**
	 * Private indicator that we are connecting to database right now
	 */
	private boolean connecting = false;

	/**
	 * Optional Hikari data source (you plugin needs to include com.zaxxer.HikariCP library in its plugin.yml (MC 1.16+ required)
	 */
	private Object hikariDataSource;

	// --------------------------------------------------------------------
	// Connecting
	// --------------------------------------------------------------------

	/**
	 * Attempts to establish a new database connection
	 */
	public final void connect(final String host, final int port, final String database, final String user, final String password) {
		this.connect(host, port, database, user, password, true);
	}

	/**
	 * Attempts to establish a new database connection
	 */
	public final void connect(final String host, final int port, final String database, final String user, final String password, final boolean autoReconnect) {
		this.connect("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&useUnicode=yes&characterEncoding=UTF-8&autoReconnect=" + autoReconnect, user, password);
	}

	/**
	 * Connects to the database.
	 * <br>
	 * WARNING: Requires a database type NOT needing a username nor a password!
	 */
	public final void connect(final String url) {
		this.connect(url, null, null);
	}

	/**
	 * Connects to the database
	 */
	public final void connect(final String url, final String user, final String password) {

		this.url = url;
		this.connecting = true;

		try {

			// Support local storage of databases on your disk, typically in your plugin's folder
			// Make sure to load the library using "libraries" and "legacy-libraries" feature in plugin.yml:
			//
			// libraries:
			// - org.xerial:sqlite-jdbc:3.36.0.3
			//
			// legacy-libraries:
			// - org.xerial:sqlite-jdbc:3.36.0.3
			//
			if (url.startsWith("jdbc:sqlite")) {
				Class.forName("org.sqlite.JDBC");

				this.connection = DriverManager.getConnection(url);
			}

			// Avoid using imports so that Foundation users don't have to include Hikari, you can
			// optionally load the library using "libraries" and "legacy-libraries" feature in plugin.yml:
			//
			// libraries:
			// - com.zaxxer:HikariCP:5.0.1
			// legacy-libraries:
			//  - org.slf4j:slf4j-simple:1.7.36
			//  - org.slf4j:slf4j-api:1.7.36
			//  - com.zaxxer:HikariCP:4.0.3
			//
			else if (ReflectionUtil.isClassAvailable("com.zaxxer.hikari.HikariConfig")) {

				final Object hikariConfig = ReflectionUtil.instantiate("com.zaxxer.hikari.HikariConfig");

				if (url.startsWith("jdbc:mysql://"))
					try {
						ReflectionUtil.invoke("setDriverClassName", hikariConfig, "com.mysql.cj.jdbc.Driver");

					} catch (final Throwable t) {

						// Fall back to legacy driver
						ReflectionUtil.invoke("setDriverClassName", hikariConfig, "com.mysql.jdbc.Driver");
					}
				else if (url.startsWith("jdbc:mariadb://"))
					ReflectionUtil.invoke("setDriverClassName", hikariConfig, "org.mariadb.jdbc.Driver");

				else
					throw new FoException("Unknown database driver, expected jdbc:mysql or jdbc:mariadb, got: " + url);

				ReflectionUtil.invoke("setJdbcUrl", hikariConfig, url);

				if (user != null)
					ReflectionUtil.invoke("setUsername", hikariConfig, user);

				if (password != null)
					ReflectionUtil.invoke("setPassword", hikariConfig, password);

				final Constructor<?> dataSourceConst = ReflectionUtil.getConstructor("com.zaxxer.hikari.HikariDataSource", hikariConfig.getClass());
				final Object hikariSource = ReflectionUtil.instantiate(dataSourceConst, hikariConfig);

				this.hikariDataSource = hikariSource;

				final Method getConnection = hikariSource.getClass().getDeclaredMethod("getConnection");

				try {
					this.connection = ReflectionUtil.invoke(getConnection, hikariSource);

				} catch (final Throwable t) {
					Common.warning("Could not get HikariCP connection, please report this with the information below to github.com/kangarko/foundation");
					Common.warning("Method: " + getConnection);
					Common.warning("Arguments: " + Common.join(getConnection.getParameters()));

					t.printStackTrace();
				}
			}

			/*
			 * Check for JDBC Drivers (MariaDB, MySQL or Legacy MySQL)
			 */
			else {
				if (url.startsWith("jdbc:mariadb://") && ReflectionUtil.isClassAvailable("org.mariadb.jdbc.Driver"))
					Class.forName("org.mariadb.jdbc.Driver");

				else if (url.startsWith("jdbc:mysql://") && ReflectionUtil.isClassAvailable("com.mysql.cj.jdbc.Driver"))
					Class.forName("com.mysql.cj.jdbc.Driver");

				else {
					Common.warning("Your database driver is outdated, switching to MySQL legacy JDBC Driver. If you encounter issues, consider updating your database or switching to MariaDB. You can safely ignore this warning");

					Class.forName("com.mysql.jdbc.Driver");
				}

				this.connection = user != null && password != null ? DriverManager.getConnection(url, user, password) : DriverManager.getConnection(url);
			}

			for (SimpleDatabaseManager m : managers){
				m.setConnector(this);
			}

			this.lastCredentials = new LastCredentials(url, user, password);
			this.onConnected();

			for (SimpleDatabaseManager m : managers){
				m.onConnected();
				if (m.getAfterConnected() != null){
					m.getAfterConnected().accept(m);
				}
			}

		} catch (final Exception ex) {

			if (Common.getOrEmpty(ex.getMessage()).contains("No suitable driver found"))
				Common.logFramed(true,
						"Failed to look up database driver! If you had database disabled,",
						"then enable it and reload - this is expected.",
						"",
						"You have have access to your server machine, try installing",
						"https://mariadb.com/downloads/connectors/connectors-data-access/",
						"",
						"If this problem persists after a restart, please contact",
						"your hosting provider with the error message below.");
			else
				Common.logFramed(true,
						"Failed to connect to database",
						"URL: " + url,
						"Error: " + ex.getMessage());

			Remain.sneaky(ex);

		} finally {
			this.connecting = false;
		}
	}

	/**
	 * Attempts to connect using last known credentials. Fails gracefully if those are not provided
	 * i.e. connect function was never called
	 */
	protected final void connectUsingLastCredentials() {
		if (this.lastCredentials != null){
			this.connect(this.lastCredentials.url, this.lastCredentials.user, this.lastCredentials.password);
		}
	}

	/**
	 * Called automatically after the first connection has been established
	 */
	protected void onConnected() {
	}

	public final SimpleDatabaseConnector addManager(SimpleDatabaseManager manager){
		managers.add(manager);
		return this;
	}

	// --------------------------------------------------------------------
	// Disconnecting
	// --------------------------------------------------------------------

	/**
	 * Attempts to close the result set if it is not already closed
	 */
	public final void close(ResultSet resultSet) {
		try {
			if (!resultSet.isClosed())
				resultSet.close();

		} catch (final SQLException e) {
			Common.error(e, "Error closing database result set!");
		}
	}

	/**
	 * Attempts to close the connection, if not null
	 */
	public final void close() {
		try {
			if (this.connection != null)
				this.connection.close();

			if (this.hikariDataSource != null)
				ReflectionUtil.invoke("close", this.hikariDataSource);

		} catch (final SQLException e) {
			Common.error(e, "Error closing database connection!");
		}
	}

	/**
	 * Is the connection established, open and valid?
	 * Performs a blocking ping request to the database
	 *
	 * @return whether the connection driver was set
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	protected final boolean isConnected() {
		if (!this.isLoaded())
			return false;

		try {
			if (!this.connection.isValid(0))
				return false;
		} catch (SQLException | AbstractMethodError err) {
			// Pass through silently
		}

		try {
			return !this.connection.isClosed();

		} catch (final SQLException ex) {
			return false;
		}
	}

	protected final Connection getConnection(){
		return this.connection;
	}

	public final boolean isConnecting() {
		return connecting;
	}

	// --------------------------------------------------------------------
	// Non-blocking checking
	// --------------------------------------------------------------------

	/**
	 * Checks if the "connect(...)" function was called
	 */
	public void checkEstablished() {
		Valid.checkBoolean(this.isLoaded(), "Connection has never been established, did you call connect() on " + this + "? Use isLoaded() to check.");
	}

	/**
	 * Check if the connect function was called so that the driver was loaded
	 *
	 * @return true if the driver was loaded
	 */
	public final boolean isLoaded() {
		return this.connection != null;
	}

	// --------------------------------------------------------------------
	// Classes
	// --------------------------------------------------------------------

	/**
	 * Stores last known credentials from the "connect()" methods
	 */
	@RequiredArgsConstructor
	private static final class LastCredentials {

		/**
		 * The connecting URL, for example:
		 * <p>
		 * jdbc:mysql://host:port/database
		 */
		private final String url;

		/**
		 * The username for the database
		 */
		private final String user;

		/**
		 * The password for the database
		 */
		private final String password;

	}
}