package ratph6.tessera.api

import ratph6.tessera.engine.AccessRegistry
import ratph6.tessera.engine.MixinManager
import ratph6.tessera.engine.ReflectAccess

/**
 * Open up private / protected / final Minecraft members at runtime — the scripting equivalent of a
 * Fabric access widener, but applied live from a Tessera module. A widened member becomes `public` (and
 * loses `final`), so scripts can then read/write/call it through the normal API (GraalJS host access or
 * the bytecode engine).
 *
 * ```ts
 * import { AccessWidener } from 'ratph6.tessera.api';
 *
 * AccessWidener.widenField('net.minecraft.client.Minecraft', 'gameMode');   // read a private field
 * AccessWidener.widenMethod('net.minecraft.client.Minecraft', 'setScreen'); // call a private method
 * AccessWidener.makeExtendable('net.minecraft.world.entity.Mob');           // drop final on a class
 * ```
 *
 * **Important — timing.** The JVM only permits access changes while a class is being *defined*. So a
 * widening takes effect only on a class that is **not yet loaded** when you register it. Targeting a
 * class that is already loaded (e.g. `Minecraft`, which loads at startup) logs a note and does nothing
 * this session — register such widenings as early as possible (top of your module), or restart. Use
 * `/te errors` to see if a widening came too late.
 *
 * Like [Mixin] this needs runtime instrumentation; widenings are reverted on `/te reload` / module
 * unload (they reapply on the next class load).
 *
 * ### Already-loaded classes
 *
 * Bytecode widening (`widenField`/`widenMethod`/`makeExtendable`) only works on classes **not yet
 * loaded**. For classes that are already loaded (e.g. `Minecraft`, `KeyMapping`, `Options` — all created
 * at startup), use the reflection accessors below instead: [getField]/[setField]/[invoke] and their
 * `static` variants reach private members on **any** object, loaded or not, with no restart.
 *
 * ```ts
 * // bump a private int counter on the already-loaded KeyMapping — no restart, no widenField:
 * const keyUse = Minecraft.getInstance().options.keyUse;
 * AccessWidener.setField(keyUse, 'clickCount', AccessWidener.getField(keyUse, 'clickCount') + 1);
 * ```
 */
object AccessWidener {

    // --- bytecode widening (transparent member access, but only for not-yet-loaded classes) -------

    /** Make `field` on `target` public and non-final (readable and writable from scripts). */
    @JvmStatic
    fun widenField(target: String, field: String) =
        MixinManager.widen(target, AccessRegistry.Kind.FIELD, field, null)

    /** Make every overload of `method` on `target` public and non-final. */
    @JvmStatic
    fun widenMethod(target: String, method: String) =
        MixinManager.widen(target, AccessRegistry.Kind.METHOD, method, null)

    /** Make the single overload of `method` whose JVM `descriptor` matches public and non-final. */
    @JvmStatic
    fun widenMethod(target: String, method: String, descriptor: String) =
        MixinManager.widen(target, AccessRegistry.Kind.METHOD, method, descriptor)

    /** Make `target` itself public and non-final, so it can be subclassed (e.g. by a Mixin). */
    @JvmStatic
    fun makeExtendable(target: String) =
        MixinManager.widen(target, AccessRegistry.Kind.CLASS, null, null)

    // --- reflection accessors (work on already-loaded classes, no restart) ------------------------

    /** Read a (private) instance field by name — walks the class hierarchy. */
    @JvmStatic
    fun getField(target: Any, field: String): Any? = ReflectAccess.getField(target, field)

    /** Write a (private) instance field by name; numeric values are coerced to the field's type. */
    @JvmStatic
    fun setField(target: Any, field: String, value: Any?) = ReflectAccess.setField(target, field, value)

    /** Read a (private) static field, e.g. `getStaticField('net.minecraft.client.Minecraft', 'instance')`. */
    @JvmStatic
    fun getStaticField(className: String, field: String): Any? = ReflectAccess.getStaticField(className, field)

    /** Write a (private) static field; numeric values are coerced to the field's type. */
    @JvmStatic
    fun setStaticField(className: String, field: String, value: Any?) =
        ReflectAccess.setStaticField(className, field, value)

    /** Call a (private) instance method by name + arg count; numeric args are coerced to the param types. */
    @JvmStatic
    fun invoke(target: Any, method: String, vararg args: Any?): Any? = ReflectAccess.invoke(target, method, args)

    /** Call a (private) static method by name + arg count. */
    @JvmStatic
    fun invokeStatic(className: String, method: String, vararg args: Any?): Any? =
        ReflectAccess.invokeStatic(className, method, args)
}
