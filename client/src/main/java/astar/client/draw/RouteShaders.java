package astar.client.draw;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Optional;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * The mod's own render pipelines, on the game's renderer: {@code route} draws the route overlay
 * in the world (ribbons, markers, block tops, arrowheads, each shaped per pixel, so edges stay
 * smooth at any distance), and {@code panel} the rounded rectangles of the screens. The shaders
 * are in {@code assets/astar/shaders/core/}.
 *
 * <p>With "Improved Transparency" on, the game draws see-through things in several passes; the
 * route types carry pipelines for those too.
 */
public final class RouteShaders {

    private RouteShaders() {}

    private static final Identifier ROUTE_SHADER = id("core/route");
    private static final Identifier PANEL_SHADER = id("core/panel");

    private static final RenderPipeline.Snippet ROUTE_SNIPPET =
            RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withVertexShader(ROUTE_SHADER)
                    .withFragmentShader(ROUTE_SHADER)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .buildSnippet();

    /** Hidden behind blocks, as the world is; doesn't hide what's drawn after it. */
    private static final RenderPipeline ROUTE = RenderPipeline.builder(ROUTE_SNIPPET)
            .withLocation(id("pipeline/route"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    /** Drawn over everything, walls included. */
    private static final RenderPipeline ROUTE_XRAY = RenderPipeline.builder(ROUTE_SNIPPET)
            .withLocation(id("pipeline/route_xray"))
            .withDepthStencilState(Optional.empty())
            .build();

    private static RenderPipeline.Builder oitBase() {
        return RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withVertexShader(ROUTE_SHADER)
                .withFragmentShader(ROUTE_SHADER)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false);
    }

    /** The route in the world, hidden by walls. */
    public static final RenderType WORLD = RenderType.create("astar_route",
            RenderSetup.builder(ROUTE)
                    .setOitPipelines(OitPipelineSet.builder("astar_route", oitBase()).build())
                    .sortOnUpload()
                    .createRenderSetup());

    /** The route seen through walls. */
    public static final RenderType XRAY = RenderType.create("astar_route_xray",
            RenderSetup.builder(ROUTE_XRAY)
                    .setOitPipelines(OitPipelineSet.builder("astar_route_xray", oitBase())
                            .withoutDepthTest().build())
                    .sortOnUpload()
                    .createRenderSetup());

    /** Rounded rectangles and their shadows, for the screens ({@link Panels}). */
    public static final RenderPipeline PANEL =
            RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withVertexShader(PANEL_SHADER)
                    .withFragmentShader(PANEL_SHADER)
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withLocation(id("pipeline/panel"))
                    .build();

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("astar", path);
    }
}
