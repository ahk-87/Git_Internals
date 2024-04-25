package gitinternals

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CommitInfo(val data: String, val hash: String = "", val merged: Boolean = false) {
    val parents: List<String>
    val tree: String
    private val author: Person
    private val committer: Person
    private val message: String

    init {
        // the info and the commit message are separated by double line feed '\n' characters
        val parts = data.split("\n\n")
        // the 1st part contains the commit data (tree, parent, author and committer) separated by '\n'
        val map = parts[0].split("\n").groupBy({ it.substringBefore(' ') }, { it.substringAfter(' ') })
        tree = map["tree"]!![0]
        parents = map["parent"] ?: emptyList()
        author = Person.getPerson(map["author"]!![0], "original")
        committer = Person.getPerson(map["committer"]!![0], "commit")
        // the 2nd part contains the commit message
        message = parts[1]
    }

    fun print(): String {
        return buildString {
            appendLine("tree: $tree")
            if (parents.isNotEmpty())
                appendLine("parents: ${parents.joinToString(" | ")}")
            appendLine("author: $author")
            appendLine("committer: $committer")
            appendLine("commit message:")
            append(message)
        }
    }

    override fun toString(): String {
        return buildString {
            appendLine("Commit: $hash${if (merged) " (merged)" else ""}")
            appendLine(committer)
            append(message)
        }
    }
}

data class Person(val name: String, val email: String, val type: String, val time: Instant, val zone: String) {
    companion object {
        fun getPerson(data: String, type: String): Person {
            //data is in the following format: "Smith <mr.smith@matrix> 1585491500 +0300"
            val (n, e, i, z) = data.split(" ")
            return Person(n, e.removeSurrounding("<", ">"), type, Instant.ofEpochSecond(i.toLong()), z)
        }
    }

    override fun toString(): String {
        val df = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")
        val date = time.atZone(ZoneId.of(zone))

        return "$name $email $type timestamp: ${df.format(date)}"
    }
}

data class TreeInfo(val permission: String, val filename: String, val hash: String) {
    companion object {
        fun getTrees(data: ByteArray): List<TreeInfo> {
            var d = data.copyOf()
            val trees = mutableListOf<TreeInfo>()
            while (d.isNotEmpty()) {
                val nullIndex = d.indexOf(0)
                val permissionAndFilename = String(d, 0, nullIndex)
                d = d.sliceArray(nullIndex + 1..d.lastIndex)
                val hash = d.take(20)
                d = d.drop(20).toByteArray()
                val (perm, fName) = permissionAndFilename.split(" ")
                trees.add(TreeInfo(perm, fName, binaryToHex(hash)))
            }
            return trees
        }
    }

    override fun toString() = "$permission $hash $filename"
}

fun binaryToHex(input: List<Byte>) = input.joinToString("") { "%02x".format(it) }

fun showBranches(dir: String) {
    val head = File("$dir\\HEAD").readLines()[0].substringAfterLast("/")
    val branches = File("$dir\\refs\\heads").listFiles()?.sortedBy { it.name }?.map {
        "${if (it.name == head) "*" else " "} ${it.name}"
    } ?: emptyList()
    branches.forEach(::println)
}

fun showLogs(dir: String) {
    println("Enter branch name:")
    val commitName = readln()
    val commitHash = File("$dir\\refs\\heads\\$commitName").readLines()[0]

    val logs = mutableListOf<CommitInfo>()
    traverseCommits(dir, commitHash, logs)

    print(logs.joinToString("\n"))
}

fun traverseCommits(dir: String, hash: String, logs: MutableList<CommitInfo>) {
    val internals = getInternals(dir, hash)
    val commitInfo = CommitInfo(String(internals.body), hash)
    logs.add(commitInfo)
    if (commitInfo.parents.isEmpty()) {
        return
    }
    if (commitInfo.parents.size == 2) {
        val mergedHash = commitInfo.parents[1]
        val internals2 = getInternals(dir, mergedHash)
        logs.add(CommitInfo(String(internals2.body), mergedHash, true))
    }
    traverseCommits(dir, commitInfo.parents[0], logs)
    return
}

fun showTree(dir: String) {
    println("Enter commit-hash:")
    val commitHash = readln()
    val commitInternals = getInternals(dir, commitHash)
    val commitInfo = CommitInfo(String(commitInternals.body), commitHash)
    traverseTrees(dir, commitInfo.tree)
}

fun traverseTrees(dir: String, hash: String, prevTree: String = "") {
    val treeInternals = getInternals(dir, hash)
    val trees = TreeInfo.getTrees(treeInternals.body)
    trees.forEach { tree ->
        val fileInternals = getInternals(dir, tree.hash)
        if (fileInternals.type == InternalsType.TREE) {
            val sep = if(prevTree.isEmpty()) "" else "/"
            traverseTrees(dir, tree.hash, "$prevTree$sep${tree.filename}/")
        } else {
            println("$prevTree${tree.filename}")
        }
    }
}

fun main() {
    println("Enter .git directory location:")
    val gitDir = readln()
    println("Enter command:")
    val command = readln()
    when (command) {
        "cat-file" -> showInternals(gitDir)
        "list-branches" -> showBranches(gitDir)
        "log" -> showLogs(gitDir)
        "commit-tree" -> showTree(gitDir)
    }
}
