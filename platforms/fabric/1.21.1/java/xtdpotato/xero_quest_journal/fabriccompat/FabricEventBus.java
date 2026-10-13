package xtdpotato.xero_quest_journal.fabriccompat;

public final class FabricEventBus {
    public static final FabricEventBus EVENT_BUS = new FabricEventBus();
    public FabricCommandEvent post(FabricCommandEvent event) { return event; }
    public <T> T post(T event) {
        if (event instanceof FabricEvents.LivingDeathEvent value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents.died(value);
            if (!value.isCanceled()) {
                xtdpotato.xero_quest_journal.core.runtime.QuestConditionEvents.killed(value);
                xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents.death(value);
            }
        } else if (event instanceof FabricEvents.LivingEvent.LivingJumpEvent value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents.jump(value);
        } else if (event instanceof FabricEvents.PlayerEvent.PlayerLoggedOutEvent value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents.leave(value);
            xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents.logout(value);
        } else if (event instanceof FabricEvents.PlayerEvent.PlayerChangedDimensionEvent value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents.dimension(value);
        } else if (event instanceof FabricEvents.LevelTickEvent.Post value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestGraphEvents.worldTick(value);
        } else if (event instanceof FabricEvents.PlayerInteractEvent.RightClickBlock value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents.useBlock(value);
        } else if (event instanceof FabricEvents.PlayerInteractEvent.RightClickItem value) {
            xtdpotato.xero_quest_journal.core.runtime.QuestStructureEvents.useItem(value);
        } else if (event instanceof FabricScreenEvent.MouseButtonPressed.Pre value) {
            xtdpotato.xero_quest_journal.client.XeroQuestClient.mouseDown(value);
        } else if (event instanceof FabricScreenEvent.KeyPressed.Pre value) {
            xtdpotato.xero_quest_journal.client.XeroQuestClient.keyDown(value);
        } else {
            throw new IllegalArgumentException("Unsupported Fabric test event: " + event.getClass());
        }
        return event;
    }
}
