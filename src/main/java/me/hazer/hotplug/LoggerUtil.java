package me.hazer.hotplug;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Централизованный логгер для префикса HazerHotPlug.
 *
 * <p>Небольшая обертка над стандартным java.util.logging.Logger,
 * чтобы во всех местах проекта использовался единый стиль сообщений.
 * Это упрощает чтение логов администратором сервера.</p>
 */
public final class LoggerUtil {

    private final Logger logger;
    private final String prefix;

    /**
     * Создание нового обертчика логгера.
     *
     * @param logger базовый логгер плагина
     */
    public LoggerUtil(Logger logger) {
        this.logger = logger;
        this.prefix = "[HazerHotPlug] ";
    }

    /**
     * Информационное сообщение.
     *
     * @param message текст сообщения
     */
    public void info(String message) {
        logger.info(prefix + message);
    }

    /**
     * Предупреждение.
     *
     * @param message текст сообщения
     */
    public void warn(String message) {
        logger.warning(prefix + message);
    }

    /**
     * Ошибка с исключением.
     *
     * @param message текст
     * @param throwable исключение
     */
    public void error(String message, Throwable throwable) {
        logger.log(Level.SEVERE, prefix + message, throwable);
    }

    /**
     * Ошибка без исключения.
     *
     * @param message текст
     */
    public void error(String message) {
        logger.severe(prefix + message);
    }
}
