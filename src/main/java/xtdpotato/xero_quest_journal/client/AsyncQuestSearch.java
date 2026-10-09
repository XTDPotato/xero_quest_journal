package xtdpotato.xero_quest_journal.client;

import com.ibm.icu.text.Transliterator;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Worker-only text indexing; never reads Minecraft objects or mutates UI state. */
public final class AsyncQuestSearch {
    public record Document(String id, List<String> fields) {
        public Document {
            fields = fields.stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).distinct().toList();
        }
    }
    public record Result(long sequence, String query, Set<String> ids, String workerThread) {}
    private record Request(long sequence, String query, List<Document> documents) {}

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "Xero Quest Search-" + THREAD_IDS.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final AtomicReference<Request> pending = new AtomicReference<>();
    private final AtomicReference<Result> completed = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long sequence;
    private List<Document> indexedSource;
    private Map<String, String> index = Map.of();

    public long request(String query, List<Document> documents) {
        long next = ++sequence;
        completed.set(null);
        pending.set(new Request(next, normalize(query), documents));
        schedule();
        return next;
    }

    public void cancel() {
        sequence++;
        pending.set(null);
        completed.set(null);
    }

    public Result takeResult() {
        Result result = completed.getAndSet(null);
        return result != null && result.sequence() == sequence ? result : null;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private void schedule() {
        if (running.compareAndSet(false, true)) WORKERS.execute(this::run);
    }

    private void run() {
        try {
            Request request;
            while ((request = pending.getAndSet(null)) != null) {
                if (request.sequence() != sequence) continue;
                if (indexedSource != request.documents()) {
                    var next = new LinkedHashMap<String, String>();
                    var fields = new HashMap<String, String>();
                    Transliterator pinyin = null;
                    boolean invalidated = false;
                    for (Document document : request.documents()) {
                        Request newer = pending.get();
                        if (sequence != request.sequence()
                                && (newer == null || newer.documents() != request.documents())) {
                            invalidated = true;
                            break;
                        }
                        var text = new StringBuilder();
                        for (String field : document.fields()) {
                            String compiled = fields.get(field);
                            if (compiled == null) {
                                if (pinyin == null && containsHan(field))
                                    pinyin = Transliterator.getInstance("Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
                                compiled = searchable(field, pinyin);
                                fields.put(field, compiled);
                            }
                            text.append(compiled).append('\n');
                        }
                        next.put(document.id(), text.toString());
                    }
                    if (invalidated) continue;
                    index = next;
                    indexedSource = request.documents();
                }
                if (request.sequence() != sequence) continue;
                String[] tokens = request.query().isBlank() ? new String[0] : request.query().split("\\s+");
                var matches = new HashSet<String>();
                int visited = 0;
                for (var entry : index.entrySet()) {
                    if ((visited++ & 127) == 0 && request.sequence() != sequence) break;
                    boolean match = true;
                    for (String token : tokens) {
                        if (!entry.getValue().contains(token)) { match = false; break; }
                    }
                    if (match) matches.add(entry.getKey());
                }
                if (request.sequence() == sequence)
                    completed.set(new Result(request.sequence(), request.query(), Set.copyOf(matches),
                            Thread.currentThread().getName()));
            }
        } finally {
            running.set(false);
            if (pending.get() != null) schedule();
        }
    }

    private static boolean containsHan(String value) {
        return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private static String searchable(String value, Transliterator pinyin) {
        String raw = normalize(value);
        String translated = pinyin != null && containsHan(value) ? normalize(pinyin.transliterate(value)) : raw;
        var compact = new StringBuilder();
        var initials = new StringBuilder();
        for (String word : translated.split("[^a-z0-9]+")) {
            if (word.isEmpty()) continue;
            compact.append(word);
            initials.append(word.charAt(0));
        }
        return raw + "\n" + translated + "\n" + compact + "\n" + initials;
    }
}
