package gitinternals

import java.io.FileInputStream
import java.util.zip.InflaterInputStream

enum class InternalsType { COMMIT, TREE, BLOB }
class FileInternal(val type: InternalsType, val size: Int, val body: ByteArray)

fun getInternals(dir: String, hash: String): FileInternal {
    val fileInputStream = FileInputStream("$dir\\objects\\${hash.take(2)}\\${hash.substring(2)}")
    val inflaterInputStream = InflaterInputStream(fileInputStream)

    val byteArray = inflaterInputStream.readBytes()

    inflaterInputStream.close()
    fileInputStream.close()
    val nullIndex = byteArray.indexOf(0)
    // the 1st line will contain the data type and size, separated by space
    // type maybe be: BLOB, COMMIT or TREE
    val line1 = String(byteArray, 0, nullIndex)
    val (type, size) = line1.split(" ")
    // the 2nd line will contain the data
    val line2 = byteArray.sliceArray((nullIndex + 1)..byteArray.lastIndex)

    return FileInternal(InternalsType.valueOf(type.uppercase()), size.toInt(), line2)
}

fun showInternals(dir: String) {
    println("Enter git object hash:")
    val objectHash = readln()
    val internals = getInternals(dir, objectHash)

    val body = when (internals.type) {
        InternalsType.COMMIT -> CommitInfo(String(internals.body)).print()
        InternalsType.TREE -> TreeInfo.getTrees(internals.body).joinToString("\n")
        else -> String(internals.body)
    }

    println("*${internals.type}*")
    println(body)
}
