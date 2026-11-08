import com.sorrowmist.useless.stretcher.content.catalog.CatalogTupleEvidence;
import java.nio.file.Path;

/** Pure strings only; exports deliberately different valid files for comparator checks. */
public final class TupleFixtureWriter {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        String[][][] cases = {
                {{"mod:a", "fp-a", "core"}, {"mod:b", "fp-b", "adapter"}, {"mod:c", "fp-c", "core"}},
                {{"mod:c", "fp-c", "core"}, {"mod:b", "fp-b", "adapter"}, {"mod:a", "fp-a", "core"}},
                {{"mod:a", "fp-a", "core"}, {"mod:b", "changed", "adapter"}, {"mod:c", "fp-c", "core"}},
                {}
        };
        for (int index = 0; index < cases.length; index++) {
            var accumulator = new CatalogTupleEvidence.Accumulator(true);
            for (var fields : cases[index]) accumulator.append(fields[0], fields[1], fields[2]);
            CatalogTupleEvidence.exportAsync(accumulator.finish(), directory.resolve("fixture-" + index + ".tsv"), () -> true).get();
        }
    }
}
