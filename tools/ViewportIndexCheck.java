import xtdpotato.xero_quest_journal.client.ViewportIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ViewportIndexCheck {
    public static void main(String[] args) {
        var entries = new ArrayList<ViewportIndex.Entry<Integer>>();
        var random = new Random(81371);
        for (int i = 0; i < 50_000; i++) {
            double x = random.nextInt(1_000_000) - 500_000;
            double y = random.nextInt(100_000) - 50_000;
            entries.add(new ViewportIndex.Entry<>(i, x, y, x + 168, y + 112, i));
        }
        entries.add(new ViewportIndex.Entry<>(50_000, -400_000, -3, 400_000, 3, 50_000));
        var index = new ViewportIndex<>(entries);
        for (int i = 0; i < 500; i++) {
            double x = random.nextInt(1_000_000) - 500_000;
            double y = i == 0 ? 0 : random.nextInt(100_000) - 50_000;
            var expected = new ArrayList<Integer>();
            for (var entry : entries)
                if (entry.intersects(x, y, x + 960, y + 540)) expected.add(entry.value());
            var actual = index.query(x, y, x + 960, y + 540);
            if (!actual.equals(expected)) throw new AssertionError("Viewport mismatch " + i);
        }
        if (!index.query(0, 0, 0, 0).contains(50_000))
            throw new AssertionError("Off-screen endpoints lost a crossing connection");
        if (!new ViewportIndex<Integer>(List.of()).query(0, 0, 1, 1).isEmpty())
            throw new AssertionError("Empty query");
        var crossing = ViewportIndex.clipSegment(-1_000_000, 200, 1_000_000, 200, 0, 0, 960, 540);
        if (crossing == null || Math.abs(crossing[0]) > .0001 || Math.abs(crossing[2] - 960) > .0001)
            throw new AssertionError("Long crossing line was not clipped");
        if (ViewportIndex.clipSegment(-1000, -1, 2000, -1, 0, 0, 960, 540) != null)
            throw new AssertionError("Invisible line was not rejected");
        for (int i = 0; i < 10_000; i++) {
            double x1 = random.nextInt(2_000_000) - 1_000_000, y1 = random.nextInt(2_000_000) - 1_000_000;
            double x2 = random.nextInt(2_000_000) - 1_000_000, y2 = random.nextInt(2_000_000) - 1_000_000;
            var line = ViewportIndex.clipSegment(x1, y1, x2, y2, 0, 0, 960, 540);
            if (line == null) continue;
            for (int point = 0; point < 4; point += 2) {
                if (line[point] < -.001 || line[point] > 960.001
                        || line[point + 1] < -.001 || line[point + 1] > 540.001)
                    throw new AssertionError("Clipped endpoint escaped viewport");
                if (Math.abs((line[point] - x1) * (y2 - y1) - (line[point + 1] - y1) * (x2 - x1)) > 1)
                    throw new AssertionError("Clipped point is not on the source line");
            }
        }
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) index.query(i * 45 - 250_000, -300, i * 45 - 249_040, 300);
        System.out.println("PASS: 50,001 objects, 500 exact queries, paint order, crossing edges and 10,000 line clips.");
        System.out.println("10,000 indexed queries: " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
