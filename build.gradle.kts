import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.0"
	// Generates the hostable HTML API docs (./gradlew dokkaGenerate -> build/dokka/html).
	id("org.jetbrains.dokka") version "2.2.0"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

repositories {
	// swc4j + Javet (TypeScript transpiler + V8 runtime) live on Maven Central.
	mavenCentral()
}

// --- Tessera scripting engine coordinates --------------------------------------
// swc4j compiles TypeScript/JS straight to JVM bytecode (via its ByteCodeCompiler), so user
// scripts run as native JVM classes — no V8, no per-call JNI overhead. swc4j ships a small
// pure-Java API jar plus one Rust native per platform (used only at compile/parse time). We bundle
// every desktop native so the produced mod jar is cross-platform.
val swc4jVersion = "2.1.0"
val nativePlatforms = listOf("macos-arm64", "macos-x86_64", "linux-x86_64", "windows-x86_64")

// GraalJS (second execution engine — real ECMAScript for modules that want normal JS over the
// bytecode path). MUST stay on the 25.0.x LTS line: only there does the optimizing Truffle compiler
// (jargraal) run on a plain (non-GraalVM) JVM like Minecraft's. 25.1+ would drop to interpreter-only
// on stock JDKs. js-community is the GraalVM CE build; it transitively pulls the language + Truffle
// runtime + compiler. Pure-JVM jars (no platform natives), so the whole set is JiJ'd cross-platform.
val graalVersion = "25.0.3"

// GraalJS-CE, as concrete artifacts. (Using the `js-community` pom aggregator instead breaks Loom on
// JDK 25 — see the dependencies block.) Transitively pulls the GraalVM SDK modules (collections,
// nativeimage, word, jniutils) and shadowed deps.
val graalArtifacts = listOf(
	"org.graalvm.polyglot:polyglot",
	"org.graalvm.js:js-language",
	"org.graalvm.regex:regex",
	"org.graalvm.truffle:truffle-api",
	"org.graalvm.truffle:truffle-runtime",
	"org.graalvm.truffle:truffle-compiler",
	"org.graalvm.shadowed:icu4j",
)

// Holds the Graal runtime so we can JiJ every resolved jar into the release jar (see afterEvaluate).
val graalBundle: Configuration by configurations.creating { isTransitive = true }

// The platform this build is running on — only this native goes on the local run/test classpath;
// every platform is still bundled into the release jar via include() below.
val currentPlatform: String = run {
	val os = System.getProperty("os.name").lowercase()
	val arch = System.getProperty("os.arch").lowercase()
	val osPart = when {
		os.contains("mac") || os.contains("darwin") -> "macos"
		os.contains("win") -> "windows"
		else -> "linux"
	}
	val archPart = if (arch.contains("aarch64") || arch.contains("arm64")) "arm64" else "x86_64"
	"$osPart-$archPart"
}

dependencies {
	// To change the versions see the gradle.properties file
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")

	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")

	// Fabric API. This is technically optional, but you probably want it anyway.
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")

	// --- scripting engine ---------------------------------------------------
	// swc4j API jar: on the compile + dev-runtime classpath, and nested into the remapped jar
	// (Fabric JiJ) so end users don't need to install anything.
	implementation("com.caoccao.javet:swc4j:$swc4jVersion")
	include("com.caoccao.javet:swc4j:$swc4jVersion")

	// swc4j Rust natives: one per platform. include() bundles all of them into the release jar;
	// runtimeOnly puts only the current platform's native on the local run/test classpath.
	nativePlatforms.forEach { platform ->
		include("com.caoccao.javet:swc4j-$platform:$swc4jVersion")
		if (platform == currentPlatform) {
			runtimeOnly("com.caoccao.javet:swc4j-$platform:$swc4jVersion")
		}
	}

	// GraalJS: on the compile + dev/test classpath. js-community is a pom aggregator that pulls the
	// js language impl, Truffle API/runtime/compiler and the GraalVM SDK transitively.
	// Declared as concrete artifacts, NOT via the `org.graalvm.polyglot:js-community` pom aggregator:
	// that pom's resolved metadata makes Fabric Loom (tinyremapper) throw a spurious
	// ProviderNotFoundException("jar") while reading dependency jars during Minecraft setup on JDK 25.
	// This explicit set is the full GraalJS-CE runtime (transitively pulls the GraalVM SDK modules).
	graalArtifacts.forEach { implementation("$it:$graalVersion"); graalBundle("$it:$graalVersion") }

	// ASM, for the runtime bytecode injection behind TypeScript mixins (Mixin.inject). compileOnly:
	// Fabric Loader already ships ASM on the runtime classpath (Mixin depends on it), so we compile
	// against it but neither bundle nor remap our own copy.
	compileOnly("org.ow2.asm:asm:9.7.1")
	compileOnly("org.ow2.asm:asm-tree:9.7.1")

	// Headless engine smoke test (TS -> JVM bytecode via swc4j, no Minecraft).
	testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// ASM is only on the runtime classpath in-game (via Fabric Loader); the mixin transformer test
	// runs outside Minecraft, so it needs ASM explicitly.
	testImplementation("org.ow2.asm:asm:9.7.1")
	testImplementation("org.ow2.asm:asm-tree:9.7.1")
}

// JiJ every resolved Graal jar into the remapped release jar so end users need nothing installed.
// graalBundle resolves the full transitive set; pom-only modules have no artifact file and are
// skipped automatically. Done in afterEvaluate so the configuration is fully resolved.
afterEvaluate {
	graalBundle.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
		val id = artifact.moduleVersion.id
		dependencies.add("include", "${id.group}:${id.name}:${id.version}")
	}
}

tasks.test {
	useJUnitPlatform()
}

// swc4j loads a Rust native via System.load; on Java 25 that needs native access granted.
loom {
	runs {
		configureEach {
			vmArgs("--enable-native-access=ALL-UNNAMED")
			// GraalJS/Truffle on a stock JVM. Silence the interpreter-only notice; if the optimizing
			// jargraal compiler fails to attach on this JDK, scripts still run correctly (just slower).
			vmArgs("-Dpolyglot.engine.WarnInterpreterOnly=false")
				// TypeScript mixins self-attach a java.lang.instrument agent to retransform already-loaded
				// Minecraft classes; JDK 25 forbids self-attach unless this is set. (Prod users must add it.)
				vmArgs("-Djdk.attach.allowAttachSelf=true")
			// Let the /te console un-headless AWT after MC launches the JVM headless (TesseraConsole
			// nulls GraphicsEnvironment.headless via reflection — needs java.awt opened on JDK 25+).
			vmArgs("--add-opens", "java.desktop/java.awt=ALL-UNNAMED")
		}
	}
}

// Generates src/main/resources/tessera/types/minecraft.d.ts from the mapped Minecraft jar, for editor
// IntelliSense on `import { ... } from 'net.minecraft.*'`. Run manually: ./gradlew genMinecraftDts
tasks.register<GenMinecraftDtsTask>("genMinecraftDts") {
	classpath.from(sourceSets.main.get().compileClasspath)
	packagePrefixes.set(listOf("net.minecraft"))
	// Don't expose MC classes whose simple name collides with a Tessera API global (those stay import-only).
	excludeGlobals.set(listOf("Player", "World", "Server", "Display", "Event", "Renderer"))
	// d.ts files are editor-only (bundled in the VS Code extension, not the mod jar).
	output.set(layout.projectDirectory.file("vscode-extension/types/minecraft.d.ts"))
	globalsOutput.set(layout.projectDirectory.file("vscode-extension/types/minecraft-globals.d.ts"))
	// The name->FQCN map IS needed at runtime (GraalRuntime binds referenced classes via Java.type).
	globalsMapOutput.set(layout.projectDirectory.file("src/main/resources/tessera/minecraft-globals.json"))
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_25
	}
}

java {
	// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
	// if it is present.
	// If you remove this line, sources will not be generated.
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_$projectName" }
	}
}

// configure the maven publication
publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	// See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
	repositories {
		// Add repositories to publish to here.
		// Notice: This block does NOT have the same function as the block in the top level.
		// The repositories here will be used for publishing your artifact, not for
		// retrieving dependencies.
	}
}

tasks.test { testLogging { showStandardStreams = true } }
