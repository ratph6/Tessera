import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * Generates TypeScript ambient declarations for the (Mojang-mapped) Minecraft API, so Tessera scripts
 * get IntelliSense/completion for `import { BlockPos } from 'net.minecraft.core'` and friends.
 *
 * Reflects over every public, top-level class under the configured package prefixes found on the
 * compile classpath's Minecraft jar, and emits one `declare module '<package>'` block per package.
 * Members are written self-contained (public methods/fields including inherited ones, via
 * `getMethods`/`getFields`) so no cross-module `extends` wiring is needed. Generics are erased
 * (reflection already yields raw types); references to other emitted MC types use inline
 * `import('<pkg>').<Name>` so there are no top-level imports to collide. Anything unknown maps to
 * `any`. Nested (`$`) classes are skipped in this version.
 */
abstract class GenMinecraftDtsTask : DefaultTask() {

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    @get:Input
    abstract val packagePrefixes: ListProperty<String>

    /** Simple names NOT to expose as bare globals (they collide with Tessera API globals, e.g. Player). */
    @get:Input
    abstract val excludeGlobals: ListProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    /** Ambient `declare global` aliases letting unique-named MC classes be used with no import. */
    @get:OutputFile
    abstract val globalsOutput: RegularFileProperty

    /** name -> fully-qualified class name, for the runtime to bind referenced classes via Java.type. */
    @get:OutputFile
    abstract val globalsMapOutput: RegularFileProperty

    @TaskAction
    fun generate() {
        val jars = classpath.files.filter { it.isFile && it.name.endsWith(".jar") }
        val loader = URLClassLoader(jars.map { it.toURI().toURL() }.toTypedArray(), ClassLoader.getPlatformClassLoader())
        val prefixes = packagePrefixes.get().map { it.replace('.', '/') + "/" }

        // 1. Collect candidate class names from the jars (top-level only, under the prefixes).
        val classNames = sortedSetOf<String>()
        for (jar in jars) {
            JarFile(jar).use { jf ->
                val e = jf.entries()
                while (e.hasMoreElements()) {
                    val name = e.nextElement().name
                    if (!name.endsWith(".class")) continue
                    if (prefixes.none { name.startsWith(it) }) continue
                    if (name.contains('$')) continue // skip nested/anonymous
                    classNames.add(name.removeSuffix(".class").replace('/', '.'))
                }
            }
        }

        // 2. Load and group public classes by package.
        val byPackage = sortedMapOf<String, MutableList<Class<*>>>()
        for (cn in classNames) {
            val c = try {
                Class.forName(cn, false, loader)
            } catch (t: Throwable) {
                continue
            }
            if (!Modifier.isPublic(c.modifiers)) continue
            if (c.isAnonymousClass || c.isLocalClass || c.isSynthetic) continue
            if (c.simpleName.isNullOrEmpty()) continue
            byPackage.getOrPut(c.packageName) { mutableListOf() }.add(c)
        }

        // FQCNs we will actually declare — only emit inline import() refs to these (else `any`).
        val willEmit: Set<String> = byPackage.values.flatten().map { it.name }.toHashSet()

        // 3. Emit.
        val sb = StringBuilder()
        sb.append("// AUTO-GENERATED Minecraft API declarations for Tessera (Mojang mappings). DO NOT EDIT.\n")
        sb.append("// Regenerate with: ./gradlew genMinecraftDts\n\n")
        var classCount = 0
        for ((pkg, classes) in byPackage) {
            sb.append("declare module '").append(pkg).append("' {\n")
            for (c in classes.sortedBy { it.simpleName }) {
                emitType(sb, c, willEmit)
                classCount++
            }
            sb.append("}\n\n")
        }

        val out = output.get().asFile
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
        logger.lifecycle("genMinecraftDts: wrote $classCount classes in ${byPackage.size} packages -> ${out.absolutePath} (${out.length() / 1024} KB)")

        // 4. Globals: simple names that are UNIQUE across all packages (and don't collide with the Tessera
        // API) can be used with no import. Emit ambient global aliases (editor) + a name->FQCN map (runtime).
        val exclude = excludeGlobals.get().toHashSet()
        val bySimple = HashMap<String, MutableList<Class<*>>>()
        for (c in byPackage.values.flatten()) bySimple.getOrPut(c.simpleName) { mutableListOf() }.add(c)
        val unique = bySimple.filter { it.value.size == 1 && it.key !in exclude }
            .map { it.key to it.value[0] }
            .sortedBy { it.first }

        val g = StringBuilder()
        g.append("// AUTO-GENERATED — unique-named Minecraft classes usable with NO import. DO NOT EDIT.\n")
        g.append("// Regenerate with: ./gradlew genMinecraftDts\n\n")
        g.append("export {};\n\ndeclare global {\n")
        for ((name, c) in unique) {
            val ref = "import('${c.packageName}').$name"
            g.append("  const ").append(name).append(": typeof ").append(ref).append(";\n")
            g.append("  type ").append(name).append(" = ").append(ref).append(";\n")
        }
        g.append("}\n")
        val gOut = globalsOutput.get().asFile
        gOut.parentFile.mkdirs()
        gOut.writeText(g.toString())

        val json = StringBuilder("{\n")
        json.append(unique.joinToString(",\n") { (name, c) -> "  \"$name\": \"${c.name}\"" })
        json.append("\n}\n")
        val mapOut = globalsMapOutput.get().asFile
        mapOut.parentFile.mkdirs()
        mapOut.writeText(json.toString())
        logger.lifecycle("genMinecraftDts: ${unique.size} unique-named classes exposed as no-import globals")
    }

    private fun emitType(sb: StringBuilder, c: Class<*>, willEmit: Set<String>) {
        val kind = if (c.isInterface) "interface" else "class"
        sb.append("  export ").append(kind).append(' ').append(c.simpleName).append(" {\n")

        // Constructors (concrete classes only).
        if (!c.isInterface && !Modifier.isAbstract(c.modifiers)) {
            val seenCtor = HashSet<String>()
            for (ctor in c.constructors) {
                if (!Modifier.isPublic(ctor.modifiers) || ctor.isSynthetic) continue
                if (!seenCtor.add(sig(ctor))) continue
                sb.append("    constructor(").append(params(ctor, willEmit)).append(");\n")
            }
        }

        // Public fields (including inherited; Object has none public).
        val seenField = HashSet<String>()
        for (f in c.fields) {
            if (!Modifier.isPublic(f.modifiers) || f.isSynthetic) continue
            if (!seenField.add(f.name)) continue
            val mods = (if (Modifier.isStatic(f.modifiers)) "static " else "") +
                (if (Modifier.isFinal(f.modifiers)) "readonly " else "")
            sb.append("    ").append(mods).append(member(f.name)).append(": ").append(tsType(f.type, willEmit)).append(";\n")
        }

        // Public methods (including inherited), minus Object's, synthetic and bridge; de-duped by erased signature.
        val seenMethod = HashSet<String>()
        for (m in c.methods) {
            if (m.declaringClass == Any::class.java) continue
            if (!Modifier.isPublic(m.modifiers) || m.isSynthetic || m.isBridge) continue
            if (!seenMethod.add(sig(m))) continue
            val stat = if (Modifier.isStatic(m.modifiers)) "static " else ""
            sb.append("    ").append(stat).append(member(m.name)).append('(')
                .append(params(m, willEmit)).append("): ").append(tsType(m.returnType, willEmit)).append(";\n")
        }

        sb.append("  }\n")
    }

    private fun sig(e: Executable): String =
        e.name + "(" + e.parameterTypes.joinToString(",") { it.name } + ")"

    private fun params(e: Executable, willEmit: Set<String>): String {
        val types = e.parameterTypes
        return types.indices.joinToString(", ") { i ->
            if (e.isVarArgs && i == types.size - 1) {
                "...p$i: " + tsType(types[i].componentType, willEmit) + "[]"
            } else {
                "p$i: " + tsType(types[i], willEmit)
            }
        }
    }

    /** A member name that's safe to emit unquoted, else a quoted string member. */
    private fun member(name: String): String =
        if (name.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) name else "\"$name\""

    private fun tsType(t: Class<*>, willEmit: Set<String>): String = when {
        t == Void.TYPE -> "void"
        t == java.lang.Boolean.TYPE -> "boolean"
        t == Character.TYPE -> "string"
        t == java.lang.Byte.TYPE || t == java.lang.Short.TYPE || t == Integer.TYPE ||
            t == java.lang.Long.TYPE || t == java.lang.Float.TYPE || t == java.lang.Double.TYPE -> "number"
        t.isArray -> tsType(t.componentType, willEmit) + "[]"
        t == String::class.java || t == CharSequence::class.java -> "string"
        t == Any::class.java -> "any"
        t.name in willEmit -> "import('${t.packageName}').${t.simpleName}"
        else -> "any"
    }
}
