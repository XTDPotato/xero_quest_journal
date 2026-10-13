package xtdpotato.xero_quest_journal.client;

public final class FabricClientAccess {
    private FabricClientAccess() {}
    public static void categoryShader(net.minecraft.client.renderer.ShaderInstance shader) {
        CategoryIconRenderer.shader(shader);
    }
}
