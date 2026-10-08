package com.goofy.goofyaddons.features.generalflipper;

import java.util.List;

/** Storage cannot perform game effects. A failed write must not authorize an action. */
interface GeneralOrderRepository {
    List<GeneralPosition> load() throws Exception;
    void save(List<GeneralPosition> positions) throws Exception;
}
