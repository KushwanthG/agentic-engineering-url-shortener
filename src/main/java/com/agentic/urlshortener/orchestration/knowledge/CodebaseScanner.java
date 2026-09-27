package com.agentic.urlshortener.orchestration.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads a source tree for brownfield impact analysis (FR-ORC-14, ADR-017): the import graph of
 * {@code src/main/java} (explicit imports plus same-package references; comments and string literals
 * are ignored), the tests of {@code src/test/java}, and the Markdown documents of {@code docs/}.
 * Everything is read below the configured root only; symbolic links are not followed. Read-only.
 */
public final class CodebaseScanner {

    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern COMMENTS_AND_LITERALS =
            Pattern.compile("//[^\\n]*|/\\*.*?\\*/|\"\"\".*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'", Pattern.DOTALL);

    private final Path root;

    public CodebaseScanner(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Whether the root holds a Java source tree. */
    public boolean available() {
        return Files.isDirectory(root.resolve("src/main/java"));
    }

    public Index scan() {
        if (!available()) {
            return new Index(Map.of(), Map.of(), Map.of());
        }
        Map<String, Source> sources = read("src/main/java", ".java");
        Map<String, String> qualifiedToName = new LinkedHashMap<>();
        Map<String, Parsed> parsed = new TreeMap<>();
        for (Source source : sources.values()) {
            String name = source.fileName().substring(0, source.fileName().length() - ".java".length());
            String code = strip(source.text());
            Matcher pkg = PACKAGE.matcher(code);
            String packageName = pkg.find() ? pkg.group(1) : "";
            String key = parsed.containsKey(name) ? packageName + "." + name : name;
            parsed.put(key, new Parsed(key, packageName, source.path(), code));
            qualifiedToName.put(packageName.isEmpty() ? name : packageName + "." + name, key);
        }
        Map<String, ClassInfo> classes = new TreeMap<>();
        for (Parsed p : parsed.values()) {
            Set<String> dependencies = new TreeSet<>();
            Matcher imports = IMPORT.matcher(p.code());
            while (imports.find()) {
                String imported = imports.group(2);
                if (imports.group(1) != null) {
                    imported = imported.substring(0, imported.lastIndexOf('.'));
                }
                String key = qualifiedToName.get(imported);
                if (key != null) {
                    dependencies.add(key);
                }
            }
            String body = IMPORT.matcher(PACKAGE.matcher(p.code()).replaceAll("")).replaceAll("");
            for (Parsed other : parsed.values()) {
                if (other != p && other.packageName().equals(p.packageName()) && mentions(body, simpleName(other.key()))) {
                    dependencies.add(other.key());
                }
            }
            dependencies.remove(p.key());
            classes.put(p.key(), new ClassInfo(p.key(), p.packageName(), p.path(), Set.copyOf(dependencies)));
        }
        Map<String, String> tests = new TreeMap<>();
        read("src/test/java", ".java").values().forEach(s -> tests.put(s.path(), strip(s.text())));
        Map<String, String> docs = new TreeMap<>();
        read("docs", ".md").values().forEach(s -> docs.put(s.path(), s.text()));
        return new Index(classes, tests, docs);
    }

    /** One main class: its key (simple name, or qualified if the simple name is taken), package, path, and dependencies. */
    public record ClassInfo(String name, String packageName, String path, Set<String> dependencies) {
    }

    /** The scanned tree; paths are relative to the root with {@code /} separators. */
    public record Index(Map<String, ClassInfo> classes, Map<String, String> tests, Map<String, String> docs) {

        /**
         * The seeds and every class that depends on them directly or transitively, in discovery order;
         * each maps to its dependency chain from itself to the seed it was reached from.
         */
        public Map<String, List<String>> reverseClosure(Set<String> seeds) {
            Map<String, List<String>> chains = new LinkedHashMap<>();
            Deque<String> queue = new ArrayDeque<>();
            new TreeSet<>(seeds).stream().filter(classes::containsKey).forEach(seed -> {
                chains.put(seed, List.of(seed));
                queue.add(seed);
            });
            while (!queue.isEmpty()) {
                String current = queue.poll();
                for (ClassInfo candidate : classes.values()) {
                    if (!chains.containsKey(candidate.name()) && candidate.dependencies().contains(current)) {
                        List<String> chain = new ArrayList<>();
                        chain.add(candidate.name());
                        chain.addAll(chains.get(current));
                        chains.put(candidate.name(), List.copyOf(chain));
                        queue.add(candidate.name());
                    }
                }
            }
            return chains;
        }

        /** Test sources that reference any of the given classes. */
        public List<String> testsReferencing(Set<String> classNames) {
            return tests.entrySet().stream()
                    .filter(t -> classNames.stream().anyMatch(n -> mentions(t.getValue(), simpleName(n))))
                    .map(Map.Entry::getKey).toList();
        }

        /** Documents that mention any of the terms (case-insensitive). */
        public List<String> docsMentioning(List<String> terms) {
            return docs.entrySet().stream()
                    .filter(d -> terms.stream().anyMatch(term -> d.getValue().toLowerCase(Locale.ROOT)
                            .contains(term.toLowerCase(Locale.ROOT))))
                    .map(Map.Entry::getKey).toList();
        }
    }

    private record Source(String path, String fileName, String text) {
    }

    private record Parsed(String key, String packageName, String path, String code) {
    }

    private Map<String, Source> read(String directory, String extension) {
        Path base = root.resolve(directory).normalize();
        Map<String, Source> sources = new TreeMap<>();
        if (!base.startsWith(root) || !Files.isDirectory(base)) {
            return sources;
        }
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(f -> Files.isRegularFile(f) && f.toString().endsWith(extension)).toList()) {
                Path normalized = file.toAbsolutePath().normalize();
                if (!normalized.startsWith(root)) {
                    continue;
                }
                String relative = root.relativize(normalized).toString().replace('\\', '/');
                sources.put(relative, new Source(relative, normalized.getFileName().toString(),
                        Files.readString(normalized, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + directory, e);
        }
        return sources;
    }

    private static String strip(String source) {
        return COMMENTS_AND_LITERALS.matcher(source).replaceAll(" ");
    }

    private static boolean mentions(String code, String name) {
        return Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(code).find();
    }

    private static String simpleName(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
