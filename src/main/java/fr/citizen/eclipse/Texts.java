package fr.citizen.eclipse;

import fr.citizen.eclipse.config.EclipseConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Mise en forme des messages (thème : or ancien / rouge sombre). */
public final class Texts {
    private Texts() {}

    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH'h'mm", Locale.FRENCH);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH);

    public static ZonedDateTime local(long ms) {
        return Instant.ofEpochMilli(ms).atZone(EclipseConfig.get().zone());
    }

    public static String date(long ms) {
        return LONG.format(local(ms));
    }

    public static String hour(long ms) {
        return HOUR.format(local(ms));
    }

    public static String duration(long ms) {
        if (ms < 0) ms = 0;
        long totalMin = (ms + 59_999) / 60_000;
        long d = totalMin / (60 * 24);
        long h = (totalMin / 60) % 24;
        long m = totalMin % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append(" j ");
        if (h > 0) sb.append(h).append(" h ");
        if (d == 0) sb.append(m).append(" min");
        return sb.toString().trim();
    }

    public static MutableComponent prefix() {
        return Component.literal("☾ ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal("Éclipse").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" » ").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent msg(String text) {
        return prefix().append(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }

    public static MutableComponent gold(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }

    public static MutableComponent link() {
        String url = EclipseConfig.get().web.urlPublique;
        return Component.literal("[Calendrier]").withStyle(s -> s
                .withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url))));
    }
}
