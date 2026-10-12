package xtdpotato.xero_quest_journal.forgecompat;

public final class RegisterPayloadHandlersEvent {
    private final PayloadRegistrar registrar = new PayloadRegistrar();

    public RegisterPayloadHandlersEvent() {
        ForgeNetworkBridge.initialize();
    }

    public PayloadRegistrar registrar(String version) {
        return registrar;
    }
}
