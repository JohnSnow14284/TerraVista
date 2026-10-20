package dev.terravista.compat;

import dev.terravista.render.RenderSession;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** 访问世界渲染器持有的会话与生命周期入口。 */
public interface RenderSessionAccess {
    RenderSession terravista$session();
    void terravista$close();
    void terravista$create();
    void terravista$level(Level level);

    static RenderSession current() {
        var holder = holder();
        return holder == null ? null : holder.terravista$session();
    }

    static RenderSessionAccess holder() {
        return (RenderSessionAccess) Minecraft.getInstance().levelRenderer;
    }
}
