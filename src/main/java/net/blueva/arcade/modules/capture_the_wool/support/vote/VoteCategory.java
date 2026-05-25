package net.blueva.arcade.modules.capture_the_wool.support.vote;

import java.util.Locale;

public enum VoteCategory {
    HEARTS("hearts"),
    TIME("time"),
    WEATHER("weather");

    private final String id;

    VoteCategory(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static VoteCategory fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (VoteCategory category : values()) {
            if (category.id.equals(normalized)) {
                return category;
            }
        }
        return null;
    }
}
