package pw.cmdev.dimucathedev.ridersharpicons

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.LayeredIcon
import javax.swing.Icon

class CsFileIconPatcher : FileIconPatcher {
    private val cacheKey = Key.create<Pair<Long, Icon?>>("cs.type.icon")

    private val noiseRegex = Regex(
        """//[^\n]*|/\*.*?\*/|^[ \t]*#[^\n]*|@"(?:[^"]|"")*"|\$?"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])'""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
    )
    private val typeRegex = Regex(
        """\b((?:(?:public|internal|private|protected|abstract|static|sealed|partial|unsafe|file|readonly|ref|new)\s+)*)(class|interface|enum|struct|record(?:\s+(?:class|struct))?|delegate)\s+(\w+)"""
    )
    private val exceptionRegex = Regex("""\b\w*Exception\b""")
    private val attributeRegex = Regex("""\b\w*Attribute\b""")
    private val controllerRegex = Regex("""\bController(?:Base)?\b""")
    private val modelNameRegex = Regex(""".*(Dto|Entity|Model)$""")
    private val testRegex = Regex("""\[\s*(?:TestFixture|TestClass|Fact|Theory|Test|TestMethod|TestCase)\b""")
    private val mainRegex = Regex("""\bstatic\s+(?:async\s+)?(?:void|int|Task(?:<int>)?)\s+Main\s*\(""")
    private val globalUsingRegex = Regex("""^\s*global\s+using\b""", RegexOption.MULTILINE)

    override fun patchIcon(baseIcon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
        if (file.extension != "cs") return baseIcon
        file.getUserData(cacheKey)?.let { (stamp, icon) ->
            if (stamp == file.modificationStamp) return icon ?: baseIcon
        }
        val icon = try { detect(file) } catch (e: Exception) { null }
        file.putUserData(cacheKey, file.modificationStamp to icon)
        return icon ?: baseIcon
    }

    private fun detect(file: VirtualFile): Icon? {
        val bytes = file.inputStream.use { it.readNBytes(16384) }
        val text = String(bytes, Charsets.UTF_8).replace(noiseRegex, " ")
        val types = topLevelTypes(text)
        val isTest = testRegex.containsMatchIn(text)
        val hasMain = mainRegex.containsMatchIn(text)

        if (types.isEmpty()) return when {
            hasMain -> AllIcons.Nodes.EntryPoints
            globalUsingRegex.containsMatchIn(text) -> AllIcons.Nodes.Include
            else -> null
        }
        if (types.size > 1) return AllIcons.Nodes.MultipleTypeDefinitions

        val m = types[0]
        val mods = m.groupValues[1]
        val kind = m.groupValues[2]
        val name = m.groupValues[3]
        val header = text.substring(m.range.last + 1)
            .substringBefore('{').substringBefore(';').take(400).substringBefore(" where ")
        val bases = header.substringAfter(':', "")
        val isAbstract = "abstract" in mods

        val base: Icon = when {
            kind == "interface" -> AllIcons.Nodes.Interface
            kind == "enum" -> AllIcons.Nodes.Enum
            kind.startsWith("record") -> AllIcons.Nodes.Record
            kind == "struct" -> AllIcons.Nodes.Type
            kind == "delegate" -> AllIcons.Nodes.Lambda
            name.endsWith("Exception") || exceptionRegex.containsMatchIn(bases) ->
                if (isAbstract) AllIcons.Nodes.AbstractException else AllIcons.Nodes.ExceptionClass
            name.endsWith("Attribute") || attributeRegex.containsMatchIn(bases) -> AllIcons.Nodes.Annotationtype
            name.endsWith("Controller") || controllerRegex.containsMatchIn(bases) -> AllIcons.Nodes.Controller
            isAbstract -> AllIcons.Nodes.AbstractClass
            modelNameRegex.matches(name) -> AllIcons.Nodes.ModelClass
            name.endsWith("Service") -> AllIcons.Nodes.Services
            else -> AllIcons.Nodes.Class
        }

        val marks = buildList {
            if (kind == "class" && "static" in mods) add(AllIcons.Nodes.StaticMark)
            if ("sealed" in mods) add(AllIcons.Nodes.FinalMark)
            if (isTest) add(AllIcons.Nodes.JunitTestMark)
            if (hasMain) add(AllIcons.Nodes.RunnableMark)
        }
        return withMarks(base, marks)
    }

    private fun withMarks(base: Icon, marks: List<Icon>): Icon {
        if (marks.isEmpty()) return base
        val layered = LayeredIcon(1 + marks.size)
        layered.setIcon(base, 0)
        marks.forEachIndexed { i, mark -> layered.setIcon(mark, i + 1) }
        return layered
    }

    private fun topLevelTypes(text: String): List<MatchResult> {
        var depth = 0
        var pos = 0
        var baseDepth = -1
        val result = mutableListOf<MatchResult>()
        for (m in typeRegex.findAll(text)) {
            val start = m.range.first
            if (!startsDeclaration(text, start)) continue
            while (pos < start) {
                when (text[pos]) { '{' -> depth++; '}' -> depth-- }
                pos++
            }
            if (baseDepth < 0) baseDepth = depth
            if (depth == baseDepth) result += m
        }
        return result
    }

    private fun startsDeclaration(text: String, start: Int): Boolean {
        var i = start - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        return i < 0 || text[i] in ";{}]"
    }
}