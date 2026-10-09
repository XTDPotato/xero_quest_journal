import xtdpotato.xero_quest_journal.client.AsyncQuestSearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Standalone worker regression: no Minecraft client or rendering objects required. */
public final class AsyncQuestSearchCheck {
    public static void main(String[] args) throws Exception {
        var documents = new ArrayList<AsyncQuestSearch.Document>();
        for (int i = 0; i < 50_000; i++)
            documents.add(new AsyncQuestSearch.Document("task:" + i,
                    List.of("wood_task_" + i, "木头任务", "Logs and materials")));
        var source = List.copyOf(documents);
        var search = new AsyncQuestSearch();
        long started = System.nanoTime();
        long first = search.request("WOOD_TASK_49999", source);
        var exact = await(search, first);
        require(exact.ids().equals(Set.of("task:49999")), "Exact ID search mismatch");
        require(exact.workerThread().startsWith("Xero Quest Search-"), "Indexing ran outside the search worker");
        require(!exact.workerThread().equals(Thread.currentThread().getName()), "Search ran on the caller thread");
        search.request("wood_task_100", source);
        search.request("not_a_real_result", source);
        long last = search.request("wood_task_300 木头", source);
        var latest = await(search, last);
        require(latest.query().equals("wood_task_300 木头") && latest.ids().contains("task:300"),
                "Stale query replaced the most recent multi-token query");
        require(!latest.ids().contains("task:100"), "Old query results survived");
        require(await(search, search.request("mutou", source)).ids().size() == 50_000, "Full pinyin lost");
        require(await(search, search.request("mt", source)).ids().size() == 50_000, "Pinyin initials lost");
        require(await(search, search.request("木头", source)).ids().size() == 50_000, "Chinese search lost");
        require(await(search, search.request("missing-value", source)).ids().isEmpty(), "No-match query was not empty");
        var replacement = List.of(new AsyncQuestSearch.Document("replacement", List.of("replacement only")));
        search.request("wood", source);
        var replaced = await(search, search.request("replacement", replacement));
        require(replaced.ids().equals(Set.of("replacement")), "Old catalog result survived replacement");
        require(await(search, search.request("", replacement)).ids().equals(Set.of("replacement")),
                "Empty query did not return current documents");
        search.request("replacement", replacement);
        search.cancel();
        Thread.sleep(30);
        require(search.takeResult() == null, "Cancelled search published a result");
        System.out.println("PASS: 50,000 search documents; worker-only indexing, English/Chinese/pinyin/initials,"
                + " token intersection, coalescing, replacement, clear and cancellation; "
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + " ms total.");
    }

    private static AsyncQuestSearch.Result await(AsyncQuestSearch search, long sequence) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            var result = search.takeResult();
            if (result != null) {
                require(result.sequence() == sequence, "Worker returned an outdated sequence");
                return result;
            }
            Thread.sleep(2);
        }
        throw new AssertionError("Search did not complete within 30 seconds");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
