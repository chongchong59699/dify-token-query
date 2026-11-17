package com.company.difytokenquery.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReplacementPlanItem {
    private final DifyAppSummary app;
    private final List<ModelSlotChange> changes;
    private final String skippedReason;

    public ReplacementPlanItem(DifyAppSummary app, List<ModelSlotChange> changes, String skippedReason) {
        this.app = app;
        this.changes = changes == null
                ? Collections.<ModelSlotChange>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelSlotChange>(changes));
        this.skippedReason = skippedReason == null ? "" : skippedReason;
    }

    public DifyAppSummary getApp() {
        return app;
    }

    public List<ModelSlotChange> getChanges() {
        return changes;
    }

    public String getSkippedReason() {
        return skippedReason;
    }

    public boolean isExecutable() {
        return !changes.isEmpty() && skippedReason.isEmpty();
    }
}