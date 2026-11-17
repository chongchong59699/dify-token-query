package com.company.difytokenquery.model;

public class ModelSlotChange {
    private final String slotLabel;
    private final String nodeType;
    private final String nodeId;
    private final String beforeProvider;
    private final String beforeModel;
    private final String beforeMode;
    private final String afterProvider;
    private final String afterModel;
    private final String afterMode;
    private final String beforeParams;
    private final String afterParams;

    public ModelSlotChange(String slotLabel, String nodeType, String nodeId,
                           String beforeProvider, String beforeModel, String beforeMode,
                           String afterProvider, String afterModel, String afterMode,
                           String beforeParams, String afterParams) {
        this.slotLabel = slotLabel;
        this.nodeType = nodeType;
        this.nodeId = nodeId;
        this.beforeProvider = beforeProvider;
        this.beforeModel = beforeModel;
        this.beforeMode = beforeMode;
        this.afterProvider = afterProvider;
        this.afterModel = afterModel;
        this.afterMode = afterMode;
        this.beforeParams = beforeParams;
        this.afterParams = afterParams;
    }

    public String getSlotLabel() { return slotLabel; }
    public String getNodeType() { return nodeType; }
    public String getNodeId() { return nodeId; }
    public String getBeforeProvider() { return beforeProvider; }
    public String getBeforeModel() { return beforeModel; }
    public String getBeforeMode() { return beforeMode; }
    public String getAfterProvider() { return afterProvider; }
    public String getAfterModel() { return afterModel; }
    public String getAfterMode() { return afterMode; }
    public String getBeforeParams() { return beforeParams; }
    public String getAfterParams() { return afterParams; }
}