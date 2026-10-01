package com.armorlab.securedroid

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资源引用不变量测试。
 *
 * 背景:本项目最初就是因 menu 引用了一个不存在的 @drawable/ic_tool 而无法构建(AAPT 失败),
 * 这类"引用了不存在资源"的问题编译期报错信息晦涩、定位成本高。本测试直接在源码层校验:
 * 任何布局 / 菜单 / 清单里出现的 @string、@drawable、@color、@xml、@mipmap 引用都必须有定义;
 * 任何 @id 引用都必须有对应的 @+id 声明。
 */
class ResourceReferenceTest {

    private val resDir: File by lazy { locateResDir() }

    private fun locateResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res")
        )
        candidates.firstOrNull { it.isDirectory }?.let { return it }
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res")
            if (f.isDirectory) return f
            dir = dir.parentFile
        }
        throw AssertionError("未能定位 res 目录")
    }

    private fun xmlFiles(): List<File> =
        resDir.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()

    private fun valueFiles(): List<File> =
        File(resDir, "values").listFiles { f -> f.isFile && f.extension == "xml" }?.toList() ?: emptyList()

    /** 收集 values 中定义的资源名 */
    private fun defined(prefix: String): Set<String> {
        val re = Regex("<$prefix\\s+name=\"([^\"]+)\"")
        return valueFiles().flatMap { f -> re.findAll(f.readText()).map { it.groupValues[1] } }.toSet()
    }

    /** 收集 res 目录下某种类型的文件名(去扩展名) */
    private fun fileNames(folderPrefix: String): Set<String> =
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith(folderPrefix) }
            ?.flatMap { dir -> dir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList() }
            ?.toSet() ?: emptySet()

    private fun references(pattern: String): Map<String, List<String>> {
        val re = Regex(pattern)
        val out = mutableMapOf<String, List<String>>()
        for (f in xmlFiles()) {
            val hits = re.findAll(f.readText()).map { it.groupValues[1] }.toList()
            if (hits.isNotEmpty()) out[f.name] = hits
        }
        return out
    }

    private fun assertResolvable(type: String, available: Set<String>) {
        val missing = mutableMapOf<String, MutableList<String>>()
        for ((file, refs) in references("@$type/([A-Za-z0-9_.]+)")) {
            for (ref in refs) {
                if (ref !in available) missing.getOrPut(ref) { mutableListOf() }.add(file)
            }
        }
        assertTrue(
            "存在无法解析的 @$type 引用(会导致 AAPT 构建失败): " +
                missing.entries.joinToString("; ") { it.key + " <- " + it.value.distinct().joinToString(",") },
            missing.isEmpty()
        )
    }

    @Test
    fun allStringReferencesResolve() {
        assertResolvable("string", defined("string"))
    }

    @Test
    fun allColorReferencesResolve() {
        // 颜色既可在 values/*.xml 定义,也可在 res/color/ 下用 selector 定义
        assertResolvable("color", defined("color") + fileNames("color"))
    }

    @Test
    fun allDrawableReferencesResolve() {
        assertResolvable("drawable", fileNames("drawable"))
    }

    @Test
    fun allMipmapReferencesResolve() {
        assertResolvable("mipmap", fileNames("mipmap"))
    }

    @Test
    fun allXmlResourceReferencesResolve() {
        assertResolvable("xml", fileNames("xml"))
    }

    @Test
    fun allIdReferencesHaveDeclarations() {
        val declared = references("@\\+id/([A-Za-z0-9_]+)").values.flatten().toSet()
        val missing = references("@id/([A-Za-z0-9_]+)")
            .filterKeys { true }
            .flatMap { (file, refs) -> refs.filter { it !in declared }.map { it to file } }
        assertTrue(
            "存在没有 @+id 声明的 @id 引用: " +
                missing.joinToString("; ") { it.first + " <- " + it.second },
            missing.isEmpty()
        )
    }

    @Test
    fun everyLayoutUsesViewBindingCompatibleIds() {
        // 布局中重复的 android:id 会导致 ViewBinding 生成失败或行为不确定
        val dupes = mutableListOf<String>()
        for (f in xmlFiles().filter { it.parentFile.name.startsWith("layout") }) {
            val ids = Regex("android:id=\"@\\+id/([A-Za-z0-9_]+)\"")
                .findAll(f.readText()).map { it.groupValues[1] }.toList()
            val dup = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            if (dup.isNotEmpty()) dupes.add(f.name + ": " + dup.joinToString(","))
        }
        assertTrue("布局内存在重复 id: " + dupes.joinToString("; "), dupes.isEmpty())
    }
}
