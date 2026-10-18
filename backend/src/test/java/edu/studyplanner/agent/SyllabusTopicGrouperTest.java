package edu.studyplanner.agent;

import edu.studyplanner.api.ApiModels.ParsedTopic;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyllabusTopicGrouperTest {
    private final SyllabusTopicGrouper grouper = new SyllabusTopicGrouper();

    @Test void groupsManyTinyNumberedHeadingsIntoAboutTenTopicsAndRetainsSourcePages() {
        List<String> pages = new ArrayList<>();
        for (int page = 0; page < 7; page++) {
            List<String> lines = new ArrayList<>();
            if (page == 0) lines.add("Unit 1: Algorithms");
            for (int item = page * 10 + 1; item <= page * 10 + 10; item++)
                lines.add("1." + item + " Graph traversal search method " + item);
            pages.add(String.join("\n", lines));
        }

        List<ParsedTopic> result = grouper.groupPages(pages);

        assertThat(result).hasSizeBetween(8, 12);
        List<String> preservedHeadings = result.stream().flatMap(topic -> topic.sourceContent().stream())
                .filter(source -> source.heading() && source.text().matches("1\\.\\d+ Graph traversal search method \\d+"))
                .map(source -> source.text()).toList();
        assertThat(preservedHeadings).hasSize(70).contains("1.1 Graph traversal search method 1", "1.70 Graph traversal search method 70");
        assertThat(result).allSatisfy(topic -> {
            assertThat(topic.unit()).isEqualTo("Unit 1");
            assertThat(topic.chapter()).isEqualTo("Algorithms");
            assertThat(topic.sourceContent()).isNotEmpty();
        });
        assertThat(result.stream().flatMap(topic -> topic.sourceContent().stream()).map(source -> source.page()).distinct())
                .contains(1, 2, 3, 4, 5, 6, 7);
    }

    @Test void combinesAdjacentHeadingsThatShareAStudyConceptWithoutLosingTheirNames() {
        String[] concepts = {
                "Graph traversal breadth first search", "Graph traversal depth first search", "Graph traversal visited sets", "Graph traversal complexity",
                "Graph shortest path Dijkstra algorithm", "Graph shortest path Bellman Ford", "Graph shortest path negative edges", "Graph shortest path complexity",
                "Tree traversal preorder sequence", "Tree traversal inorder sequence", "Tree traversal postorder sequence", "Tree traversal complexity",
                "Dynamic programming knapsack recurrence", "Dynamic programming memoization", "Dynamic programming tabulation", "Dynamic programming optimization"
        };
        List<String> headings = new ArrayList<>(List.of("Unit 1: Algorithms"));
        for (int index = 0; index < concepts.length; index++) headings.add("1." + (index + 1) + " " + concepts[index]);

        List<ParsedTopic> result = grouper.groupPages(List.of(String.join("\n", headings)));

        assertThat(result.size()).isLessThan(concepts.length);
        List<String> retained = result.stream().flatMap(topic -> topic.sourceContent().stream())
                .filter(source -> source.heading() && source.text().startsWith("1."))
                .map(source -> source.text()).toList();
        assertThat(retained).hasSize(16);
        assertThat(result).anySatisfy(topic -> assertThat(topic.sourceContent().stream().filter(source -> source.heading() && source.text().startsWith("1.")).count()).isGreaterThan(1));
    }

    @Test void topicSeedEstimateGrowsWithRetainedContentVolume() {
        ParsedTopic shortTopic = grouper.groupPages(List.of("Unit 1: Algorithms\n1.1 Graph traversal")).getFirst();
        String detailed = "This section explains breadth first search, depth first search, queue management, visited vertices, traversal trees, and their time and space complexity. ".repeat(8);
        ParsedTopic detailedTopic = grouper.groupPages(List.of("Unit 1: Algorithms\n1.1 Graph traversal\n" + detailed)).getFirst();

        assertThat(detailedTopic.estimatedMinutes()).isGreaterThan(shortTopic.estimatedMinutes());
        assertThat(detailedTopic.description()).contains("breadth first search");
        assertThat(detailedTopic.sourceContent()).anyMatch(source -> !source.heading() && source.text().contains("space complexity"));
    }
}
