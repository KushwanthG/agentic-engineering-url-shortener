package demo.web;

import demo.store.Counter;

/** Fixture: depends on Counter through an import (GET /{code}). */
public class Controller {
    private final Counter counter = new Counter();

    public int handle() {
        return counter.next();
    }
}
