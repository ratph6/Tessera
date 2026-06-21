package ratph6.tessera.api

/**
 * Safe access to a command callback's argument array. A `command` trigger's value is a `string[]`,
 * but JS-style indexing (`args[0]`, `args.length`) isn't guaranteed by the bytecode compiler — these
 * helpers are plain method calls, which always compile cleanly.
 *
 * ```ts
 * Tessera.register(Event.COMMAND, (args) => {
 *   if (Args.count(args) >= 1) ChatLib.chat("hi " + Args.get(args, 0));
 * }).setName("greet");
 * ```
 */
object Args {
    /** Number of arguments passed to the command. */
    @JvmStatic fun count(args: Any?): Int = (args as? Array<*>)?.size ?: 0

    /** The argument at [index], or "" if out of range. */
    @JvmStatic fun get(args: Any?, index: Int): String {
        val arr = args as? Array<*> ?: return ""
        return if (index in arr.indices) arr[index]?.toString() ?: "" else ""
    }
}
