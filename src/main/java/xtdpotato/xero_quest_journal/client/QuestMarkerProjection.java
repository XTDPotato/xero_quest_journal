package xtdpotato.xero_quest_journal.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/** Projects into the rendered camera basis, including detached and reversed third-person views. */
public final class QuestMarkerProjection {
    public record Marker(int x, int y, boolean onScreen) {}

    private QuestMarkerProjection() {}

    public static Marker project(Camera camera, Vec3 target, int width, int height, double fov) {
        Vec3 delta = target.subtract(camera.getPosition());
        // Camera LEFT really is screen-left; GUI coordinates increase rightward and downward.
        double right = -delta.dot(new Vec3(camera.getLeftVector()));
        double up = delta.dot(new Vec3(camera.getUpVector()));
        double forward = delta.dot(new Vec3(camera.getLookVector()));
        double centerX = width / 2.0, centerY = height / 2.0;
        double focal = height / (2.0 * Math.tan(Math.toRadians(Math.clamp(fov, 30, 110) / 2.0)));
        int marginX = Math.min(42, Math.max(16, width / 18));
        int marginY = Math.min(52, Math.max(20, height / 14));
        double projectedX = centerX + right * focal / Math.max(.001, forward);
        double projectedY = centerY - up * focal / Math.max(.001, forward);
        boolean onScreen = forward > .001
                && projectedX >= marginX && projectedX <= width - marginX
                && projectedY >= marginY && projectedY <= height - marginY;
        if (onScreen) return new Marker((int) Math.round(projectedX), (int) Math.round(projectedY), true);

        double rayX = right, rayY = -up;
        // Exactly behind (or coincident) has no unique side. Keep the indicator stable at the bottom.
        if (Math.abs(rayX) + Math.abs(rayY) < .001) rayY = 1;
        double scaleX = Math.abs(rayX) < .001 ? Double.POSITIVE_INFINITY
                : Math.max(1, centerX - marginX) / Math.abs(rayX);
        double scaleY = Math.abs(rayY) < .001 ? Double.POSITIVE_INFINITY
                : Math.max(1, centerY - marginY) / Math.abs(rayY);
        double scale = Math.min(scaleX, scaleY);
        return new Marker((int) Math.round(centerX + rayX * scale),
                (int) Math.round(centerY + rayY * scale), false);
    }
}
