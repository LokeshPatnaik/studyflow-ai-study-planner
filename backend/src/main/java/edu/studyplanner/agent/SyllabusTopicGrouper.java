package edu.studyplanner.agent;

import edu.studyplanner.api.ApiModels.ParsedTopic;
import edu.studyplanner.api.ApiModels.SourceText;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Converts page-preserving PDF text into editable, study-sized topic suggestions. */
public final class SyllabusTopicGrouper {
    private static final Pattern UNIT = Pattern.compile("(?i)^(unit|module|chapter|part)\\s+([\\p{Alnum}]+)(?:\\s*[:.)-]\\s*(.*))?$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+(?:\\.\\d+){0,4}|[A-Z]|[IVXLCDM]+)[.)]?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_FOOTER = Pattern.compile("(?i)^(?:page\\s+)?\\d+(?:\\s+(?:of|/)\\s*\\d+)?$");
    private static final Set<String> BOILERPLATE = Set.of("contents", "table of contents", "syllabus", "references", "textbooks", "bibliography");
    private static final Set<String> STOP_WORDS = Set.of("and", "or", "of", "the", "to", "in", "for", "with", "by", "from", "an", "a", "on", "using", "introduction", "overview", "basics", "basic", "concepts", "concept");

    public List<ParsedTopic> groupPages(List<String> pages) {
        List<RawHeading> headings = extract(pages == null ? List.of() : pages);
        if (headings.isEmpty()) return List.of();
        List<Candidate> candidates = hierarchicalCandidates(headings);
        if (candidates.size() > 12) candidates = mergeToStudySizedGroups(candidates);
        return candidates.stream().map(this::toTopic).toList();
    }

    private List<RawHeading> extract(List<String> pages) {
        List<RawHeading> result = new ArrayList<>();
        String unit = null;
        String chapter = null;
        SourceText unitSource = null;
        RawHeading current = null;
        for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
            String pageText = Objects.toString(pages.get(pageIndex), "");
            for (String raw : pageText.split("\\R")) {
                String line = raw.replaceAll("\\s+", " ").trim();
                if (line.length() < 3 || PAGE_FOOTER.matcher(line).matches()) continue;
                Matcher unitMatch = UNIT.matcher(line);
                if (unitMatch.matches()) {
                    unit = unitMatch.group(1) + " " + unitMatch.group(2);
                    chapter = blankToNull(unitMatch.group(3));
                    unitSource = new SourceText(line, pageIndex + 1, true);
                    current = null;
                    continue;
                }
                if (BOILERPLATE.contains(line.toLowerCase(Locale.ROOT))) continue;

                Matcher numbered = NUMBERED.matcher(line);
                String number = null;
                String title = line;
                if (numbered.matches()) {
                    number = numbered.group(1).toUpperCase(Locale.ROOT);
                    title = numbered.group(2).trim();
                } else if (line.matches("^[•▪◦*–-]\\s*.+$")) {
                    title = line.replaceFirst("^[•▪◦*–-]\\s*", "").trim();
                } else if (!looksLikeHeading(line)) {
                    if (current != null) current.addDetail(line, pageIndex + 1);
                    else {
                        current = new RawHeading(null, line, line, unit, chapter, unitSource, pageIndex + 1);
                        result.add(current);
                    }
                    continue;
                }

                if (title.length() < 3) continue;
                current = new RawHeading(number, title, line, unit, chapter, unitSource, pageIndex + 1);
                result.add(current);
            }
        }
        // PDF headers and repeated table-of-contents rows often repeat on every page.
        LinkedHashMap<String, RawHeading> unique = new LinkedHashMap<>();
        for (RawHeading heading : result) {
            String key = normalize(heading.unit) + "|" + normalize(heading.number) + "|" + normalize(heading.title);
            RawHeading first = unique.putIfAbsent(key, heading);
            if (first != null) first.mergeContent(heading);
        }
        return new ArrayList<>(unique.values());
    }

    private boolean looksLikeHeading(String line) {
        if (line.length() > 130 || line.matches(".*[.!?;]$")) return false;
        if (line.matches(".*[a-z].*") && line.matches(".*[A-Z].*") && line.matches(".*\\b(?:is|are|was|were|will|shall|should|must|can|may)\\b.*")) return false;
        return line.matches(".*\\p{L}.*");
    }

    private List<Candidate> hierarchicalCandidates(List<RawHeading> headings) {
        Map<String, List<RawHeading>> byUnit = new LinkedHashMap<>();
        for (RawHeading heading : headings) byUnit.computeIfAbsent(Objects.toString(heading.unit, "Syllabus topics"), ignored -> new ArrayList<>()).add(heading);
        List<Candidate> candidates = new ArrayList<>();
        for (List<RawHeading> unitHeadings : byUnit.values()) {
            int minDepth = unitHeadings.stream().mapToInt(h -> h.number == null ? Integer.MAX_VALUE : depth(h.number)).min().orElse(Integer.MAX_VALUE);
            int baseDepth = minDepth == Integer.MAX_VALUE ? Integer.MAX_VALUE : (minDepth > 2 ? minDepth - 1 : minDepth);
            Candidate current = null;
            String currentKey = null;
            for (RawHeading heading : unitHeadings) {
                if (heading.number == null) {
                    current = new Candidate(heading.unit, heading.chapter, new ArrayList<>(List.of(heading)));
                    candidates.add(current);
                    currentKey = null;
                    continue;
                }
                String key = prefix(heading.number, baseDepth);
                if (current == null || !Objects.equals(key, currentKey)) {
                    current = new Candidate(heading.unit, heading.chapter, new ArrayList<>());
                    candidates.add(current);
                    currentKey = key;
                }
                current.headings.add(heading);
            }
        }
        return candidates;
    }

    private List<Candidate> mergeToStudySizedGroups(List<Candidate> original) {
        int words = original.stream().flatMap(c -> c.headings.stream()).mapToInt(h -> h.title.split("\\s+").length).sum();
        int desired = Math.min(original.size(), Math.max(10, Math.max((int)Math.ceil(original.size() / 8.0), (int)Math.ceil(words / 120.0))));
        Map<String, List<Candidate>> byUnit = new LinkedHashMap<>();
        for (Candidate candidate : original) byUnit.computeIfAbsent(Objects.toString(candidate.unit, "Syllabus topics"), ignored -> new ArrayList<>()).add(candidate);
        desired = Math.max(desired, byUnit.size());
        Map<String, Integer> targets = distributeTargets(byUnit, desired);
        List<Candidate> merged = new ArrayList<>();
        for (var entry : byUnit.entrySet()) {
            List<Candidate> groups = new ArrayList<>(entry.getValue());
            int target = targets.get(entry.getKey());
            while (groups.size() > target) {
                int bestIndex = 0;
                double bestScore = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < groups.size() - 1; i++) {
                    double score = similarity(groups.get(i), groups.get(i + 1));
                    if (score > bestScore) { bestScore = score; bestIndex = i; }
                }
                groups.get(bestIndex).headings.addAll(groups.get(bestIndex + 1).headings);
                groups.remove(bestIndex + 1);
            }
            merged.addAll(groups);
        }
        return merged;
    }

    private Map<String, Integer> distributeTargets(Map<String, List<Candidate>> byUnit, int desired) {
        int total = byUnit.values().stream().mapToInt(List::size).sum();
        Map<String, Integer> targets = new LinkedHashMap<>();
        for (var entry : byUnit.entrySet()) targets.put(entry.getKey(), Math.max(1, (int)Math.floor(desired * entry.getValue().size() / (double)total)));
        while (targets.values().stream().mapToInt(Integer::intValue).sum() < desired) {
            String largest = byUnit.keySet().stream().filter(key -> targets.get(key) < byUnit.get(key).size())
                    .max(Comparator.comparingDouble(key -> byUnit.get(key).size() / (double)targets.get(key))).orElse(null);
            if (largest == null) break;
            targets.put(largest, targets.get(largest) + 1);
        }
        return targets;
    }

    private double similarity(Candidate left, Candidate right) {
        Set<String> a = tokens(left.headings);
        Set<String> b = tokens(right.headings);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        return union.isEmpty() ? 0 : intersection.size() / (double)union.size();
    }

    private Set<String> tokens(List<RawHeading> headings) {
        return headings.stream().flatMap(h -> Arrays.stream(h.title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")))
                .filter(t -> t.length() > 2 && !STOP_WORDS.contains(t)).collect(Collectors.toSet());
    }

    private ParsedTopic toTopic(Candidate candidate) {
        List<RawHeading> source = candidate.headings;
        String title = source.size() == 1 ? source.get(0).title : groupedTitle(source);
        List<SourceText> content = new ArrayList<>();
        for (RawHeading heading : source) {
            if (heading.unitSource != null && (content.isEmpty() || !content.get(content.size() - 1).equals(heading.unitSource))) content.add(heading.unitSource);
            content.add(new SourceText(heading.original, heading.page, true));
            content.addAll(heading.details);
        }
        String description = content.stream().filter(x -> !x.heading()).map(SourceText::text).collect(Collectors.joining(" "));
        if (description.isBlank() && source.size() > 1) description = "Includes: " + source.stream().map(h -> h.title).collect(Collectors.joining("; "));
        if (description.length() > 1000) description = description.substring(0, 997) + "...";
        int words = content.stream().mapToInt(x -> x.text().split("\\s+").length).sum();
        int minutes = Math.max(45, Math.min(720, 35 + source.size() * 8 + words / 3));
        String allText = source.stream().map(h -> h.title + " " + h.details.stream().map(SourceText::text).collect(Collectors.joining(" "))).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
        String difficulty = allText.matches("(?s).*(advanced|proof|complexity|optimization|distributed|concurrency|cryptograph|formal|derivation).*" ) ? "HARD"
                : allText.matches("(?s).*(introduction|overview|fundamental|basic|definition).*" ) ? "EASY" : "MEDIUM";
        return new ParsedTopic(limit(title, 180), candidate.unit, candidate.chapter, description, difficulty, minutes, 3, 3, true, content);
    }

    private String groupedTitle(List<RawHeading> headings) {
        LinkedHashMap<String, Integer> frequency = new LinkedHashMap<>();
        for (RawHeading heading : headings) for (String token : heading.title.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
            if (token.length() > 2 && !STOP_WORDS.contains(token)) frequency.merge(token, 1, Integer::sum);
        List<String> common = frequency.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).limit(3).toList();
        if (!common.isEmpty()) {
            String label = String.join(" ", common);
            return Character.toUpperCase(label.charAt(0)) + label.substring(1) + " concepts";
        }
        String first = headings.get(0).title;
        if (headings.size() == 2) return limit(first + " & " + headings.get(1).title, 180);
        return limit(first + " + " + (headings.size() - 1) + " related concepts", 180);
    }

    private int depth(String number) { return number.split("\\.").length; }
    private String prefix(String number, int depth) {
        String[] parts = number.split("\\.");
        if (depth == Integer.MAX_VALUE || parts.length <= depth) return number;
        return String.join(".", Arrays.copyOf(parts, depth));
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String normalize(String value) { return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max).trim(); }

    private static final class Candidate {
        private final String unit;
        private final String chapter;
        private final List<RawHeading> headings;
        private Candidate(String unit, String chapter, List<RawHeading> headings) { this.unit = unit; this.chapter = chapter; this.headings = headings; }
    }
    private static final class RawHeading {
        private final String number;
        private final String title;
        private final String original;
        private final String unit;
        private final String chapter;
        private final SourceText unitSource;
        private final int page;
        private final List<SourceText> details = new ArrayList<>();
        private RawHeading(String number, String title, String original, String unit, String chapter, SourceText unitSource, int page) {
            this.number = number; this.original = original; this.title = title.trim();
            this.unit = unit; this.chapter = chapter; this.unitSource = unitSource; this.page = page;
        }
        private void addDetail(String text, int page) { details.add(new SourceText(text, page, false)); }
        private void mergeContent(RawHeading duplicate) { details.addAll(duplicate.details); }
    }
}
