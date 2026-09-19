package net.dvmn2.chatFix;

/**
 * Режимы чата. Каждый режим знает имена своих ключей в chatdata.yml
 * (у игрока) и в config.yml (общие настройки), чтобы не собирать строки вручную.
 * <ul>
 *   <li>LOCAL  — сообщение без префикса, игроки того же мира в радиусе;</li>
 *   <li>WORLD  — сообщение с "!", игроки того же мира (радиус 0 = весь мир);</li>
 *   <li>GLOBAL — сообщение с "!!", все онлайн-игроки (радиуса нет).</li>
 * </ul>
 */
public enum ChatMode {
    LOCAL("local"),
    WORLD("world"),
    GLOBAL("global");

    private final String key;

    ChatMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /**
     * У глобального чата радиуса нет.
     */
    public boolean hasRadius() {
        return this != GLOBAL;
    }

    public String prefixKey() {
        return key + "-prefix";
    }

    public String postfixKey() {
        return key + "-postfix";
    }

    /**
     * Флаг: использовать индивидуальное значение enabled вместо общего.
     */
    public String customModeKey() {
        return "custom-" + key + "-chat-mode";
    }

    /**
     * Индивидуальное значение enabled (также имя ключа в config.yml -> settings).
     */
    public String enabledKey() {
        return key + "-chat-enabled";
    }

    /**
     * Флаг: использовать индивидуальный радиус вместо общего.
     */
    public String customRadiusKey() {
        return "custom-" + key + "-chat-radius";
    }

    /**
     * Индивидуальный радиус (также имя ключа в config.yml -> settings).
     */
    public String radiusKey() {
        return key + "-chat-radius";
    }

    /**
     * @return режим по имени (без учёта регистра) или {@code null}.
     */
    public static ChatMode fromKey(String key) {
        for (ChatMode mode : values()) {
            if (mode.key.equalsIgnoreCase(key)) {
                return mode;
            }
        }
        return null;
    }
}