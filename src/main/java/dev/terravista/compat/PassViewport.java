package dev.terravista.compat;

import com.mojang.renderpearl.api.commands.RenderPass;

/** Remembers an explicit viewport across a host pass suspension without per-draw allocations. */
public final class PassViewport {
    private boolean set;
    private int x, y, width, height;

    public void set(RenderPass pass, int x, int y, int width, int height) {
        pass.setViewport(x, y, width, height);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        set = true;
    }

    public void restore(RenderPass pass) {
        if (set) pass.setViewport(x, y, width, height);
    }
}
