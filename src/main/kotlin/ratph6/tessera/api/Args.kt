package ratph6.tessera.api

// Safe command-arg access — JS-style args[0]/args.length isn't guaranteed by the bytecode compiler.
object Args {
    @JvmStatic fun count(args: Any?): Int = (args as? Array<*>)?.size ?: 0

    // arg at index, or "" if out of range
    @JvmStatic fun get(args: Any?, index: Int): String {
        val arr = args as? Array<*> ?: return ""
        return if (index in arr.indices) arr[index]?.toString() ?: "" else ""
    }
}
