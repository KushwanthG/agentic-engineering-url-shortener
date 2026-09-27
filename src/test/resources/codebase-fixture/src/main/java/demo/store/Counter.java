package demo.store;

/** Fixture: depends on Store through the same package, without an import. */
public class Counter {
    private final Store store = new Store();

    public int next() {
        return store.count() + 1;
    }
}
