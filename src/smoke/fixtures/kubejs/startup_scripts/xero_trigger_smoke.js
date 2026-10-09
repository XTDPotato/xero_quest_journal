// Test-only fixture: copied exclusively into run-control-graphs-kubejs.
global.xeroQuestSmoke = { questId: "", calls: 0, scripts: 0 };
global.xeroQuestSmokeCall = function(event) {
    if (event.eventType !== "click" || event.controlId !== "kube_call") {
        throw new Error("Missing trigger context");
    }
    global.xeroQuestSmoke.questId = event.questId;
    global.xeroQuestSmoke.calls++;
};
