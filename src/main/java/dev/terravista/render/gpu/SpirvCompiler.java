package dev.terravista.render.gpu;

import java.nio.ByteBuffer;

import static org.lwjgl.util.shaderc.Shaderc.*;

/** 同步管理原生编译器、缓存与独立结果缓冲区。 */
public final class SpirvCompiler {
    private static final ShaderBinaryCache BINARIES = new ShaderBinaryCache();

    public static synchronized ByteBuffer compile(String source, ShaderStage stage, String name) {
        var key = new ShaderBinaryCache.Key(source, stage, name);
        byte[] cached = BINARIES.find(key);
        if (cached != null) return ByteBuffer.allocateDirect(cached.length).put(cached).flip();
        long compiler = shaderc_compiler_initialize();
        long options = shaderc_compile_options_initialize();
        try {
            shaderc_compile_options_set_target_env(options, shaderc_target_env_vulkan, shaderc_env_version_vulkan_1_2);
            shaderc_compile_options_set_auto_bind_uniforms(options, true);
            shaderc_compile_options_set_auto_map_locations(options, true);
            shaderc_compile_options_add_macro_definition(options, "TERRAVISTA_VULKAN", "1");
            int kind = switch (stage) {
                case VERTEX -> shaderc_vertex_shader;
                case FRAGMENT -> shaderc_fragment_shader;
                case COMPUTE -> shaderc_compute_shader;
                default -> throw new IllegalArgumentException("TerraVista Render: Unsupported stage for VK: " + stage);
            };
            long result = shaderc_compile_into_spv(compiler, source, kind, name, "main", options);
            try {
                if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
                    throw new IllegalStateException("TerraVista Render: SPIR-V compile failed for " + name + ":\n"
                            + shaderc_result_get_error_message(result));
                }
                ByteBuffer binary = shaderc_result_get_bytes(result);
                BINARIES.remember(key, binary);
                ByteBuffer copy = ByteBuffer.allocateDirect(binary.remaining());
                return copy.put(binary).flip();
            } finally {
                shaderc_result_release(result);
            }
        } finally {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private SpirvCompiler() {}
}
