package com.donnnno.arcticons.helper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies fork-specific icon aliases to generated appfilter resources.
 * Keeps the upstream newicons/appfilter.xml untouched for easy future rebasing.
 */
public final class CustomIconMappings {
    private static final Pattern ITEM = Pattern.compile(
            "<item\\s+component=\"([^\"]+)\"\\s+drawable=\"([^\"]+)\"\\s*/>");

    private CustomIconMappings() {}

    public static void writeMerged(Path upstream, Path overrides, Set<String> availableIcons,
                                   Path... destinations) throws IOException {
        String base = Files.readString(upstream, StandardCharsets.UTF_8);
        int closing = base.lastIndexOf("</resources>");
        if (closing < 0) {
            throw new IOException("Upstream appfilter.xml is missing </resources>");
        }

        StringBuilder additions = new StringBuilder();
        int newMappings = 0;
        if (Files.isRegularFile(overrides)) {
            String custom = Files.readString(overrides, StandardCharsets.UTF_8);
            if (!custom.contains("<resources>") || !custom.contains("</resources>")) {
                throw new IOException("Custom icon mappings require a <resources> root");
            }
            Matcher matcher = ITEM.matcher(custom);
            Set<String> unique = new HashSet<>();
            int parsed = 0;
            while (matcher.find()) {
                parsed++;
                String component = matcher.group(1);
                String drawable = matcher.group(2);
                if (!component.matches("ComponentInfo\\{[A-Za-z0-9_.$/-]+\\}") ||
                        !drawable.matches("[a-z0-9_]+")) {
                    throw new IOException("Invalid custom mapping: " + component);
                }
                if (!availableIcons.contains(drawable)) {
                    throw new IOException("Custom mapping references missing SVG: " + drawable);
                }
                if (unique.add(component) && !base.contains("component=\"" + component + "\"")) {
                    additions.append("\t<item component=\"").append(component)
                            .append("\" drawable=\"").append(drawable).append("\"/>\n");
                    newMappings++;
                }
            }
            if (parsed != custom.split("<item", -1).length - 1) {
                throw new IOException("Malformed <item> entry in custom icon mappings");
            }
        }

        String result = base.substring(0, closing) + additions + base.substring(closing);
        for (Path destination : destinations) {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, result, StandardCharsets.UTF_8);
        }
        System.out.println("Applied " + newMappings + " fork-specific icon mappings");
    }
}
