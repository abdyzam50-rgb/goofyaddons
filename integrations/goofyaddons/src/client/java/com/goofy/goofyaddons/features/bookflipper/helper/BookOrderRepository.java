package com.goofy.goofyaddons.features.bookflipper.helper;

import java.util.List;
import java.util.Set;

/** Read/reconcile/write ownership evidence. Implementations never perform game actions. */
public interface BookOrderRepository {
    List<BookPosition> read() throws Exception;
    void backupVerified(List<BookPosition> expected) throws Exception;
    void reconcileVerified(List<BookPosition> expected,Set<String> stillPresent) throws Exception;
    void writeTracked(List<BookPosition> plans,Set<String> exposed) throws Exception;
    void write(List<BookPosition> positions) throws Exception;
}
