package com.direkjames.dkchat.format;

import java.util.List;

/**
 * One chat format from formats.yml.
 *
 * @param hover the format's own hover lines, or {@code null} to use the default hover
 */
public record ChatFormat(String id, int priority, String permission, String format, List<String> hover) {
}
