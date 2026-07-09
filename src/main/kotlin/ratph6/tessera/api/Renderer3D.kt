package ratph6.tessera.api

import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.Shapes

// World-space 3D drawing. Only valid inside a RENDER_WORLD trigger — every call is a no-op otherwise,
// so a script that forgets and draws from a chat handler simply draws nothing (never crashes).
//
// MC 26.2 has no immediate-mode buffer/`endBatch`; geometry is handed to a deferred SubmitNodeCollector
// that the engine batches and flushes for us. We bind that collector + the world PoseStack (translated
// once to camera-relative so scripts pass absolute world coordinates) for the duration of the dispatch.
//
// Colours are packed ARGB — build them with color(r,g,b[,a]).
object Renderer3D {
    // full-bright packed light (block=15, sky=15); world text/geometry shouldn't be dimmed by lightmap
    private const val FULL_BRIGHT = 0xF000F0

    // bound per-frame by the render hook; null outside a RENDER_WORLD dispatch
    @Volatile private var pose: PoseStack? = null
    @Volatile private var collector: SubmitNodeCollector? = null
    @Volatile private var camera: CameraRenderState? = null

    // false = draw through terrain (ESP-style, the default); true = occluded by blocks like normal geometry
    @Volatile private var depth = false

    // ------------------------------------------------------------------------------------------------
    // bind / unbind (engine-internal)
    // ------------------------------------------------------------------------------------------------

    internal fun begin(ctx: LevelRenderContext) {
        val ps = ctx.poseStack()
        val cam = ctx.levelState().cameraRenderState
        pose = ps
        collector = ctx.submitNodeCollector()
        camera = cam
        // The context stack is NOT pre-translated to the camera; do it once so every draw takes world
        // coordinates. Pushed here, popped in end() — scripts never see the raw camera-relative frame.
        ps.pushPose()
        ps.translate(-cam.pos.x, -cam.pos.y, -cam.pos.z)
    }

    internal fun end() {
        pose?.popPose()
        pose = null
        collector = null
        camera = null
    }

    // ------------------------------------------------------------------------------------------------
    // colours + state
    // ------------------------------------------------------------------------------------------------

    @JvmStatic fun color(r: Int, g: Int, b: Int): Int = color(r, g, b, 255)

    @JvmStatic fun color(r: Int, g: Int, b: Int, a: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    // depth(false) draws through walls (default); depth(true) makes geometry occlude like blocks
    @JvmStatic fun setDepth(enabled: Boolean) { depth = enabled }

    // Custom LINES render type with the depth TEST disabled (CompareOp.ALWAYS_PASS), so edges draw
    // through terrain. No built-in type does this — the debug/secondary types only disable depth WRITE,
    // which still fails the test behind walls. Built lazily on first draw (after the render system is up)
    // and reused; relies on the tessera.accesswidener entries for RenderType.create + LINES_SNIPPET.
    private val linesThroughWalls: RenderType by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("tessera", "pipeline/lines_no_depth"))
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build()
        RenderType.create(
            "tessera_lines_no_depth",
            RenderSetup.builder(pipeline)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup(),
        )
    }

    // the line/edge render type for the current depth mode: depth-tested lines() when depth is on,
    // else the custom through-walls type above.
    private fun edgeType(): RenderType = if (depth) RenderTypes.lines() else linesThroughWalls

    // Custom QUADS render type for filled boxes with the depth test disabled — the through-walls twin of
    // debugFilledBox(). Cloned from DEBUG_FILLED_SNIPPET (which carries the QUADS topology + debug shaders)
    // with the depth state overridden. Same lazy/AW notes as linesThroughWalls.
    private val filledThroughWalls: RenderType by lazy {
        val pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("tessera", "pipeline/filled_no_depth"))
            .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .build()
        RenderType.create(
            "tessera_filled_no_depth",
            RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .createRenderSetup(),
        )
    }

    private fun filledType(): RenderType = if (depth) RenderTypes.debugFilledBox() else filledThroughWalls

    // ------------------------------------------------------------------------------------------------
    // lines
    // ------------------------------------------------------------------------------------------------

    @JvmStatic
    fun drawLine(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Int, lineWidth: Double,
    ) {
        val col = collector ?: return
        val ps = pose ?: return
        val w = lineWidth.toFloat()
        col.submitCustomGeometry(ps, edgeType()) { p, vc ->
            edge(vc, p, x1, y1, z1, x2, y2, z2, color, w)
        }
    }

    // one line segment: two vertices sharing the segment direction as their normal (the line shader
    // expands the quad along it) and the requested pixel width.
    private fun edge(
        vc: VertexConsumer, p: PoseStack.Pose,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        color: Int, w: Float,
    ) {
        var nx = x2 - x1
        var ny = y2 - y1
        var nz = z2 - z1
        val len = Math.sqrt(nx * nx + ny * ny + nz * nz)
        if (len < 1e-9) return
        nx /= len; ny /= len; nz /= len
        vc.addVertex(p, x1.toFloat(), y1.toFloat(), z1.toFloat()).setColor(color).setLineWidth(w)
            .setNormal(p, nx.toFloat(), ny.toFloat(), nz.toFloat())
        vc.addVertex(p, x2.toFloat(), y2.toFloat(), z2.toFloat()).setColor(color).setLineWidth(w)
            .setNormal(p, nx.toFloat(), ny.toFloat(), nz.toFloat())
    }

    // ------------------------------------------------------------------------------------------------
    // boxes
    // ------------------------------------------------------------------------------------------------

    @JvmStatic
    fun drawBox(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Int, lineWidth: Double,
    ) {
        val col = collector ?: return
        val ps = pose ?: return
        val shape = Shapes.create(AABB(minX, minY, minZ, maxX, maxY, maxZ))
        // isTranslucent = false: opaque outline ordering (see submitBlockOutline in LevelRenderer)
        col.submitShapeOutline(ps, shape, edgeType(), color, lineWidth.toFloat(), false)
    }

    // box centred on (cx,cy,cz) with the given full extents
    @JvmStatic
    fun drawBoxAt(
        cx: Double, cy: Double, cz: Double,
        sizeX: Double, sizeY: Double, sizeZ: Double,
        color: Int, lineWidth: Double,
    ) {
        val hx = sizeX / 2; val hy = sizeY / 2; val hz = sizeZ / 2
        drawBox(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, color, lineWidth)
    }

    @JvmStatic
    fun drawFilledBox(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        color: Int,
    ) {
        val col = collector ?: return
        val ps = pose ?: return
        col.submitCustomGeometry(ps, filledType()) { p, vc ->
            fun v(x: Double, y: Double, z: Double) =
                vc.addVertex(p, x.toFloat(), y.toFloat(), z.toFloat()).setColor(color)
            // 6 faces, 4 verts each
            v(minX, minY, minZ); v(maxX, minY, minZ); v(maxX, minY, maxZ); v(minX, minY, maxZ) // bottom
            v(minX, maxY, minZ); v(minX, maxY, maxZ); v(maxX, maxY, maxZ); v(maxX, maxY, minZ) // top
            v(minX, minY, minZ); v(minX, maxY, minZ); v(maxX, maxY, minZ); v(maxX, minY, minZ) // north
            v(minX, minY, maxZ); v(maxX, minY, maxZ); v(maxX, maxY, maxZ); v(minX, maxY, maxZ) // south
            v(minX, minY, minZ); v(minX, minY, maxZ); v(minX, maxY, maxZ); v(minX, maxY, minZ) // west
            v(maxX, minY, minZ); v(maxX, maxY, minZ); v(maxX, maxY, maxZ); v(maxX, minY, maxZ) // east
        }
    }

    // ------------------------------------------------------------------------------------------------
    // entities
    // ------------------------------------------------------------------------------------------------

    // outline an entity's (interpolated) hitbox. Your exact ask: renderEntityBox(entity, r, g, b, width).
    @JvmStatic
    fun renderEntityBox(entity: EntityWrapper, r: Int, g: Int, b: Int, lineWidth: Double) =
        renderEntityBox(entity, color(r, g, b), lineWidth)

    @JvmStatic
    fun renderEntityBox(entity: EntityWrapper, color: Int, lineWidth: Double) {
        if (collector == null) return // no-op outside RENDER_WORLD, before touching the client
        val box = interpolatedBox(entity)
        drawBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, lineWidth)
    }

    // line from the camera to the entity's centre
    @JvmStatic
    fun renderEntityTracer(entity: EntityWrapper, color: Int, lineWidth: Double) {
        val cam = camera ?: return
        val box = interpolatedBox(entity)
        drawLine(
            cam.pos.x, cam.pos.y, cam.pos.z,
            (box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2,
            color, lineWidth,
        )
    }

    // line from the camera to an arbitrary world point (emanates from screen centre)
    @JvmStatic
    fun drawTracer(x: Double, y: Double, z: Double, color: Int, lineWidth: Double) {
        val cam = camera ?: return
        drawLine(cam.pos.x, cam.pos.y, cam.pos.z, x, y, z, color, lineWidth)
    }

    // the entity hitbox shifted to its smooth render position for this partial tick
    private fun interpolatedBox(entity: EntityWrapper): AABB {
        val e = entity.handle
        val pt = Mc.client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val cur = e.position()
        val ip = e.getPosition(pt)
        return e.boundingBox.move(ip.x - cur.x, ip.y - cur.y, ip.z - cur.z)
    }

    // ------------------------------------------------------------------------------------------------
    // text
    // ------------------------------------------------------------------------------------------------

    // vanilla NAMETAG_SCALE — the base glyph size at scale 1.0, matching a normal name tag
    private const val NAMETAG_BASE = 0.025f

    @JvmStatic
    fun drawText3D(text: String, x: Double, y: Double, z: Double, color: Int) =
        drawText3D(text, x, y, z, color, 1.0, true)

    // scale-only overload (the common one): text at (x,y,z), camera-billboarded, `scale`× glyph size.
    @JvmStatic
    fun drawText3D(text: String, x: Double, y: Double, z: Double, color: Int, scale: Double) =
        drawText3D(text, x, y, z, color, scale, true)

    // Billboarded world text, genuinely scalable. We build the camera-facing pose ourselves (translate →
    // face camera → scale) and submit through submitText, which copies the FULL pose matrix — so `scale`
    // actually reaches the glyphs. (The old name-tag path ignored pose scale: submitNameTag rebuilds its
    // own fixed NAMETAG_SCALE billboard from the camera, so any pose scale was silently dropped.)
    // `shadow` is accepted for API symmetry (kept true). Colour is ARGB — build it with color(r,g,b[,a]);
    // §-codes in the string still override per-glyph. depth(false) draws through terrain.
    @JvmStatic
    fun drawText3D(text: String, x: Double, y: Double, z: Double, color: Int, scale: Double, shadow: Boolean) {
        val col = collector ?: return
        val ps = pose ?: return
        val cam = camera ?: return
        val font = Mc.client.font

        ps.pushPose()
        ps.translate(x, y, z)
        ps.mulPose(cam.orientation)                 // face the camera
        // per-call scale × the persistent setTextScale multiplier — so setTextScale() scales text even
        // if a host-interop overload picked the no-scale form and dropped the explicit arg.
        val s = NAMETAG_BASE * (scale * defaultScale).toFloat()
        // match vanilla name tags exactly: +X, -Y, +Z. A negative X mirrors the text AND flips the
        // quad winding, which backface-culls the glyphs (invisible) — only Y is negated.
        ps.scale(s, -s, s)

        val fcs = Component.literal(text).getVisualOrderText()
        val width = font.width(text)
        val mode = if (depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH
        // centre on the point (x=-width/2, y=-lineHeight/2); bg 0 = no plate, outline 0 = none
        col.submitText(ps, -width / 2f, -font.lineHeight / 2f, fcs, shadow, mode, FULL_BRIGHT, color, 0, 0)
        ps.popPose()
    }

    // set a persistent default scale that every drawText3D call without an explicit scale uses.
    @Volatile private var defaultScale = 1.0

    @JvmStatic fun setTextScale(scale: Double) { defaultScale = if (scale > 0) scale else 1.0 }
    @JvmStatic fun getTextScale(): Double = defaultScale
}
