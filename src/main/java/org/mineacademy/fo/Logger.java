package org.mineacademy.fo;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.mineacademy.fo.exception.CommandException;

/**
 * This class is a wrapper on Common logger methods.
 * It has some improvements:<br>
 * <ul>
 *     <li>It does not require all messages to be String.</li>
 *     <li>It appends the message with a prefix.<br>
 *     This prefix is empty by default. You can change it by calling {@link #setLogPrefix}.</li>
 * </ul>
 */
public class Logger {

    @Getter
    private static String logPrefix = "";

    public static void setLogPrefix(String prefix){
        Common.setLogPrefix("");
        logPrefix = prefix;
    }

    /**
     * Log an info message to the console.
     * @param s the message
     */
    public static void info(Object s){ Common.log(logPrefix + s); }

    /**
     * Log an info message without a prefix.
     * @param s the message
     */
    public static void infoNoPrefix(Object s){
        Common.logNoPrefix(s.toString());
    }

    /**
     * See {@link Common#logFramed}
     * @param s the message
     */
    public static void infoFramed(Object s){
        Common.logFramed(logPrefix + s);
    }

    /**
     * See {@link Common#logF}
     * @param s the message
     * @param args replacements
     */
    public static void infoF(Object s, Object... args){
        Common.logF(logPrefix + s.toString(), args);
    }

    /**
     * Log a warning to the console.
     * @param s the warning message
     */
    public static void warning(Object s){
        Common.warning("&e" + logPrefix + s);
    }

    /**
     * Log an error message to the console.
     * @param s the error message
     */
    public static void error(Object s){
        Common.error(new CommandException(), "&6" + logPrefix + s);
    }

    /**
     * Throw an exception and log the message to the console.
     * @param t an exception or an error
     * @param s the message
     */
    public static void error(Throwable t, Object s){
        Common.error(t, "&6" + logPrefix + s);
    }

    /**
     * Append prefix and colorize the message.
     * @param s the message
     * @return colorized prefixed message
     */
    public static String get(String s){
        return Common.colorize(logPrefix + s);
    }

    public static void printErrors(String... lines){
        Bukkit.getLogger().severe(Common.consoleLine());
        for (String s : lines){
            Bukkit.getLogger().severe(s);
        }
        Bukkit.getLogger().severe(Common.consoleLine());
    }
}
